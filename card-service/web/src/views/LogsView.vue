<script setup lang="ts">
/**
 * 操作日志（契约 3.5）：卡密维度的校验/上线/心跳/换绑/后台操作记录。
 */
import { computed, onMounted, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import PageHeader from '../components/PageHeader.vue'
import AsyncSection from '../components/AsyncSection.vue'
import AppPagination from '../components/AppPagination.vue'
import LogTable from '../components/LogTable.vue'
import { fetchLogs } from '../api/admin'
import { usePagedList } from '../composables/usePagedList'
import { ACTION_LABELS, RESULT_LABELS } from '../utils/labels.mjs'
import { dayRangeToTimestamps, normalizeCardCode } from '../utils/format.mjs'
import type { Log, LogAction, LogListQuery, LogResult } from '../types/api'

const cardCode = ref('')
const action = ref<LogAction | ''>('')
const result = ref<LogResult | ''>('')
const range = ref<[string, string] | null>(null)

const actionOptions = Object.entries(ACTION_LABELS).map(([value, label]) => ({ value, label }))
const resultOptions = Object.entries(RESULT_LABELS).map(([value, label]) => ({ value, label }))

const { rows, loading, error, page, pageSize, total, load, search, retry, changePage, changePageSize } =
  usePagedList<Log>((targetPage, targetSize) => fetchLogs(buildQuery(targetPage, targetSize)))

function buildQuery(targetPage: number, targetSize: number): LogListQuery {
  const query: LogListQuery = { page: targetPage, page_size: targetSize }
  const code = normalizeCardCode(cardCode.value)
  if (code !== '') query.card_code = code
  if (action.value !== '') query.action = action.value
  if (result.value !== '') query.result = result.value
  return { ...query, ...dayRangeToTimestamps(range.value) }
}

onMounted(() => {
  void load()
})

const isEmpty = computed(() => rows.value.length === 0)

function reset(): void {
  cardCode.value = ''
  action.value = ''
  result.value = ''
  range.value = null
  void search()
}
</script>

<template>
  <div class="page">
    <PageHeader title="操作日志" subtitle="卡密与机器码均已脱敏；成功心跳默认不记录，可在系统设置中开启">
      <template #actions>
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
      </template>
    </PageHeader>

    <section class="panel">
      <div class="filter-bar">
        <div class="filter-item filter-item-grow">
          <span class="filter-label">卡密</span>
          <el-input
            v-model="cardCode"
            placeholder="卡密（可带分隔符）"
            clearable
            @keyup.enter="search"
            @clear="search"
          />
        </div>
        <div class="filter-item">
          <span class="filter-label">动作</span>
          <el-select v-model="action" placeholder="全部" clearable style="width: 150px">
            <el-option v-for="item in actionOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </div>
        <div class="filter-item">
          <span class="filter-label">结果</span>
          <el-select v-model="result" placeholder="全部" clearable style="width: 110px">
            <el-option v-for="item in resultOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </div>
        <div class="filter-item">
          <span class="filter-label">日期</span>
          <el-date-picker
            v-model="range"
            type="daterange"
            value-format="YYYY-MM-DD"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            unlink-panels
            style="width: 260px"
          />
        </div>
        <div class="filter-item">
          <el-button type="primary" @click="search">查询</el-button>
          <el-button @click="reset">重置</el-button>
        </div>
      </div>

      <AsyncSection
        :loading="loading"
        :error="error"
        :empty="!loading && error === null && isEmpty"
        empty-text="没有符合条件的日志"
        :skeleton-rows="6"
        @retry="retry"
      >
        <LogTable :logs="rows" :loading="loading" />
        <AppPagination
          :page="page"
          :page-size="pageSize"
          :total="total"
          @update:page="changePage"
          @update:page-size="changePageSize"
        />
      </AsyncSection>
    </section>
  </div>
</template>

<style scoped>
.filter-item-grow {
  flex: 1 1 220px;
  min-width: 200px;
}
</style>
