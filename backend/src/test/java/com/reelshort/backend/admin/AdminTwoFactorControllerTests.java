package com.reelshort.backend.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.reelshort.backend.system.security.TotpService;

import jakarta.servlet.http.HttpServletRequest;

class AdminTwoFactorControllerTests {

	@Test
	void enableRejectsAlreadyEnabledAdminWithoutReplacingSecret() {
		AdminUserRepository admins = mock(AdminUserRepository.class);
		TotpService totp = mock(TotpService.class);
		AdminTwoFactorController controller = new AdminTwoFactorController(admins, totp);
		AdminUser admin = AdminUser.create("two-factor-admin", "hash", AdminUserStatus.ACTIVE);
		admin.enableTotp("ORIGINALSECRET");
		CurrentAdmin current = new CurrentAdmin(admin.id(), admin.username(), Set.of());
		when(admins.findById(admin.id())).thenReturn(Optional.of(admin));

		assertThatThrownBy(() -> controller.enable(current,
				new AdminTwoFactorController.TwoFactorEnableRequest("REPLACEMENTSECRET", "123456"),
				mock(HttpServletRequest.class)))
				.isInstanceOf(AdminException.class)
				.hasMessageContaining("already enabled");

		assertThat(admin.totpSecret()).isEqualTo("ORIGINALSECRET");
		verify(totp, never()).verify("REPLACEMENTSECRET", "123456");
		verify(admins, never()).save(admin);
	}
}
