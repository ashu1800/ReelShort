/** 页面筛选条件类型（与契约查询参数一一对应）。 */

import type { CardStateFilter } from './api'

/** 卡密列表筛选条件。 */
export interface CardFilters {
  keyword: string
  state: CardStateFilter | ''
  type_id: number | null
  batch_no: string
  /** `YYYY-MM-DD` 区间，对应 `created_from` / `created_to` */
  createdRange: [string, string] | null
}

/** 操作日志筛选条件。 */
export interface LogFilters {
  card_code: string
  action: string
  result: string
  range: [string, string] | null
}

/** 审计日志筛选条件。 */
export interface AuditLogFilters {
  admin: string
  action: string
  range: [string, string] | null
}
