package com.reelshort.backend.wallet;

import jakarta.validation.constraints.NotBlank;

public record WalletUnbindRequest(@NotBlank String verificationCode) {
}
