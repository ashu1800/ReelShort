# 2026-10-12 代码审查修复实施计划

**目标：** 按已批准的修复方案落地 `artifacts/code-review-2026-10-12.md` 的 P1 全部条目与 P2/P3 中影响安全、账实一致性和可验证性的条目，并把文档对齐到真实实现。

**架构：** 不引入新模块。资金侧在既有提现状态机上补流水、补逃生口、补幂等；认证侧收敛为用户名 + 密码 + 图形验证码并叠加凭证锁定与限流；内容侧把上游调用移出事务并补 single-flight；部署侧修 Composed 必填键、健康检查、非 root 与备份落点；客户端侧删除不存在的采集面与第三方依赖。

**技术栈：** Spring Boot 3 / Java 17 / Flyway / PostgreSQL、Flask + gunicorn / pytest、Kotlin + Compose / Gradle、Vue 3 + Vite（`node --test` 契约测试）、Docker Compose、PowerShell 发布脚本、GitHub Actions。

工作流编号 W1–W7 与已批准修复方案一致；如其他实现分支使用不同编号，以工作流名称为准。

---

## 工作流总览

| 工作流 | 覆盖问题 | 主要模块 |
| --- | --- | --- |
| W1 资金账实与提现状态机 | P1-2、P1-3、P1-4、P1-1（代码侧）、P2-10、P2-11、P2-18 | `backend/withdrawal`、`backend/points`、`backend/wallet`、Flyway V31、`infra/scripts` |
| W2 认证收敛与凭证防护 | P1-5、P1-6、P1-8（代码侧）、P2-14、P2-16（验证码） | `backend/auth`、`backend/system/security`、`backend/system/ratelimit`、`infra` |
| W3 内容、观看与社交健壮性 | P1-7、P2-1（代码侧）、P2-5、P2-6、P2-7、P2-16（内容源/评论） | `backend/content`、`backend/watch`、`backend/social`、`content-provider/app.py` |
| W4 后端系统面与订单/发布一致性 | P2-12、P2-13、P2-15、P2-17（后端）、P3-12、P3-13、P3-18、P3-19、P3-20 | `backend/system/*`、`backend/order`、`backend/release`、`backend/operations` |
| W5 内容源生产化与单机部署 | P1-1、P1-6（Compose 侧）、P2-4、P2-18、P3-22 | `content-provider`、`infra`、`backend/Dockerfile` |
| W6 客户端与入口加固 | P1-4（Android 侧）、P2-2、P2-17（前端）、P3-1、P3-2、P3-3、P3-5、P3-7、P3-11 | `android-app`、`admin-web` |
| W7 脚本、CI 与文档对齐 | P2-2（门禁）、P3-23、P3-25、第七节文档漂移全表 | `scripts`、`.github/workflows`、`docs`、`AGENTS.md`、`README.md` |

---

## W1 资金账实与提现状态机

### 任务

1. **人工确认补写积分流水**（P1-2）
   - `WithdrawalService.manualConfirmLocked` 在 `deductFrozen` 之后、同一事务内写入 `PointTransaction.withdrawal(userId, totalDeductedPoints, account.balance(), request.id().toString())`，幂等键 `withdrawal:<id>`。
2. **作废卡死 attempt 的逃生口**（P1-3）
   - 新增 `WithdrawalPayoutStatus.VOIDED` 与 `WithdrawalPayoutAttempt.markVoided(...)`，新增 `WithdrawalVoidAttemptRequest`（`@NotBlank @Size(max = 255) reason`）与 `PayoutAttemptReconciler`（只读链上核对，不签名、不广播）。
   - `POST /api/admin/withdrawals/{id}/payout-attempt/void`：`SIGNING`/`PREPARED` 直接作废；`BROADCASTED` 确认数达标转 `MANUAL_REVIEW`，查无或失败才作废，`PENDING`/`UNKNOWN` 返回 409；全部路径写审计。
3. **提现提交幂等与单笔 PENDING**（P2-10）
   - `WithdrawalController.create` 接收可选 `Idempotency-Key` 请求头；仓库新增 `findByIdempotencyKey`；同一用户已存在 `PENDING` 时返回 409。
