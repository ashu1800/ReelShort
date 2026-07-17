package com.reelshort.backend.order;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.reelshort.backend.admin.AdminPermissions;
import com.reelshort.backend.admin.RequireAdminPermission;
import com.reelshort.backend.withdrawal.TronClient;

class VipReceiptContractTests {

	@Test
	void incomingTransferCarriesCompleteReceiptEvidence() {
		Set<String> components = Arrays.stream(TronClient.IncomingTransfer.class.getRecordComponents())
				.map(RecordComponent::getName)
				.collect(Collectors.toSet());

		assertThat(components).contains("txHash", "amount", "recipient", "contract", "blockTimestamp",
				"confirmationCount", "successful");
	}

	@Test
	void vipResponseExposesAdminReceiptFields() {
		Set<String> components = Arrays.stream(VipOrderResponse.class.getRecordComponents())
				.map(RecordComponent::getName)
				.collect(Collectors.toSet());

		assertThat(components).contains("userId", "confirmedBy", "receivingAddress", "payableAmount",
				"confirmationCount", "paymentObservedAt");
	}

	@Test
	void manualOrderMutationsRequireWritePermissionAndTotp() throws Exception {
		RequireAdminPermission confirmPermission = AdminVipOrderController.class
				.getMethod("confirm", com.reelshort.backend.admin.CurrentAdmin.class, java.util.UUID.class,
						AdminVipOrderController.VipConfirmRequest.class, jakarta.servlet.http.HttpServletRequest.class)
				.getAnnotation(RequireAdminPermission.class);
		RequireAdminPermission rejectPermission = AdminVipOrderController.class
				.getMethod("reject", com.reelshort.backend.admin.CurrentAdmin.class, java.util.UUID.class,
						jakarta.servlet.http.HttpServletRequest.class)
				.getAnnotation(RequireAdminPermission.class);
		Set<String> requestFields = Arrays.stream(AdminVipOrderController.VipConfirmRequest.class.getRecordComponents())
				.map(RecordComponent::getName)
				.collect(Collectors.toSet());

		assertThat(confirmPermission.value()).isEqualTo("ORDER_WRITE");
		assertThat(rejectPermission.value()).isEqualTo("ORDER_WRITE");
		assertThat(AdminPermissions.ALL).contains("ORDER_WRITE");
		assertThat(requestFields).contains("txHash", "totpCode");
	}

	@Test
	void tronReceiptApisRequireSnapshotAddressAndContract() throws Exception {
		assertThat(com.reelshort.backend.withdrawal.TronClient.class.getMethod(
				"fetchIncomingUsdtTransferPage", String.class, String.class, int.class, String.class)).isNotNull();
		assertThat(com.reelshort.backend.withdrawal.TronClient.class.getMethod(
				"fetchIncomingUsdtTransfer", String.class, String.class, String.class)).isNotNull();
	}
}
