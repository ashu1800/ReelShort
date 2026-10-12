# 2026-10-12 全面代码审查修复设计

## 背景

`artifacts/code-review-2026-10-12.md` 对 `master @ 138a68b` 做了五路并行深审：后端安全面、资金面、内容与内容源、Android、前端与运维发布。P0 为零，但暴露三类系统性问题：

1. **账实与状态机缺陷（资金）**：人工确认提现只扣冻结积分不写流水；遗留 `ACTIVE` 打款 attempt 让提现单永久卡死；提现提交无幂等。
2. **文档与实现大规模漂移**：`AGENTS.md` 与 `docs/` 仍在描述已从代码删除的"手机号 + 短信验证码 + AccountManager 回调 + 内部开户注册"认证体系，以及实现上并不存在的 `en`/`zh-TW` 双语能力。
3. **部署面配置断裂**：生产限流因 `REELSHORT_TRUSTED_PROXIES` 无法注入而塌缩为全站单一 IP 桶；nginx 健康检查走 SPA 兜底导致后端宕机也显示 healthy；生产库转储躺在仓库根目录且未被 `.gitignore` 覆盖。

本设计文档确定修复范围、关键决策与回滚边界；实施清单见 `2026-10-12-review-remediation-implementation.md`。

## 问题分级摘要

| 级别 | 报告条目数 | 实际分布 |
| --- | --- | --- |
| P0 | 0 | 无阻断级问题 |
| P1 | 8 | 数据泄漏 1（P1-1 备份未忽略）、资金不变量 2（P1-2 缺流水、P1-3 attempt 卡死）、敏感数据采集 1（P1-4 银行卡）、凭证爆破 1（P1-5）、限流失效 1（P1-6）、事务边界 1（P1-7）、文档漂移 1（P1-8） |
| P2 | 18 | 一致性 6（P2-1/2/3/4/9/14）、资金与状态机 3（P2-10/13/15）、数据与审计 3（P2-11/12/18）、健壮性与资源边界 5（P2-5/6/7/16/17）、验证码与哈希（并入 P2-16）1 |
| P3 | 25 | Android 客户端 11、后端细节 10（含死代码与告警粘滞）、发布与 CI 4 |

计数口径：报告正文的 P1 小节 8 条、P2 小节 18 条、P3 表 25 行，与上表一致。

## 修复范围

### W1 资金状态机与账实一致（backend/withdrawal、backend/points、backend/wallet）

- 人工确认提现在同一事务内补写 `WITHDRAWAL` 积分流水（负数、余额快照），幂等键 `withdrawal:<id>`；这是当前唯一在用的出金路径，不补流水则 `sum(point_transactions.amount) ≠ point_accounts.balance` 永久成立。
- 新增受审计的"作废卡死 attempt"接口，解除"确认 409、拒绝 409"的死结；`BROADCASTED` 先做只读链上核对再决定作废或转人工复核。
- 提交提现支持可选 `Idempotency-Key` 请求头，并强制同一用户同时只有一笔 `PENDING`。
- 银行卡提现入口彻底下线：后端固定 410，不再接收卡号/有效期/CVV。

### W2 认证收敛与凭证防护（backend/auth、backend/system/security、backend/system/ratelimit）

- 认证模型收敛为**用户名 + 密码 + 4 位图形验证码**，删除全部短信/手机号描述与死配置。
- 图形验证码答案只以 SHA-256 哈希落库、答错即作废、过期挑战由定时任务清理，并新增限流规则。
- 钱包改绑/解绑密码错误改为 `403 invalid current password`（不再被客户端误判为会话过期），连续失败触发凭证锁定（默认 5 次 / 15 分钟），锁定期返回 `429 too many attempts, try again later`；改密共用同一计数。
- 限流补齐验证码、钱包、提现、社交规则；`REELSHORT_TRUSTED_PROXIES` 支持 CIDR，生产 Compose 改为必填。

### W3 内容与社交健壮性（backend/content、watch、social、system）

- 上游 HTTP 调用移出数据库事务，货架刷新加 single-flight 去重，避免未鉴权接口耗尽连接池。
- 语言收敛为 `en` 单语：内容接口与缓存只支持 `en`，`REELSHORT_CONTENT_REFRESH_LOCALES` 默认 `en`。
- 观看进度只允许前进，消除"进度 8% + rewardClaimed=true"的永久不一致。
- 评论列表加分页（默认 20、上限 50），伪评论随页裁剪；请求 DTO 补长度约束。
- 内容源诊断告警改为按水位线判定并挂定时评估，消除长期 OPEN。

### W4 内容源生产化（content-provider）

- 生产改由 gunicorn 承载，非 root 运行；除 `/health` 外的端点要求 `X-Content-Provider-Token` 内部共享密钥（fail-closed）。

### W5 Android 客户端（android-app）

