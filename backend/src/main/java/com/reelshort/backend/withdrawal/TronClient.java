package com.reelshort.backend.withdrawal;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.HexFormat;

import org.bouncycastle.asn1.sec.SECNamedCurves;
import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.crypto.signers.ECDSASigner;
import org.bouncycastle.math.ec.ECPoint;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import org.tron.trident.proto.Chain;
import org.tron.trident.proto.Contract;

/**
 * TronGrid HTTP client for TRC20 USDT transfer and balance queries. Signs transactions in-memory
 * using a private key provided per-call by the admin (never persisted).
 *
 * <p>Transfer flow: triggersmartcontract (build tx) → sign raw_data_hex with secp256k1 → broadcasthex.
 */
@Service
public class TronClient {
	private static final Logger log = LoggerFactory.getLogger(TronClient.class);

	private static final X9ECParameters SECP256K1 = SECNamedCurves.getByName("secp256k1");
	private static final ECDomainParameters DOMAIN =
			new ECDomainParameters(SECP256K1.getCurve(), SECP256K1.getG(), SECP256K1.getN(), SECP256K1.getH());
	private static final BigDecimal USDT_DECIMALS = new BigDecimal("1000000"); // 6 decimals
	private static final byte[] TRANSFER_SELECTOR = HexFormat.of().parseHex("a9059cbb");
	private static final long MAX_TRANSACTION_LIFETIME_MILLIS = Duration.ofMinutes(5).toMillis();
	private static final long MAX_TIMESTAMP_SKEW_MILLIS = Duration.ofMinutes(1).toMillis();

	private final TronProperties properties;
	private final ObjectMapper objectMapper;
	private final HttpClient httpClient;

	public TronClient(TronProperties properties, ObjectMapper objectMapper) {
		this.properties = properties;
		this.objectMapper = objectMapper;
		this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
	}

	/**
	 * Derive the Tron base58 address from a hex private key.
	 */
	public String addressFromPrivateKey(String hexPrivateKey) {
		byte[] privKeyBytes = hexStringToBytes(hexPrivateKey);
		ECPoint pubKeyPoint = DOMAIN.getG().multiply(new BigInteger(1, privKeyBytes)).normalize();
		byte[] pubKey = pubKeyPoint.getEncoded(false); // uncompressed: 0x04 + X(32) + Y(32)
		byte[] xy = Arrays.copyOfRange(pubKey, 1, 65); // drop 0x04 prefix

		// Tron address = 0x41 + last 20 bytes of Keccak256(x || y), then Base58Check
		byte[] keccak = keccak256(xy);
		byte[] addressBytes = new byte[21];
		addressBytes[0] = 0x41;
		System.arraycopy(keccak, keccak.length - 20, addressBytes, 1, 20);
		return base58CheckEncode(addressBytes);
	}

	/**
	 * Get USDT (TRC20) balance for an address, in human-readable USDT (6 decimals).
	 */
	public BigDecimal getUsdtBalance(String address) {
		try {
			JsonNode account = getAccount(address);
			JsonNode trc20 = account.path("trc20");
			if (trc20.isArray()) {
				for (JsonNode token : trc20) {
					String key = properties.getUsdtContract();
					if (token.has(key)) {
						return new BigDecimal(token.get(key).asText()).divide(USDT_DECIMALS, 6, RoundingMode.DOWN);
					}
				}
			}
			return BigDecimal.ZERO;
		}
		catch (Exception exception) {
			throw new WithdrawalException(503, "failed to query USDT balance: " + exception.getMessage());
		}
	}

	/**
	 * Get TRX balance for an address, in human-readable TRX.
	 */
	public BigDecimal getTrxBalance(String address) {
		try {
			JsonNode account = getAccount(address);
			long sun = account.path("balance").asLong(0);
			return new BigDecimal(sun).divide(new BigDecimal("1000000"), 6, RoundingMode.DOWN);
		}
		catch (Exception exception) {
			throw new WithdrawalException(503, "failed to query TRX balance: " + exception.getMessage());
		}
	}

