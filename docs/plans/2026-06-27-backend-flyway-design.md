# 后端 Flyway 数据库迁移设计

## 背景

后端目前依赖 `spring.jpa.hibernate.ddl-auto=update` 自动维护数据库结构。这个模式适合早期迭代，但不适合长期单机部署：表结构变化没有版本记录，重启或升级时无法清晰审计，生产数据迁移也缺少可重复执行的边界。

本阶段目标是引入 Flyway 作为 schema 版本管理工具，提供当前实体模型的初始 V1 迁移，并把 Hibernate 从“建表/改表”调整为“校验 schema”。后续新增字段、索引和表都必须通过新的 Flyway 版本脚本演进。

## 方案选择

### 方案 A：继续使用 Hibernate `ddl-auto=update`

改动最小，但无法满足长期部署的可追踪和可回滚规划。后续商业化、积分、订单、支付等表一旦有真实数据，自动改表风险不可接受。

### 方案 B：引入 Flyway，保留 JPA validate

Spring Boot 在 classpath 存在 Flyway 时会自动运行 `classpath:db/migration` 下的迁移。应用启动时先执行迁移，再由 Hibernate validate 校验实体和数据库结构是否一致。推荐采用该方案。

### 方案 C：引入 Liquibase

Liquibase 适合复杂变更审计和多数据库差异，但当前项目以单机 PostgreSQL 为主，Flyway SQL 脚本更简单直接。

推荐采用方案 B。

## 架构设计

新增依赖：

- `org.flywaydb:flyway-core`
- `org.flywaydb:flyway-database-postgresql`

新增迁移目录：

- `backend/src/main/resources/db/migration`

新增初始迁移：

- `V1__initial_schema.sql`

迁移脚本覆盖当前 JPA 实体表和关键关联表：

- 用户与认证：`users`、`access_tokens`
- 后台与权限：`admin_users`、`admin_tokens`、`permissions`、`roles`、`role_permissions`、`admin_user_roles`、`admin_audit_logs`
- 内容缓存：`content_book_cache`、`content_episode_cache`、`content_shelf_cache`
- 观看与积分：`watch_records`、`point_accounts`、`point_transactions`、`watch_reward_claims`
- 订单与支付：`recharge_orders`、`payment_events`
- 系统配置：`system_configs`

应用配置调整：

- 默认 `spring.jpa.hibernate.ddl-auto=validate`
- 保留环境变量 `REELSHORT_JPA_DDL_AUTO` 覆盖能力，但默认不再自动建表
- Flyway 默认启用，使用 `classpath:db/migration`

## 测试设计

新增 `DatabaseMigrationTests`：

- 使用 H2 PostgreSQL 模式启动 Spring 上下文
- 明确设置 `spring.jpa.hibernate.ddl-auto=validate`
- 验证 Flyway 已应用 V1
- 验证核心表存在
- 验证关键唯一约束存在并能阻止重复数据

测试使用真实 Spring Boot 上下文，证明迁移先于 JPA validate 执行。

## 边界与后续规则

本阶段只提供初始 schema，不做历史数据迁移。因为当前项目还处于开发阶段，尚未接入真实生产数据。

后续规则：

- 新增表/字段/索引必须新增 `V2__*.sql`、`V3__*.sql` 等迁移脚本。
- 不直接修改已提交的迁移脚本，除非尚未发布且明确重置开发库。
- 实体字段变化必须同步迁移和测试。
