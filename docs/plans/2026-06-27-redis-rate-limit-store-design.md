# Redis Rate Limit Store Design

## Goal

补齐 `system/rate-limit` 的 Redis 计数存储，使长期单机 Docker Compose 部署可以把限流状态放到 Redis，同时保留当前内存存储作为本地和测试默认实现。

## Context

当前后端已经有 `RateLimitStore` 抽象、`InMemoryRateLimitStore`、统一 429 响应和默认敏感接口规则。部署层已经包含 Redis，但后端业务代码尚未真正接入 Redis。架构文档要求 Redis 用于限流计数和短期状态，因此本模块只落地限流计数，不扩大到会话、验证码或内容缓存。

## Options

推荐方案：新增可配置的 `RedisRateLimitStore`。通过 `spring-boot-starter-data-redis` 使用 Spring Boot 自动配置的 `StringRedisTemplate`，由 `reelshort.rate-limit.store=redis` 显式启用；默认值为 `memory`，避免本地没有 Redis 时后端启动失败。

备选方案 1：直接用 Redis 替换内存存储。部署更贴近生产，但会让本地开发和现有测试默认依赖 Redis，不适合当前环境。

备选方案 2：同时迁移 Token、缓存和防重复提交。架构更完整，但范围过大，会把多个业务边界混在一次变更里。

## Design

- `RateLimitProperties` 新增 `store` 配置，支持 `memory` 和 `redis`。
- `InMemoryRateLimitStore` 仅在 `reelshort.rate-limit.store=memory` 或未配置时注册。
- `RedisRateLimitStore` 仅在 `reelshort.rate-limit.store=redis` 时注册。
- `RateLimitInterceptor` 通过配置类注册，避免 `@WebMvcTest` 切片加载拦截器却没有加载存储配置。
- Redis key 使用 `reelshort:rate-limit:<resolved-key>`，其中 `<resolved-key>` 仍由现有 `RateLimitKeyResolver` 生成，保持用户/IP 维度不变。
- Redis 计数采用固定窗口：第一次请求 `INCREMENT` 后设置过期时间，后续请求复用 TTL；超过限制时返回剩余 TTL 作为 `Retry-After`。
- Redis 不可用时不静默放行，交给统一异常处理暴露系统错误。限流存储配置错误属于部署问题，应尽早暴露。

## Test Plan

- 单元测试验证 Redis 存储第一次请求设置 TTL、窗口内递增、超限返回 retry after、TTL 丢失时修复过期时间。
- 上下文测试验证默认配置只注册内存存储。
- 上下文测试验证 `reelshort.rate-limit.store=redis` 时注册 Redis 存储，并且内存存储不注册。
- 后端全量测试验证现有限流行为不回归。

## Out of Scope

- 不启动真实 Redis 或 Docker。
- 不迁移 Token、验证码、内容缓存或支付幂等锁。
- 不改变客户端、Controller、限流规则或 429 响应结构。
