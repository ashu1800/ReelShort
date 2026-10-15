/** 全站常量。 */

export const APP_TITLE = '卡密服务管理后台'

export const APP_BRAND = '卡密服务'

export const APP_BRAND_SUBTITLE = '管理后台'

/** 列表默认每页条数（契约 `page_size` ≤ 200）。 */
export const DEFAULT_PAGE_SIZE = 20

/** 每页条数候选。 */
export const PAGE_SIZE_OPTIONS = [20, 50, 100, 200]

/** 批量生成上限（契约 3.3：`count` ≤ 10000）。 */
export const MAX_BATCH_COUNT = 10000

/** 卡密长度约束（契约 3.3：12–32）。 */
export const MIN_CARD_LENGTH = 12
export const MAX_CARD_LENGTH = 32

/** 后台新密码最小长度（前端校验，后端另有校验）。 */
export const MIN_PASSWORD_LENGTH = 10