4. **银行卡入口下线**（P1-4）
   - 删除 `BankCardAttempt`、`BankCardAttemptRepository`、`BankCardBindRequest`；`POST /api/app/wallet/bank-card` 固定 410；`WalletService` 只保留 ERC20 校验，删除 Luhn/CVV/后四位落库与死掉的 TRC20 Base58Check 校验（P3-15）。
5. **索引迁移与备份落点**（P2-11、P1-1、P2-18）
   - V31 新增 `idx_point_transactions_user_created`、`idx_withdrawal_requests_status_network_reviewed`；`.gitignore` 增加 `reelshort-prod-db-backup-*/`、`*.dump`、`database.dump`；根目录 dump 目录迁入 `infra/backups/`；`backup.ps1` 拒绝绝对路径与 `..` 逃逸并在删除前打印待删目录。

### 涉及文件

- `backend/src/main/java/com/reelshort/backend/withdrawal/WithdrawalService.java`
- `backend/src/main/java/com/reelshort/backend/withdrawal/{AdminWithdrawalController,WithdrawalController,WithdrawalRequest,WithdrawalRequestRepository,WithdrawalPayoutAttempt,WithdrawalPayoutStatus,WithdrawalVoidAttemptRequest,PayoutAttemptReconciler,WithdrawalPayoutCoordinator,WithdrawalPayoutTransactionService,WithdrawalPayoutConfirmationService}.java`
- `backend/src/main/java/com/reelshort/backend/points/PointTransaction.java`
- `backend/src/main/java/com/reelshort/backend/wallet/{WalletController,WalletService}.java`（`BankCardAttempt*`、`BankCardBindRequest` 已删除）
- `backend/src/main/resources/db/migration/V31__review_remediation.sql`
- `backend/src/main/resources/application.properties`（`reelshort.withdrawal.payout.enabled` 默认 `false`）
- `backend/src/test/java/com/reelshort/backend/withdrawal/{AdminWithdrawalContractTests,WithdrawalPayoutIntegrationTests}.java`、`backend/src/test/java/com/reelshort/backend/commerce/WalletWithdrawalTransferControllerTests.java`
- `.gitignore`、`infra/scripts/backup.ps1`

### 验收命令

```powershell
# 后端相关测试
cd backend
.\gradlew.bat test --tests "com.reelshort.backend.withdrawal.*" --tests "com.reelshort.backend.commerce.*" --no-daemon

# 校验迁移只做新增/宽松化（无 DROP TABLE / DELETE）
Select-String -Path src\main\resources\db\migration\V31__review_remediation.sql -Pattern "DROP TABLE|TRUNCATE|DELETE FROM"

# 备份脚本路径约束
powershell -ExecutionPolicy Bypass -File ..\infra\scripts\verify-backup-scripts.ps1
```

预期：测试全绿；迁移无破坏性语句；`backup.ps1` 传入 `-OutputRoot ..` 时明确报错退出。

---

## W2 认证收敛与凭证防护

### 任务

1. **认证模型收敛**（P1-8 代码侧）：`AuthController` 只保留 `register`/`login`/`captcha`/`password/change`/`logout`；删除短信发送、改密验证码发送与内部开户接口；Compose 与 `.env.example` 删除 `REELSHORT_SMS_CALLBACK_*`。
2. **验证码哈希化与清理**（P2-16）：`CaptchaChallenge` 的明文答案改为 `@Transient`，落库 `answer_hash`；答错立即作废；新增 `CaptchaCleanupService` 定时清理过期挑战。
3. **凭证失败锁定**（P1-5）：新增 `CredentialAttempt`、`CredentialAttemptRepository`、`CredentialAttemptGuard`、`CredentialGuardProperties`、`CredentialLockedException`；`WalletService` 与 `AuthService.changePassword` 共用计数；锁定判断先于 BCrypt 比对；失败计数 `REQUIRES_NEW`。
4. **错误码调整**（P2-14）：钱包类密码错误由 401 改为 403 `invalid current password`；`GlobalExceptionHandler` 把 `CredentialLockedException` 映射为 429 `too many attempts, try again later`；登录对禁用账号先做 BCrypt 比对再判状态。
5. **限流补齐与 CIDR**（P1-6）：新增 `app-auth-captcha`、`app-wallet-write`、`app-wallet-bind`、`app-withdrawal-create`、`app-social-write` 五条规则；`RateLimitKeyResolver` 支持 CIDR，前缀过宽（IPv4 < /8、IPv6 < /16）启动失败，白名单未覆盖代理时只 WARN 一次；Compose 改为 `${REELSHORT_TRUSTED_PROXIES:?}`。

