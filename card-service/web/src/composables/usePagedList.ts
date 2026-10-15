import { ref, shallowRef, type Ref, type ShallowRef } from 'vue'
import { errorMessage } from '../api/http'
import { DEFAULT_PAGE_SIZE } from '../constants'
import type { PagedList } from '../types/api'

export interface UsePagedListOptions {
  pageSize?: number
}

export interface PagedListState<T> {
  rows: ShallowRef<T[]>
  loading: Ref<boolean>
  loaded: Ref<boolean>
  error: Ref<string | null>
  page: Ref<number>
  pageSize: Ref<number>
  total: Ref<number>
  load: (targetPage?: number) => Promise<void>
  search: () => Promise<void>
  retry: () => Promise<void>
  changePage: (nextPage: number) => Promise<void>
  changePageSize: (nextSize: number) => Promise<void>
  reset: () => void
}

/**
 * 契约分页列表的通用状态机：加载态 / 错误态（可重试）/ 空态 / 翻页。
 * 视图层只负责把筛选条件拼成请求参数。
 */
export function usePagedList<T>(
  fetchPage: (page: number, pageSize: number) => Promise<PagedList<T>>,
  options: UsePagedListOptions = {},
): PagedListState<T> {
  const rows = shallowRef<T[]>([])
  const loading = ref(false)
  const loaded = ref(false)
  const error = ref<string | null>(null)
  const page = ref(1)
  const pageSize = ref(options.pageSize ?? DEFAULT_PAGE_SIZE)
  const total = ref(0)

  async function load(targetPage = page.value): Promise<void> {
    loading.value = true
    error.value = null
    try {
      const result = await fetchPage(Math.max(1, targetPage), pageSize.value)
      rows.value = result.data
      page.value = result.pagination.page
      pageSize.value = result.pagination.page_size
      total.value = result.pagination.total_items
      loaded.value = true
    } catch (caught) {
      error.value = errorMessage(caught, '数据加载失败')
      rows.value = []
      total.value = 0
    } finally {
      loading.value = false
    }
  }

  function search(): Promise<void> {
    return load(1)
  }

  function retry(): Promise<void> {
    return load()
  }

  function changePage(nextPage: number): Promise<void> {
    return load(nextPage)
  }

  function changePageSize(nextSize: number): Promise<void> {
    pageSize.value = nextSize
    return load(1)
  }

  function reset(): void {
    rows.value = []
    total.value = 0
    page.value = 1
    loaded.value = false
    error.value = null
  }

  return {
    rows,
    loading,
    loaded,
    error,
    page,
    pageSize,
    total,
    load,
    search,
    retry,
    changePage,
    changePageSize,
    reset,
  }
}
