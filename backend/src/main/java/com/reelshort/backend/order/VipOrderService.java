package com.reelshort.backend.order;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.reelshort.backend.admin.AdminException;
import com.reelshort.backend.system.config.SystemConfigRegistry;
import com.reelshort.backend.system.config.SystemConfigService;
import com.reelshort.backend.user.UserAccount;
import com.reelshort.backend.user.UserAccountRepository;

@Service
public class VipOrderService {

	private static final int VIP_DURATION_DAYS = 30;
	private static final int SUFFIX_MAX = 99;

	private final VipOrderRepository vipOrderRepository;
	private final UserAccountRepository userAccountRepository;
	private final SystemConfigService systemConfigService;

	public VipOrderService(VipOrderRepository vipOrderRepository, UserAccountRepository userAccountRepository,
			SystemConfigService systemConfigService) {
		this.vipOrderRepository = vipOrderRepository;
		this.userAccountRepository = userAccountRepository;
		this.systemConfigService = systemConfigService;
	}

	@Transactional
	public VipOrder create(UUID userId) {
		BigDecimal price = systemConfigService.decimalValue(SystemConfigRegistry.VIP_PRICE_USDT);
		int timeoutMinutes = systemConfigService.intValue(SystemConfigRegistry.VIP_ORDER_TIMEOUT_MINUTES);
		String orderNo = "VIP" + System.currentTimeMillis() + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
		int suffix = allocateUniqueSuffix();
		return vipOrderRepository.save(VipOrder.create(userId, orderNo, price, suffix, timeoutMinutes));
	}

	private int allocateUniqueSuffix() {
		// M4: 使用悲观锁读取 PENDING 订单的 suffix，防止并发分配到同一 suffix
		List<Integer> usedSuffixes = vipOrderRepository.findPendingForUpdate().stream()
				.map(VipOrder::uniqueSuffix)
				.toList();
		for (int s = 1; s <= SUFFIX_MAX; s++) {
			if (!usedSuffixes.contains(s)) {
				return s;
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
		// M6: 使用悲观锁读取，防止过期与自动确认并发
		List<VipOrder> pending = vipOrderRepository.findPendingForUpdate();
		int expired = 0;
		for (VipOrder order : pending) {
			if (order.isExpired()) {
				order.expire();
				vipOrderRepository.save(order);
				expired++;
			}
		}
		return expired;
	}

	@Transactional
	public VipOrder confirm(UUID orderId, String txHash, String adminUsername) {
		// M6: 悲观锁防止与 autoConfirm/expire 并发
		VipOrder order = vipOrderRepository.findByIdForUpdate(orderId)
				.orElseThrow(() -> new AdminException(404, "VIP order not found"));
		order.confirm(txHash, adminUsername);
		grantVip(order.userId());
		return vipOrderRepository.save(order);
	}

	@Transactional
	public VipOrder autoConfirm(UUID orderId, String txHash) {
		// M6: 悲观锁防止与手动 confirm/reject/expire 并发
		VipOrder order = vipOrderRepository.findByIdForUpdate(orderId)
				.orElseThrow(() -> new AdminException(404, "VIP order not found"));
		if (!"PENDING".equals(order.status())) {
			return order;
		}
		order.confirm(txHash, "auto-confirm");
		grantVip(order.userId());
		return vipOrderRepository.save(order);
	}

	@Transactional
	public VipOrder reject(UUID orderId, String adminUsername) {
		VipOrder order = vipOrderRepository.findByIdForUpdate(orderId)
				.orElseThrow(() -> new AdminException(404, "VIP order not found"));
		order.reject(adminUsername);
		return vipOrderRepository.save(order);
	}

	private void grantVip(UUID userId) {
		UserAccount user = userAccountRepository.findById(userId)
				.orElseThrow(() -> new AdminException(404, "user not found"));
		OffsetDateTime base = user.vipUntil() != null && user.vipUntil().isAfter(OffsetDateTime.now())
				? user.vipUntil()
				: OffsetDateTime.now();
		user.grantVip(base.plusDays(VIP_DURATION_DAYS));
		userAccountRepository.save(user);
	}
}
