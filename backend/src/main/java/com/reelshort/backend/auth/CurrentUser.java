package com.reelshort.backend.auth;

import java.util.UUID;

public record CurrentUser(UUID userId, String username) {
}