	/**
	 * Transfer USDT (TRC20) from the hot wallet to a recipient. Returns the transaction hash.
	 *
	 * @param hexPrivateKey hot wallet private key (hex, no 0x prefix)
	 * @param toAddress     recipient Tron base58 address
	 * @param usdtAmount    amount in USDT (e.g. "10.5")
	 * @return on-chain transaction hash (64 hex chars)
	 */
	public PreparedPayoutTransaction prepareTransfer(String hexPrivateKey, String toAddress, BigDecimal usdtAmount) {
		String ownerAddress = addressFromPrivateKey(hexPrivateKey);
		BigDecimal rawAmount = usdtAmount.multiply(USDT_DECIMALS).setScale(0, RoundingMode.DOWN);
		String parameter = encodeTransferParams(toAddress, rawAmount.toBigInteger());
		long intentCreatedAt = System.currentTimeMillis();

		// 1. Build the unsigned transaction
		JsonNode triggerResponse = triggersmartcontract(ownerAddress, parameter);
		// TronGrid returns error as { "result": { "code": <nonzero>, "message": "..." } }
		// Success may be { "result": { "code": 0 } } or { "result": true } or no result field at all.
		JsonNode resultNode = triggerResponse.path("result");
		if (resultNode.isObject() && resultNode.has("code") && resultNode.get("code").asInt(0) != 0) {
			String message = resultNode.path("message").asText("trigger failed");
			throw new WithdrawalException(502, "TRC20 trigger failed: " + message);
		}
		JsonNode transaction = triggerResponse.path("transaction");
		String rawDataHex = transaction.path("raw_data_hex").asText("");
		if (rawDataHex.isEmpty()) {
			rawDataHex = transaction.path("transaction").path("raw_data_hex").asText("");
		}
		if (rawDataHex.isEmpty()) {
			throw new WithdrawalException(502, "missing raw_data_hex in trigger response");
		}
		// TronGrid returns "txID" (camelCase) — try both casing
		String txid = transaction.path("txID").asText("");
		if (txid.isEmpty()) {
			txid = transaction.path("txid").asText("");
		}

		// 2. Sign the transaction
		byte[] rawBytes = hexStringToBytes(rawDataHex);
		validateRawTransaction(rawBytes, ownerAddress, toAddress, rawAmount.toBigInteger(), intentCreatedAt);
		byte[] hash = sha256(rawBytes);
		String computedTxId = bytesToHex(hash);
		if (!txid.isBlank() && !txid.equalsIgnoreCase(computedTxId)) {
			throw new WithdrawalException(502, "TronGrid returned a mismatched transaction id");
		}
		ObjectNode signedTransaction = transaction.deepCopy();
		signedTransaction.put("txID", computedTxId);
		ArrayNode signatures = objectMapper.createArrayNode();
		signatures.add(sign(hash, hexPrivateKey));
		signedTransaction.set("signature", signatures);
		try {
			return new PreparedPayoutTransaction("TRC20", ownerAddress, properties.getUsdtContract(), 0L,
					BigInteger.ZERO, objectMapper.writeValueAsString(signedTransaction), computedTxId);
		}
		catch (Exception exception) {
			throw new WithdrawalException(500, "failed to serialize signed TRON transaction");
		}
	}

	private void validateRawTransaction(byte[] rawBytes, String ownerAddress, String toAddress,
			BigInteger rawAmount, long intentCreatedAt) {
		try {
			Chain.Transaction.raw raw = Chain.Transaction.raw.parseFrom(rawBytes);
			long now = System.currentTimeMillis();
			if (raw.getContractCount() != 1 || raw.getFeeLimit() != properties.getFeeLimit()
					|| raw.getTimestamp() < intentCreatedAt - MAX_TIMESTAMP_SKEW_MILLIS
					|| raw.getTimestamp() > now + MAX_TIMESTAMP_SKEW_MILLIS
					|| raw.getExpiration() <= now
					|| raw.getExpiration() > intentCreatedAt + MAX_TRANSACTION_LIFETIME_MILLIS
					|| raw.getExpiration() <= raw.getTimestamp()) {
				throw rawIntentMismatch();
			}
			Chain.Transaction.Contract transactionContract = raw.getContract(0);
			if (transactionContract.getType()
					!= Chain.Transaction.Contract.ContractType.TriggerSmartContract) {
				throw rawIntentMismatch();
			}
			Any parameter = transactionContract.getParameter();
			Contract.TriggerSmartContract trigger = parameter.unpack(Contract.TriggerSmartContract.class);
			byte[] callData = trigger.getData().toByteArray();
			if (!trigger.getOwnerAddress().equals(ByteString.copyFrom(addressPayload(ownerAddress)))
					|| !trigger.getContractAddress().equals(
							ByteString.copyFrom(addressPayload(properties.getUsdtContract())))
					|| trigger.getCallValue() != 0
					|| trigger.getCallTokenValue() != 0
					|| trigger.getTokenId() != 0
					|| callData.length != 68
					|| !Arrays.equals(Arrays.copyOfRange(callData, 0, 4), TRANSFER_SELECTOR)
					|| !Arrays.equals(Arrays.copyOfRange(callData, 4, 36), abiAddress(toAddress))
					|| !new BigInteger(1, Arrays.copyOfRange(callData, 36, 68)).equals(rawAmount)) {
				throw rawIntentMismatch();
			}
		}
		catch (WithdrawalException exception) {
			throw exception;
		}
		catch (Exception exception) {
			throw rawIntentMismatch();
		}
	}