### 涉及文件

- `backend/src/main/java/com/reelshort/backend/auth/{AuthController,AuthService,CaptchaChallenge,CaptchaChallengeRepository,CaptchaService,TokenHasher,SecurityConfig}.java`（`CaptchaCleanupService` 新增）
- `backend/src/main/java/com/reelshort/backend/system/security/*`（5 个新文件）
- `backend/src/main/java/com/reelshort/backend/system/ratelimit/RateLimitKeyResolver.java`
- `backend/src/main/java/com/reelshort/backend/system/web/{GlobalExceptionHandler,WebMvcConfig,RequestIdFilter}.java`
- `backend/src/main/resources/application.properties`
- `backend/src/test/java/com/reelshort/backend/auth/{AuthServiceTests,CaptchaServiceTests,CaptchaCleanupServiceTests}.java`、`backend/src/test/java/com/reelshort/backend/system/ratelimit/{RateLimitRuleTests,RateLimitKeyResolverTests}.java`、`backend/src/test/java/com/reelshort/backend/TestAppUsers*.java`
- `infra/docker-compose.yml`、`infra/.env.example`、`.env.example`

### 验收命令

```powershell
cd backend
.\gradlew.bat test --tests "com.reelshort.backend.auth.*" --tests "com.reelshort.backend.system.*" --no-daemon

# 确认短信链路在代码中已不存在
Select-String -Path src\main\java -Pattern "phoneNumber|sms|register-phone" -Recurse
```

预期：测试全绿；第二条命令零命中（仅 Flyway 历史 SQL 可保留 `sms_verification_codes` 表定义）。

---

## W3 内容、观看与社交健壮性

### 任务

1. **上游调用移出事务 + single-flight**（P1-7）：新增 `ContentCacheWriter`（每个公开方法自带事务边界）、`ContentCacheWriterProvider`、`ContentCacheWriterConfiguration`、`ShelfSingleFlight`；`ContentCacheService` 改为"事务外取上游 → writer 落库"；顺手把播放缓存的静态锁表改为有界实现并给 `bookId`/`chapterId` 加长度上限（P2-7）。
2. **语言收敛为 `en`**（P2-1）：`ContentLocale` 只保留 `ENGLISH`，任何传入 locale 都解析为 `ENGLISH`；`REELSHORT_CONTENT_REFRESH_LOCALES` 默认 `en`；Flask `SUPPORTED_LOCALES = {"en"}`，删除繁中片库关键词死配置。
3. **观看进度单调**（P2-5）：`WatchProgressTransaction` 对 `progressPercent` 取历史与本次上报的较大值，进度只允许前进；标题/章节等元数据仍按最新上报更新。
4. **内容源容错**（P2-6、P2-16）：`app.py` 抽取 `_as_int`/`_as_list` 容错转换，新增异常处理器把上游结构异常映射为 502 并记录诊断事件；`next_episode` 改为 best-effort；诊断字段截断。
5. **评论分页**（P2-16）：`GET /api/app/social/books/{bookId}/comments` 支持 `page`（默认 0）与 `size`（默认 20、上限 50），响应改为 `{comments, hasMore, page, size}`，伪评论随页裁剪。
6. **请求体长度约束**（P3-18）：`WatchProgressRequest`、`SocialDtos` 增加 `@Size`；`GlobalExceptionHandler` 把 `DataIntegrityViolationException` 映射为 400 而不是 500。

### 涉及文件

