package com.reelshort.backend.admin;

public record AdminAuthTokenResponse(String username, String token, String tokenType) {
}
