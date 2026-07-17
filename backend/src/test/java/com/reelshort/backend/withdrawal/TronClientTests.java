package com.reelshort.backend.withdrawal;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;

class TronClientTests {

	private static final String PRIVATE_KEY =
			"4f3edf983ac63ad7c49d5f6d4b6e8f4f82d4b3d9f7d5e4c3b2a1908070605040";
	private static final String DESTINATION = "TQn9Y2khEsLJW1ChVWFMSMeRDow5KcbLSE";
	private static final String RAW_DATA_HEX = "0a02abcd22081234567890abcdef40c0c4075a680801";

	private final ObjectMapper objectMapper = new ObjectMapper();
	private HttpServer server;

	@AfterEach
	void stopServer() {
		if (server != null) {
			server.stop(0);
		}
	}

	@Test
	void signatureUsesRThenSThenRecoveryId() throws Exception {
		AtomicReference<String> broadcastBody = new AtomicReference<>();
		TronClient client = client(broadcastBody);

		PreparedPayoutTransaction prepared = client.prepareTransfer(
				PRIVATE_KEY, DESTINATION, new BigDecimal("1.250000"));
		JsonNode payload = objectMapper.readTree(prepared.signedRawTransaction());
		String signature = payload.path("signature").get(0).asText();

		assertThat(signature).hasSize(130);
		assertThat(new BigInteger(signature.substring(0, 64), 16)).isPositive();
		assertThat(new BigInteger(signature.substring(64, 128), 16)).isPositive();
		assertThat(Integer.parseInt(signature.substring(128), 16)).isBetween(0, 3);
		assertThat(signature.substring(0, 2)).isNotIn("1b", "1c", "1d", "1e");
		assertThat(prepared.txHash()).isEqualTo(sha256Hex(RAW_DATA_HEX));
	}

	@Test
	void broadcastsCompleteSignedTransactionPayload() throws Exception {
		AtomicReference<String> broadcastBody = new AtomicReference<>();
		TronClient client = client(broadcastBody);
		PreparedPayoutTransaction prepared = client.prepareTransfer(
				PRIVATE_KEY, DESTINATION, new BigDecimal("1.250000"));

		PayoutBroadcastResult result = client.broadcastSignedTransaction(
				prepared.signedRawTransaction(), prepared.txHash());

		assertThat(result.disposition()).isEqualTo(PayoutBroadcastDisposition.ACCEPTED);
		assertThat(objectMapper.readTree(broadcastBody.get()))
				.isEqualTo(objectMapper.readTree(prepared.signedRawTransaction()));
	}

	private TronClient client(AtomicReference<String> broadcastBody) throws IOException {
		String txId = sha256Hex(RAW_DATA_HEX);
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/wallet/triggersmartcontract", exchange -> respond(exchange,
				"{\"result\":{\"result\":true},\"transaction\":{\"visible\":true,"
						+ "\"txID\":\"" + txId + "\",\"raw_data_hex\":\"" + RAW_DATA_HEX + "\","
						+ "\"raw_data\":{\"expiration\":9999999999999,\"timestamp\":1}}}"));
		server.createContext("/wallet/broadcasttransaction", exchange -> {
			broadcastBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
			respond(exchange, "{\"result\":true,\"txid\":\"" + txId + "\"}");
		});
		server.start();
		TronProperties properties = new TronProperties();
		properties.setNodeUrl("http://127.0.0.1:" + server.getAddress().getPort());
		return new TronClient(properties, objectMapper);
	}

	private void respond(com.sun.net.httpserver.HttpExchange exchange, String body) throws IOException {
		byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().add("Content-Type", "application/json");
		exchange.sendResponseHeaders(200, bytes.length);
		exchange.getResponseBody().write(bytes);
		exchange.close();
	}

	private String sha256Hex(String hex) {
		try {
			byte[] bytes = java.util.HexFormat.of().parseHex(hex);
			return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
		}
		catch (Exception exception) {
			throw new IllegalStateException(exception);
		}
	}
}