- 银行卡入口不再采集任何卡信息；VIP 收款二维码本地生成，不再请求第三方 `api.qrserver.com`。
- 删除 GeoIP 封禁死代码与 GitHub Release 更新客户端；COS 下载域名收紧为精确桶前缀并拒绝 versionCode 降级；401 清理本地会话。

### W6 后台与入口加固（admin-web、admin-web/nginx.conf）

- 删除不存在的"订单管理/支付事件"入口与 `phone*` 字段类型；资金敏感系统配置保存前二次确认。
- nginx 增加 CSP、`/assets/` 长缓存与 `index.html` no-store。

### W7 文档与实现对齐（docs、AGENTS.md、README.md）

- 按本轮权威决策重写 `docs/api/auth-user.md`，逐份对齐 `docs/api/*.md`、`docs/deploy/*.md`、根 `README.md`，并同步 `AGENTS.md` 模块结构与变更历史。

## 不修范围

- **后端分页**（用户列表、提现列表）本轮不做，前端也不改接口形态。
- **签名/广播代码物理迁移**：`TronClient`/EVM 客户端本轮不动，只用 `@ConditionalOnProperty` 让执行链 Bean 默认不装配。
- **`bank_card_attempts` 表**保留（仅删除 Java 代码），历史数据不清理。
- **`admin-web` 非 root 化**与 `style-src 'unsafe-inline'` 收紧（Element Plus 运行时注入内联样式，必须保留）。
- **Android 评论分页前端接入**未做，后端已支持 `page/size/hasMore`。
- 真实支付平台接入、生产环境存量数据核查（报告附录 B）不在本轮范围。

## 关键设计决策

### 认证：用户名 + 密码 + 图形验证码

`AuthController` 只保留 `register`、`login`、`captcha`、`password/change`、`logout`。`RegisterRequest` 为 `username + password + captchaId + captchaAnswer`；`LoginRequest` 为 `username + password`。用户名规则 `^[a-zA-Z][a-zA-Z0-9_]{3,19}$`，密码最少 8 位。

短信验证码、手机号开户、`REELSHORT_SMS_CALLBACK_*` 与 AccountManager 供应商回调链路**在代码中不存在**：`register-phone`/`register-phone/batch` 与 `auth/sms/send` 均已删除，Compose 与 `.env.example` 不再注入 SMS 变量。文档一律改写为历史说明或直接删除，内部开户接口若代码不存在也一并删除说明。

登录对禁用账号也执行一次 BCrypt 比对后再判定状态，避免 `403 user disabled` 变成"该用户名存在且被封禁"的枚举名单。

### 语言：英文单语（`en`）

`ContentLocale` 只有 `ENGLISH`（注释明示 app is English-only），Flask `SUPPORTED_LOCALES = {"en"}`，Android `AppLanguage` 只有 `ENGLISH`。因此：

- `locale` 参数保留但只解析为 `en`，文档如实描述为"仅 `en`"。
- `REELSHORT_CONTENT_REFRESH_LOCALES` 默认 `en`；写多个值只会让同一货架按同一语言被重复刷新。
- 后台内容缓存页语言选择器只含 `English`，另有"刷新全部语言"按钮串行刷新当前唯一的 locale。
- 繁中片库关键词扩展配置（`REELSHORT_CATALOG_SEARCH_KEYWORDS_*` 的繁中变体）为死配置，已删除。

### 提现：人工确认写流水 + 执行能力默认关闭

- `manual-confirm` 在 `manualConfirmLocked` 内 `deductFrozen` 之后写入 `PointTransaction.withdrawal(userId, totalDeductedPoints, balance, requestId)`，负数 + 余额快照 + 幂等键 `withdrawal:<id>`，重复确认不写第二条。
- 自动打款执行能力由 `reelshort.withdrawal.payout.enabled`（`REELSHORT_WITHDRAWAL_PAYOUT_ENABLED`，默认 `false`）门禁；`approve`、`batch-approve`、`batch-preview` 固定返回 `410 automatic payout is disabled`。
- 新增 `POST /api/admin/withdrawals/{id}/payout-attempt/void`（body `{"reason": "..."}`，需 `WITHDRAWAL_WRITE`）：`SIGNING`/`PREPARED` 直接作废；`BROADCASTED` 先只读链上核对——确认数达标转 `MANUAL_REVIEW` 交由人工确认，查无或失败才作废，状态未定（`PENDING`/`UNKNOWN`）返回 409。该接口只查询、不签名、不广播，因此打款能力关闭时仍可用。
- 提交提现可选 `Idempotency-Key`（≤128 字符，跨用户复用返回 409）；同一用户已存在 `PENDING` 时返回 409。
- 银行卡提现入口下线：`POST /api/app/wallet/bank-card` 固定 410，不再接收卡号/CVV；钱包绑定只支持 `ERC20`。

