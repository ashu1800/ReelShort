package com.reelshort.backend.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import com.reelshort.backend.admin.AdminException;
import com.reelshort.backend.system.config.SystemConfigRegistry;
import com.reelshort.backend.system.config.SystemConfigService;
import com.reelshort.backend.user.UserAccount;
import com.reelshort.backend.user.UserAccountRepository;
import com.reelshort.backend.user.UserStatus;
import com.reelshort.backend.withdrawal.TronClient;

class VipOrderServiceTests {

	private final VipOrderRepository orders = mock(VipOrderRepository.class);
	private final UserAccountRepository users = mock(UserAccountRepository.class);
	private final SystemConfigService configs = mock(SystemConfigService.class);
	private VipOrderService service;
	private UserAccount user;

	@BeforeEach
	void setUp() {
		service = new VipOrderService(orders, users, configs);
		user = UserAccount.create("vip-create-user", "hash", UserStatus.ACTIVE);
		when(users.findByIdForUpdate(user.id())).thenReturn(Optional.of(user));
		when(configs.decimalValue(SystemConfigRegistry.VIP_PRICE_USDT)).thenReturn(new BigDecimal("15"));
		when(configs.intValue(SystemConfigRegistry.VIP_ORDER_TIMEOUT_MINUTES)).thenReturn(20);
		when(configs.stringValue(SystemConfigRegistry.VIP_COLLECTION_ADDRESS))
				.thenReturn("TQn9Y2khEsLJW1ChVWFMSMeRDow5KcbLSE");
		when(orders.findPendingForUpdate()).thenReturn(List.of());
		when(orders.save(any(VipOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
	}

	@Test
	void createReturnsExistingUnexpiredPendingOrderForSameUser() {
		VipOrder existing = VipOrder.create(user.id(), "VIP-existing", new BigDecimal("15"), 1, 20);
		when(orders.findPendingForUpdate()).thenReturn(List.of(existing));

		VipOrder result = service.create(user.id());

		assertThat(result.id()).isEqualTo(existing.id());
	}

	@Test
	void createRejectsMissingCollectionAddress() {
		when(orders.findByUserIdOrderByCreatedAtDesc(user.id())).thenReturn(List.of());
		when(configs.stringValue(SystemConfigRegistry.VIP_COLLECTION_ADDRESS)).thenReturn("");

		assertThatThrownBy(() -> service.create(user.id()))
				.isInstanceOf(AdminException.class)
				.hasMessageContaining("collection address");
	}

	@Test
	void createRejectsInvalidCollectionAddress() {
		when(orders.findByUserIdOrderByCreatedAtDesc(user.id())).thenReturn(List.of());
		when(configs.stringValue(SystemConfigRegistry.VIP_COLLECTION_ADDRESS)).thenReturn("T-not-base58check");

		assertThatThrownBy(() -> service.create(user.id()))
				.isInstanceOf(AdminException.class)
				.hasMessageContaining("collection address");
	}

	@Test
	void createRejectsPriceWithMoreThanSixDecimals() {
		when(orders.findByUserIdOrderByCreatedAtDesc(user.id())).thenReturn(List.of());
		when(configs.decimalValue(SystemConfigRegistry.VIP_PRICE_USDT)).thenReturn(new BigDecimal("15.0000001"));

		assertThatThrownBy(() -> service.create(user.id()))
				.isInstanceOf(AdminException.class)
				.hasMessageContaining("precision");
	}

	@Test
	void vipGrantUsesDatabaseUserLock() throws Exception {
		assertThat(UserAccountRepository.class.getMethod("findByIdForUpdate", UUID.class)).isNotNull();
	}

	@Test
	void allocationSkipsPayableAmountUsedByOrderCreatedAtAnotherBasePrice() {
		VipOrder oldPriceOrder = VipOrder.create(UUID.randomUUID(), "VIP-old-price", new BigDecimal("14.99"),
				2, 20, "TQn9Y2khEsLJW1ChVWFMSMeRDow5KcbLSE", "TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t");
		when(orders.findPendingForUpdate()).thenReturn(List.of(oldPriceOrder));

		VipOrder created = service.create(user.id());

		assertThat(created.payableAmount()).isEqualByComparingTo("15.02");
		assertThat(created.uniqueSuffix()).isEqualTo(2);
	}

	@Test
	void historicalTransactionCannotConfirmAnotherOrder() {
		VipOrder order = VipOrder.create(user.id(), "VIP-replay", new BigDecimal("15"), 1, 20,
				"TQn9Y2khEsLJW1ChVWFMSMeRDow5KcbLSE", "TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t");
		TronClient.IncomingTransfer transfer = new TronClient.IncomingTransfer("c".repeat(64), order.payableAmount(),
				order.receivingWalletAddress(), order.tokenContractAddress(), order.createdAt().plusSeconds(5), 20, true);
		when(orders.findByIdForUpdate(order.id())).thenReturn(Optional.of(order));
		when(orders.existsByTxHash(transfer.txHash())).thenReturn(true);

		assertThatThrownBy(() -> service.autoConfirm(order.id(), transfer))
				.isInstanceOf(AdminException.class)
				.hasMessageContaining("already been consumed");
	}

	@Test
	void creationLocksOrdersBeforeUserToMatchConfirmationLockOrder() {
		service.create(user.id());

		InOrder locks = inOrder(orders, users);
		locks.verify(orders).lockAllocation();
		locks.verify(orders).findPendingForUpdate();
		locks.verify(users).findByIdForUpdate(user.id());
	}

	@Test
	void ninetyNineActivePayableSlotsAreExhaustedButOneUserStillConsumesOnlyOne() {
		List<VipOrder> occupied = IntStream.rangeClosed(1, 99)
				.mapToObj(suffix -> VipOrder.create(UUID.randomUUID(), "VIP-slot-" + suffix,
						new BigDecimal("15"), suffix, 20, "TQn9Y2khEsLJW1ChVWFMSMeRDow5KcbLSE",
						"TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t"))
				.toList();
		when(orders.findPendingForUpdate()).thenReturn(occupied);

		assertThatThrownBy(() -> service.create(user.id()))
				.isInstanceOf(AdminException.class)
				.hasMessageContaining("too many pending VIP orders");

		VipOrder own = VipOrder.create(user.id(), "VIP-own", new BigDecimal("15"), 1, 20);
		when(orders.findPendingForUpdate()).thenReturn(List.of(own));
		assertThat(IntStream.range(0, 100).mapToObj(index -> service.create(user.id())).distinct())
				.containsExactly(own);
	}
}
