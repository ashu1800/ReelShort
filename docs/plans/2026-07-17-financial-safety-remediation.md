# Financial Safety Remediation Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 修复审查确认的资金、权限、迁移、链上恢复、前后端契约和 content-provider TLS 问题，使自动打款与自动收款具备可恢复、可审计和可测试的状态边界。

**Architecture:** 提现采用数据库持久化 payout outbox：请求内验证 TOTP 和后台提交的私钥，事务内签名并保存确定性交易，提交后广播，后台任务按 txHash 重播和确认，确认后再原子结算冻结积分。VIP 收款按订单快照和唯一 txHash 核验；管理员、钱包和积分操作补齐权限、二次验证、幂等与数据库约束。

**Tech Stack:** Java 17、Spring Boot、Spring Data JPA、Flyway、PostgreSQL/H2、Web3j、Bouncy Castle、Vue 3/TypeScript、Kotlin/Compose、Python/Flask/pytest。

---

### Task 1: 安全迁移与资金数据模型

**Files:**
- Modify: `backend/src/main/resources/db/migration/V13__refactor_auth_vip.sql`
- Modify: `backend/src/main/resources/db/migration/V18__fair_mode_fractional_field.sql`
- Modify: `backend/src/main/resources/db/migration/V19__wallet_network_erc20.sql`
- Create: `backend/src/main/resources/db/migration/V20__financial_safety.sql`
- Create: `backend/src/test/java/com/reelshort/backend/migration/FinancialMigrationTests.java`

**Step 1: Write failing migration tests**

增加测试验证 V13 不包含清空 `users`、`point_accounts`、`withdrawal_requests` 的语句，V19 不把 Tron 地址直接改成 ERC20；使用 PostgreSQL 兼容 schema 验证 V20 创建 payout attempt、热钱包 nonce、VIP 快照、唯一索引、订单写权限、积分幂等字段和账户 CHECK 约束。

**Step 2: Run RED**

Run: `backend/gradlew.bat test --tests "com.reelshort.backend.migration.FinancialMigrationTests"`

Expected: FAIL，指出危险 DELETE/UPDATE 仍存在或 V20 不存在。

**Step 3: Implement minimal safe migrations**

- V13 保留旧列和旧表，不删除用户或资金数据，仅增加新认证/VIP schema。
- V18 删除依赖清库的注释与逻辑假设。
- V19 改为无破坏迁移，不修改历史地址。
- V20 新增 `withdrawal_payout_attempts`、`hot_wallet_nonces`、VIP 快照字段、唯一约束、`ORDER_WRITE` 权限、积分幂等键和账户约束。

**Step 4: Run GREEN**

Run targeted migration tests, then `backend/gradlew.bat test`.

**Step 5: Commit**

`git commit -m "fix(migration): preserve financial data and add payout schema"`

### Task 2: 提现 outbox、签名和确认状态机

**Files:**
- Create: `backend/src/main/java/com/reelshort/backend/withdrawal/WithdrawalPayoutAttempt.java`
- Create: `backend/src/main/java/com/reelshort/backend/withdrawal/WithdrawalPayoutAttemptRepository.java`
- Create: `backend/src/main/java/com/reelshort/backend/withdrawal/WithdrawalPayoutStatus.java`
- Create: `backend/src/main/java/com/reelshort/backend/withdrawal/WithdrawalPayoutCoordinator.java`
- Create: `backend/src/main/java/com/reelshort/backend/withdrawal/WithdrawalPayoutConfirmationService.java`
- Modify: `backend/src/main/java/com/reelshort/backend/withdrawal/WithdrawalService.java`
- Modify: `backend/src/main/java/com/reelshort/backend/withdrawal/WithdrawalRequest.java`
- Modify: `backend/src/main/java/com/reelshort/backend/withdrawal/WithdrawalStatus.java`
- Modify: `backend/src/main/java/com/reelshort/backend/withdrawal/TronClient.java`
- Modify: `backend/src/main/java/com/reelshort/backend/withdrawal/EthereumClient.java`
- Test: `backend/src/test/java/com/reelshort/backend/withdrawal/WithdrawalPayoutCoordinatorTests.java`
- Test: `backend/src/test/java/com/reelshort/backend/withdrawal/WithdrawalPayoutIntegrationTests.java`
- Test: `backend/src/test/java/com/reelshort/backend/withdrawal/TronClientTests.java`
- Test: `backend/src/test/java/com/reelshort/backend/withdrawal/EthereumClientTests.java`

