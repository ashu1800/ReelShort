package com.reelshort.backend.withdrawal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

class BscClientTests {

	private static final String PRIVATE_KEY =
			"4f3edf983ac63ad7c49d5f6d4b6e8f4f82d4b3d9f7d5e4c3b2a1908070605040";
	private static final String DESTINATION = "0x1111111111111111111111111111111111111111";

	private HttpServer server;

	@AfterEach
	void stopServer() {
		if (server != null) {
			server.stop(0);
		}
	}

	@Test
	void signsTransferWithBscNetworkAnd18DecimalsAmount() throws Exception {
		// BSC USDT 使用 18 decimals（与以太坊 6 位不同），签名金额换算必须乘 10^18。
		// 用 nonce query 响应占位（签名前会查 gas price，这里返回固定值）。
		BscClient client = clientResponding("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":\"0x4a817c800\"}");

		PreparedPayoutTransaction prepared = client.signTransfer(
				PRIVATE_KEY, DESTINATION, new BigDecimal("1.5"), BigInteger.valueOf(7));

		assertThat(prepared.network()).isEqualTo("BEP20");
		assertThat(prepared.nonce()).isEqualTo(BigInteger.valueOf(7));
		assertThat(prepared.chainId()).isEqualTo(56L);
		assertThat(prepared.tokenContractAddress()).isEqualTo("0x55d398326f99059fF775485246999027B3197955");
		assertThat(prepared.signedRawTransaction()).startsWith("0x");
		assertThat(prepared.txHash()).matches("0x[0-9a-f]{64}");
		// 确定性：相同输入应产生相同签名。
		assertThat(client.signTransfer(PRIVATE_KEY, DESTINATION, new BigDecimal("1.5"), BigInteger.valueOf(7)))
				.isEqualTo(prepared);
	}

	@Test
	void revertedReceiptIsFailed() throws Exception {
		BscClient client = clientResponding("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{"
				+ "\"transactionHash\":\"0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\","
				+ "\"blockNumber\":\"0x10\",\"status\":\"0x0\"}}");

		PayoutChainStatus status = client.queryTransactionStatus(
				"0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");

		assertThat(status.state()).isEqualTo(PayoutChainState.FAILED);
		assertThat(status.confirmations()).isZero();
	}

	@Test
	void successfulReceiptReportsActualBnbFee() throws Exception {
		BscClient client = clientRespondingByMethod(
				"{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"transactionHash\":\"0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\",\"blockNumber\":\"0x10\",\"status\":\"0x1\",\"gasUsed\":\"0x5208\",\"effectiveGasPrice\":\"0x12a05f200\"}}",
				"{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":\"0x10\"}");

		PayoutChainStatus status = client.queryTransactionStatus(
				"0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");

		assertThat(status.actualFeeAmount()).isEqualByComparingTo("0.000105");
		assertThat(status.actualFeeAsset()).isEqualTo("BNB");
	}

	@Test
	void successfulReceiptUsesPersistedGasPriceWhenEffectiveGasPriceIsMissing() throws Exception {
		BscClient client = clientRespondingByMethod(
				"{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"transactionHash\":\"0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\",\"blockNumber\":\"0x10\",\"status\":\"0x1\",\"gasUsed\":\"0x5208\"}}",
				"{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":\"0x10\"}");

		PayoutChainStatus status = client.queryTransactionStatus(
				"0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", BigInteger.valueOf(2_000_000_000L));

		assertThat(status.actualFeeAmount()).isEqualByComparingTo("0.000042");
		assertThat(status.actualFeeAsset()).isEqualTo("BNB");
	}

	@Test
	void nonceQueryFailureDoesNotFallBackToZero() throws Exception {
		BscClient client = clientResponding("{\"jsonrpc\":\"2.0\",\"id\":1,"
				+ "\"error\":{\"code\":-32000,\"message\":\"node unavailable\"}}");

		assertThatThrownBy(() -> client.queryPendingNonce(client.addressFromPrivateKey(PRIVATE_KEY)))
				.isInstanceOf(WithdrawalException.class)
				.hasMessageContaining("nonce");
	}

	@Test
	void broadcastsThePreviouslySignedRawTransaction() throws Exception {
		AtomicReference<String> requestBody = new AtomicReference<>();
		BscClient client = clientResponding(requestBody,
				"{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":\"0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\"}");

		PayoutBroadcastResult result = client.broadcastSignedTransaction(
				"0xf86c018504a817c800830186a09411111111111111111111111111111111111111118080",
				"0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");

		assertThat(result.disposition()).isEqualTo(PayoutBroadcastDisposition.ACCEPTED);
		assertThat(requestBody.get()).contains("eth_sendRawTransaction");
	}

	private BscClient clientResponding(String response) throws IOException {
		return clientResponding(new AtomicReference<>(), response);
	}

	private BscClient clientResponding(AtomicReference<String> requestBody, String response) throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/", exchange -> {
			requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
			byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			exchange.sendResponseHeaders(200, bytes.length);
			exchange.getResponseBody().write(bytes);
			exchange.close();
		});
		server.start();
		BscProperties properties = new BscProperties();
		properties.setNodeUrl("http://127.0.0.1:" + server.getAddress().getPort());
		properties.setApiKey("");
		return new BscClient(properties);
	}

	private BscClient clientRespondingByMethod(String receiptResponse, String blockResponse) throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/", exchange -> {
			String request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
			String response = request.contains("eth_blockNumber") ? blockResponse : receiptResponse;
			byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			exchange.sendResponseHeaders(200, bytes.length);
			exchange.getResponseBody().write(bytes);
			exchange.close();
		});
		server.start();
		BscProperties properties = new BscProperties();
		properties.setNodeUrl("http://127.0.0.1:" + server.getAddress().getPort());
		properties.setApiKey("");
		return new BscClient(properties);
	}
}
