package com.reelshort.backend.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.reelshort.backend.content.account.ReelshortAccountGateway;

/**
 * 账号实时状态：内容源返回的 JSON 必须能映射进 {@code LiveStatus}，
 * 否则后台只会一直显示"会员未知"。
 */
class FlaskContentProviderAccountTests {

	private static final String ACCOUNT_URL = "http://provider.local/api/v1/reelshort/account";

	@Test
	void liveStatusMapsAccountPayloadFromProvider() {
		RestClient.Builder builder = providerBuilder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		FlaskContentProvider provider = new FlaskContentProvider(builder, "http://provider.local", "secret-token");

		server.expect(requestTo(ACCOUNT_URL))
				.andExpect(method(HttpMethod.GET))
				.andExpect(header(FlaskContentProvider.INTERNAL_TOKEN_HEADER, "secret-token"))
				.andRespond(withSuccess("""
						{"authenticated":true,"configured":true,"emailMasked":"op***@example.com","loggedIn":true,
						 "sessionFingerprint":"2fd75321b43f",
						 "vip":{"known":true,"coins":120,"vipActive":true,"vipStatus":1,
						        "vipExpireAt":"2026-11-13T07:59:59Z","vipRenew":true,"adFreeExpireAt":null}}
						""", MediaType.APPLICATION_JSON));

		Optional<ReelshortAccountGateway.LiveStatus> status = provider.liveStatus();

		assertThat(status).isPresent();
		ReelshortAccountGateway.LiveStatus live = status.get();
		assertThat(live.authenticated()).isTrue();
		assertThat(live.emailMasked()).isEqualTo("op***@example.com");
		assertThat(live.vipKnown()).isTrue();
		assertThat(live.vipActive()).isTrue();
		assertThat(live.vipExpireAt()).isEqualTo("2026-11-13T07:59:59Z");
		assertThat(live.vipRenew()).isTrue();
		assertThat(live.coins()).isEqualTo(120);
	}

	/** 后台保存账号走 POST：嵌套的 account 对象必须能映射回来，否则"保存成功"会显示成空邮箱。 */
	@Test
	void updateAccountPushesCredentialsAndMapsTheNestedAccount() {
		RestClient.Builder builder = providerBuilder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		FlaskContentProvider provider = new FlaskContentProvider(builder, "http://provider.local", "secret-token");

		server.expect(requestTo("http://provider.local/api/v1/reelshort/credentials"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(header(FlaskContentProvider.INTERNAL_TOKEN_HEADER, "secret-token"))
				.andExpect(jsonPath("$.email").value("operator@example.com"))
				.andExpect(jsonPath("$.password").value("Secret123"))
				.andRespond(withSuccess("""
						{"authenticated":true,
						 "account":{"configured":true,"emailMasked":"op***@example.com","authenticated":true,
						            "loggedIn":true,"sessionFingerprint":"2fd75321b43f"}}
						""", MediaType.APPLICATION_JSON));

		ReelshortAccountGateway.ReelshortAccountPushResult result = provider.updateAccount("operator@example.com",
				"Secret123");

		assertThat(result.authenticated()).isTrue();
		assertThat(result.emailMasked()).isEqualTo("op***@example.com");
	}

	@Test
	void liveStatusIsEmptyWhenProviderIsUnavailable() {
		RestClient.Builder builder = providerBuilder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		FlaskContentProvider provider = new FlaskContentProvider(builder, "http://provider.local", "");

		server.expect(requestTo(ACCOUNT_URL)).andRespond(withServerError());

		assertThat(provider.liveStatus()).isEmpty();
	}

	/** 与生产一致：忽略内容源多给的字段（vipStatus、sessionFingerprint 等）。 */
	private static RestClient.Builder providerBuilder() {
		MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter(
				Jackson2ObjectMapperBuilder.json()
						.featuresToDisable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
						.build());
		RestClient.Builder builder = RestClient.builder();
		builder.messageConverters((converters) -> converters.add(0, converter));
		return builder;
	}
}
