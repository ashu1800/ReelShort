/**
 * CSV / TXT 导出内容生成（契约 3.3 导出列顺序：卡密,类型,状态,绑定机器码,激活时间,到期时间,换绑次数,批次,备注,创建时间）。
 * 只生成文本内容，不触碰 DOM；文件下载在 `src/utils/download.ts` 中完成。
 * 纯函数模块，浏览器与 node --test 共用。
 */

import { DISPLAY_STATE_LABELS, displayStateLabel } from './labels.mjs'
import { formatExpiry, groupCardCode, normalizeCardCode } from './format.mjs'

/** 契约导出列（顺序与表头一致）。 @type {ReadonlyArray<{ header: string, field: string }>} */
export const CARD_EXPORT_COLUMNS = [
  { header: '卡密', field: 'code' },
  { header: '类型', field: 'type_name' },
  { header: '状态', field: 'state_text' },
  { header: '绑定机器码', field: 'bound_machine' },
  { header: '激活时间', field: 'activated_at' },
  { header: '到期时间', field: 'expires_text' },
  { header: '换绑次数', field: 'rebind_text' },
  { header: '批次', field: 'batch_no' },
  { header: '备注', field: 'remark' },
  { header: '创建时间', field: 'created_at' },
]

/** UTF-8 BOM（契约：CSV 带 UTF-8 BOM，便于 Excel 正确识别中文）。 */
export const UTF8_BOM = '\uFEFF'

/**
 * 单元格转义：含逗号、引号、换行时用双引号包裹并转义内部引号。
 * @param {unknown} value
 * @returns {string}
 */
export function csvEscape(value) {
  const text = value === null || value === undefined ? '' : String(value)
  if (/[",\r\n]/.test(text)) {
    return `"${text.replace(/"/g, '""')}"`
  }
  return text
}

/**
 * @param {ReadonlyArray<ReadonlyArray<unknown>>} rows
 * @returns {string}
 */
function joinCsvRows(rows) {
  return rows.map((row) => row.map(csvEscape).join(',')).join('\r\n')
}

/**
 * 卡密规范化后按 4 位分组展示（与页面展示一致）。
 * @param {unknown} code
 * @returns {string}
 */
export function displayCardCode(code) {
  const canonical = normalizeCardCode(code)
  return canonical === '' ? '' : groupCardCode(canonical)
}

/**
 * 已点击过的状态文案兜底：未知枚举值原样输出。
 * @param {string} state
 * @returns {string}
 */
function stateText(state) {
  return DISPLAY_STATE_LABELS[state] ?? displayStateLabel(state)
}

/**
 * 批量生成结果 → CSV 内容（不含 BOM，下载时再补）。
 * @param {readonly string[]} codes
 * @returns {string}
 */
export function buildCodesCsv(codes) {
  const rows = [['卡密'], ...codes.map((code) => [displayCardCode(code)])]
  return `${joinCsvRows(rows)}\r\n`
}

/**
 * 批量生成结果 → TXT 内容（每行一个卡密）。
 * @param {readonly string[]} codes
 * @returns {string}
 */
export function buildCodesTxt(codes) {
  if (codes.length === 0) return ''
  return `${codes.map(displayCardCode).join('\r\n')}\r\n`
}

/**
 * @typedef {object} CardExportRow
 * @property {string} [code]
 * @property {string} [type_name]
 * @property {string} [display_state]
 * @property {string} [bound_machine]
 * @property {string} [activated_at]
 * @property {string} [expires_at]
 * @property {boolean} [permanent]
 * @property {number} [rebind_count]
 * @property {number} [max_rebinds]
 * @property {string} [batch_no]
 * @property {string} [remark]
 * @property {string} [created_at]
 */

/**
 * 卡密列表行 → 契约列顺序的一行。
 * @param {CardExportRow} card
 * @returns {ReadonlyArray<unknown>}
 */
function cardRow(card) {
  const permanent = card.permanent === true
  const rebindCount = typeof card.rebind_count === 'number' ? card.rebind_count : 0
  const maxRebinds = typeof card.max_rebinds === 'number' ? card.max_rebinds : 0
  return [
    displayCardCode(card.code),
    card.type_name ?? '',
    stateText(typeof card.display_state === 'string' ? card.display_state : ''),
    card.bound_machine ?? '',
    card.activated_at ?? '',
    permanent ? '永久有效' : (card.expires_at ?? ''),
    `${rebindCount}/${maxRebinds}`,
    card.batch_no ?? '',
    card.remark ?? '',
    card.created_at ?? '',
  ]
}

/**
 * 卡密列表 → 契约列顺序的 CSV（用于导出当前页）。
 * @param {ReadonlyArray<CardExportRow>} cards
 * @returns {string}
 */
export function buildCardsCsv(cards) {
  const header = CARD_EXPORT_COLUMNS.map((column) => column.header)
  const rows = [header, ...cards.map(cardRow)]
  return `${joinCsvRows(rows)}\r\n`
}

/**
 * 为内容补 UTF-8 BOM（CSV 下载用）。
 * @param {string} content
 * @returns {string}
 */
export function withUtf8Bom(content) {
  return `${UTF8_BOM}${content}`
}

/**
 * 导出文件名：`cards-YYYYMMDD-HHmmss.csv`。
 * @param {Date} date
 * @param {string} extension 不含点，如 `csv`
 * @returns {string}
 */
export function exportFileName(date, extension) {
  const pad = (/** @type {number} */ value) => String(value).padStart(2, '0')
  const stamp = `${date.getFullYear()}${pad(date.getMonth() + 1)}${pad(date.getDate())}-${pad(date.getHours())}${pad(date.getMinutes())}${pad(date.getSeconds())}`
  return `cards-${stamp}.${extension}`
}

/**
 * 供 CSV 表头展示的到期文案（永久卡固定「永久有效」）。
 * @param {CardExportRow} card
 * @returns {string}
 */
export function cardExpiryText(card) {
  return formatExpiry(
    typeof card.expires_at === 'string' ? card.expires_at : '',
    card.permanent === true,
  )
}
