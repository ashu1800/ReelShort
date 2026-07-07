package com.reelshort.backend.withdrawal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WithdrawalRejectRequest(@NotBlank @Size(max = 255) String reason) {
}