### 钱包密码错误与凭证锁定

`PUT /api/app/wallet` 与 `POST /api/app/wallet/unbind` 密码错误返回 `403 invalid current password`（403 而非 401，避免客户端清会话）。锁定判断（`assertNotLocked`）优先于任何密码比对，锁定期间即使密码正确也返回 429；失败计数用 `REQUIRES_NEW` 落库，业务事务回滚不丢计数；成功凭证交换清空计数。改密共用同一用户计数。

### 验证码哈希化

答案只在生成时的内存对象里（`@Transient`），数据库保存 `answer_hash`（SHA-256 十六进制）；校验对输入做去空白 + 大写规范化后同样哈希比较，答错立即 `markUsed` 作废（一次性机会）。过期挑战由 `CaptchaCleanupService` 每 `1h`（`reelshort.auth.captcha.cleanup-interval`）清理早于 1 小时保留期的行。

### 限流与可信代理

新增规则：`GET /api/app/auth/captcha` 10/分、`POST /api/app/wallet/**` 10/分、`PUT /api/app/wallet` 10/分、`POST /api/app/withdrawals` 5/分、`POST /api/app/social/**` 30/分。

`REELSHORT_TRUSTED_PROXIES` 支持精确 IP 与 CIDR；前缀过宽（IPv4 < /8、IPv6 < /16）直接启动失败，避免 `0.0.0.0/0` 让任意客户端伪造 `X-Forwarded-For`。生产 Compose 用 `${REELSHORT_TRUSTED_PROXIES:?}` 强制必填：容器内 `remoteAddr` 是 Docker 网关地址而非 loopback，漏配会让 XFF 被整条忽略，全部匿名客户端塌缩为同一个限流键（单人即可打满全平台登录配额）。

### 迁移 V31：纯新增，旧代码可继续运行

`V31__review_remediation.sql` 只做以下变更，无任何数据删除：

1. `withdrawal_payout_attempts`：两个 CHECK 约束放宽以容纳新终态 `VOIDED`；新增可空列 `voided_at`、`voided_by`、`void_reason`。
2. 新建 `credential_attempts`（凭证失败计数与锁定）。
3. `captcha_challenges` 新增可空 `answer_hash`，`answer` 列放开 `NOT NULL`（仅为兼容历史数据保留）。
4. `withdrawal_requests` 新增可空 `idempotency_key` + 唯一索引。
5. 新增热点索引 `idx_point_transactions_user_created`、`idx_withdrawal_requests_status_network_reviewed`。

新增列均可空、宽松化后的约束对既有状态集合仍然成立，因此**旧应用代码可以在本迁移之后继续运行**，回滚只需回退代码，不必回退 schema。

## 风险与回滚边界

| 风险 | 影响 | 回滚 |
| --- | --- | --- |
| V31 约束放宽 | 无数据删除；`VOIDED` 对旧代码是未知枚举值 | 旧代码不查询 `VOIDED` 行即可；必要时回退代码，schema 保持 |
| 人工确认新增流水写入 | 历史提现缺流水仍存在（不回填） | 流水写入与扣减在同一事务；重复确认靠 `withdrawal:<id>` 幂等键 |
| 打款能力默认关闭 | `TronClient` 等签名代码仍在类路径，仅 Bean 不装配 | 显式设置 `REELSHORT_WITHDRAWAL_PAYOUT_ENABLED=true` 并复核热钱包白名单后恢复 |
| 作废 attempt 接口 | 误作废会释放 `active_slot`，但不动积分 | 作废只写 `VOIDED` 与审计；积分释放仍需管理员显式 reject |
| 凭证锁定 | 正常用户连续输错 5 次会被锁定 15 分钟 | 通过 `REELSHORT_CREDENTIAL_GUARD_*` 调整；成功一次即清空计数 |
| 评论分页响应由数组改为对象 | 未接入分页的旧客户端解析失败 | 后端已支持 `page/size/hasMore`；需要时回退为数组形态 |
| `REELSHORT_TRUSTED_PROXIES` 必填 | 未配置时 Compose 拒绝解析部署配置 | 填 Nginx 所在 Docker 子网即可；本地调试用 `docker-compose.local-debug.yml` |
| 生产库转储迁移 | 迁移过程不可逆删除备份目录 | 迁移只做移动，`infra/backups/` 已被 `.gitignore` 忽略并收紧为 owner-only ACL |

## 文档与上下文

历史计划文档（`docs/plans/2026-*`）保留当时事实，不作为当前实现依据；本轮只更新"当前实现"类文档：`docs/api/*.md`、`docs/deploy/*.md`、`docs/README.md`、`docs/api/README.md`、根 `README.md`、`AGENTS.md`。`AGENTS.md` 是后续开发（含 AI 辅助开发）的权威输入，因此模块结构与变更历史必须与代码一致。