	private byte[] abiAddress(String address) {
		byte[] payload = addressPayload(address);
		byte[] padded = new byte[32];
		System.arraycopy(payload, 1, padded, 12, 20);
		return padded;
	}

	private byte[] addressPayload(String address) {
		byte[] decoded = base58Decode(address);
		if (decoded.length != 25) {
			throw rawIntentMismatch();
		}
		byte[] payload = Arrays.copyOf(decoded, 21);
		byte[] checksum = Arrays.copyOfRange(decoded, 21, 25);
		if (!Arrays.equals(checksum, Arrays.copyOf(doubleSha256(payload), 4))) {
			throw rawIntentMismatch();
		}
		return payload;
	}

	private WithdrawalException rawIntentMismatch() {
		return new WithdrawalException(502, "TRON raw transaction does not match payout intent");
	}

	private JsonNode triggersmartcontract(String ownerAddress, String parameter) {
		Map<String, Object> body = Map.of(
				"owner_address", ownerAddress,
				"contract_address", properties.getUsdtContract(),
				"function_selector", "transfer(address,uint256)",
				"parameter", parameter,
				"fee_limit", properties.getFeeLimit(),
				"visible", true);
		return postJson(properties.getNodeUrl() + "/wallet/triggersmartcontract", body);
	}

	public PayoutBroadcastResult broadcastSignedTransaction(String signedRawTransaction, String expectedTxHash) {
		try {
			JsonNode transaction = objectMapper.readTree(signedRawTransaction);
			if (!expectedTxHash.equalsIgnoreCase(transaction.path("txID").asText())) {
				return PayoutBroadcastResult.unknown("signed transaction hash mismatch");
			}
			JsonNode result = postJson(properties.getNodeUrl() + "/wallet/broadcasttransaction", transaction);
			if (result.path("result").asBoolean(false)) {
				return PayoutBroadcastResult.accepted();
			}
			String code = result.path("code").asText("");
			String message = result.path("message").asText(code);
			if ("DUP_TRANSACTION_ERROR".equals(code)) {
				return PayoutBroadcastResult.accepted();
			}
			if ("TRANSACTION_EXPIRATION_ERROR".equals(code)) {
				return new PayoutBroadcastResult(PayoutBroadcastDisposition.EXPIRED, message);
			}
			if ("SIGERROR".equals(code) || "CONTRACT_VALIDATE_ERROR".equals(code)) {
				return PayoutBroadcastResult.rejected(message);
			}
			return PayoutBroadcastResult.unknown(message);
		}
		catch (Exception exception) {
			return PayoutBroadcastResult.unknown(exception.getMessage());
		}
	}

	public PayoutChainStatus queryTransactionStatus(String txHash) {
		try {
			JsonNode transactionInfo = postJson(properties.getNodeUrl() + "/wallet/gettransactioninfobyid",
					Map.of("value", txHash));
			if (transactionInfo == null || transactionInfo.isEmpty()) {
				return PayoutChainStatus.of(PayoutChainState.NOT_FOUND, 0);
			}
			String receiptResult = transactionInfo.path("receipt").path("result").asText("SUCCESS");
			String result = transactionInfo.path("result").asText("SUCCESS");
			if (!"SUCCESS".equalsIgnoreCase(receiptResult) || !"SUCCESS".equalsIgnoreCase(result)) {
				return new PayoutChainStatus(PayoutChainState.FAILED, 0,
						transactionInfo.path("resMessage").asText(receiptResult));
			}
			long blockNumber = transactionInfo.path("blockNumber").asLong(-1);
			if (blockNumber < 0) {
				return PayoutChainStatus.of(PayoutChainState.PENDING, 0);
			}
			JsonNode currentBlock = postJson(properties.getNodeUrl() + "/wallet/getnowblock", Map.of());
			long latestBlock = currentBlock.path("block_header").path("raw_data").path("number").asLong(blockNumber);
			long depth = Math.max(0, latestBlock - blockNumber + 1);
			int confirmations = (int) Math.min(Integer.MAX_VALUE, depth);
			return PayoutChainStatus.of(PayoutChainState.CONFIRMED, confirmations);
		}
		catch (Exception exception) {
			return PayoutChainStatus.unknown(exception.getMessage());
		}
	}

