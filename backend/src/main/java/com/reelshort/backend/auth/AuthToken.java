package com.reelshort.backend.auth;

import java.util.UUID;

public record AuthToken(UUID userId, String username, String phoneE164, String token, String tokenType) {
}
