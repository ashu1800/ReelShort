package com.reelshort.backend.admin;

import java.time.OffsetDateTime;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

public record AdminUserVipRequest(@NotNull @Future OffsetDateTime vipUntil) {
}
