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

class EthereumClientTests {

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
	void nonceQueryFailureDoesNotFallBackToZero() throws Exception {
		EthereumClient client = clientResponding("{\"jsonrpc\":\"2.0\",\"id\":1,"
				+ "\"error\":{\"code\":-32000,\"message\":\"node unavailable\"}}");

		assertThatThrownBy(() -> client.queryPendingNonce(client.addressFromPrivateKey(PRIVATE_KEY)))
				.isInstanceOf(WithdrawalException.class)
				.hasMessageContaining("nonce");
	}

	@Test
	void signsDeterministicRawTransactionWithAllocatedNonce() throws Exception {
		EthereumClient client = clientResponding("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":\"0x4a817c800\"}");

		PreparedPayoutTransaction prepared = client.signTransfer(
				PRIVATE_KEY, DESTINATION, new BigDecimal("12.345678"), BigInteger.valueOf(17));

		assertThat(prepared.network()).isEqualTo("ERC20");
		assertThat(prepared.nonce()).isEqualTo(BigInteger.valueOf(17));
		assertThat(prepared.signedRawTransaction()).startsWith("0x");
		assertThat(prepared.txHash()).matches("0x[0-9a-f]{64}");
		assertThat(client.signTransfer(PRIVATE_KEY, DESTINATION, new BigDecimal("12.345678"),
				BigInteger.valueOf(17))).isEqualTo(prepared);
	}

	@Test
	void broadcastsThePreviouslySignedRawTransaction() throws Exception {
		AtomicReference<String> requestBody = new AtomicReference<>();
		EthereumClient client = clientResponding(requestBody,
				"{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":\"0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\"}");

		PayoutBroadcastResult result = client.broadcastSignedTransaction(
				"0xf86c018504a817c800830186a09411111111111111111111111111111111111111118080",
				"0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");

		assertThat(result.disposition()).isEqualTo(PayoutBroadcastDisposition.ACCEPTED);
		assertThat(requestBody.get()).contains("eth_sendRawTransaction")
				.contains("0xf86c018504a817c800830186a09411111111111111111111111111111111111111118080");
	}

	@Test
	void revertedReceiptIsFailedAndNeverConfirmed() throws Exception {
		EthereumClient client = clientResponding("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{"
				+ "\"transactionHash\":\"0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\","
				+ "\"blockNumber\":\"0x10\",\"status\":\"0x0\"}}");

		PayoutChainStatus status = client.queryTransactionStatus(
				"0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");

		assertThat(status.state()).isEqualTo(PayoutChainState.FAILED);
		assertThat(status.confirmations()).isZero();
	}

	private EthereumClient clientResponding(String response) throws IOException {
		return clientResponding(new AtomicReference<>(), response);
	}

	private EthereumClient clientResponding(AtomicReference<String> requestBody, String response) throws IOException {
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
		EthereumProperties properties = new EthereumProperties();
		properties.setNodeUrl("http://127.0.0.1:" + server.getAddress().getPort());
		properties.setApiKey("");
		return new EthereumClient(properties);
	}
}