**Step 1: Write failing state-machine tests**

覆盖：事务提交前不广播；同一 attempt 只签名一次；PREPARED 重播同一 raw tx；RPC 超时进入结果未知；确认前不扣冻结积分；确认成功后只结算一次；明确失败可重试；人工核对禁止新签名。

**Step 2: Run RED**

Run: `backend/gradlew.bat test --tests "com.reelshort.backend.withdrawal.*Payout*"`

Expected: FAIL because payout model/coordinator do not exist.

**Step 3: Implement payout model and transaction boundary**

把数据库事务方法移到独立 Spring Bean。`prepare` 锁定提现单、验证 `PENDING`、分配 nonce/参数、保存 signed raw transaction 和 txHash，状态改为 `PREPARED`。使用 after-commit 或独立调用广播，外部 RPC 不进入数据库事务。

**Step 4: Implement deterministic chain signing**

- Ethereum 禁止 nonce 查询失败回退 0；数据库按 hot wallet 地址分配 nonce；保存 signed raw transaction 和确定性 txHash。
- TRON 使用 `r+s+recoveryId`，构造节点接受的已签名交易序列化格式。
- 两条链提供 `broadcastSignedTransaction` 和 `queryTransactionStatus`，返回明确的 pending/confirmed/failed/unknown 与确认数。

**Step 5: Implement confirmation settlement**

确认服务扫描 PREPARED/BROADCASTED attempts，重播同一 raw tx 或查询状态；链上确认后事务内锁定 attempt、withdrawal 和 point account，扣冻结积分、写流水、标记 CONFIRMED。所有入口必须幂等。

**Step 6: Run GREEN and full withdrawal tests**

Run targeted tests and `backend/gradlew.bat test --tests "com.reelshort.backend.withdrawal.*"`.

**Step 7: Commit**

`git commit -m "fix(withdrawal): add durable payout outbox and confirmation"`

### Task 3: 提现后台 API、审计和 Admin Web

**Files:**
- Modify: `backend/src/main/java/com/reelshort/backend/withdrawal/AdminWithdrawalController.java`
- Modify: `backend/src/main/java/com/reelshort/backend/withdrawal/WithdrawalApprovalRequest.java`
- Modify: `backend/src/main/java/com/reelshort/backend/withdrawal/BatchWithdrawalRequest.java`
- Modify: `backend/src/main/java/com/reelshort/backend/withdrawal/BatchWithdrawalPreviewRequest.java`
- Modify: `backend/src/main/java/com/reelshort/backend/withdrawal/WithdrawalResponse.java`
- Modify: `admin-web/src/services/adminApi.ts`
- Modify: `admin-web/src/views/WithdrawalsView.vue`
- Test: `backend/src/test/java/com/reelshort/backend/withdrawal/AdminWithdrawalControllerTests.java`

**Step 1: Write failing controller tests**

验证预览不接受私钥；执行接口需要 TOTP 和按链私钥；派生地址必须等于配置热钱包地址；私钥永不出现在响应或审计；部分批量失败逐笔返回；所有状态转换写审计。

**Step 2: Run RED**

Run controller tests and confirm contract failures.

**Step 3: Implement API and UI contract**

后台页面只在执行时发送私钥，finally、dialog close、route unmount 都清空。页面展示 attempt 状态、txHash、确认数、失败原因与人工核对标记。删除单笔手工 txHash 旧契约。

**Step 4: Run GREEN**

Run backend controller tests and `npm run build` in `admin-web`.

**Step 5: Commit**

`git commit -m "fix(admin): align withdrawal payout workflow"`

### Task 4: VIP 收款唯一匹配与权益交付

**Files:**
- Modify: `backend/src/main/java/com/reelshort/backend/order/VipOrder.java`
- Modify: `backend/src/main/java/com/reelshort/backend/order/VipOrderRepository.java`
- Modify: `backend/src/main/java/com/reelshort/backend/order/VipOrderService.java`
- Modify: `backend/src/main/java/com/reelshort/backend/order/VipAutoConfirmService.java`
- Modify: `backend/src/main/java/com/reelshort/backend/order/AdminVipOrderController.java`
- Modify: `backend/src/main/java/com/reelshort/backend/order/VipOrderResponse.java`
- Modify: `backend/src/main/java/com/reelshort/backend/withdrawal/TronClient.java`
- Create: `backend/src/test/java/com/reelshort/backend/order/VipOrderServiceTests.java`
- Create: `backend/src/test/java/com/reelshort/backend/order/VipAutoConfirmServiceTests.java`
- Create: `backend/src/test/java/com/reelshort/backend/order/AdminVipOrderControllerTests.java`

