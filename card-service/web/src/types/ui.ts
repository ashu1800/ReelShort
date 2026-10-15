/** 仅前端使用的界面类型（与契约无关）。 */

/** 卡密列表行的操作项。 */
export type CardRowAction =
  | 'detail'
  | 'disable'
  | 'enable'
  | 'unbind'
  | 'reset-rebinds'
  | 'extend'
  | 'delete'

/** 卡密生成弹窗的两种模式：单个新增（`POST /cards`）与批量生成（`POST /cards/batch`）。 */
export type GenerateCardsMode = 'single' | 'batch'

/** 导出目标：契约导出接口（csv / txt）或当前页客户端 CSV。 */
export type ExportTarget = 'csv' | 'txt' | 'page-csv'

/** 表格/表单加载态的三态。 */
export type LoadState = 'idle' | 'loading' | 'ready' | 'error'
