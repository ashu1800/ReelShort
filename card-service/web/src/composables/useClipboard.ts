import { ref, type Ref } from 'vue'
import { ElMessage } from 'element-plus'

export interface ClipboardApi {
  copying: Ref<boolean>
  copy: (text: string, successMessage?: string) => Promise<boolean>
}

/** 复制到剪贴板并给出中文反馈（不写入任何其他状态）。 */
export function useClipboard(): ClipboardApi {
  const copying = ref(false)

  async function copy(text: string, successMessage = '已复制到剪贴板'): Promise<boolean> {
    if (typeof text !== 'string' || text === '') {
      ElMessage.warning('没有可复制的内容')
      return false
    }
    copying.value = true
    try {
      await navigator.clipboard.writeText(text)
      ElMessage.success(successMessage)
      return true
    } catch {
      ElMessage.error('复制失败，请手动选择文本复制')
      return false
    } finally {
      copying.value = false
    }
  }

  return { copying, copy }
}
