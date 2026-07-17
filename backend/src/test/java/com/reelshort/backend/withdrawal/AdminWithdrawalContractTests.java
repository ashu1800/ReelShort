package com.reelshort.backend.withdrawal;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

class AdminWithdrawalControllerTests {

	@Test
	void batchPreviewRequestContainsOnlyWithdrawalIds() {
		List<String> fields = Arrays.stream(BatchWithdrawalPreviewRequest.class.getRecordComponents())
				.map(RecordComponent::getName)
				.toList();

		assertThat(fields).containsExactly("withdrawalIds");
	}

	@Test
	void adminResponsesNeverExposeSigningMaterial() {
		List<String> responseFields = Arrays.stream(WithdrawalResponse.class.getRecordComponents())
				.map(RecordComponent::getName)
				.toList();

		assertThat(responseFields)
				.doesNotContain("privateKey", "tronPrivateKey", "ethPrivateKey", "signedRawTransaction",
						"signingOwner");
	}

	@Test
	void withdrawalResponseExposesLatestPayoutState() {
		List<String> fields = Arrays.stream(WithdrawalResponse.class.getRecordComponents())
				.map(RecordComponent::getName)
				.toList();

		assertThat(fields).contains("payoutStatus", "payoutTxHash", "confirmationCount", "failureReason",
				"manualReview");
	}

	@Test
	void previewResponseContainsPublicAddressesButNoDerivedBalances() {
		List<String> responseFields = Arrays.stream(BatchWithdrawalPreviewResponse.class.getRecordComponents())
				.map(RecordComponent::getName)
				.toList();
		List<String> itemFields = Arrays.stream(BatchWithdrawalPreviewResponse.PreviewItem.class.getRecordComponents())
				.map(RecordComponent::getName)
				.toList();

		assertThat(responseFields).contains("tronHotWalletAddress", "ethHotWalletAddress", "totalUsdt", "items")
				.doesNotContain("tronUsdtBalance", "tronTrxBalance", "ethUsdtBalance", "ethEthBalance");
		assertThat(itemFields).contains("status");
	}

	@Test
	void batchResponseReportsPartialFailuresAndAttemptStatePerItem() {
		List<String> responseFields = Arrays.stream(BatchWithdrawalResponse.class.getRecordComponents())
				.map(RecordComponent::getName)
				.toList();
		List<String> itemFields = Arrays.stream(BatchWithdrawalResponse.ItemResult.class.getRecordComponents())
				.map(RecordComponent::getName)
				.toList();

		assertThat(responseFields).contains("succeeded", "failed", "items");
		assertThat(itemFields).contains("payoutStatus", "confirmationCount", "failureReason", "manualReview");
	}
}
