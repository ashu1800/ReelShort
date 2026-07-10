package com.reelshort.backend.auth;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class AccountManagerSmsCallbackClientTests {

	private static final String CALLBACK_URL = "http://account-manager.test/api/v1/supplier/sms";
	private static final String API_KEY = "test-key";
	private static final String API_SECRET = "test-secret";

	@Test
	void sendsSignedCallbackWithRawBodyHmac() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		AccountManagerSmsCallbackClient client = new AccountManagerSmsCallbackClient(
				builder.build(),
				new ObjectMapper(),
				new SmsCallbackProperties(true, CALLBACK_URL, API_KEY, API_SECRET, Duration.ofSeconds(5)));
		SmsCallbackMessage message = new SmsCallbackMessage(
				"shortlink-sms-1",
				"+12025550101",
				"Your ShortLink verification code is 123456.",
				OffsetDateTime.parse("2026-07-09T13:50:22Z"));

		server.expect(once(), requestTo(CALLBACK_URL))
				.andExpect(method(HttpMethod.POST))
				.andExpect(header("X-API-Key", API_KEY))
				.andExpect(request -> {
					String timestamp = request.getHeaders().getFirst("X-Timestamp");
					String nonce = request.getHeaders().getFirst("X-Nonce");
					String signature = request.getHeaders().getFirst("X-Signature");
					String body = ((MockClientHttpRequest) request).getBodyAsString();
					assertThat(timestamp).isNotBlank();
					assertThat(nonce).isNotBlank();
					assertThat(signature).isEqualTo(hmacHex(API_SECRET, timestamp + "." + nonce + "." + body));
					assertThat(body).contains("\"supplier_message_id\":\"shortlink-sms-1\"");
					assertThat(body).contains("\"phone\":\"+12025550101\"");
					assertThat(body).contains("\"received_at\":\"2026-07-09 13:50:22\"");
				})
				.andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON).body("{\"id\":\"sms-1\"}"));

		client.send(message);

		server.verify();
	}

	@Test
	void treatsDuplicateResponseAsSuccess() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		AccountManagerSmsCallbackClient client = new AccountManagerSmsCallbackClient(
				builder.build(),
				new ObjectMapper(),
				new SmsCallbackProperties(true, CALLBACK_URL, API_KEY, API_SECRET, Duration.ofSeconds(5)));

		server.expect(once(), requestTo(CALLBACK_URL))
				.andRespond(withSuccess("{\"duplicate\":true,\"id\":\"sms-1\"}", MediaType.APPLICATION_JSON));

		client.send(new SmsCallbackMessage("shortlink-sms-1", "+12025550101", "Your ShortLink verification code is 123456.",
				OffsetDateTime.parse("2026-07-09T13:50:22Z")));

		server.verify();
	}

	@Test
	void treatsNonDuplicateOkAndErrorStatusesAsFailure() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		AccountManagerSmsCallbackClient client = new AccountManagerSmsCallbackClient(
				builder.build(),
				new ObjectMapper(),
				new SmsCallbackProperties(true, CALLBACK_URL, API_KEY, API_SECRET, Duration.ofSeconds(5)));

		server.expect(once(), requestTo(CALLBACK_URL))
				.andRespond(withSuccess("{\"duplicate\":false}", MediaType.APPLICATION_JSON));

		assertThatThrownBy(() -> client.send(new SmsCallbackMessage("shortlink-sms-1", "+12025550101",
				"Your ShortLink verification code is 123456.", OffsetDateTime.parse("2026-07-09T13:50:22Z"))))
				.isInstanceOf(SmsCallbackException.class);
		server.verify();

		RestClient.Builder errorBuilder = RestClient.builder();
		MockRestServiceServer errorServer = MockRestServiceServer.bindTo(errorBuilder).build();
		AccountManagerSmsCallbackClient errorClient = new AccountManagerSmsCallbackClient(
				errorBuilder.build(),
				new ObjectMapper(),
				new SmsCallbackProperties(true, CALLBACK_URL, API_KEY, API_SECRET, Duration.ofSeconds(5)));
		errorServer.expect(once(), requestTo(CALLBACK_URL))
				.andRespond(withStatus(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON)
						.body("{\"detail\":\"invalid_signature\"}"));

		assertThatThrownBy(() -> errorClient.send(new SmsCallbackMessage("shortlink-sms-2", "+12025550102",
				"Your ShortLink verification code is 654321.", OffsetDateTime.parse("2026-07-09T13:50:22Z"))))
				.isInstanceOf(SmsCallbackException.class);
		errorServer.verify();
	}

	@Test
	void treatsAccountNotFoundAsSpecificCallbackResult() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		AccountManagerSmsCallbackClient client = new AccountManagerSmsCallbackClient(
				builder.build(),
				new ObjectMapper(),
				new SmsCallbackProperties(true, CALLBACK_URL, API_KEY, API_SECRET, Duration.ofSeconds(5)));

		server.expect(once(), requestTo(CALLBACK_URL))
				.andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON)
						.body("{\"detail\":\"account_not_found\"}"));

		assertThatThrownBy(() -> client.send(new SmsCallbackMessage("shortlink-sms-missing", "+12025550103",
				"Your ShortLink verification code is 123456.", OffsetDateTime.parse("2026-07-09T13:50:22Z"))))
				.isInstanceOf(SmsAccountNotFoundException.class);
		server.verify();
	}

	private static String hmacHex(String secret, String payload) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
			return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
		}
		catch (Exception exception) {
			throw new IllegalStateException(exception);
		}
	}
}