**Step 1: Write failing VIP tests**

覆盖历史 txHash 重放、交易时间窗、错误地址/合约/金额、未确认或失败交易、一个用户多个 PENDING、99 个尾差耗尽、并发 suffix、地址配置变更、同用户并发续期、只读管理员写操作和手工确认 TOTP/审计。

**Step 2: Run RED**

Run: `backend/gradlew.bat test --tests "com.reelshort.backend.order.Vip*"`

**Step 3: Implement order snapshots and matching**

创建订单时快照收款地址与 `payableAmount`；数据库保证一个用户一个 PENDING 和活动金额唯一。IncomingTransfer 增加 block timestamp、contract、recipient、confirmed/success。自动确认先过期再读取地址，并在锁内消费唯一 txHash。

**Step 4: Implement admin permissions and user locking**

新增 `ORDER_WRITE`，confirm/reject 使用写权限；confirm 需要 TOTP、链上验证与审计。授予 VIP 时悲观锁用户行。

**Step 5: Run GREEN and commit**

`git commit -m "fix(vip): make on-chain receipt matching unique"`

### Task 5: 旧充值、管理员 2FA 与钱包二次验证

**Files:**
- Modify: `backend/src/main/java/com/reelshort/backend/order/OrderController.java`
- Modify: `backend/src/main/java/com/reelshort/backend/order/RechargeOrderService.java`
- Modify: `backend/src/main/java/com/reelshort/backend/order/CreateRechargeOrderRequest.java`
- Modify: `backend/src/main/java/com/reelshort/backend/admin/AdminAuthService.java`
- Modify: `backend/src/main/java/com/reelshort/backend/admin/AdminLoginRequest.java`
- Modify: `backend/src/main/java/com/reelshort/backend/admin/AdminTwoFactorController.java`
- Modify: `backend/src/main/java/com/reelshort/backend/wallet/WalletBindRequest.java`
- Create: `backend/src/main/java/com/reelshort/backend/wallet/WalletUnbindRequest.java`
- Modify: `backend/src/main/java/com/reelshort/backend/wallet/WalletService.java`
- Modify: `backend/src/main/java/com/reelshort/backend/wallet/WalletController.java`
- Modify: `admin-web/src/views/LoginView.vue`
- Test: existing auth/order/commerce tests plus new 2FA tests.

**Step 1: Write failing security tests**

验证旧充值创建返回不支持且不创建订单；启用 TOTP 的管理员登录缺码/错码失败；enable 不能覆盖已有 secret；钱包绑定、更换、解绑缺少或错误当前密码失败；ERC20 零地址失败。

**Step 2: Run RED**

Run targeted order/admin/commerce tests.

**Step 3: Implement minimal security changes**

充值创建停止接受 pointAmount 并返回明确业务错误；管理员登录 DTO 增加可选 totpCode 并在启用时强制验证；enable 已启用时拒绝；钱包写操作验证当前密码。银行卡代码保持不变。

**Step 4: Run GREEN and builds**

Run backend tests and Admin Web build.

**Step 5: Commit**

`git commit -m "fix(security): require step-up checks for financial actions"`

### Task 6: 积分幂等与公平模式对账

**Files:**
- Modify: `backend/src/main/java/com/reelshort/backend/admin/AdminPointAdjustRequest.java`
- Modify: `backend/src/main/java/com/reelshort/backend/admin/AdminUserService.java`
- Modify: `backend/src/main/java/com/reelshort/backend/points/PointTransaction.java`
- Modify: `backend/src/main/java/com/reelshort/backend/points/PointAwardTransaction.java`
- Modify: `backend/src/main/java/com/reelshort/backend/points/PointsService.java`
- Test: `backend/src/test/java/com/reelshort/backend/points/PointsServiceTests.java`
- Test: `backend/src/test/java/com/reelshort/backend/admin/AdminUserControllerTests.java`

