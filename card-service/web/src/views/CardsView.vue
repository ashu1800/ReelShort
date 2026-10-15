<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import PageHeader from '../components/PageHeader.vue'
import AsyncSection from '../components/AsyncSection.vue'
import AppPagination from '../components/AppPagination.vue'
import CardFilterBar from '../components/CardFilterBar.vue'
import CardsTable from '../components/CardsTable.vue'
import CardsToolbar from '../components/CardsToolbar.vue'
import CardDetailDrawer from '../components/CardDetailDrawer.vue'
import GenerateCardsDialog from '../components/GenerateCardsDialog.vue'
import ExtendCardsDialog from '../components/ExtendCardsDialog.vue'
import { exportCards, fetchCards, fetchCardTypes } from '../api/admin'
import { errorMessage } from '../api/http'
import { usePagedList } from '../composables/usePagedList'
import { useCardActions } from '../composables/useCardActions'
import { downloadBlob, downloadText } from '../utils/download'
import { buildCardsCsv, exportFileName } from '../utils/export.mjs'
import { dayRangeToCardCreated } from '../utils/format.mjs'
import type { BatchAction, Card, CardListQuery, CardSort, CardType } from '../types/api'
import type { CardFilters } from '../types/filters'
import type { CardRowAction, ExportTarget, GenerateCardsMode } from '../types/ui'

const cardTypes = ref<CardType[]>([])
const filters = ref<CardFilters>({
  keyword: '',
  state: '',
  type_id: null,
  batch_no: '',
  createdRange: null,
})
const sort = ref<CardSort>('created_at_desc')
const selected = ref<Card[]>([])
const revealCodes = ref(false)
const exporting = ref(false)

const { rows, loading, loaded, error, page, pageSize, total, load, search, retry, changePage, changePageSize } =
  usePagedList<Card>((targetPage, targetSize) => fetchCards(buildQuery(targetPage, targetSize)))
const { busy, disable, enable, unbind, resetRebinds, remove, extend, batch } = useCardActions(reload)

/** 筛选条件（不含分页），导出与列表共用。 */
function buildFilters(): CardListQuery {
  const query: CardListQuery = { sort: sort.value }
  if (filters.value.keyword.trim() !== '') query.keyword = filters.value.keyword.trim()
  if (filters.value.state !== '') query.state = filters.value.state
  if (filters.value.type_id !== null) query.type_id = filters.value.type_id
  if (filters.value.batch_no.trim() !== '') query.batch_no = filters.value.batch_no.trim()
  return { ...query, ...dayRangeToCardCreated(filters.value.createdRange) }
}

function buildQuery(targetPage: number, targetSize: number): CardListQuery {
  return { ...buildFilters(), page: targetPage, page_size: targetSize }
}

function reload(): Promise<void> {
  selected.value = []
  return load()
}

function handleSearch(): void {
  selected.value = []
  void search()
}

function handleReset(): void {
  filters.value = { keyword: '', state: '', type_id: null, batch_no: '', createdRange: null }
  handleSearch()
}

function handleSort(next: CardSort): void {
  sort.value = next
  handleSearch()
}

const generateOpen = ref(false)
const generateMode = ref<GenerateCardsMode>('single')
const detailOpen = ref(false)
const detailCardId = ref<number | null>(null)
const extendOpen = ref(false)
const extendTargets = ref<Card[]>([])

function openGenerate(mode: GenerateCardsMode): void {
  generateMode.value = mode
  generateOpen.value = true
}

function openExtend(cards: Card[]): void {
  extendTargets.value = cards
  extendOpen.value = true
}

function handleGenerated(): void {
  void reload()
}

function handleRowAction(action: CardRowAction, card: Card): void {
  switch (action) {
    case 'detail':
      detailCardId.value = card.id
      detailOpen.value = true
      return
    case 'disable':
      void disable(card)
      return
    case 'enable':
      void enable(card)
      return
    case 'unbind':
      void unbind(card)
      return
    case 'reset-rebinds':
      void resetRebinds(card)
      return
    case 'extend':
      openExtend([card])
      return
    case 'delete':
      void remove(card)
      return
  }
}

async function handleExtendSubmit(days: number): Promise<void> {
  if (extendTargets.value.length === 1) {
    if (await extend(extendTargets.value[0], days)) extendOpen.value = false
    return
  }
  if (await batch('extend', extendTargets.value, days)) extendOpen.value = false
}

function handleBatch(action: BatchAction): void {
  if (action === 'extend') {
    openExtend(selected.value)
    return
  }
  void batch(action, selected.value)
}

async function handleExport(target: ExportTarget): Promise<void> {
  if (target === 'page-csv') {
    if (rows.value.length === 0) {
      ElMessage.warning('当前页没有可导出的数据')
      return
    }
    downloadText(exportFileName(new Date(), 'csv'), buildCardsCsv(rows.value), {
      bom: true,
      mime: 'text/csv;charset=utf-8',
    })
    ElMessage.success(`已导出当前页 ${rows.value.length} 条`)
    return
  }

  exporting.value = true
  try {
    const blob = await exportCards({ ...buildFilters(), format: target })
    downloadBlob(blob, exportFileName(new Date(), target))
    ElMessage.success('导出文件已开始下载')
  } catch (caught) {
    ElMessage.error(errorMessage(caught, '导出失败，请稍后重试'))
  } finally {
    exporting.value = false
  }
}

onMounted(async () => {
  await Promise.all([
    load(),
    fetchCardTypes()
      .then((types) => {
        cardTypes.value = types
      })
      .catch(() => {
        cardTypes.value = []
      }),
  ])
})
</script>

<template>
  <div class="page">
    <PageHeader title="卡密管理" subtitle="支持筛选、批量生成、批量操作与导出（导出上限 10 万行）。" />

    <CardFilterBar
      v-model="filters"
      :types="cardTypes"
      :loading="loading"
      @search="handleSearch"
      @reset="handleReset"
    />

    <CardsToolbar
      v-model:reveal="revealCodes"
      :selected-count="selected.length"
      :exporting="exporting"
      :batch-busy="busy"
      @create="openGenerate('single')"
      @generate="openGenerate('batch')"
      @batch="handleBatch"
      @export="handleExport"
    />

    <section class="panel">
      <AsyncSection
        :loading="loading && !loaded"
        :error="error"
        :empty="loaded && rows.length === 0 && total === 0"
        empty-text="没有符合条件的卡密"
        :skeleton-rows="6"
        @retry="retry"
      >
        <CardsTable
          :rows="rows"
          :loading="loading"
          :reveal-codes="revealCodes"
          @action="handleRowAction"
          @selection-change="selected = $event"
          @sort="handleSort"
        />
        <AppPagination
          :page="page"
          :page-size="pageSize"
          :total="total"
          :disabled="loading"
          @update:page="changePage"
          @update:pageSize="changePageSize"
        />
      </AsyncSection>
    </section>

    <GenerateCardsDialog
      v-model="generateOpen"
      :mode="generateMode"
      :types="cardTypes"
      :default-type-id="filters.type_id"
      @generated="handleGenerated"
    />

    <ExtendCardsDialog
      v-model="extendOpen"
      :cards="extendTargets"
      :submitting="busy"
      @submit="handleExtendSubmit"
    />

    <CardDetailDrawer v-model="detailOpen" :card-id="detailCardId" />
  </div>
</template>