- `backend/src/main/java/com/reelshort/backend/content/{ContentCacheService,FlaskContentProvider,ContentBookCacheRepository}.java` + 新增 `ContentCacheWriter.java`、`ContentCacheWriterProvider.java`、`ContentCacheWriterConfiguration.java`、`ShelfSingleFlight.java`
- `backend/src/main/java/com/reelshort/backend/watch/{WatchProgressTransaction,WatchProgressRequest}.java`
- `backend/src/main/java/com/reelshort/backend/social/{SocialController,SocialService,SocialDtos,SocialDisplayFallbacks,CommentRepository}.java`
- `content-provider/app.py`、`content-provider/tests/test_app.py`

### 验收命令

```powershell
python -m pytest content-provider -q
cd backend
.\gradlew.bat test --tests "com.reelshort.backend.content.*" --tests "com.reelshort.backend.watch.*" --tests "com.reelshort.backend.social.*" --no-daemon
```

预期：Flask `85 passed`；后端相关测试全绿。

---

## W4 后端系统面与订单/发布一致性

### 任务

1. **审计独立事务**（P2-12）：`AdminUserService` 等关键管理操作与失败路径改走 `recordIndependent`，新增 `AdminAuditIndependentRecordingTests`。
2. **管理员引导语义**（P2-13）：`AdminRbacBootstrapService` 只在新建管理员时挂 `SUPER_ADMIN`，已存在账号不静默还原角色；`REELSHORT_ADMIN_PASSWORD_HASH` 的覆盖/不覆盖策略在启动日志打印事实。
3. **VIP 订单过期后补单**（P2-15）：`VipOrder.recoverExpired(...)` + `VipPaymentMatcher.matchesLatePayment(...)`，仅管理员人工路径允许对 `EXPIRED` 订单在链上核验通过后补单，审计动作 `VIP_ORDER_RECOVERED_AFTER_EXPIRY`。
4. **系统配置资金校验**（P2-17 后端）：`SystemConfigDefinition` 增加自定义校验钩子，`vip.collection-address` 非空必须是合法 TRON 地址；`SystemConfigUpdateRequest` 由 `@NotBlank` 改为 `@NotNull` 以支持清空。
5. **状态机异常映射**（P2-17/P3）：`IllegalStateException` → 409 `conflict`（不回显异常消息，避免 P2-8 的私钥回显面）。
6. **请求 ID 收敛**（P3-12）：`RequestIdFilter` 只接受 `^[A-Za-z0-9_-]{8,64}$`，否则重新生成 UUID。
7. **发布 versionCode**（P2-3 后端）：`ReleaseService` 的 `republish` 接收并更新 `versionCode`，回退时返回 400。
8. **内容源诊断告警水位线**（P3-20）：`SystemAlertService` 按累计事件数水位线判定，新增 `SystemAlertEvaluationService` 每 5 分钟自动评估，恢复后自动 resolve。
9. **运营任务可观测**（P3-19）：`InternalOperationsService` 吞错分支补日志、消除 N+1。

### 涉及文件

- `backend/src/main/java/com/reelshort/backend/admin/{AdminRbacBootstrapService,AdminUserService,AdminUser}.java`
- `backend/src/main/java/com/reelshort/backend/order/{VipOrder,VipOrderService,VipPaymentMatcher,RechargeOrderRepository,RechargeOrderService}.java`
- `backend/src/main/java/com/reelshort/backend/system/config/{SystemConfigDefinition,SystemConfigRegistry,SystemConfigUpdateRequest,AdminSystemConfigController}.java`
- `backend/src/main/java/com/reelshort/backend/system/alerts/*`、`backend/src/main/java/com/reelshort/backend/system/runtime/ContentProviderRuntimeDependencyChecker.java`
- `backend/src/main/java/com/reelshort/backend/system/web/{GlobalExceptionHandler,RequestIdFilter}.java`
- `backend/src/main/java/com/reelshort/backend/release/{AppRelease,ReleaseService}.java`
- `backend/src/main/java/com/reelshort/backend/operations/InternalOperationsService.java`

### 验收命令

```powershell
cd backend
.\gradlew.bat test --tests "com.reelshort.backend.admin.*" --tests "com.reelshort.backend.order.*" --tests "com.reelshort.backend.release.*" --tests "com.reelshort.backend.system.*" --no-daemon
```

---

## W5 内容源生产化与单机部署

### 任务

