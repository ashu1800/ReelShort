package com.reelshort.backend.wallet;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WalletUnbindRequest(@NotBlank @Size(max = 128) String password) {
}