	private JsonNode getAccount(String address) {
		return postJson(properties.getNodeUrl() + "/wallet/getaccount",
				Map.of("address", address, "visible", true));
	}

	/**
	 * Fetch recent incoming TRC20 USDT transfers to an address. Returns a list of {txHash, amount}
	 * pairs for matching against pending VIP orders.
	 */
	public List<IncomingTransfer> fetchIncomingUsdtTransfers(String address, String contract, int limit,
			OffsetDateTime earliest) {
		try {
			List<IncomingTransfer> transfers = new ArrayList<>();
			String fingerprint = null;
			int pages = 0;
			do {
				IncomingTransferPage page = fetchIncomingUsdtTransferPage(address, contract, limit, fingerprint);
				transfers.addAll(page.transfers().stream()
						.filter(transfer -> earliest == null || transfer.blockTimestamp() == null
								|| !transfer.blockTimestamp().isBefore(earliest))
						.toList());
				pages++;
				boolean reachedEarliest = earliest != null && page.transfers().stream()
						.map(IncomingTransfer::blockTimestamp)
						.filter(java.util.Objects::nonNull)
						.anyMatch(timestamp -> timestamp.isBefore(earliest));
				fingerprint = reachedEarliest ? null : page.nextFingerprint();
			} while (fingerprint != null && !fingerprint.isBlank()
					&& pages < properties.getIncomingTransferMaxPages());
			if (fingerprint != null && !fingerprint.isBlank()) {
				log.warn("TRON incoming transfer pagination reached configured page limit {} for address {}",
						properties.getIncomingTransferMaxPages(), address);
			}
			return transfers;
		}
		catch (Exception exception) {
			throw new WithdrawalException(503, "failed to fetch TRC20 transfers: " + exception.getMessage());
		}
	}

