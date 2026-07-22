package com.reelshort.backend.withdrawal;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

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
				"manualReview", "actualFeeAmount", "actualFeeAsset");
	}

	@Test
	void withdrawalResponseOnlyExposesActualFeeForConfirmedAttempt() {
		WithdrawalRequest request = WithdrawalRequest.create(UUID.randomUUID(), 3600, 0,
				new WithdrawalConversion(new BigDecimal("0.14"), new BigDecimal("10"), 0),
				"ERC20", "0x1111111111111111111111111111111111111111");
		PreparedPayoutTransaction signed = new PreparedPayoutTransaction("ERC20",
				"0x2222222222222222222222222222222222222222",
				"0xdAC17F958D2ee523a2206206994597C13D831ec7", 1L, BigInteger.ONE,
				"0xsigned", "0x" + "a".repeat(64));
		WithdrawalPayoutAttempt attempt = WithdrawalPayoutAttempt.prepared(request.id(), 1,
				request.walletAddress(), request.usdtAmount(), signed, "admin");
		attempt.markBroadcasted();
		attempt.recordActualFee(new BigDecimal("0.000021"), "ETH");

		WithdrawalResponse pending = WithdrawalResponse.from(request, "account", attempt);
		assertThat(pending.actualFeeAmount()).isNull();
		assertThat(pending.actualFeeAsset()).isNull();

		attempt.markConfirmed(2);
		WithdrawalResponse confirmed = WithdrawalResponse.from(request, "account", attempt);
		assertThat(confirmed.actualFeeAmount()).isEqualTo("0.000021");
		assertThat(confirmed.actualFeeAsset()).isEqualTo("ETH");
	}

	@Test
	void previewResponseContainsPublicAddressesButNoDerivedBalances() {
		List<String> responseFields = Arrays.stream(BatchWithdrawalPreviewResponse.class.getRecordComponents())
				.map(RecordComponent::getName)
				.toList();
		List<String> itemFields = Arrays.stream(BatchWithdrawalPreviewResponse.PreviewItem.class.getRecordComponents())
				.map(RecordComponent::getName)
				.toList();

		assertThat(responseFields).contains("tronHotWalletAddress", "ethHotWalletAddress", "totalUsdt", "items",
				"feeEstimates")
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
		assertThat(itemFields).contains("payoutStatus", "confirmationCount", "failureReason", "manualReview",
				"actualFeeAmount", "actualFeeAsset");
	}
}
