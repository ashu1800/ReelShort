# TRON USDT Balance Query Fix Design

## Problem

The payout preflight derives the TRON hot-wallet address from the submitted private key, then reads USDT from `/wallet/getaccount`. TronGrid may return a valid account response without the optional `trc20` array. The current implementation treats that missing field as a zero balance, so a wallet with 20 USDT is reported as having 0 USDT.

Production evidence for the derived hot-wallet address showed:

- `/wallet/getaccount`: HTTP 200, no `trc20` field.
- `/v1/accounts/{address}`: 20 USDT.
- USDT contract `balanceOf(address)`: 20 USDT.

The private-key derivation and wallet address are therefore correct; the balance data source is wrong.

## Considered Approaches

### 1. Query the USDT contract directly (selected)

Call `/wallet/triggerconstantcontract` with `balanceOf(address)`, ABI-encode the derived address as a 32-byte argument, and decode the first `constant_result` value as the 6-decimal USDT balance.

This is the authoritative contract state used by transfers and does not depend on optional account metadata or an indexed API.

### 2. Use TronGrid v1 account indexing

Read `trc20` from `/v1/accounts/{address}`. This is simple and currently returns the correct amount, but it is an indexed view and may lag the chain.

### 3. Fall back from `/wallet/getaccount` to v1

Keep the current query and use v1 only when `trc20` is missing. This preserves two data paths and makes zero-versus-error behavior harder to reason about without adding reliability over a direct contract call.

## Design

`TronClient.getUsdtBalance` will call `triggerconstantcontract` with:

- `owner_address`: the derived hot-wallet address.
- `contract_address`: the configured TRC20 USDT contract.
- `function_selector`: `balanceOf(address)`.
- `parameter`: the Base58Check address payload with the TRON `0x41` prefix removed, left-padded to 32 bytes.
- `visible`: `true`.

The first `constant_result` value is decoded as an unsigned hexadecimal integer and divided by `1_000_000`. A node business failure, missing result, blank result, malformed hexadecimal value, or oversized response returns a 503 balance-query error. These states must never become a zero balance. A valid encoded zero remains `0 USDT`.

TRX balance lookup and the existing 100 TRX configured fee-limit preflight remain unchanged. With the current production wallet, fixing the USDT lookup will expose the next independent check: approximately 19.97 TRX is below the configured 100 TRX per-transaction maximum.

## Tests

HTTP-level `TronClientTests` will verify:

- The request uses `balanceOf(address)` and the correct 32-byte ABI address parameter.
- A `constant_result` of `0x1312d00` returns exactly 20 USDT.
- A successful HTTP response without `constant_result` throws a balance-query error instead of returning zero.
- A node business failure throws a balance-query error.

No API signature or database schema changes are required. This is a contained bug fix in the withdrawal module.
