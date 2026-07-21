package com.reelshort.backend.withdrawal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
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
	void queriesUsdtBalanceDirectlyFromContract() throws Exception {
		AtomicReference<String> requestBody = new AtomicReference<>();
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/wallet/triggerconstantcontract", exchange -> {
			requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
			respond(exchange, "{\"result\":{\"result\":true},\"constant_result\":[\""
					+ "0".repeat(57) + "1312d00\"]}");
		});
		server.start();
		TronProperties properties = new TronProperties();
		properties.setNodeUrl("http://127.0.0.1:" + server.getAddress().getPort());
		TronClient client = new TronClient(properties, objectMapper);

		assertThat(client.getUsdtBalance(DESTINATION)).isEqualByComparingTo("20.000000");
		JsonNode request = objectMapper.readTree(requestBody.get());
		assertThat(request.path("function_selector").asText()).isEqualTo("balanceOf(address)");
		assertThat(request.path("parameter").asText()).isEqualTo(abiAddress(DESTINATION));
	}

	@Test
	void rejectsUsdtBalanceResponseWithoutContractResult() throws Exception {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/wallet/triggerconstantcontract", exchange ->
				respond(exchange, "{\"result\":{\"result\":true}}"));
		server.start();
		TronProperties properties = new TronProperties();
		properties.setNodeUrl("http://127.0.0.1:" + server.getAddress().getPort());
		TronClient client = new TronClient(properties, objectMapper);

		assertThatThrownBy(() -> client.getUsdtBalance(DESTINATION))
				.isInstanceOf(WithdrawalException.class)
				.hasMessageContaining("failed to query USDT balance");
	}

	@Test
	void rejectsNonTextualUsdtContractResult() throws Exception {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/wallet/triggerconstantcontract", exchange ->
				respond(exchange, "{\"result\":{\"result\":true},\"constant_result\":[20000000]}"));
		server.start();
		TronProperties properties = new TronProperties();
		properties.setNodeUrl("http://127.0.0.1:" + server.getAddress().getPort());
		TronClient client = new TronClient(properties, objectMapper);

		assertThatThrownBy(() -> client.getUsdtBalance(DESTINATION))
				.isInstanceOf(WithdrawalException.class)
				.hasMessageContaining("failed to query USDT balance");
	}

	@Test
	void rejectsShortUsdtContractResult() throws Exception {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/wallet/triggerconstantcontract", exchange ->
				respond(exchange, "{\"result\":{\"result\":true},\"constant_result\":[\"1\"]}"));
		server.start();
		TronProperties properties = new TronProperties();
		properties.setNodeUrl("http://127.0.0.1:" + server.getAddress().getPort());
		TronClient client = new TronClient(properties, objectMapper);

		assertThatThrownBy(() -> client.getUsdtBalance(DESTINATION))
				.isInstanceOf(WithdrawalException.class)
				.hasMessageContaining("failed to query USDT balance");
	}

	@Test
	void estimatesExactTronTransferFeesFromEnergyAndResources() throws Exception {
		List<String> simulationBodies = new ArrayList<>();
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/wallet/triggerconstantcontract", exchange -> {
			simulationBodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
			respond(exchange, "{\"result\":{\"result\":true},\"energy_used\":130285}"
			);
		});
		server.createContext("/wallet/getchainparameters", exchange -> respond(exchange, """
				{"chainParameter":[
				 {"key":"getEnergyFee","value":100},
				 {"key":"getTransactionFee","value":1000}
				]}
				"""));
		server.createContext("/wallet/getaccountresource", exchange -> respond(exchange, """
				{"EnergyLimit":100000,"EnergyUsed":50000,
				 "freeNetLimit":600,"freeNetUsed":100,
				 "NetLimit":1000,"NetUsed":700}
				"""));
		server.start();
		TronProperties properties = new TronProperties();
		properties.setNodeUrl("http://127.0.0.1:" + server.getAddress().getPort());
		TronClient client = new TronClient(properties, objectMapper);
		WithdrawalRequest first = withdrawal(DESTINATION, "1.250000");
		WithdrawalRequest second = withdrawal("TJRabPrwbZy45sbavfcjinPJC18kjpRTv8", "2.500000");

		TronFeeQuote quote = client.estimateTransferFees(
				client.addressFromPrivateKey(PRIVATE_KEY), List.of(first, second));

		assertThat(quote.requiredTrx()).isEqualByComparingTo("26.428400");
		assertThat(quote.totalEnergy()).isEqualTo(260570L);
		assertThat(quote.availableEnergy()).isEqualTo(50000L);
		assertThat(quote.marginPercent()).isEqualTo(20);
		assertThat(simulationBodies).hasSize(2);
		JsonNode firstRequest = objectMapper.readTree(simulationBodies.get(0));
		assertThat(firstRequest.path("function_selector").asText()).isEqualTo("transfer(address,uint256)");
		assertThat(firstRequest.path("parameter").asText())
				.isEqualTo(abiParams(DESTINATION, new BigInteger("1250000")));
	}

	@Test
	void rejectsTransferFeeEstimateWithoutEnergyUsage() throws Exception {
		TronClient client = feeEstimateClient(new TronProperties(),
				"{\"result\":{\"result\":true}}", validChainParameters(), validResources());

		assertThatThrownBy(() -> client.estimateTransferFees(
				client.addressFromPrivateKey(PRIVATE_KEY), List.of(withdrawal(DESTINATION, "1"))))
				.isInstanceOf(WithdrawalException.class)
				.hasMessageContaining("failed to estimate TRON payout fee")
				.hasMessageContaining("invalid transfer simulation response");
	}

	@Test
	void rejectsFractionalTransferEnergyUsage() throws Exception {
		TronClient client = feeEstimateClient(new TronProperties(),
				"{\"result\":{\"result\":true},\"energy_used\":130285.9}",
				validChainParameters(), validResources());

		assertThatThrownBy(() -> client.estimateTransferFees(
				client.addressFromPrivateKey(PRIVATE_KEY), List.of(withdrawal(DESTINATION, "1"))))
				.isInstanceOf(WithdrawalException.class)
				.hasMessageContaining("invalid transfer simulation response");
	}

	@Test
	void rejectsZeroTransferEnergyUsage() throws Exception {
		TronClient client = feeEstimateClient(new TronProperties(),
				"{\"result\":{\"result\":true},\"energy_used\":0}",
				validChainParameters(), validResources());

		assertThatThrownBy(() -> client.estimateTransferFees(
				client.addressFromPrivateKey(PRIVATE_KEY), List.of(withdrawal(DESTINATION, "1"))))
				.isInstanceOf(WithdrawalException.class)
				.hasMessageContaining("invalid transfer simulation response");
	}

	@Test
	void appliesSafetyMarginBeforeDeductingAvailableEnergy() throws Exception {
		TronClient client = feeEstimateClient(new TronProperties(),
				"{\"result\":{\"result\":true},\"energy_used\":100000}",
				validChainParameters(),
				"{\"EnergyLimit\":95000,\"EnergyUsed\":0,\"freeNetLimit\":480,\"freeNetUsed\":0}");

		TronFeeQuote quote = client.estimateTransferFees(
				client.addressFromPrivateKey(PRIVATE_KEY), List.of(withdrawal(DESTINATION, "1")));

		assertThat(quote.requiredTrx()).isEqualByComparingTo("2.500000");
	}

	@Test
	void rejectsTransferFeeEstimateWithoutEnergyPrice() throws Exception {
		TronClient client = feeEstimateClient(new TronProperties(),
				"{\"result\":{\"result\":true},\"energy_used\":130285}",
				"{\"chainParameter\":[{\"key\":\"getTransactionFee\",\"value\":1000}]}",
				validResources());

		assertThatThrownBy(() -> client.estimateTransferFees(
				client.addressFromPrivateKey(PRIVATE_KEY), List.of(withdrawal(DESTINATION, "1"))))
				.isInstanceOf(WithdrawalException.class)
				.hasMessageContaining("missing chain parameter getEnergyFee");
	}

	@Test
	void rejectsZeroTransferFeePrices() throws Exception {
		TronClient client = feeEstimateClient(new TronProperties(),
				"{\"result\":{\"result\":true},\"energy_used\":130285}",
				"{\"chainParameter\":[{\"key\":\"getEnergyFee\",\"value\":0},"
						+ "{\"key\":\"getTransactionFee\",\"value\":0}]}", validResources());

		assertThatThrownBy(() -> client.estimateTransferFees(
				client.addressFromPrivateKey(PRIVATE_KEY), List.of(withdrawal(DESTINATION, "1"))))
				.isInstanceOf(WithdrawalException.class)
				.hasMessageContaining("missing chain parameter getEnergyFee");
	}

	@Test
	void rejectsSuccessfulJsonFromFailedHttpResponse() throws Exception {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/wallet/getaccount", exchange -> respond(exchange, 500,
				"{\"balance\":20000000}"));
		server.start();
		TronProperties properties = new TronProperties();
		properties.setNodeUrl("http://127.0.0.1:" + server.getAddress().getPort());
		TronClient client = new TronClient(properties, objectMapper);

		assertThatThrownBy(() -> client.getTrxBalance(DESTINATION))
				.isInstanceOf(WithdrawalException.class)
				.hasMessageContaining("TronGrid HTTP status 500");
	}

	@Test
	void retriesReplaySafeRequestAfterRateLimit() throws Exception {
		AtomicInteger requests = new AtomicInteger();
		List<Long> delays = new ArrayList<>();
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/wallet/getaccount", exchange -> {
			if (requests.getAndIncrement() == 0) {
				exchange.getResponseHeaders().add("Retry-After", "3");
				respond(exchange, 429, "{\"error\":\"rate limited\"}");
			}
			else {
				respond(exchange, "{\"balance\":20000000}");
			}
		});
		server.start();
		TronProperties properties = retryProperties(server);
		TronClient client = new TronClient(properties, objectMapper,
				System::nanoTime, delays::add);

		assertThat(client.getTrxBalance(DESTINATION)).isEqualByComparingTo("20.000000");
		assertThat(requests).hasValue(2);
		assertThat(delays).contains(3_000L);
	}

	@Test
	void reportsChineseMessageAfterRateLimitRetriesExhausted() throws Exception {
		AtomicInteger requests = new AtomicInteger();
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/wallet/getaccount", exchange -> {
			requests.incrementAndGet();
			respond(exchange, 429, "{\"error\":\"rate limited\"}");
		});
		server.start();
		TronProperties properties = retryProperties(server);
		properties.setRateLimitRetries(2);
		TronClient client = new TronClient(properties, objectMapper,
				System::nanoTime, ignored -> { });

		assertThatThrownBy(() -> client.getTrxBalance(DESTINATION))
				.isInstanceOf(WithdrawalException.class)
				.hasMessageContaining("TRON 节点请求过于频繁，请稍后重试；打款未执行");
		assertThat(requests).hasValue(3);
	}

	@Test
	void rateLimitedStatusQueryDoesNotClaimPayoutWasNotExecuted() throws Exception {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/wallet/gettransactioninfobyid", exchange ->
				respond(exchange, 429, "{\"error\":\"rate limited\"}"));
		server.start();
		TronProperties properties = retryProperties(server);
		properties.setRateLimitRetries(0);

		PayoutChainStatus status = new TronClient(properties, objectMapper,
				System::nanoTime, ignored -> { }).queryTransactionStatus("tx");

		assertThat(status.state()).isEqualTo(PayoutChainState.UNKNOWN);
		assertThat(status.detail()).contains("TRON 节点请求过于频繁，请稍后重试")
				.doesNotContain("打款未执行");
	}

	@Test
	void doesNotRetryRateLimitedBroadcast() throws Exception {
		AtomicInteger requests = new AtomicInteger();
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/wallet/broadcasttransaction", exchange -> {
			requests.incrementAndGet();
			respond(exchange, 429, "{\"error\":\"rate limited\"}");
		});
		server.start();
		TronProperties properties = retryProperties(server);
		TronClient client = new TronClient(properties, objectMapper,
				System::nanoTime, ignored -> { });

		PayoutBroadcastResult result = client.broadcastSignedTransaction(
				"{\"txID\":\"tx\",\"signature\":[\"00\"]}", "tx");

		assertThat(result.disposition()).isEqualTo(PayoutBroadcastDisposition.UNKNOWN);
		assertThat(requests).hasValue(1);
	}

	@Test
	void spacesConsecutiveTronRpcRequests() throws Exception {
		AtomicLong nowNanos = new AtomicLong();
		List<Long> delays = new ArrayList<>();
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/wallet/getaccount", exchange -> respond(exchange,
				"{\"balance\":20000000}"));
		server.start();
		TronProperties properties = retryProperties(server);
		properties.setRequestInterval(Duration.ofMillis(250));
		TronClient client = new TronClient(properties, objectMapper, nowNanos::get, delay -> {
			delays.add(delay);
			nowNanos.addAndGet(Duration.ofMillis(delay).toNanos());
		});

		client.getTrxBalance(DESTINATION);
		client.getTrxBalance(DESTINATION);

		assertThat(delays).containsExactly(250L);
	}

	@Test
	void cachesChainPricesButRefreshesSimulationsAndResources() throws Exception {
		AtomicInteger simulations = new AtomicInteger();
		AtomicInteger chainParameters = new AtomicInteger();
		AtomicInteger resources = new AtomicInteger();
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/wallet/triggerconstantcontract", exchange -> {
			simulations.incrementAndGet();
			respond(exchange, "{\"result\":{\"result\":true},\"energy_used\":130285}");
		});
		server.createContext("/wallet/getchainparameters", exchange -> {
			chainParameters.incrementAndGet();
			respond(exchange, validChainParameters());
		});
		server.createContext("/wallet/getaccountresource", exchange -> {
			resources.incrementAndGet();
			respond(exchange, validResources());
		});
		server.start();
		TronProperties properties = retryProperties(server);
		properties.setChainParameterCacheTtl(Duration.ofMinutes(5));
		TronClient client = new TronClient(properties, objectMapper,
				System::nanoTime, ignored -> { });
		List<WithdrawalRequest> withdrawals = List.of(withdrawal(DESTINATION, "1"));

		client.estimateTransferFees(client.addressFromPrivateKey(PRIVATE_KEY), withdrawals);
		client.estimateTransferFees(client.addressFromPrivateKey(PRIVATE_KEY), withdrawals);

		assertThat(simulations).hasValue(2);
		assertThat(chainParameters).hasValue(1);
		assertThat(resources).hasValue(2);
	}

	@Test
	void retriesReplaySafeEventGetAfterRateLimit() throws Exception {
		AtomicInteger requests = new AtomicInteger();
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/v1/transactions/", exchange -> {
			if (requests.getAndIncrement() == 0) {
				respond(exchange, 429, "{\"error\":\"rate limited\"}");
			}
			else {
				respond(exchange, "{\"data\":[]}");
			}
		});
		server.start();
		TronProperties properties = retryProperties(server);
		TronClient client = new TronClient(properties, objectMapper,
				System::nanoTime, ignored -> { });

		assertThatThrownBy(() -> client.fetchIncomingUsdtTransfer(
				"a".repeat(64), DESTINATION, properties.getUsdtContract(), AMOUNT))
				.isInstanceOf(WithdrawalException.class)
				.hasMessageContaining("transaction has no matching TRC20 transfer event");
		assertThat(requests).hasValue(2);
	}

	@Test
	void retriesReplaySafeTransferPageGetAfterRateLimit() throws Exception {
		AtomicInteger requests = new AtomicInteger();
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/v1/accounts/", exchange -> {
			if (requests.getAndIncrement() == 0) {
				respond(exchange, 429, "{\"error\":\"rate limited\"}");
			}
			else {
				respond(exchange, "{\"data\":[]}");
			}
		});
		server.start();
		TronProperties properties = retryProperties(server);
		TronClient client = new TronClient(properties, objectMapper,
				System::nanoTime, ignored -> { });

		TronClient.IncomingTransferPage page = client.fetchIncomingUsdtTransferPage(
				DESTINATION, properties.getUsdtContract(), 20, null);

		assertThat(page.transfers()).isEmpty();
		assertThat(requests).hasValue(2);
	}

	private TronProperties retryProperties(HttpServer httpServer) {
		TronProperties properties = new TronProperties();
		properties.setNodeUrl("http://127.0.0.1:" + httpServer.getAddress().getPort());
		properties.setRequestInterval(Duration.ZERO);
		properties.setRetryInitialDelay(Duration.ofSeconds(1));
		return properties;
	}

	@Test
	void rejectsTransferFeeEstimateWithInvalidResources() throws Exception {
		TronClient client = feeEstimateClient(new TronProperties(),
				"{\"result\":{\"result\":true},\"energy_used\":130285}",
				validChainParameters(), "{\"EnergyLimit\":-1}");

		assertThatThrownBy(() -> client.estimateTransferFees(
				client.addressFromPrivateKey(PRIVATE_KEY), List.of(withdrawal(DESTINATION, "1"))))
				.isInstanceOf(WithdrawalException.class)
				.hasMessageContaining("invalid account resource EnergyLimit");
	}

	@Test
	void rejectsSimulatedTransferAboveConfiguredFeeLimit() throws Exception {
		TronProperties properties = new TronProperties();
		properties.setFeeLimit(10_000_000L);
		TronClient client = feeEstimateClient(properties,
				"{\"result\":{\"result\":true},\"energy_used\":130285}",
				validChainParameters(), validResources());

		assertThatThrownBy(() -> client.estimateTransferFees(
				client.addressFromPrivateKey(PRIVATE_KEY), List.of(withdrawal(DESTINATION, "1"))))
				.isInstanceOf(WithdrawalException.class)
				.hasMessageContaining("simulated transfer fee exceeds configured fee limit");
	}

	@Test
	void rejectsBufferedTransferFeeAboveConfiguredFeeLimit() throws Exception {
		TronProperties properties = new TronProperties();
		properties.setFeeLimit(10_000_000L);
		TronClient client = feeEstimateClient(properties,
				"{\"result\":{\"result\":true},\"energy_used\":90000}",
				validChainParameters(), validResources());

		assertThatThrownBy(() -> client.estimateTransferFees(
				client.addressFromPrivateKey(PRIVATE_KEY), List.of(withdrawal(DESTINATION, "1"))))
				.isInstanceOf(WithdrawalException.class)
				.hasMessageContaining("simulated transfer fee exceeds configured fee limit");
	}

	private TronClient feeEstimateClient(TronProperties properties, String simulation,
			String chainParameters, String resources) throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/wallet/triggerconstantcontract", exchange -> respond(exchange, simulation));
		server.createContext("/wallet/getchainparameters", exchange -> respond(exchange, chainParameters));
		server.createContext("/wallet/getaccountresource", exchange -> respond(exchange, resources));
		server.start();
		properties.setNodeUrl("http://127.0.0.1:" + server.getAddress().getPort());
		return new TronClient(properties, objectMapper);
	}

	private String validChainParameters() {
		return "{\"chainParameter\":[{\"key\":\"getEnergyFee\",\"value\":100},"
				+ "{\"key\":\"getTransactionFee\",\"value\":1000}]}";
	}

	private String validResources() {
		return "{\"EnergyLimit\":0,\"EnergyUsed\":0,\"freeNetLimit\":0,"
				+ "\"freeNetUsed\":0,\"NetLimit\":0,\"NetUsed\":0}";
	}

	private WithdrawalRequest withdrawal(String address, String amount) {
		WithdrawalRequest request = mock(WithdrawalRequest.class);
		when(request.walletAddress()).thenReturn(address);
		when(request.usdtAmount()).thenReturn(new BigDecimal(amount));
		return request;
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
		assertThat(prepared.txHash()).isEqualTo(sha256Hex(fixture.rawDataHex()));
	}

	@Test
	void encodesRecipientWithoutBase58Checksum() throws Exception {
		ClientFixture fixture = client(new AtomicReference<>(), null, null, null);

		fixture.client().prepareTransfer(PRIVATE_KEY, DESTINATION, AMOUNT);

		JsonNode triggerRequest = objectMapper.readTree(fixture.triggerBody().get());
		assertThat(triggerRequest.path("parameter").asText())
				.isEqualTo(abiParams(DESTINATION, AMOUNT.movePointRight(6).toBigIntegerExact()));
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
	void successfulTransactionInfoReportsActualTrxFee() throws Exception {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/wallet/gettransactioninfobyid", exchange -> respond(exchange,
				"{\"id\":\"tx\",\"blockNumber\":100,\"fee\":13742000,\"result\":\"SUCCESS\",\"receipt\":{\"result\":\"SUCCESS\"}}"));
		server.createContext("/wallet/getnowblock", exchange -> respond(exchange,
				"{\"block_header\":{\"raw_data\":{\"number\":100}}}"));
		server.start();
		TronProperties properties = new TronProperties();
		properties.setNodeUrl("http://127.0.0.1:" + server.getAddress().getPort());
		PayoutChainStatus status = new TronClient(properties, objectMapper).queryTransactionStatus("tx");

		assertThat(status.actualFeeAmount()).isEqualByComparingTo("13.742");
		assertThat(status.actualFeeAsset()).isEqualTo("TRX");
	}

	@Test
	void rejectsRawTransactionWhoseRecipientDiffersFromClaimedJson() throws Exception {
		TronClient client = client(new AtomicReference<>(),
				"TJRabPrwbZy45sbavfcjinPJC18kjpRTv8", null, null).client();

		assertThatThrownBy(() -> client.prepareTransfer(PRIVATE_KEY, DESTINATION, AMOUNT))
				.isInstanceOf(WithdrawalException.class)
				.hasMessageContaining("raw transaction does not match payout intent: recipient");
	}

	@Test
	void rejectsRawTransactionWhoseAmountDiffersFromClaimedJson() throws Exception {
		TronClient client = client(new AtomicReference<>(), null, new BigInteger("999999"), null).client();

		assertThatThrownBy(() -> client.prepareTransfer(PRIVATE_KEY, DESTINATION, AMOUNT))
				.isInstanceOf(WithdrawalException.class)
				.hasMessageContaining("raw transaction does not match payout intent: amount");
	}

	@Test
	void rejectsRawTransactionWhoseContractDiffersFromClaimedJson() throws Exception {
		TronClient client = client(new AtomicReference<>(), null, null,
				"TJRabPrwbZy45sbavfcjinPJC18kjpRTv8").client();

		assertThatThrownBy(() -> client.prepareTransfer(PRIVATE_KEY, DESTINATION, AMOUNT))
				.isInstanceOf(WithdrawalException.class)
				.hasMessageContaining("raw transaction does not match payout intent: token_contract");
	}

	@Test
	void rejectsRawTransactionWithAttachedTrc10CallTokenValue() throws Exception {
		TronClient client = client(new AtomicReference<>(), null, null, null, 1L, 0L).client();

		assertThatThrownBy(() -> client.prepareTransfer(PRIVATE_KEY, DESTINATION, AMOUNT))
				.isInstanceOf(WithdrawalException.class)
				.hasMessageContaining("raw transaction does not match payout intent: call_token_value");
	}

	@Test
	void rejectsRawTransactionWithAttachedTrc10TokenId() throws Exception {
		TronClient client = client(new AtomicReference<>(), null, null, null, 0L, 1L).client();

		assertThatThrownBy(() -> client.prepareTransfer(PRIVATE_KEY, DESTINATION, AMOUNT))
				.isInstanceOf(WithdrawalException.class)
				.hasMessageContaining("raw transaction does not match payout intent: token_id");
	}

	@Test
	void rebuildsUnsignedTransactionOnceAfterIntentMismatch() throws Exception {
		TronProperties properties = new TronProperties();
		TronClient addressClient = new TronClient(properties, objectMapper);
		String invalidRaw = rawDataHex(addressClient, "TJRabPrwbZy45sbavfcjinPJC18kjpRTv8",
				AMOUNT.movePointRight(6).toBigIntegerExact(), properties.getUsdtContract(), 0L, 0L);
		String validRaw = rawDataHex(addressClient, DESTINATION,
				AMOUNT.movePointRight(6).toBigIntegerExact(), properties.getUsdtContract(), 0L, 0L);
		RetryFixture fixture = clientWithRawResponses(properties, invalidRaw, validRaw);

		PreparedPayoutTransaction prepared = fixture.client().prepareTransfer(PRIVATE_KEY, DESTINATION, AMOUNT);

		assertThat(fixture.requestCount()).hasValue(2);
		assertThat(prepared.txHash()).isEqualTo(sha256Hex(validRaw));
	}

	@Test
	void reportsSpecificReasonAfterSecondIntentMismatch() throws Exception {
		TronProperties properties = new TronProperties();
		TronClient addressClient = new TronClient(properties, objectMapper);
		String invalidRaw = rawDataHex(addressClient, DESTINATION, new BigInteger("999999"),
				properties.getUsdtContract(), 0L, 0L);
		RetryFixture fixture = clientWithRawResponses(properties, invalidRaw, invalidRaw);

		assertThatThrownBy(() -> fixture.client().prepareTransfer(PRIVATE_KEY, DESTINATION, AMOUNT))
				.isInstanceOf(WithdrawalException.class)
				.hasMessageContaining("raw transaction does not match payout intent: amount");
		assertThat(fixture.requestCount()).hasValue(2);
	}

	@Test
	void doesNotRebuildWhenTransactionIdMismatches() throws Exception {
		TronProperties properties = new TronProperties();
		TronClient addressClient = new TronClient(properties, objectMapper);
		String validRaw = rawDataHex(addressClient, DESTINATION,
				AMOUNT.movePointRight(6).toBigIntegerExact(), properties.getUsdtContract(), 0L, 0L);
		AtomicInteger requestCount = new AtomicInteger();
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/wallet/triggersmartcontract", exchange -> {
			requestCount.incrementAndGet();
			respond(exchange, "{\"result\":{\"result\":true},\"transaction\":{\"visible\":true,"
					+ "\"txID\":\"" + "0".repeat(64) + "\",\"raw_data_hex\":\""
					+ validRaw + "\"}}");
		});
		server.start();
		properties.setNodeUrl("http://127.0.0.1:" + server.getAddress().getPort());
		TronClient client = new TronClient(properties, objectMapper);

		assertThatThrownBy(() -> client.prepareTransfer(PRIVATE_KEY, DESTINATION, AMOUNT))
				.isInstanceOf(WithdrawalException.class)
				.hasMessageContaining("mismatched transaction id");
		assertThat(requestCount).hasValue(1);
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
				 {"event_name":"Transfer","contract_address":"%s",
				  "block_timestamp":1000,"result":{"to":"%s","value":"999999"}},
				 {"event_name":"Transfer","contract_address":"%s","block_timestamp":2000,
				  "result":{"to":"%s","value":"1250000"}}
				]}
				""".formatted("TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t", DESTINATION,
				"TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t", DESTINATION)));
		server.start();
		TronProperties properties = new TronProperties();
		properties.setNodeUrl("http://127.0.0.1:" + server.getAddress().getPort());
		properties.setUsdtContract("TJRabPrwbZy45sbavfcjinPJC18kjpRTv8");
		TronClient client = new TronClient(properties, objectMapper);

		TronClient.IncomingTransfer transfer = client.fetchIncomingUsdtTransfer("2".repeat(64), DESTINATION,
				"TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t", AMOUNT);

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
		AtomicReference<String> triggerBody = new AtomicReference<>();
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/wallet/triggersmartcontract", exchange -> {
			triggerBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
			respond(exchange, "{\"result\":{\"result\":true},\"transaction\":{\"visible\":true,"
					+ "\"txID\":\"" + txId + "\",\"raw_data_hex\":\"" + rawDataHex + "\","
					+ "\"raw_data\":{\"expiration\":9999999999999,\"timestamp\":1}}}");
		});
		server.createContext("/wallet/broadcasttransaction", exchange -> {
			broadcastBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
			respond(exchange, "{\"result\":true,\"txid\":\"" + txId + "\"}");
		});
		server.start();
		properties.setNodeUrl("http://127.0.0.1:" + server.getAddress().getPort());
		return new ClientFixture(new TronClient(properties, objectMapper), rawDataHex, triggerBody);
	}

	private RetryFixture clientWithRawResponses(TronProperties properties, String... rawResponses)
			throws IOException {
		AtomicInteger requestCount = new AtomicInteger();
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/wallet/triggersmartcontract", exchange -> {
			int index = Math.min(requestCount.getAndIncrement(), rawResponses.length - 1);
			String rawDataHex = rawResponses[index];
			respond(exchange, "{\"result\":{\"result\":true},\"transaction\":{\"visible\":true,"
					+ "\"txID\":\"" + sha256Hex(rawDataHex) + "\",\"raw_data_hex\":\""
					+ rawDataHex + "\"}}");
		});
		server.start();
		properties.setNodeUrl("http://127.0.0.1:" + server.getAddress().getPort());
		return new RetryFixture(new TronClient(properties, objectMapper), requestCount);
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

	private String abiAddress(String address) {
		byte[] payload = addressPayload(address);
		return "0".repeat(24) + HexFormat.of().formatHex(Arrays.copyOfRange(payload, 1, 21));
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
		respond(exchange, 200, body);
	}

	private void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String body) throws IOException {
		byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().add("Content-Type", "application/json");
		exchange.sendResponseHeaders(status, bytes.length);
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

	private record ClientFixture(TronClient client, String rawDataHex, AtomicReference<String> triggerBody) {
	}

	private record RetryFixture(TronClient client, AtomicInteger requestCount) {
	}
}
