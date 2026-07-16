/**
 * 中文显示名和说明的映射表。
 * 后端返回的 key 和 description 是英文原文，这里按 key 映射到中文，供后台配置页展示。
 * 未映射的 key 回退显示原始英文 key 和 description。
 */
export const systemConfigLabels: Record<string, { name: string; description: string }> = {
  'points.watch.seconds-per-point': {
    name: '观看奖励 · 每积分秒数',
    description: '每完成多少秒视频播放奖励 1 积分。',
  },
  'points.daily-earned.maximum': {
    name: '每日获取积分上限',
    description: '每个账号每天自动获取的积分上限，0 表示不限制。',
  },
  'points.daily-earned.fluctuation-percent': {
    name: '每日积分随机下浮百分比',
    description: '每个账号每天自动获取积分的随机向下浮动百分比上限。',
  },
  'points.fair-mode.enabled': {
    name: '公平模式',
    description: '开启后积分按小数精确计算（1=开启，0=关闭），前端仍显示整数。',
  },
  'withdraw.cny-per-point': {
    name: '提现 · 每积分人民币',
    description: '提现时每个积分折合的人民币价值。',
  },
  'withdraw.cny-per-usd': {
    name: '提现 · 人民币兑美元',
    description: '提现换算时的人民币兑美元汇率。',
  },
  'withdraw.minimum-usd': {
    name: '提现 · 最低 USD',
    description: '申请提现所需的最低 USD 金额。',
  },
  'withdraw.fee-percent': {
    name: '提现手续费（%）',
    description: '提现时扣除的积分手续费百分比。',
  },
  'content.recommendation.strategy': {
    name: '推荐策略',
    description: '默认内容推荐策略（最新 / 热门）。',
  },
  'vip.price-usdt': {
    name: 'VIP 月费（USDT）',
    description: 'VIP 月度订阅价格（USDT）。',
  },
  'vip.free-episodes': {
    name: '免费集数',
    description: '非 VIP 用户可观看的免费集数。',
  },
  'vip.collection-address': {
    name: 'VIP 收款地址',
    description: 'VIP 充值的 TRC20 USDT 收款钱包地址。',
  },
  'vip.order-timeout-minutes': {
    name: 'VIP 订单有效期（分钟）',
    description: 'VIP 订单的支付有效期，超时自动过期。',
  },
}

export function configDisplayName(key: string): string {
  return systemConfigLabels[key]?.name ?? key
}

export function configDisplayDescription(key: string, fallback: string): string {
  return systemConfigLabels[key]?.description ?? fallback
}
