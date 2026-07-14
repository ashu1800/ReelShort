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
		String orderNo = "VIP" + System.currentTimeMillis() + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
		return vipOrderRepository.save(VipOrder.create(userId, orderNo, price));
	}

	@Transactional(readOnly = true)
	public List<VipOrder> userOrders(UUID userId) {
		return vipOrderRepository.findByUserIdOrderByCreatedAtDesc(userId);
	}

	@Transactional(readOnly = true)
	public List<VipOrder> allOrders() {
		return vipOrderRepository.findAllByOrderByCreatedAtDesc();
	}

	@Transactional
	public VipOrder confirm(UUID orderId, String txHash, String adminUsername) {
		VipOrder order = vipOrderRepository.findById(orderId)
				.orElseThrow(() -> new AdminException(404, "VIP order not found"));
		order.confirm(txHash, adminUsername);
		UserAccount user = userAccountRepository.findById(order.userId())
				.orElseThrow(() -> new AdminException(404, "user not found"));
		OffsetDateTime base = user.vipUntil() != null && user.vipUntil().isAfter(OffsetDateTime.now())
				? user.vipUntil()
				: OffsetDateTime.now();
		user.grantVip(base.plusDays(VIP_DURATION_DAYS));
		userAccountRepository.save(user);
		return vipOrderRepository.save(order);
	}

	@Transactional
	public VipOrder reject(UUID orderId, String adminUsername) {
		VipOrder order = vipOrderRepository.findById(orderId)
				.orElseThrow(() -> new AdminException(404, "VIP order not found"));
		order.reject(adminUsername);
		return vipOrderRepository.save(order);
	}
}
