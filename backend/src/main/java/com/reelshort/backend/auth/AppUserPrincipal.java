package com.reelshort.backend.auth;

import java.util.UUID;

import com.reelshort.backend.user.UserStatus;

public record AppUserPrincipal(UUID userId, String username, UserStatus status) {
}
