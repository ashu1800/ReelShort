package com.reelshort.backend.content.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.PlatformTransactionManager;

import com.reelshort.backend.system.config.SystemConfig;
import com.reelshort.backend.system.config.SystemConfigRepository;

/** 账号视图把"库里存的账号"和"内容源报回的会员状态"合成一张卡片。 */
@ExtendWith(MockitoExtension.class)
class ReelshortAccountServiceTests {

	private SystemConfigRepository repository;

	private ReelshortAccountGateway gateway;

	private ObjectProvider<ReelshortAccountGateway> gatewayProvider;

	private ReelshortAccountService service;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		repository = mock(SystemConfigRepository.class);
		gateway = mock(ReelshortAccountGateway.class);
		gatewayProvider = mock(ObjectProvider.class);
		lenient().when(gatewayProvider.getIfAvailable()).thenReturn(gateway);
		service = new ReelshortAccountService(repository, gatewayProvider, mock(PlatformTransactionManager.class));
	}

	@Test
	void emptyAccountReportsNothingConfigured() {
		when(repository.findById(anyString())).thenReturn(Optional.empty());

		ReelshortAccountView view = service.view();

		assertThat(view.configured()).isFalse();
		assertThat(view.live()).isFalse();
		assertThat(view.vipActive()).isNull();
	}

	@Test
	void viewMergesVipStatusReportedByTheProvider() {
		givenStoredAccount("operator@example.com", "Secret123");
		when(gateway.liveStatus()).thenReturn(Optional.of(
				new ReelshortAccountGateway.LiveStatus(true, "op***@example.com", true, true, "2026-11-13T07:59:59Z", true, 120)));

		ReelshortAccountView view = service.view();

		assertThat(view.configured()).isTrue();
		assertThat(view.live()).isTrue();
		assertThat(view.authenticated()).isTrue();
		assertThat(view.vipKnown()).isTrue();
		assertThat(view.vipActive()).isTrue();
		assertThat(view.vipExpireAt()).isEqualTo("2026-11-13T07:59:59Z");
		assertThat(view.vipRenew()).isTrue();
		assertThat(view.coins()).isEqualTo(120);
		assertThat(view.emailMasked()).isEqualTo("op***@example.com");
	}

	@Test
	void unreachableProviderKeepsVipUnknownInsteadOfGuessing() {
		givenStoredAccount("operator@example.com", "Secret123");
		when(gateway.liveStatus()).thenReturn(Optional.empty());

		ReelshortAccountView view = service.view();

		assertThat(view.configured()).isTrue();
		assertThat(view.live()).isFalse();
		assertThat(view.vipKnown()).isFalse();
		assertThat(view.vipActive()).isNull();
		assertThat(view.vipExpireAt()).isNull();
	}

	private void givenStoredAccount(String email, String password) {
		when(repository.findById(ReelshortAccountService.EMAIL_KEY))
				.thenReturn(Optional.of(SystemConfig.create(ReelshortAccountService.EMAIL_KEY, email, "email")));
		when(repository.findById(ReelshortAccountService.PASSWORD_KEY))
				.thenReturn(Optional.of(SystemConfig.create(ReelshortAccountService.PASSWORD_KEY, password, "password")));
	}
}