**Step 1: Write failing ledger tests**

验证相同 idempotencyKey 的后台调整只执行一次；不同管理员/请求的键作用域明确；公平模式 0.7+0.6 场景中余额变化、流水、claim 与响应一致；数据库约束拒绝非法余额。

**Step 2: Run RED**

Run points/admin tests.

**Step 3: Implement single settlement result**

由 PointAwardTransaction 返回统一的整数余额变化和小数余数结果，所有消费者使用同一结果。后台调整在唯一幂等键冲突时读取首次流水并返回。

**Step 4: Run GREEN and commit**

`git commit -m "fix(points): make ledger adjustments idempotent"`

### Task 7: Content Provider 固定 IP 与 TLS SNI

**Files:**
- Modify: `content-provider/app.py`
- Modify: `content-provider/requirements.txt`
- Modify: `content-provider/tests/test_app.py`

**Step 1: Write failing transport tests**

用受控 HTTPS server/adapter 验证连接目标为解析后的 IP，但 SNI、证书 hostname 和 Host 都是原始域名；重定向逐跳重新解析；私网、回环和 DNS rebinding 仍被阻断。

**Step 2: Run RED**

Run the focused TLS tests and confirm current IP URL implementation fails.

**Step 3: Implement pinned HTTPS transport**

使用支持显式 connection IP、server_hostname 和 assert_hostname 的传输层。保持当前响应字节上限、总 deadline、手工重定向和 diagnostics。

**Step 4: Run GREEN**

Run `pytest -q`, then a real read-only upstream smoke request.

**Step 5: Commit**

`git commit -m "fix(content-provider): preserve TLS SNI with DNS pinning"`

### Task 8: Android 与后台契约回归

**Files:**
- Modify: `android-app/app-core/src/main/kotlin/com/reelshort/app/network/ReelShortApiClient.kt`
- Modify: `android-app/app-core/src/main/kotlin/com/reelshort/app/network/OkHttpReelShortApiClient.kt`
- Modify: `android-app/app-core/src/main/kotlin/com/reelshort/app/state/AppStateController.kt`
- Modify: `android-app/app/src/main/java/com/reelshort/app/ui/screens/account/AccountScreen.kt`
- Modify: `android-app/app-core/src/test/kotlin/com/reelshort/app/state/AppStateControllerTest.kt`
- Modify: related DTO and fake client files.

**Step 1: Repair tests to current domain and add failing wallet/VIP contract tests**

删除已移除 PointTransferRecord 的陈旧测试，更新 bindWallet(network,address,password) 和 unbindWallet(password)，增加 VIP 快照/状态解析测试。

**Step 2: Verify RED for new password contract**

Run `android-app/gradlew.bat :app-core:test` and confirm implementation signature failures.

**Step 3: Implement client/UI changes**

钱包写操作提示并提交当前密码；同步后端 DTO。银行卡 UI 保持不变。

**Step 4: Run GREEN**

Run `:app-core:test`, `assembleDebug`, and `lint`.

**Step 5: Commit**

`git commit -m "fix(android): align wallet and VIP contracts"`

### Task 9: 文档、AGENTS 与全量验证

**Files:**
- Modify: `AGENTS.md`
- Modify: `docs/api/withdrawals.md`
- Modify: `docs/api/orders.md`
- Modify: `docs/api/payment-callback.md`
- Modify: `docs/api/auth-security.md`
- Modify: deployment/config examples as required.

**Step 1: Update architecture and change history**

在 AGENTS 模块描述同步用户名认证、双链自动打款 outbox、VIP 收款快照、钱包密码验证和 content-provider TLS transport；在变更历史顶部增加 2026-07-17 记录。

**Step 2: Run full verification**

- `backend/gradlew.bat test`
- `android-app/gradlew.bat :app-core:test assembleDebug lint`
- `npm run build` in `admin-web`
- `pytest -q` in `content-provider`
- `powershell -ExecutionPolicy Bypass -File scripts/verify-release-baseline.ps1`
- `git diff --check`

**Step 3: Final review**

进行规格符合性审查和代码质量审查，处理所有 P0/P1/P2 回归问题。

**Step 4: Commit and integrate**

按仓库自动化规范 fetch/rebase、提交、推送分支，合并到 master，再推送 master。
