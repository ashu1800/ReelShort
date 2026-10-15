import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  batchCardAction,
  deleteCard,
  disableCard,
  enableCard,
  extendCard,
  resetCardRebinds,
  unbindCard,
} from '../api/admin'
import { errorMessage } from '../api/http'
import { maskCardCode } from '../utils/format.mjs'
import type { BatchAction, Card } from '../types/api'

interface ConfirmOptions {
  title: string
  message: string
  confirmText: string
  danger?: boolean
}

async function askConfirm(options: ConfirmOptions): Promise<boolean> {
  try {
    await ElMessageBox.confirm(options.message, options.title, {
      confirmButtonText: options.confirmText,
      cancelButtonText: '取消',
      type: options.danger === true ? 'error' : 'warning',
    })
    return true
  } catch {
    return false
  }
}

/**
 * 卡密行操作与批量操作：统一二次确认、统一错误提示、成功后回调刷新列表。
 */
export function useCardActions(onChanged: () => void | Promise<void>) {
  const busy = ref(false)

  async function run(task: () => Promise<unknown>, successText: string, fallbackError: string): Promise<boolean> {
    busy.value = true
    try {
      await task()
      ElMessage.success(successText)
      await onChanged()
      return true
    } catch (caught) {
      ElMessage.error(errorMessage(caught, fallbackError))
      return false
    } finally {
      busy.value = false
    }
  }

  async function disable(card: Card): Promise<void> {
    const ok = await askConfirm({
      title: '确认禁用卡密？',
      message: `禁用后卡密立即失效并踢下线，客户端将无法再校验：${maskCardCode(card.code)}`,
      confirmText: '禁用',
      danger: true,
    })
    if (!ok) return
    await run(() => disableCard(card.id), '卡密已禁用', '禁用失败')
  }

  async function enable(card: Card): Promise<void> {
    const ok = await askConfirm({
      title: '确认启用卡密？',
      message: `启用后卡密恢复可用（有效期与绑定关系不变）：${maskCardCode(card.code)}`,
      confirmText: '启用',
    })
    if (!ok) return
    await run(() => enableCard(card.id), '卡密已启用', '启用失败')
  }

  async function unbind(card: Card): Promise<void> {
    const ok = await askConfirm({
      title: '确认解绑机器码？',
      message: `解绑会清空绑定机器码并踢下线，不重置换绑次数：${maskCardCode(card.code)}`,
      confirmText: '解绑',
      danger: true,
    })
    if (!ok) return
    await run(() => unbindCard(card.id), '已解绑机器码', '解绑失败')
  }

  async function resetRebinds(card: Card): Promise<void> {
    const ok = await askConfirm({
      title: '确认重置换绑次数？',
      message: `重置后换绑次数恢复为上限值：${maskCardCode(card.code)}`,
      confirmText: '重置',
    })
    if (!ok) return
    await run(() => resetCardRebinds(card.id), '已重置换绑次数', '重置换绑次数失败')
  }

  async function remove(card: Card): Promise<void> {
    const ok = await askConfirm({
      title: '确认删除卡密？',
      message: `删除后该卡密从后台列表中移除（后端为软删），操作不可撤销：${maskCardCode(card.code)}`,
      confirmText: '删除',
      danger: true,
    })
    if (!ok) return
    await run(() => deleteCard(card.id), '卡密已删除', '删除失败')
  }

  async function extend(card: Card, days: number): Promise<boolean> {
    return run(() => extendCard(card.id, { days }), `已延期 ${days} 天`, '延期失败')
  }

  /** 批量操作：action 由契约 3.3 `batch_action` 定义。 */
  async function batch(action: BatchAction, cards: Card[], days?: number): Promise<boolean> {
    if (cards.length === 0) {
      ElMessage.warning('请先勾选卡密')
      return false
    }
    const actionText: Record<BatchAction, string> = {
      disable: '批量禁用',
      enable: '批量启用',
      delete: '批量删除',
      reset_rebinds: '批量重置换绑次数',
      extend: '批量延期',
    }
    const suffix = action === 'extend' && typeof days === 'number' ? `（${days} 天）` : ''
    const ok = await askConfirm({
      title: `确认${actionText[action]}？`,
      message: `将对已勾选的 ${cards.length} 张卡密执行「${actionText[action]}」${suffix}。`,
      confirmText: '执行',
      danger: action === 'delete' || action === 'disable',
    })
    if (!ok) return false

    const payload = days === undefined
      ? { ids: cards.map((card) => card.id), action }
      : { ids: cards.map((card) => card.id), action, days }
    return run(
      () => batchCardAction(payload),
      `${actionText[action]}已完成`,
      `${actionText[action]}失败`,
    )
  }

  return { busy, disable, enable, unbind, resetRebinds, remove, extend, batch }
}
