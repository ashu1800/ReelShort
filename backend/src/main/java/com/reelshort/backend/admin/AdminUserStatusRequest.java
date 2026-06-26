package com.reelshort.backend.admin;

import com.reelshort.backend.user.UserStatus;

import jakarta.validation.constraints.NotNull;

public record AdminUserStatusRequest(@NotNull UserStatus status) {
}
