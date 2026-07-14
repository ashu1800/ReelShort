package com.reelshort.backend.auth;

public record CaptchaResponse(String captchaId, String imageBase64) {
}
