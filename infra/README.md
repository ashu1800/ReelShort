# Infrastructure

单机部署配置目录。当前机器未安装 Docker，本阶段仅维护配置草案。

## 目标组件

- Nginx：统一入口和静态资源托管。
- Spring Boot backend：核心业务服务。
- Vue admin-web：后台静态资源。
- Flask content-provider：内部内容源服务。
- PostgreSQL：核心业务数据库。
- Redis：缓存和短期状态。

