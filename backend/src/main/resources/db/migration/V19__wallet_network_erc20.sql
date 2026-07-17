-- V19: 用户钱包网络从 TRC20 切换为 ERC20（以太坊）
-- 提现打款链路改为以太坊 ERC-20 USDT，用户钱包地址格式改为 0x 开头的以太坊地址。
-- 现有 Tron 格式地址已失效，用户需在 App 内重新绑定以太坊地址。

UPDATE user_wallets SET network = 'ERC20' WHERE network = 'TRC20';
