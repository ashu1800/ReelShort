# System Rate Limit Design

## Goal

补齐 `system` 模块的限流和防刷基础能力，保护登录、搜索、播放地址、观看进度、积分查询等高频或敏感接口，并保持后续替换为 Redis 计数存储的边界。

## Approach

本阶段采用 Spring MVC `HandlerInterceptor` + 单机内存滑动窗口计数。它不引入 Redis 依赖，适合当前单机开发和 H2 测试环境；同时通过 `RateLimitStore` 接口隔离计数存储，后续可新增 Redis 实现。

备选方案：

- 直接接入 Redis：更接近长期目标，但当前项目没有 Redis 依赖、测试容器或本机 Redis 验证，范围过大。
- 使用 Nginx 限流：部署层有效，但后端无法按用户、接口语义返回统一错误响应。
- 后端内存限流：可测试、改动集中、足够支撑单机第一阶段，并保留 Redis 替换点。

本切片选择后端内存限流。

## Architecture

- `RateLimitRule` 定义接口匹配、窗口秒数、窗口内最大请求数。
- `RateLimitProperties` 从配置文件读取默认规则和开关。
- `RateLimitStore` 抽象计数，当前实现为 `InMemoryRateLimitStore`。
- `RateLimitInterceptor` 在 Controller 前执行，匹配规则后按 `rule + principal/ip` 计数。
- `WebMvcConfig` 注册限流拦截器。
- 命中限流时返回统一错误结构，HTTP 状态 `429`，消息 `too many requests`，并携带当前 `requestId`。

## Scope

默认保护：

- App 登录和注册。
- Admin 登录。
- App 搜索、货架、播放地址。
- App 观看进度上报。
- App 积分账户和流水查询。

本阶段不实现：

- Redis 分布式计数。
- 动态按后台配置实时调整所有规则。
- 验证码、人机校验、IP 黑白名单。
- 按设备指纹或复杂风控模型限流。

## Keying Strategy

- 已认证请求优先使用认证主体：`APP:<userId>` 或 `ADMIN:<username>`。
- 未认证请求使用客户端 IP。
- 若存在反向代理头 `X-Forwarded-For`，取第一个 IP；否则使用 `remoteAddr`。

## Testing

- 单元测试覆盖内存计数窗口、超限、窗口过期。
- MVC 测试覆盖登录接口超限后返回 `429` 和统一错误结构。
- MVC 测试覆盖不同 IP 独立计数。
- MVC 测试覆盖未配置规则的健康检查不被限流。
- 全量后端测试确保现有认证、内容、观看、积分和后台接口不回归。
