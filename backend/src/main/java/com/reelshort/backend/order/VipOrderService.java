package com.reelshort.backend.order;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.reelshort.backend.admin.AdminException;
import com.reelshort.backend.admin.AdminAuditService;
import com.reelshort.backend.system.config.SystemConfigRegistry;
import com.reelshort.backend.system.config.SystemConfigService;
import com.reelshort.backend.user.UserAccount;
import com.reelshort.backend.user.UserAccountRepository;
import com.reelshort.backend.user.UserStatus;
import com.reelshort.backend.withdrawal.TronAddress;
import com.reelshort.backend.withdrawal.TronClient;
import com.reelshort.backend.withdrawal.TronClient.IncomingTransfer;
import com.reelshort.backend.withdrawal.TronProperties;

@Service
public class VipOrderService {

	private static final int SUFFIX_MAX = 99;
	private static final int CREATE_RETRIES = 3;

	private final VipOrderRepository vipOrderRepository;
	private final UserAccountRepository userAccountRepository;
	private final SystemConfigService systemConfigService;
	private final TronProperties tronProperties;
	private final TronClient tronClient;
	private final TransactionTemplate transactionTemplate;
	private final Duration confirmationGrace;
	private final AdminAuditService adminAuditService;
	private final VipEntitlementService entitlementService;

	@Autowired
	public VipOrderService(VipOrderRepository vipOrderRepository, UserAccountRepository userAccountRepository,
			SystemConfigService systemConfigService, TronProperties tronProperties, TronClient tronClient,
			PlatformTransactionManager transactionManager, AdminAuditService adminAuditService,
			@Value("${reelshort.vip.confirmation-grace:10m}") Duration confirmationGrace,
			VipEntitlementService entitlementService) {
		this.vipOrderRepository = vipOrderRepository;
		this.userAccountRepository = userAccountRepository;
		this.systemConfigService = systemConfigService;
		this.tronProperties = tronProperties;
		this.tronClient = tronClient;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
		this.confirmationGrace = confirmationGrace;
		this.adminAuditService = adminAuditService;
		this.entitlementService = entitlementService;
	}

	VipOrderService(VipOrderRepository vipOrderRepository, UserAccountRepository userAccountRepository,
			SystemConfigService systemConfigService) {
		this.vipOrderRepository = vipOrderRepository;
		this.userAccountRepository = userAccountRepository;
		this.systemConfigService = systemConfigService;
		this.tronProperties = new TronProperties();
		this.tronClient = null;
		this.transactionTemplate = null;
		this.confirmationGrace = Duration.ofMinutes(10);
		this.adminAuditService = null;
		this.entitlementService = new VipEntitlementService(userAccountRepository);
	}

	public VipOrder create(UUID userId) {
		if (transactionTemplate == null) {
			return createOnce(userId);
		}
		DataIntegrityViolationException lastConflict = null;
		for (int attempt = 0; attempt < CREATE_RETRIES; attempt++) {
			try {
				return transactionTemplate.execute(status -> createOnce(userId));
			}
			catch (DataIntegrityViolationException conflict) {
				lastConflict = conflict;
			}
		}
		throw new AdminException(409, lastConflict == null
				? "VIP order allocation conflict"
				: "VIP order allocation conflict, retry later");
	}

	private VipOrder createOnce(UUID userId) {
		vipOrderRepository.lockAllocation();
		List<VipOrder> pending = vipOrderRepository.findPendingForUpdate();
		for (VipOrder order : pending) {
			if (order.isExpired(confirmationGrace)) {
				order.expire();
				vipOrderRepository.save(order);
				recordSystemAudit("VIP_ORDER_EXPIRED", order, "status=EXPIRED");
			}
		}
		UserAccount user = userAccountRepository.findByIdForUpdate(userId)
				.orElseThrow(() -> new AdminException(404, "user not found"));
		if (user.status() != UserStatus.ACTIVE) {
			throw new AdminException(403, "user disabled");
		}
		VipOrder existing = pending.stream()
				.filter(order -> "PENDING".equals(order.status()) && order.userId().equals(userId))
				.findFirst()
				.orElse(null);
		if (existing != null) {
			return existing;
		}

		BigDecimal price = systemConfigService.decimalValue(SystemConfigRegistry.VIP_PRICE_USDT);
		if (price.scale() > 6) {
			throw new AdminException(400, "VIP price precision must not exceed 6 decimals");
		}
		price = price.setScale(6, RoundingMode.UNNECESSARY);
		String collectionAddress = systemConfigService.stringValue(SystemConfigRegistry.VIP_COLLECTION_ADDRESS);
		if (!TronAddress.isValid(collectionAddress)) {
			throw new AdminException(409, "VIP collection address is missing or invalid");
		}
		String tokenContract = tronProperties.getUsdtContract();
		if (!TronAddress.isValid(tokenContract)) {
			throw new AdminException(500, "TRC20 USDT contract is invalid");
		}
		int timeoutMinutes = systemConfigService.intValue(SystemConfigRegistry.VIP_ORDER_TIMEOUT_MINUTES);
		int suffix = allocateUniqueSuffix(price, pending);
		String orderNo = "VIP" + System.currentTimeMillis()
				+ UUID.randomUUID().toString().replace("-", "").substring(0, 8);
		return vipOrderRepository.save(VipOrder.create(userId, orderNo, price, suffix, timeoutMinutes,
				collectionAddress, tokenContract));
	}

