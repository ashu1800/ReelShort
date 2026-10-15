import { withUtf8Bom } from './export.mjs'

/** 触发浏览器下载。 */
export function downloadBlob(blob: Blob, fileName: string): void {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  link.rel = 'noopener'
  document.body.appendChild(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(url)
}

export interface DownloadTextOptions {
  /** CSV 需要 UTF-8 BOM，便于 Excel 识别中文 */
  bom?: boolean
  mime?: string
}

/** 下载文本文件（TXT / CSV）。 */
export function downloadText(fileName: string, content: string, options: DownloadTextOptions = {}): void {
  const payload = options.bom === true ? withUtf8Bom(content) : content
  downloadBlob(new Blob([payload], { type: options.mime ?? 'text/plain;charset=utf-8' }), fileName)
}
