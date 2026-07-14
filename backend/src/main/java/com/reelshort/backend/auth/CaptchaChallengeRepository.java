package com.reelshort.backend.auth;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CaptchaChallengeRepository extends JpaRepository<CaptchaChallenge, UUID> {
}
