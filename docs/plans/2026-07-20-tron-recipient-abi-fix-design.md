# TRON Recipient ABI Encoding Fix Design

## 问题

`TronClient.encodeTransferParams` 对 Base58Check 解码后的 25 字节直接截取最后 20 字节，错误包含了 4 字节 checksum。TRON 节点因此按错误收款地址构建未签名交易，后续严格原始交易校验使用正确地址 payload 时稳定返回 `recipient`。

## 方案

复用现有 `abiAddress` 生成 32 字节 ABI 地址参数。该方法通过 `addressPayload` 校验 Base58Check，并明确取 21 字节 payload 中去掉网络前缀后的 20 字节地址。金额编码保持不变，严格原始交易校验和单次重建策略保持不变。

## 测试

扩展 `TronClientTests` 的测试 HTTP 服务，捕获 `/wallet/triggersmartcontract` 请求体。新增回归测试调用 `prepareTransfer` 后断言请求中的 `parameter` 等于正确的 32 字节地址加 32 字节金额。测试必须在旧实现上因实际地址 word 含 checksum 而失败，修复后通过。

## 部署

本次只修改 backend，不涉及数据库迁移、接口签名、admin-web 或 Android。通过提现模块测试、后端全量测试、构建和发布基线后合并 `master`，备份生产 PostgreSQL、旧 backend 源码和镜像，再仅重建并替换 backend。
