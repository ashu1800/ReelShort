# TronGrid Rate Limit Recovery Design

## Problem

Production uses the public `https://api.trongrid.io` endpoint without an API key. A two-item payout preflight performs a USDT balance query, two exact transfer simulations, a chain-parameter query, an account-resource query and a TRX balance query. Bursts from the same server IP can therefore receive HTTP 429 even though the wallet, private key and payout intent are valid.

## Decision

Add client-side pacing and bounded 429 recovery inside `TronClient`.

- Start replay-safe TRON RPC requests at least 250 ms apart, approximately four requests per second per backend instance.
- Retry HTTP 429 at most three times after the original request. Honor a positive `Retry-After` seconds value when present; otherwise use 1, 2 and 4 seconds.
- Cache the `getEnergyFee` and `getTransactionFee` pair for five minutes. Balances, account resources, transfer simulations and transaction status remain uncached.
- Return a Chinese 503 message after retries are exhausted so the administrator knows the node is busy and the payout was not executed.
- Never retry `/wallet/broadcasttransaction`. Its result may be uncertain after a response failure, and automatic replay would weaken the payout outbox boundary.

The existing optional `TRON-PRO-API-KEY` header remains supported. Configuring a key is recommended but is not required for correctness.

## Request Classification

Replay-safe calls include account/balance queries, chain parameters, account resources, transaction status, event queries and unsigned `triggerconstantcontract`/`triggersmartcontract` construction. These calls do not broadcast a signed transaction.

Broadcast is sent once. A 429 or transport failure remains an unknown broadcast result and is handled by the existing persistent attempt recovery flow.

## Configuration

Add validated `TronProperties` defaults:

- `request-interval=250ms`
- `rate-limit-retries=3`
- `retry-initial-delay=1s`
- `chain-parameter-cache-ttl=5m`

Environment overrides are exposed for operations, while production works safely with the defaults.

## Concurrency And Cache

A synchronized reservation method assigns each outgoing RPC a start slot. Threads wait outside the state update after reserving their slot, preventing simultaneous bursts while avoiding a monitor held during sleep or network I/O.

The chain-price cache is a single immutable pair with an expiry timestamp. Refresh is synchronized so concurrent payouts do not all refresh an expired cache. Failed refreshes are not cached.

## Testing

- Verify a replay-safe request retries 429 and succeeds on the next response.
- Verify exhausted 429 retries return the Chinese node-busy message.
- Verify `Retry-After` is honored through an injected test sleeper without real delay.
- Verify broadcast is attempted exactly once on HTTP 429.
- Verify repeated fee quotes reuse cached chain parameters while still querying simulations and current resources.
- Verify request pacing assigns at least the configured spacing through an injected monotonic clock/sleeper.

No database migration or new dependency is required.
