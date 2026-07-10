package com.reelshort.backend.auth;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class AccountManagerSmsCallbackClient implements SmsCallbackClient {

	private static final DateTimeFormatter RECEIVED_AT_FORMAT =
			DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
	private static final String USER_AGENT = "ShortLinkBackend/1.0";

	private final RestClient restClient;
	private final ObjectMapper objectMapper;
	private final SmsCallbackProperties properties;

	@Autowired
	public AccountManagerSmsCallbackClient(RestClient.Builder restClientBuilder,
			ObjectMapper objectMapper, SmsCallbackProperties properties) {
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(properties.timeout());
		requestFactory.setReadTimeout(properties.timeout());
		this.restClient = restClientBuilder.requestFactory(requestFactory).build();
		this.objectMapper = objectMapper;
		this.properties = properties;
	}

	AccountManagerSmsCallbackClient(RestClient restClient,
			ObjectMapper objectMapper, SmsCallbackProperties properties) {
		this.restClient = restClient;
		this.objectMapper = objectMapper;
		this.properties = properties;
	}

	@Override
	public void send(SmsCallbackMessage message) {
		ensureConfigured();
		try {
			String body = objectMapper.writeValueAsString(SmsCallbackRequest.from(message));
			String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
			String nonce = UUID.randomUUID().toString();
			String signature = hmacHex(properties.apiSecret(), timestamp + "." + nonce + "." + body);
			Boolean accepted = restClient.post()
					.uri(properties.url())
					.contentType(MediaType.APPLICATION_JSON)
					.accept(MediaType.APPLICATION_JSON)
					.header("User-Agent", USER_AGENT)
					.header("X-API-Key", properties.apiKey())
					.header("X-Timestamp", timestamp)
					.header("X-Nonce", nonce)
					.header("X-Signature", signature)
					.body(body)
					.exchange((request, response) -> isAccepted(response.getStatusCode(),
							StreamUtils.copyToString(response.getBody(), StandardCharsets.UTF_8)));
			if (!Boolean.TRUE.equals(accepted)) {
				throw new SmsCallbackException("sms callback rejected");
			}
		}
		catch (SmsCallbackException exception) {
			throw exception;
		}
		catch (RestClientException exception) {
			throw new SmsCallbackException("sms callback request failed", exception);
		}
		catch (Exception exception) {
			throw new SmsCallbackException("sms callback request could not be built", exception);
		}
	}

	private void ensureConfigured() {
		if (!properties.enabled()) {
			throw new SmsCallbackException("sms callback is disabled");
		}
		if (!StringUtils.hasText(properties.url())
				|| !StringUtils.hasText(properties.apiKey())
				|| !StringUtils.hasText(properties.apiSecret())) {
			throw new SmsCallbackException("sms callback is not configured");
		}
	}

	private boolean isAccepted(HttpStatusCode statusCode, String body) {
		if (statusCode.value() == 201) {
			return true;
		}
		if (statusCode.value() == 404 && isAccountNotFound(body)) {
			throw new SmsAccountNotFoundException("sms callback account not found");
		}
		if (statusCode.value() == 200 && StringUtils.hasText(body)) {
			try {
				JsonNode node = objectMapper.readTree(body);
				return node.path("duplicate").asBoolean(false);
			}
			catch (Exception exception) {
				throw new SmsCallbackException("sms callback response could not be parsed", exception);
			}
		}
		return false;
	}

	private boolean isAccountNotFound(String body) {
		if (!StringUtils.hasText(body)) {
			return false;
		}
		try {
			JsonNode node = objectMapper.readTree(body);
			return "account_not_found".equals(node.path("detail").asText())
					|| "account_not_found".equals(node.path("code").asText())
					|| "account_not_found".equals(node.path("error").asText());
		}
		catch (Exception exception) {
			return body.contains("account_not_found");
		}
	}

	private static String hmacHex(String secret, String payload)
			throws NoSuchAlgorithmException, InvalidKeyException {
		Mac mac = Mac.getInstance("HmacSHA256");
		mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
		return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
	}

	private record SmsCallbackRequest(
			@JsonProperty("supplier_message_id") String supplierMessageId,
			String phone,
			String content,
			@JsonProperty("received_at") String receivedAt) {

		static SmsCallbackRequest from(SmsCallbackMessage message) {
			return new SmsCallbackRequest(
					message.supplierMessageId(),
					message.phone(),
					message.content(),
					message.receivedAt().withOffsetSameInstant(ZoneOffset.UTC).format(RECEIVED_AT_FORMAT));
		}
	}
}