1. **内容源非 root + gunicorn + 内部 Token**（P2-16、P3-22）：`content-provider/Dockerfile` 改 `USER nobody` 与 `gunicorn -w 2 --threads 8 -t 30`；除 `/health` 外所有端点要求 `X-Content-Provider-Token`，未配置 `CONTENT_PROVIDER_INTERNAL_TOKEN` 时 fail-closed；后端 `FlaskContentProvider` 默认带上该请求头。
2. **Compose 加固**（P2-4、P3-22）：所有服务 `restart: unless-stopped` 与 `logging.options`（`max-size=10m`、`max-file=3`）；nginx healthcheck 改探 `http://backend:8080/actuator/health`（直连后端容器，避免 SPA 兜底恒为 200）；`REELSHORT_TRUSTED_PROXIES` 与 `REELSHORT_WITHDRAWAL_PAYOUT_ENABLED` 透传。
3. **后端容器非 root**：`backend/Dockerfile` 创建 `reelshort` 账号、预建 `/app/logs` 并 `USER reelshort`。
4. **备份落点**（P1-1、P2-18）：生产库转储移出仓库根目录到 `infra/backups/`（`.gitignore` 忽略 + owner-only ACL），`backup.ps1` 限制 `-OutputRoot` 必须落在仓库内。

### 涉及文件

- `content-provider/Dockerfile`、`content-provider/requirements.txt`、`content-provider/app.py`
- `backend/Dockerfile`、`backend/src/main/java/com/reelshort/backend/content/FlaskContentProvider.java`
- `infra/docker-compose.yml`、`infra/.env.example`、`infra/README.md`、`infra/scripts/backup.ps1`
- `.gitignore`、`.env.example`

### 验收命令

```powershell
# Compose 语法与必填键（需要本机有 Docker）
cd infra
docker compose --env-file .env.example -f docker-compose.yml config --quiet

# 静态校验（不需要 Docker）
powershell -ExecutionPolicy Bypass -File ..\infra\scripts\verify-backup-scripts.ps1

# 备份目录已被忽略
git check-ignore -v infra/backups/
```

预期：`config --quiet` 通过；静态校验通过；`infra/backups/` 命中忽略规则。

---

## W6 客户端与入口加固

### 任务

1. **Android 银行卡入口**（P1-4）：`AccountScreen` 删除卡号/CVV/持卡人表单，只保留"暂不支持"说明；同步删除 `BankCardBindRequestDto`、`submitBankCard` 数据源链路、`MainActivity`/`MainShell` 透传参数；契约测试改为断言 UI 中不存在 CVV 输入框（P3-6）。
2. **本地生成 VIP 二维码**（P3-1）：新增纯 Kotlin `app-core/.../qr/QrMatrixEncoder.kt` 与 `QrCodeImage.kt`，删除对 `api.qrserver.com` 的图片请求。
3. **更新链路收紧**（P3-3、P3-5）：COS 下载域名从 `.myqcloud.com` 后缀匹配收紧为精确桶域名；拒绝 versionCode 降级；APK SHA-256 与文件搬移移出主线程（P3-2）；401 清理本地会话（P3-3）。
4. **删除死代码**（P3-7、P3-11）：删除 `GeoIpClient`（含 `ipapi.co` 外发）、`GeoBlockedScreen`、`GitHubReleaseUpdateClient`、`WithdrawalBottomSheet`；`FakeReelShortApiClient` 移出 main 源集。
5. **admin-web 契约清理**（P2-17）：删除不存在的"订单管理/支付事件"入口与 `phone*` 类型/列（账号唯一标识为 `username`）；资金敏感系统配置保存前二次确认并展示 old→new；提现确认弹窗明确"不签名、不广播、不核验链上交易"。
6. **nginx 加固**（P2-17）：新增 CSP（`style-src 'self' 'unsafe-inline'` 因 Element Plus 必须保留）、`/assets/` 长缓存 `immutable`、`index.html` `no-store`。

### 涉及文件

