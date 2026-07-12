package com.reelshort.backend.security;

import java.util.Arrays;
import java.util.regex.Pattern;

import org.springframework.core.env.Environment;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import com.reelshort.backend.admin.AdminProperties;
import com.reelshort.backend.payment.PaymentProperties;

@Component
public final class SecurityConfigurationValidator {
    private static final Pattern BCRYPT = Pattern.compile("^\\$2[aby]\\$(\\d{2})\\$[./A-Za-z0-9]{53}$");
    private static final String PUBLIC_HASH = "$2b$12$Z6hLISkw3ha14uQQ8PKANun8vjeUlMA4U8S841Sz5vfrhmQRhr6wm";
    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();

    public SecurityConfigurationValidator(Environment environment, AdminProperties adminProperties,
            PaymentProperties paymentProperties) {
        String[] activeProfiles = environment.getActiveProfiles();
        if (activeProfiles.length > 0 && Arrays.stream(activeProfiles)
                .allMatch(profile -> profile.equals("app-dev") || profile.equals("test"))) {
            return;
        }

        String passwordHash = adminProperties.passwordHash();
        var matcher = passwordHash == null ? null : BCRYPT.matcher(passwordHash);
        if (matcher == null || !matcher.matches() || passwordHash.equals(PUBLIC_HASH)) {
            throw new IllegalStateException("REELSHORT_ADMIN_PASSWORD_HASH must be a valid BCrypt hash");
        }
        int cost = Integer.parseInt(matcher.group(1));
        if (cost < 10 || cost > 16) {
            throw new IllegalStateException("REELSHORT_ADMIN_PASSWORD_HASH must be a valid BCrypt hash");
        }
        try {
            if (PASSWORD_ENCODER.matches("Admin123", passwordHash)) {
                throw new IllegalStateException("REELSHORT_ADMIN_PASSWORD_HASH must not use the public password");
            }
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("REELSHORT_ADMIN_PASSWORD_HASH must be a valid BCrypt hash", exception);
        }

        String callbackSecret = paymentProperties.callbackSecret();
        if (callbackSecret == null || callbackSecret.length() < 32
                || callbackSecret.equals("dev-payment-callback-secret")
                || callbackSecret.equals("change-me-payment-callback-secret")
                || callbackSecret.chars().distinct().count() < 8
                || isLowEntropy(callbackSecret)) {
            throw new IllegalStateException("REELSHORT_PAYMENT_CALLBACK_SECRET must be strong");
        }
    }

    private static boolean isLowEntropy(String value) {
        for (int period = 1; period <= 8; period++) {
            if (value.length() % period != 0) {
                continue;
            }
            boolean repeats = true;
            for (int index = period; index < value.length(); index++) {
                if (value.charAt(index) != value.charAt(index % period)) {
                    repeats = false;
                    break;
                }
            }
            if (repeats) {
                return true;
            }
        }
        return false;
    }
}