	private int allocateUniqueSuffix(BigDecimal price, List<VipOrder> pending) {
		for (int suffix = 1; suffix <= SUFFIX_MAX; suffix++) {
			BigDecimal payable = price.add(BigDecimal.valueOf(suffix, 2)).setScale(6, RoundingMode.UNNECESSARY);
			boolean used = pending.stream()
					.filter(order -> "PENDING".equals(order.status()))
					.anyMatch(order -> order.payableAmount().compareTo(payable) == 0);
			if (!used) {
				return suffix;
			}
		}
		throw new AdminException(503, "too many pending VIP orders, try again later");
	}

	@Transactional(readOnly = true)
	public List<VipOrder> userOrders(UUID userId) {
		return vipOrderRepository.findByUserIdOrderByCreatedAtDesc(userId);
	}

	@Transactional(readOnly = true)
	public List<VipOrder> allOrders() {
		return vipOrderRepository.findAllByOrderByCreatedAtDesc();
	}

	@Transactional(readOnly = true)
	public List<VipOrder> pendingOrders() {
		return vipOrderRepository.findByStatusOrderByCreatedAtAsc("PENDING");
	}

	@Transactional
	public int expireOverdueOrders() {
		vipOrderRepository.lockAllocation();
		List<VipOrder> pending = vipOrderRepository.findPendingForUpdate();
		int expired = 0;
		for (VipOrder order : pending) {
			if (order.isExpired(confirmationGrace)) {
				order.expire();
				vipOrderRepository.save(order);
				recordSystemAudit("VIP_ORDER_EXPIRED", order, "status=EXPIRED");
				expired++;
			}
		}
		return expired;
	}

	public VipOrder manualConfirm(UUID orderId, String txHash, String adminUsername) {
		if (tronClient == null) {
			throw new AdminException(503, "chain verification unavailable");
		}
		VipOrder snapshot = vipOrderRepository.findById(orderId)
				.orElseThrow(() -> new AdminException(404, "VIP order not found"));
		IncomingTransfer transfer = tronClient.fetchIncomingUsdtTransfer(txHash,
				snapshot.receivingWalletAddress(), snapshot.tokenContractAddress(), snapshot.payableAmount());
		IncomingTransfer verified = tronClient.verifyIncomingTransfer(transfer);
		if (transactionTemplate == null) {
			return confirmTransfer(orderId, verified, adminUsername);
		}
		return transactionTemplate.execute(status -> confirmTransfer(orderId, verified, adminUsername));
	}

	@Transactional
	public VipOrder autoConfirm(UUID orderId, IncomingTransfer transfer) {
		return confirmTransfer(orderId, transfer, "auto-confirm");
	}

	private VipOrder confirmTransfer(UUID orderId, IncomingTransfer transfer, String confirmedBy) {
		VipOrder order = vipOrderRepository.findByIdForUpdate(orderId)
				.orElseThrow(() -> new AdminException(404, "VIP order not found"));
		if (!"PENDING".equals(order.status())) {
			if (order.txHash() != null && order.txHash().equalsIgnoreCase(transfer.txHash())) {
				return order;
			}
			throw new AdminException(409, "VIP order is not pending");
		}
		if (vipOrderRepository.existsByTxHash(transfer.txHash())) {
			throw new AdminException(409, "transaction has already been consumed");
		}
		if (!VipPaymentMatcher.matches(order, transfer, tronProperties.getRequiredConfirmations())) {
			throw new AdminException(409, "transaction does not match this VIP order");
		}
		order.confirm(transfer.txHash(), confirmedBy, transfer.blockTimestamp(), transfer.confirmationCount());
		entitlementService.grantMonth(order.userId());
		VipOrder saved = vipOrderRepository.save(order);
		if ("auto-confirm".equals(confirmedBy)) {
			recordSystemAudit("VIP_ORDER_AUTO_CONFIRMED", saved,
					"status=CONFIRMED, txHash=" + saved.txHash());
		}
		return saved;
	}

	@Transactional
	public VipOrder reject(UUID orderId, String adminUsername) {
		VipOrder order = vipOrderRepository.findByIdForUpdate(orderId)
				.orElseThrow(() -> new AdminException(404, "VIP order not found"));
		order.reject(adminUsername);
		return vipOrderRepository.save(order);
	}

	private void recordSystemAudit(String action, VipOrder order, String summary) {
		if (adminAuditService != null) {
			adminAuditService.record("system", action, "VIP_ORDER", order.id(), summary);
		}
	}

	public void recordTerminalReceiptFailure(UUID orderId, String txHash) {
		if (adminAuditService != null) {
			adminAuditService.recordIndependent("system", "VIP_RECEIPT_TERMINAL_FAILURE", "VIP_ORDER", orderId,
					"status=CHAIN_FAILED, txHash=" + txHash);
		}
	}
}