	public IncomingTransferPage fetchIncomingUsdtTransferPage(String address, String contract, int limit,
			String fingerprint) {
		try {
			String cursor = fingerprint == null || fingerprint.isBlank() ? ""
					: "&fingerprint=" + URLEncoder.encode(fingerprint, StandardCharsets.UTF_8);
			HttpRequest.Builder builder = HttpRequest.newBuilder()
					.uri(URI.create(String.format("%s/v1/accounts/%s/transactions/trc20"
							+ "?limit=%d&contract_address=%s&only_to=true&only_confirmed=true"
							+ "&order_by=block_timestamp,desc%s",
							properties.getNodeUrl(), address, limit, contract, cursor)))
					.timeout(Duration.ofSeconds(15))
					.header("Accept", "application/json");
			if (!properties.getApiKey().isBlank()) {
				builder.header("TRON-PRO-API-KEY", properties.getApiKey());
			}
			HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() < 200 || response.statusCode() >= 300) {
				throw new WithdrawalException(503, "TronGrid transfer query failed with HTTP " + response.statusCode());
			}
			JsonNode root = objectMapper.readTree(response.body());
			JsonNode data = root.path("data");
			List<IncomingTransfer> transfers = new ArrayList<>();
			if (data.isArray()) {
				for (JsonNode tx : data) {
					String toAddress = tx.path("to").asText("");
					if (!address.equals(toAddress)) {
						continue;
					}
					String txHash = tx.path("transaction_id").asText("");
					String transferContract = tx.path("token_info").path("address").asText("");
					BigDecimal rawAmount = new BigDecimal(tx.path("value").asText("0"));
					BigDecimal amount = rawAmount.divide(USDT_DECIMALS, 6, RoundingMode.DOWN);
					long timestampMillis = tx.path("block_timestamp").asLong(0);
					OffsetDateTime blockTimestamp = timestampMillis > 0
							? OffsetDateTime.ofInstant(Instant.ofEpochMilli(timestampMillis), ZoneOffset.UTC)
							: null;
					boolean successful = "Transfer".equalsIgnoreCase(tx.path("type").asText("Transfer"));
					transfers.add(new IncomingTransfer(txHash, amount, toAddress, transferContract, blockTimestamp,
							0, successful));
				}
			}
			return new IncomingTransferPage(transfers, root.path("meta").path("fingerprint").asText(null));
		}
		catch (Exception exception) {
			throw new WithdrawalException(503, "failed to fetch TRC20 transfers: " + exception.getMessage());
		}
	}

	public record IncomingTransfer(String txHash, BigDecimal amount, String recipient, String contract,
			OffsetDateTime blockTimestamp, int confirmationCount, boolean successful) {
	}

	public IncomingTransfer verifyIncomingTransfer(IncomingTransfer transfer) {
		PayoutChainStatus status = queryTransactionStatus(transfer.txHash());
		boolean successful = transfer.successful() && status.state() == PayoutChainState.CONFIRMED;
		return new IncomingTransfer(transfer.txHash(), transfer.amount(), transfer.recipient(), transfer.contract(),
				transfer.blockTimestamp(), status.confirmations(), successful);
	}

	public IncomingTransfer fetchIncomingUsdtTransfer(String txHash, String recipient, String contract) {
		try {
			String url = properties.getNodeUrl() + "/v1/transactions/"
					+ URLEncoder.encode(txHash, StandardCharsets.UTF_8) + "/events?only_confirmed=true";
			JsonNode data = getJson(url).path("data");
			if (data.isArray()) {
				for (JsonNode event : data) {
					if (!"Transfer".equalsIgnoreCase(event.path("event_name").asText())) {
						continue;
					}
					String eventContract = normalizeEventAddress(event.path("contract_address").asText());
					String eventRecipient = normalizeEventAddress(event.path("result").path("to").asText());
					if (!contract.equals(eventContract) || !recipient.equals(eventRecipient)) {
						continue;
					}
					BigDecimal amount = new BigDecimal(event.path("result").path("value").asText("0"))
							.divide(USDT_DECIMALS, 6, RoundingMode.DOWN);
					long timestamp = event.path("block_timestamp").asLong(0);
					return new IncomingTransfer(txHash, amount, recipient, contract,
							OffsetDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneOffset.UTC), 0, true);
				}
			}
			throw new WithdrawalException(409, "transaction has no matching TRC20 transfer event");
		}
		catch (WithdrawalException exception) {
			throw exception;
		}
		catch (Exception exception) {
			throw new WithdrawalException(503, "failed to fetch TRC20 transfer event: " + exception.getMessage());
		}
	}

	private String normalizeEventAddress(String value) {
		if (TronAddress.isValid(value)) {
			return value;
		}
		String hex = value == null ? "" : value.replaceFirst("^(0x|0X)", "");
		if (hex.length() == 42 && hex.startsWith("41") && hex.matches("(?i)[0-9a-f]{42}")) {
			return base58CheckEncode(hexStringToBytes(hex));
		}
		if (hex.length() == 40 && hex.matches("(?i)[0-9a-f]{40}")) {
			byte[] payload = new byte[21];
			payload[0] = 0x41;
			System.arraycopy(hexStringToBytes(hex), 0, payload, 1, 20);
			return base58CheckEncode(payload);
		}
		return value;
	}

	private JsonNode getJson(String url) throws Exception {
		HttpRequest.Builder builder = HttpRequest.newBuilder().uri(URI.create(url))
				.timeout(Duration.ofSeconds(15)).header("Accept", "application/json").GET();
		if (!properties.getApiKey().isBlank()) {
			builder.header("TRON-PRO-API-KEY", properties.getApiKey());
		}
		HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
		if (response.statusCode() < 200 || response.statusCode() >= 300) {
			throw new WithdrawalException(503, "TronGrid event query failed with HTTP " + response.statusCode());
		}
		return objectMapper.readTree(response.body());
	}

	public record IncomingTransferPage(List<IncomingTransfer> transfers, String nextFingerprint) {
	}


	/**
	 * Encode the transfer(address,uint256) parameters as ABI hex: 32-byte address + 32-byte amount.
	 */
	private String encodeTransferParams(String toBase58Address, BigInteger amount) {
		byte[] addressBytes = base58Decode(toBase58Address);
		// address is 21 bytes (0x41 prefix + 20 bytes); take last 20, pad to 32
		byte[] addr20 = Arrays.copyOfRange(addressBytes, addressBytes.length - 20, addressBytes.length);
		byte[] paddedAddr = new byte[32];
		System.arraycopy(addr20, 0, paddedAddr, 32 - 20, 20);

		byte[] amountBytes = amount.toByteArray();
		byte[] paddedAmount = new byte[32];
		System.arraycopy(amountBytes, 0, paddedAmount, 32 - amountBytes.length, amountBytes.length);

		return bytesToHex(paddedAddr) + bytesToHex(paddedAmount);
	}

	private String sign(byte[] hash, String hexPrivateKey) {
		BigInteger privateKey = new BigInteger(1, hexStringToBytes(hexPrivateKey));
		ECDSASigner signer = new ECDSASigner();
		signer.init(true, new ECPrivateKeyParameters(privateKey, DOMAIN));
		byte[] hashPadded = padTo32(hash);
		BigInteger[] rs = signer.generateSignature(hashPadded);
		BigInteger otherS = DOMAIN.getN().subtract(rs[1]);
		BigInteger s = rs[1].compareTo(otherS) <= 0 ? rs[1] : otherS;
		int recId = findRecoveryId(hashPadded, privateKey, rs[0], s);
		return String.format("%064x", rs[0]) + String.format("%064x", s) + String.format("%02x", recId);
	}

	@Deprecated
	public String transferUSDT(String hexPrivateKey, String toAddress, BigDecimal usdtAmount) {
		PreparedPayoutTransaction prepared = prepareTransfer(hexPrivateKey, toAddress, usdtAmount);
		PayoutBroadcastResult result = broadcastSignedTransaction(prepared.signedRawTransaction(), prepared.txHash());
		if (result.disposition() != PayoutBroadcastDisposition.ACCEPTED) {
			throw new WithdrawalException(502, "TRC20 broadcast failed: " + result.detail());
		}
		return prepared.txHash();
	}

	private static byte[] padTo32(byte[] hash) {
		byte[] padded = new byte[32];
		int srcPos = Math.max(0, hash.length - 32);
		int length = hash.length - srcPos;
		System.arraycopy(hash, srcPos, padded, 32 - length, length);
		return padded;
	}

	private int findRecoveryId(byte[] hash, BigInteger privateKey, BigInteger r, BigInteger s) {
		for (int recId = 0; recId < 4; recId++) {
			try {
				ECPoint q = recoverFromSignature(recId, hash, r, s);
				if (q != null) {
					ECPoint expectedQ = DOMAIN.getG().multiply(privateKey).normalize();
					if (q.equals(expectedQ)) {
						return recId;
					}
				}
			}
			catch (Exception ignored) {
				// try next
			}
		}
		return 0;
	}

	private ECPoint recoverFromSignature(int recId, byte[] hash, BigInteger r, BigInteger s) {
		BigInteger n = DOMAIN.getN();
		BigInteger e = new BigInteger(1, hash);
		if (r.compareTo(BigInteger.ONE) < 0 || r.compareTo(n) >= 0) {
			return null;
		}
		if (s.compareTo(BigInteger.ONE) < 0 || s.compareTo(n) >= 0) {
			return null;
		}
		BigInteger x = r;
		if ((recId & 2) != 0) {
			x = x.add(n);
		}
		// Encode the x-coordinate as a compressed point: prefix byte (2 or 3) + x(32 bytes)
		byte[] encoded = new byte[33];
		encoded[0] = (byte) ((recId & 1) == 1 ? 0x03 : 0x02);
		byte[] xBytes = x.toByteArray();
		int copyLen = Math.min(32, xBytes.length);
		System.arraycopy(xBytes, xBytes.length - copyLen, encoded, 33 - copyLen, copyLen);
		ECPoint R = DOMAIN.getCurve().decodePoint(encoded);
		if (R == null) {
			return null;
		}
		BigInteger eInv = BigInteger.ZERO.subtract(e.mod(n));
		BigInteger rInv = r.modInverse(n);
		BigInteger srInv = rInv.multiply(s).mod(n);
		BigInteger eInvRInv = rInv.multiply(eInv).mod(n);
		ECPoint q = ECAlgorithms.sumOfTwoMultiplies(DOMAIN.getG(), eInvRInv, R, srInv);
		return q.normalize();
	}

	// --- HTTP ---

	private JsonNode postJson(String url, Object body) {
		try {
			HttpRequest.Builder builder = HttpRequest.newBuilder()
					.uri(URI.create(url))
					.timeout(Duration.ofSeconds(30))
					.header("Content-Type", "application/json")
					.header("Accept", "application/json")
					.POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)));
			if (!properties.getApiKey().isBlank()) {
				builder.header("TRON-PRO-API-KEY", properties.getApiKey());
			}
			HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
			return objectMapper.readTree(response.body());
		}
		catch (Exception exception) {
			throw new WithdrawalException(503, "TronGrid request failed: " + exception.getMessage());
		}
	}

	// --- Crypto utilities ---

	private static byte[] keccak256(byte[] input) {
		org.bouncycastle.crypto.digests.KeccakDigest digest = new org.bouncycastle.crypto.digests.KeccakDigest(256);
		byte[] output = new byte[digest.getDigestSize()];
		digest.update(input, 0, input.length);
		digest.doFinal(output, 0);
		return output;
	}

	private static byte[] sha256(byte[] input) {
		try {
			return MessageDigest.getInstance("SHA-256").digest(input);
		}
		catch (Exception exception) {
			throw new IllegalStateException("SHA-256 not available", exception);
		}
	}

	private static byte[] doubleSha256(byte[] input) {
		return sha256(sha256(input));
	}

	private static byte[] hexStringToBytes(String hex) {
		hex = hex.replace("0x", "");
		int len = hex.length();
		byte[] data = new byte[len / 2];
		for (int i = 0; i < len; i += 2) {
			data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
					+ Character.digit(hex.charAt(i + 1), 16));
		}
		return data;
	}

	private static String bytesToHex(byte[] bytes) {
		StringBuilder sb = new StringBuilder();
		for (byte b : bytes) {
			sb.append(String.format("%02x", b));
		}
		return sb.toString();
	}

	private static String base58CheckEncode(byte[] payload) {
		byte[] checksum = Arrays.copyOfRange(doubleSha256(payload), 0, 4);
		byte[] full = new byte[payload.length + checksum.length];
		System.arraycopy(payload, 0, full, 0, payload.length);
		System.arraycopy(checksum, 0, full, payload.length, checksum.length);
		return base58Encode(full);
	}

	private static final String BASE58_ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";

	private static String base58Encode(byte[] input) {
		BigInteger num = new BigInteger(1, input);
		StringBuilder encoded = new StringBuilder();
		while (num.compareTo(BigInteger.ZERO) > 0) {
			BigInteger[] divRem = num.divideAndRemainder(BigInteger.valueOf(58));
			encoded.insert(0, BASE58_ALPHABET.charAt(divRem[1].intValue()));
			num = divRem[0];
		}
		for (byte b : input) {
			if (b == 0) {
				encoded.insert(0, '1');
			}
			else {
				break;
			}
		}
		return encoded.toString();
	}

	private static byte[] base58Decode(String input) {
		BigInteger num = BigInteger.ZERO;
		for (int i = 0; i < input.length(); i++) {
			int digit = BASE58_ALPHABET.indexOf(input.charAt(i));
			if (digit < 0) {
				return new byte[0];
			}
			num = num.multiply(BigInteger.valueOf(58)).add(BigInteger.valueOf(digit));
		}
		byte[] raw = num.toByteArray();
		if (raw.length > 0 && raw[0] == 0) {
			raw = Arrays.copyOfRange(raw, 1, raw.length);
		}
		int leadingOnes = 0;
		while (leadingOnes < input.length() && input.charAt(leadingOnes) == '1') {
			leadingOnes++;
		}
		byte[] decoded = new byte[leadingOnes + raw.length];
		System.arraycopy(raw, 0, decoded, leadingOnes, raw.length);
		return decoded;
	}

	// Minimal EC point math for recovery
	private static class ECAlgorithms {
		static ECPoint sumOfTwoMultiplies(ECPoint p1, BigInteger a1, ECPoint p2, BigInteger a2) {
			return p1.multiply(a1).add(p2.multiply(a2)).normalize();
		}
	}
}
