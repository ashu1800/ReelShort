package com.reelshort.backend;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import com.reelshort.backend.admin.AdminProperties;
import com.reelshort.backend.payment.PaymentProperties;
import com.reelshort.backend.security.SecurityConfigurationValidator;

class SecurityConfigurationTests {
    private static final String VALID_HASH = "$2b$12$abcdefghijklmnopqrstuu5w6Qx3q9Q0v9nQ9mQ9mQ9mQ9mQ9mQ9m";
    private static final String VALID_SECRET = "A9f3xK2mP7qR4tY8vN6cL1sD5hJ0wZ3e";

    private SecurityConfigurationValidator validator(String hash, String secret) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        return new SecurityConfigurationValidator(environment,
                new AdminProperties("admin", hash, null), new PaymentProperties(secret));
    }

    @Test
    void rejectsMissingAdminHash() {
        assertThatThrownBy(() -> validator("", VALID_SECRET)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsPlaintextAdminHash() {
        assertThatThrownBy(() -> validator("Admin123", VALID_SECRET)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsInvalidBcryptAdminHash() {
        assertThatThrownBy(() -> validator("$2b$99$invalid", VALID_SECRET))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsKnownPasswordWithDifferentSalt() {
        assertThatThrownBy(() -> validator(
                "$2a$10$uoIrlmdsjZR18uxYrLXuI./fVACQj7UFgMejtYSDOvloBKyA1yfC6", VALID_SECRET))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void acceptsValidAdminHash() {
        assertThatCode(() -> validator(VALID_HASH, VALID_SECRET)).doesNotThrowAnyException();
    }

    @Test
    void rejectsKnownWeakPaymentSecrets() {
        String[] weakSecrets = { "", "dev-payment-callback-secret", "change-me-payment-callback-secret",
                "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", "12345678123456781234567812345678" };
        for (String secret : weakSecrets) {
            assertThatThrownBy(() -> validator(VALID_HASH, secret)).isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void acceptsStrongPaymentSecret() {
        assertThatCode(() -> validator(VALID_HASH, VALID_SECRET)).doesNotThrowAnyException();
    }

    @Test
    void appDevAndTestProfilesAllowDevelopmentValues() {
        for (String profile : new String[] { "app-dev", "test" }) {
            MockEnvironment environment = new MockEnvironment().withProperty("spring.profiles.active", profile);
            assertThatCode(() -> new SecurityConfigurationValidator(environment,
                    new AdminProperties("admin", "Admin123", null),
                    new PaymentProperties("dev-payment-callback-secret"))).doesNotThrowAnyException();
        }
    }

    @Test
    void mixedProfilesDoNotAllowDevelopmentValues() {
        MockEnvironment environment = new MockEnvironment().withProperty("spring.profiles.active", "test,prod");
        assertThatThrownBy(() -> new SecurityConfigurationValidator(environment,
                new AdminProperties("admin", "Admin123", null),
                new PaymentProperties("dev-payment-callback-secret")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsValidShapeWithInvalidCost() {
        assertThatThrownBy(() -> validator(
                "$2b$99$abcdefghijklmnopqrstuu5w6Qx3q9Q0v9nQ9mQ9mQ9mQ9mQ9mQ9m", VALID_SECRET))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsCostAboveConfiguredMaximumBeforePasswordMatching() {
        assertThatThrownBy(() -> validator(
                "$2b$17$abcdefghijklmnopqrstuu5w6Qx3q9Q0v9nQ9mQ9mQ9mQ9mQ9mQ9m", VALID_SECRET))
                .isInstanceOf(IllegalStateException.class);
    }
}
