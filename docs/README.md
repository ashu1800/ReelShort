# Docs

项目文档目录。

- `api`：接口文档。
- `deploy`：部署文档。
- `plans`：阶段执行计划（设计与实施成对出现，`*-design.md` 记录决策与边界，同名无后缀文档记录实施清单与验收命令）。

发布前固定验收入口见 `deploy/release-checklist.md`。

当前生效的整改计划：

- `plans/2026-10-12-review-remediation-design.md`：2026-10-12 全面代码审查的修复范围、关键决策与回滚边界。
- `plans/2026-10-12-review-remediation-implementation.md`：按工作流 W1–W7 的实施清单、验收命令、验证矩阵与剩余事项。

> `plans/` 下的历史文档记录当时的设计与实施事实，可能描述已被删除的能力（例如手机号 + 短信验证码认证、`zh-TW` 双语内容缓存、自动打款链路）。判断"当前实现"请以 `api/`、`deploy/`、根 `README.md` 与 `AGENTS.md` 为准。
