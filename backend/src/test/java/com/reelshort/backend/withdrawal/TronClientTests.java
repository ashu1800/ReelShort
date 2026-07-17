package com.reelshort.backend.withdrawal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicReference;
import java.time.OffsetDateTime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.tron.trident.proto.Chain;
import org.tron.trident.proto.Contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.sun.net.httpserver.HttpServer;

class TronClientTests {

	private static final String PRIVATE_KEY =
			"4f3edf983ac63ad7c49d5f6d4b6e8f4f82d4b3d9f7d5e4c3b2a1908070605040";
	private static final String DESTINATION = "TQn9Y2khEsLJW1ChVWFMSMeRDow5KcbLSE";
	private static final BigDecimal AMOUNT = new BigDecimal("1.250000");

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
		ClientFixture fixture = client(broadcastBody, null, null, null);

		PreparedPayoutTransaction prepared = fixture.client().prepareTransfer(PRIVATE_KEY, DESTINATION, AMOUNT);
		JsonNode payload = objectMapper.readTree(prepared.signedRawTransaction());
		String signature = payload.path("signature").get(0).asText();

		assertThat(signature).hasSize(130);
		assertThat(new BigInteger(signature.substring(0, 64), 16)).isPositive();
		assertThat(new BigInteger(signature.substring(64, 128), 16)).isPositive();
		assertThat(Integer.parseInt(signature.substring(128), 16)).isBetween(0, 3);
		assertThat(signature.substring(0, 2)).isNotIn("1b", "1c", "1d", "1e");
		assertThat(prepared.txHash()).isEqualTo(sha256Hex(fixture.rawDataHex()));
	}

	@Test
	void broadcastsCompleteSignedTransactionPayload() throws Exception {
		AtomicReference<String> broadcastBody = new AtomicReference<>();
		TronClient client = client(broadcastBody, null, null, null).client();
		PreparedPayoutTransaction prepared = client.prepareTransfer(PRIVATE_KEY, DESTINATION, AMOUNT);

		PayoutBroadcastResult result = client.broadcastSignedTransaction(
				prepared.signedRawTransaction(), prepared.txHash());

		assertThat(result.disposition()).isEqualTo(PayoutBroadcastDisposition.ACCEPTED);
		assertThat(objectMapper.readTree(broadcastBody.get()))
				.isEqualTo(objectMapper.readTree(prepared.signedRawTransaction()));
	}

	@Test
	void reportsExpiredBroadcastSeparatelyFromDefinitiveFailure() throws Exception {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/wallet/broadcasttransaction", exchange -> respond(exchange,
				"{\"result\":false,\"code\":\"TRANSACTION_EXPIRATION_ERROR\",\"message\":\"expired\"}"));
		server.start();
		TronProperties properties = new TronProperties();
		properties.setNodeUrl("http://127.0.0.1:" + server.getAddress().getPort());
		TronClient client = new TronClient(properties, objectMapper);

		PayoutBroadcastResult result = client.broadcastSignedTransaction(
				"{\"txID\":\"expired-hash\",\"signature\":[\"00\"]}", "expired-hash");

		assertThat(result.disposition()).isEqualTo(PayoutBroadcastDisposition.EXPIRED);
	}

	@Test
	void rejectsRawTransactionWhoseRecipientDiffersFromClaimedJson() throws Exception {
		TronClient client = client(new AtomicReference<>(),
				"TJRabPrwbZy45sbavfcjinPJC18kjpRTv8", null, null).client();

		assertThatThrownBy(() -> client.prepareTransfer(PRIVATE_KEY, DESTINATION, AMOUNT))
				.isInstanceOf(WithdrawalException.class)
				.hasMessageContaining("raw transaction does not match payout intent");
	}

	@Test
	void rejectsRawTransactionWhoseAmountDiffersFromClaimedJson() throws Exception {
		TronClient client = client(new AtomicReference<>(), null, new BigInteger("999999"), null).client();

		assertThatThrownBy(() -> client.prepareTransfer(PRIVATE_KEY, DESTINATION, AMOUNT))
				.isInstanceOf(WithdrawalException.class)
				.hasMessageContaining("raw transaction does not match payout intent");
	}

	@Test
	void rejectsRawTransactionWhoseContractDiffersFromClaimedJson() throws Exception {
		TronClient client = client(new AtomicReference<>(), null, null,
				"TJRabPrwbZy45sbavfcjinPJC18kjpRTv8").client();

		assertThatThrownBy(() -> client.prepareTransfer(PRIVATE_KEY, DESTINATION, AMOUNT))
				.isInstanceOf(WithdrawalException.class)
				.hasMessageContaining("raw transaction does not match payout intent");
	}

	@Test
	void rejectsRawTransactionWithAttachedTrc10CallTokenValue() throws Exception {
		TronClient client = client(new AtomicReference<>(), null, null, null, 1L, 0L).client();

		assertThatThrownBy(() -> client.prepareTransfer(PRIVATE_KEY, DESTINATION, AMOUNT))
				.isInstanceOf(WithdrawalException.class)
				.hasMessageContaining("raw transaction does not match payout intent");
	}

	@Test
	void rejectsRawTransactionWithAttachedTrc10TokenId() throws Exception {
		TronClient client = client(new AtomicReference<>(), null, null, null, 0L, 1L).client();

		assertThatThrownBy(() -> client.prepareTransfer(PRIVATE_KEY, DESTINATION, AMOUNT))
				.isInstanceOf(WithdrawalException.class)
				.hasMessageContaining("raw transaction does not match payout intent");
	}

	@Test
	void parsesCompleteIncomingTransferEvidenceFromTronGridResponse() throws Exception {
		long blockTimestamp = System.currentTimeMillis() - 30_000;
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/v1/accounts/", exchange -> respond(exchange, """
				{
				  "data": [{
				    "transaction_id": "%s",
				    "token_info": {"address": "%s"},
				    "block_timestamp": %d,
				    "to": "%s",
				    "value": "1250000",
				    "type": "Transfer"
				  }]
				}
				""".formatted("d".repeat(64), "TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t",
				blockTimestamp, DESTINATION)));
		server.start();
		TronProperties properties = new TronProperties();
		properties.setNodeUrl("http://127.0.0.1:" + server.getAddress().getPort());
		TronClient client = new TronClient(properties, objectMapper);

		TronClient.IncomingTransfer transfer = client.fetchIncomingUsdtTransfers(DESTINATION,
				properties.getUsdtContract(), 10, null).get(0);

		assertThat(transfer.txHash()).isEqualTo("d".repeat(64));
		assertThat(transfer.amount()).isEqualByComparingTo("1.250000");
		assertThat(transfer.recipient()).isEqualTo(DESTINATION);
		assertThat(transfer.contract()).isEqualTo(properties.getUsdtContract());
		assertThat(transfer.blockTimestamp()).isEqualTo(
				OffsetDateTime.ofInstant(java.time.Instant.ofEpochMilli(blockTimestamp), java.time.ZoneOffset.UTC));
		assertThat(transfer.confirmationCount()).isZero();
		assertThat(transfer.successful()).isTrue();
	}

	@Test
	void followsTronGridFingerprintPagination() throws Exception {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/v1/accounts/", exchange -> {
			boolean secondPage = exchange.getRequestURI().getQuery().contains("fingerprint=next-page");
			String txHash = secondPage ? "f".repeat(64) : "e".repeat(64);
			String meta = secondPage ? "{}" : "{\"fingerprint\":\"next-page\"}";
			respond(exchange, """
					{"data":[{"transaction_id":"%s","token_info":{"address":"%s"},
					"block_timestamp":%d,"to":"%s","value":"1000000","type":"Transfer"}],
					"meta":%s}
					""".formatted(txHash, "TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t",
					System.currentTimeMillis(), DESTINATION, meta));
		});
		server.start();
		TronProperties properties = new TronProperties();
		properties.setNodeUrl("http://127.0.0.1:" + server.getAddress().getPort());
		TronClient client = new TronClient(properties, objectMapper);

		assertThat(client.fetchIncomingUsdtTransfers(DESTINATION, properties.getUsdtContract(), 200, null))
				.extracting(TronClient.IncomingTransfer::txHash)
				.containsExactly("e".repeat(64), "f".repeat(64));
	}

	@Test
	void verifiesIncomingTransferFromReceiptAndCurrentBlock() throws Exception {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/wallet/gettransactioninfobyid", exchange -> respond(exchange,
				"{\"receipt\":{\"result\":\"SUCCESS\"},\"result\":\"SUCCESS\",\"blockNumber\":100}"));
		server.createContext("/wallet/getnowblock", exchange -> respond(exchange,
				"{\"block_header\":{\"raw_data\":{\"number\":119}}}"));
		server.start();
		TronProperties properties = new TronProperties();
		properties.setNodeUrl("http://127.0.0.1:" + server.getAddress().getPort());
		TronClient client = new TronClient(properties, objectMapper);
		TronClient.IncomingTransfer raw = new TronClient.IncomingTransfer("1".repeat(64), AMOUNT,
				DESTINATION, properties.getUsdtContract(), OffsetDateTime.now(), 0, true);

		TronClient.IncomingTransfer verified = client.verifyIncomingTransfer(raw);

		assertThat(verified.successful()).isTrue();
		assertThat(verified.confirmationCount()).isEqualTo(20);
	}

	@Test
	void fetchesExactTransferEventUsingSnapshotRecipientAndContract() throws Exception {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/v1/transactions/", exchange -> respond(exchange, """
				{"data":[
				 {"event_name":"Transfer","contract_address":"TJRabPrwbZy45sbavfcjinPJC18kjpRTv8",
				  "block_timestamp":1000,"result":{"to":"%s","value":"999999"}},
				 {"event_name":"Transfer","contract_address":"%s","block_timestamp":2000,
				  "result":{"to":"%s","value":"1250000"}}
				]}
				""".formatted(DESTINATION, "TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t", DESTINATION)));
		server.start();
		TronProperties properties = new TronProperties();
		properties.setNodeUrl("http://127.0.0.1:" + server.getAddress().getPort());
		properties.setUsdtContract("TJRabPrwbZy45sbavfcjinPJC18kjpRTv8");
		TronClient client = new TronClient(properties, objectMapper);

		TronClient.IncomingTransfer transfer = client.fetchIncomingUsdtTransfer("2".repeat(64), DESTINATION,
				"TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t");

		assertThat(transfer.amount()).isEqualByComparingTo("1.250000");
		assertThat(transfer.contract()).isEqualTo("TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t");
		assertThat(transfer.recipient()).isEqualTo(DESTINATION);
	}

	private ClientFixture client(AtomicReference<String> broadcastBody, String rawDestination,
			BigInteger rawAmount, String rawContract) throws IOException {
		return client(broadcastBody, rawDestination, rawAmount, rawContract, 0L, 0L);
	}

	private ClientFixture client(AtomicReference<String> broadcastBody, String rawDestination,
			BigInteger rawAmount, String rawContract, long callTokenValue, long tokenId) throws IOException {
		TronProperties properties = new TronProperties();
		TronClient addressClient = new TronClient(properties, objectMapper);
		String rawDataHex = rawDataHex(addressClient,
				rawDestination == null ? DESTINATION : rawDestination,
				rawAmount == null ? AMOUNT.movePointRight(6).toBigIntegerExact() : rawAmount,
				rawContract == null ? properties.getUsdtContract() : rawContract,
				callTokenValue, tokenId);
		String txId = sha256Hex(rawDataHex);
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/wallet/triggersmartcontract", exchange -> respond(exchange,
				"{\"result\":{\"result\":true},\"transaction\":{\"visible\":true,"
						+ "\"txID\":\"" + txId + "\",\"raw_data_hex\":\"" + rawDataHex + "\","
						+ "\"raw_data\":{\"expiration\":9999999999999,\"timestamp\":1}}}"));
		server.createContext("/wallet/broadcasttransaction", exchange -> {
			broadcastBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
			respond(exchange, "{\"result\":true,\"txid\":\"" + txId + "\"}");
		});
		server.start();
		properties.setNodeUrl("http://127.0.0.1:" + server.getAddress().getPort());
		return new ClientFixture(new TronClient(properties, objectMapper), rawDataHex);
	}

	private String rawDataHex(TronClient client, String destination, BigInteger amount, String contractAddress,
			long callTokenValue, long tokenId) {
		byte[] data = HexFormat.of().parseHex("a9059cbb" + abiParams(destination, amount));
		Contract.TriggerSmartContract trigger = Contract.TriggerSmartContract.newBuilder()
				.setOwnerAddress(ByteString.copyFrom(addressPayload(client.addressFromPrivateKey(PRIVATE_KEY))))
				.setContractAddress(ByteString.copyFrom(addressPayload(contractAddress)))
				.setCallValue(0)
				.setCallTokenValue(callTokenValue)
				.setTokenId(tokenId)
				.setData(ByteString.copyFrom(data))
				.build();
		Chain.Transaction.Contract contract = Chain.Transaction.Contract.newBuilder()
				.setType(Chain.Transaction.Contract.ContractType.TriggerSmartContract)
				.setParameter(Any.pack(trigger))
				.build();
		long now = System.currentTimeMillis();
		Chain.Transaction.raw raw = Chain.Transaction.raw.newBuilder()
				.setTimestamp(now)
				.setExpiration(now + 60_000)
				.setFeeLimit(100_000_000L)
				.addContract(contract)
				.build();
		return HexFormat.of().formatHex(raw.toByteArray());
	}

	private String abiParams(String destination, BigInteger amount) {
		byte[] payload = addressPayload(destination);
		return "0".repeat(24) + HexFormat.of().formatHex(Arrays.copyOfRange(payload, 1, 21))
				+ String.format("%064x", amount);
	}

	private byte[] addressPayload(String address) {
		byte[] decoded = base58Decode(address);
		return Arrays.copyOf(decoded, 21);
	}

	private byte[] base58Decode(String input) {
		String alphabet = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";
		BigInteger number = BigInteger.ZERO;
		for (int index = 0; index < input.length(); index++) {
			number = number.multiply(BigInteger.valueOf(58))
					.add(BigInteger.valueOf(alphabet.indexOf(input.charAt(index))));
		}
		byte[] raw = number.toByteArray();
		return raw.length > 0 && raw[0] == 0 ? Arrays.copyOfRange(raw, 1, raw.length) : raw;
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
			byte[] bytes = HexFormat.of().parseHex(hex);
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
		}
		catch (Exception exception) {
			throw new IllegalStateException(exception);
		}
	}

	private record ClientFixture(TronClient client, String rawDataHex) {
	}
}
