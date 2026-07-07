package com.reelshort.backend.wallet;

import com.reelshort.backend.auth.SmsVerificationPurpose;

import jakarta.validation.constraints.NotNull;

public record WalletVerificationRequest(@NotNull SmsVerificationPurpose purpose) {
}
