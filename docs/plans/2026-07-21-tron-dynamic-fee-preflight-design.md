# TRON Dynamic Fee Preflight Design

## Problem

TRC20 payout transactions keep a `100 TRX` `feeLimit` as a hard per-transaction safety cap. The current balance preflight incorrectly treats that cap as the expected spend and requires `feeLimit * transactionCount` in the hot wallet. A two-item batch is therefore rejected unless the wallet holds 200 TRX even when a live constant-contract simulation shows about 13 TRX of energy cost per transfer.

## Decision

Use a resource-aware live estimate for the final payout execution preflight while retaining the existing `feeLimit` on the transaction itself.

For each TRC20 withdrawal, the backend will call `triggerconstantcontract` with the exact owner, recipient, contract and amount used by the payout intent. It will sum `energy_used`, query the current `getEnergyFee` and `getTransactionFee` chain parameters, and query the owner's remaining Energy and bandwidth through `getaccountresource`.

The required TRX balance is:

```text
chargeable energy = max(0, total simulated energy - available Energy)
chargeable bandwidth = max(0, count * 400 bytes - available bandwidth)
base fee in sun = chargeable energy * energy price
                + chargeable bandwidth * bandwidth price
required TRX = ceil(base fee * 120%) / 1,000,000
```

The 400-byte allowance is deliberately conservative for a signed TRC20 transfer. The 20% margin covers small changes between simulation and broadcast. Calculations use integer sun and round upward so preflight never understates a fractional sun result.

If a single transfer's simulated energy charge would exceed its configured `feeLimit`, execution fails before signing because the transaction would not have enough fee allowance even if the wallet balance were sufficient.

## Alternatives Considered

1. **Live simulation with resource accounting (selected).** Tracks current chain pricing and wallet resources, while keeping a bounded safety margin. It adds several read-only RPC calls during final execution but no signing or broadcast.
2. **Fixed per-transfer reserve such as 30 TRX.** Simple, but becomes wrong when TRON energy prices or contract execution costs change and does not credit staked Energy.
3. **Keep `feeLimit * count` and only clarify the UI.** Preserves maximum conservatism but continues rejecting valid payouts, so it does not solve the operational problem.

## API And UI Behavior

- Final single and batch execution uses the dynamic quote after deriving the hot-wallet address from the submitted private key.
- The insufficient-balance error identifies the amount as an estimate and continues to show required, current and missing TRX.
- Batch preview remains `MAXIMUM` because it does not receive a private key and the expected public hot-wallet address is optional. It continues to label `feeLimit * count` as an upper bound, not actual consumption.
- The private key remains confined to address derivation and signing. Fee estimation uses only public addresses, amounts and read-only node calls.
- ERC20 and BEP20 behavior is unchanged.

## Failure Handling

- Missing, malformed or unsuccessful simulation, resource or chain-parameter responses fail closed with a 503 balance-estimation error. They are never converted to zero cost.
- Chain parameters must be non-negative and required response fields must be present.
- Integer multiplication uses `BigInteger` to avoid overflow.
- No payout attempt is created when estimation or balance validation fails.

## Testing

- HTTP tests verify exact `transfer(address,uint256)` simulation parameters and parse `energy_used`.
- Tests verify chain parameters, available Energy/bandwidth accounting, 20% upward margin and multiple-transfer aggregation.
- Tests verify malformed node responses fail closed and an estimate above `feeLimit` is rejected.
- Service tests verify final preflight uses the dynamic quote instead of `feeLimit * count`, while preview continues returning `MAXIMUM`.
- Existing signing, transaction-intent validation, actual-fee recording and non-TRON tests remain unchanged.

No database migration or new dependency is required.