- `android-app/app-core/src/main/kotlin/com/reelshort/app/**`（新增 `qr/QrMatrixEncoder.kt`，删除 `network/GeoIpClient.kt`、`update/GitHubReleaseUpdateClient.kt`、`network/FakeReelShortApiClient.kt`）
- `android-app/app/src/main/java/com/reelshort/app/**`（新增 `ui/components/QrCodeImage.kt`，删除 `ui/components/GeoBlockedScreen.kt`）
- `admin-web/src/services/adminApi.ts`、`admin-web/src/views/{ContentCacheView,SystemConfigsView,UsersView,WithdrawalsView}.vue`、`admin-web/nginx.conf`、`admin-web/README.md`

### 验收命令

```powershell
# Android JVM 单测（M9 起不再要求签名密钥）
cd android-app
.\gradlew.bat :app-core:test :app:testDebugUnitTest --no-daemon

# 第三方域名与死代码必须零命中
Select-String -Path . -Pattern "api\.qrserver\.com|ipapi\.co|GitHubReleaseUpdateClient" -Recurse

# 后台类型检查 + 构建 + 契约测试
cd ..\admin-web
npm run build
npm run test:contracts
```

预期：Android JVM 单测在无 `ANDROID_SIGNING_*` 环境下通过；第三条命令零命中；`npm run build` 通过；契约测试 `10 pass`。

---

## W7 脚本、CI 与文档对齐

### 任务

1. **发布基线强化**（P2-2、P3-23）：`scripts/verify-release-baseline.ps1` 新增"未提交的已跟踪文件修改"检查与 `-AllowDirtyTree`；Android 打包可单独跳过（`-SkipAndroidPackage`，无签名密钥时仅跑 JVM 单测）；新增 `-SkipInfraChecks`、`-SkipScriptTests`、`-SkipAdminWebContracts`。
2. **CI 门禁**（P3-25）：新增 `.github/workflows/ci.yml`（后端 Gradle、pytest、admin-web 构建 + 契约测试、Android JVM 单测，均不需要签名密钥）；`.github/workflows/security.yml`（gitleaks）保留。
3. **文档对齐**（P1-8、第七节全表）：
   - 重写 `docs/api/auth-user.md` 为用户名 + 密码 + 图形验证码流程；删除短信/手机号/内部开户接口说明。
   - `docs/api/content-cache.md`、`docs/api/backend-foundation.md` 的 `zh-TW` 与"按 locale 分桶/双语"表述改为"仅 `en`"。
   - `docs/api/withdrawals.md` 补 `WITHDRAWAL` 流水、作废 attempt 接口、`Idempotency-Key`、单笔 `PENDING`、银行卡 410、打款开关与 `CUSTOM` 统计。
   - `docs/api/admin.md` 补 `idempotencyKey`、`BLACKLISTED`、控制台字段、账号字段说明；删除不存在的订单/支付事件视图表述。
   - `docs/api/auth-security.md` 修正公开接口清单（内容浏览类游客可访问）与已删除路径。
   - `docs/api/rate-limit.md`、`docs/deploy/*.md`、根 `README.md`、`AGENTS.md` 同步。
4. **新增本轮成对计划文档**：`2026-10-12-review-remediation-design.md`（本设计的决策与边界）与本文档。

### 涉及文件

- `scripts/verify-release-baseline.ps1`、`.github/workflows/ci.yml`
- `docs/api/*.md`、`docs/deploy/*.md`、`docs/README.md`、`docs/api/README.md`
- `README.md`、`AGENTS.md`
- `docs/plans/2026-10-12-review-remediation-design.md`、`docs/plans/2026-10-12-review-remediation-implementation.md`

### 验收命令

```powershell
git status --porcelain -- docs AGENTS.md README.md
Select-String -Path docs,AGENTS.md,README.md -Pattern "zh-TW|双语|api\.qrserver\.com|SMS_CALLBACK" -Recurse
powershell -ExecutionPolicy Bypass -File scripts/tests/verify-release-baseline-tests.ps1
```

预期：改动文件清单与计划一致；关键词剩余命中只有明确的历史说明或"剩余事项"；脚本回归测试通过。

---

## 最终验证矩阵

