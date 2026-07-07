package com.reelshort.backend.withdrawal;

import jakarta.validation.constraints.Min;

public record WithdrawalCreateRequest(@Min(1) int pointAmount) {
}