| 检查项 | 命令 | 预期 | W7 文档核对时的实测 |
| --- | --- | --- | --- |
| 后端全量测试 | `backend> .\gradlew.bat test --no-daemon` | 全绿，0 失败 0 错误 | ⚠️ **未通过**：576 用例中 26 失败（见下方"当前验证状态"） |
| content-provider 测试 | `python -m pytest content-provider -q` | `85 passed` | ✅ `85 passed in 0.63s` |
| admin-web 类型检查 + 构建 | `admin-web> npm run build` | 通过（`vue-tsc -b && vite build`） | 未在本次文档核对中重跑 |
| admin-web 契约测试 | `admin-web> npm run test:contracts` | `10 pass` | ✅ `pass 10 / fail 0` |
| app-core JVM 单测 | `android-app> .\gradlew.bat :app-core:test --no-daemon` | 通过 | 未在本次文档核对中重跑 |
| app 模块 JVM 单测（无签名密钥） | `android-app> .\gradlew.bat :app:testDebugUnitTest --no-daemon` | 通过（不再被签名校验阻断） | 未在本次文档核对中重跑 |
| Android 打包 | `android-app> .\gradlew.bat :app:assembleDebug --no-daemon` | 仅在具备 `ANDROID_SIGNING_*` 时执行 | 未执行（无签名密钥） |
| 发布质量基线 | `powershell -ExecutionPolicy Bypass -File scripts/verify-release-baseline.ps1` | 干净工作树上通过；脏树需 `-AllowDirtyTree` | 未执行（当前工作树为本轮修复的未提交改动） |
| 基线脚本回归 | `powershell -ExecutionPolicy Bypass -File scripts/tests/verify-release-baseline-tests.ps1` | 通过 | 未在本次文档核对中重跑 |
| infra 静态校验 | `powershell -ExecutionPolicy Bypass -File infra/scripts/verify-backup-scripts.ps1` | 通过 | 未在本次文档核对中重跑 |
| Compose 语法与必填键 | `cd infra; docker compose --env-file .env.example -f docker-compose.yml config --quiet` | 通过 | 未执行（本机无 Docker） |
| 迁移非破坏性 | `Select-String -Path backend\src\main\resources\db\migration\V31__review_remediation.sql -Pattern "DROP TABLE\|TRUNCATE\|DELETE FROM"` | 零命中 | ✅ 零命中 |
| 文档关键词自查 | `Select-String -Path docs,AGENTS.md,README.md -Pattern "zh-TW\|双语\|api\.qrserver\.com\|SMS_CALLBACK" -Recurse` | 仅历史说明或剩余事项 | ✅ 仅历史说明与剩余事项 |
| 空白检查 | `git diff --check` | 无空白错误 | 未在本次文档核对中重跑 |

### 当前验证状态（2026-10-12 文档核对实测）

`backend> .\gradlew.bat test --no-daemon`：**576 用例，26 失败**。失败集中在以下 8 个测试类，均属 W1/W3/W4 的实现与测试尚未收敛，不是文档问题：

| 测试类 | 失败数 | 代表信息 |
| --- | --- | --- |
| `com.reelshort.backend.content.ContentCacheServiceTests` | 12 | `IllegalStateException: Recursive update`（single-flight 与缓存写入路径） |
| `com.reelshort.backend.content.ContentCacheServiceTransactionTests` | 3 | 同上，含并发 single-flight 用例 |
| `com.reelshort.backend.system.SystemAlertServiceTests` | 3 | `ArrayIndexOutOfBoundsException: Index 0 out of bounds for length 0`（诊断水位线） |
| `com.reelshort.backend.admin.AdminPermissionControllerTests` | 2 | 期望 200 实得 400 |
| `com.reelshort.backend.content.AdminContentCacheControllerTests` | 2 | 期望 200 实得 409；`$.data[*].status` 断言不符 |
| `com.reelshort.backend.social.SocialServiceTests` | 2 | 评论分页用例断言失败 |
| `com.reelshort.backend.commerce.WalletWithdrawalTransferControllerTests` | 1 | H2 缺少 `point_transactions.idempotency_key` 列（`BadSqlGrammarException`） |
| `com.reelshort.backend.order.AdminOrderControllerTests` | 1 | 期望 200 实得 400 |

发布前必须由 W1/W3/W4 的实现方把这 26 个用例修到全绿，并重跑本矩阵中的后端与基线两行。


## 回滚与发布顺序

1. 先备份 PostgreSQL（V31 之前的 dump），再按 W1 → W2 → W3 → W4 → W5 的顺序部署后端与内容源；W6 客户端可在后端就绪后单独发布。
2. `REELSHORT_TRUSTED_PROXIES` 必须在部署时填成 Nginx 所在 Docker 子网，否则限流塌缩为全站单桶。
3. 后端回滚只需回退代码：V31 全部为新增可空列与索引放宽，旧代码可继续运行。
4. Android 回滚不能降级 `versionCode`；如需撤回客户端问题，用更高 `versionCode` 发布修复版本。
5. 打款执行能力保持关闭（`REELSHORT_WITHDRAWAL_PAYOUT_ENABLED=false`）；重新启用前必须复核热钱包地址白名单、权限与审计。

## 剩余事项

以下是本轮**未完成**的条目，不得当作已完成：

0. **后端测试未全绿**：2026-10-12 文档核对时 `backend> .\gradlew.bat test --no-daemon` 为 576 用例 / 26 失败，失败类与代表信息见"当前验证状态"。发布前必须由 W1/W3/W4 实现方修复并重跑矩阵中的后端与基线两行。
1. **后端分页未做**：`GET /api/admin/users`、`GET /api/admin/withdrawals` 仍全量返回，前端也未改接口形态（P2-17 分页项未闭环）。
2. **签名/广播代码未物理迁移**：`TronClient`/`EthereumClient`/`BscClient` 的签名与广播实现仍在类路径，只通过 `@ConditionalOnProperty`（`reelshort.withdrawal.payout.enabled=false`）让 `WithdrawalPayoutCoordinator`、`WithdrawalPayoutTransactionService`、`WithdrawalPayoutConfirmationService` 默认不装配；进程内其他调用点仍可直接签名广播真实资金。
3. **`bank_card_attempts` 表保留**：只删除了 Java 代码与实体，历史表与历史数据未清理。
4. **`admin-web` 仍以 root 运行**，且 `style-src 'unsafe-inline'` 因 Element Plus 运行时注入内联样式必须保留。
5. **Android 评论分页前端未接入**：后端已支持 `page`/`size`/`hasMore`，但 Android 客户端仍按数组解析评论响应，评论列表在 App 侧需要后续接入分页（旧客户端兼容性需在发版前确认）。
6. **作废 attempt 接口无后台 UI**：`POST /api/admin/withdrawals/{id}/payout-attempt/void` 只有后端接口与契约测试，`admin-web` 提现页未提供操作入口，需运维用脚本或接口直接调用。
7. **`captcha_challenges.answer` 列保留**：V31 只放开 `NOT NULL`，历史明文答案列未物理删除。
8. **`sms_verification_codes` 表保留**：V9 遗留空表未清理，仅标注为历史遗留。
9. **生产存量数据未核实**：`withdrawal_payout_attempts` 中是否存在 `active_slot='ACTIVE'` 且状态非 `MANUAL_REVIEW` 的行、容器内 `remoteAddr` 实际取值、宿主机 Nginx 的 actuator 暴露策略，需要在部署环境执行报告附录 B 的查询。
10. **历史提现缺流水不回填**：V31 不回溯补写历史 `WITHDRAWAL` 流水，只保证本轮之后的一致性。
11. **本地联调脚本未注入内容源内部密钥**：`content-provider` 除 `/health` 外全部端点 fail-closed（未配置 `CONTENT_PROVIDER_INTERNAL_TOKEN` 时 503），而 `infra/scripts/start-app-local-dev.ps1` 只导出 `CONTENT_PROVIDER_PORT`，没有生成或导出该密钥，也没有传给后端。当前必须由使用者先在同一 PowerShell 会话 `$env:CONTENT_PROVIDER_INTERNAL_TOKEN = "..."` 再运行脚本（两个子进程继承环境变量），否则 `/health` 通过但内容接口全部 503。已在 `docs/deploy/app-local-dev.md` 写明该前置步骤与症状，脚本自动化属 W5 待办。
