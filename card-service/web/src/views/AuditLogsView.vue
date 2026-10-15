<script setup lang="ts">
/**
 * 审计日志（契约 3.5）：管理员在后台做过的所有写操作。
 */
import { computed, onMounted, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import PageHeader from '../components/PageHeader.vue'
import AsyncSection from '../components/AsyncSection.vue'
import AppPagination from '../components/AppPagination.vue'
import { fetchAuditLogs } from '../api/admin'
import { usePagedList } from '../composables/usePagedList'
import { AUDIT_ACTION_LABELS, auditActionLabel, auditTargetLabel } from '../utils/labels.mjs'
import { dayRangeToTimestamps, formatDateTime } from '../utils/format.mjs'
import type { AuditLog, AuditLogListQuery } from '../types/api'

const admin = ref('')
const action = ref('')
const range = ref<[string, string] | null>(null)

const actionOptions = Object.entries(AUDIT_ACTION_LABELS).map(([value, label]) => ({ value, label }))

const { rows, loading, error, page, pageSize, total, load, search, retry, changePage, changePageSize } =
  usePagedList<AuditLog>((targetPage, targetSize) => fetchAuditLogs(buildQuery(targetPage, targetSize)))

function buildQuery(targetPage: number, targetSize: number): AuditLogListQuery {
  const query: AuditLogListQuery = { page: targetPage, page_size: targetSize }
  if (admin.value.trim() !== '') query.admin = admin.value.trim()
  if (action.value.trim() !== '') query.action = action.value.trim()
  return { ...query, ...dayRangeToTimestamps(range.value) }
}

onMounted(() => {
  void load()
})

const isEmpty = computed(() => rows.value.length === 0)

function reset(): void {
  admin.value = ''
  action.value = ''
  range.value = null
  void search()
}
</script>

<template>
  <div class="page">
    <PageHeader title="审计日志" subtitle="管理员写操作留痕：谁在什么时候改了什么">
      <template #actions>
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
      </template>
    </PageHeader>

    <section class="panel">
      <div class="filter-bar">
        <div class="filter-item filter-item-grow">
          <span class="filter-label">管理员</span>
          <el-input v-model="admin" placeholder="管理员账号" clearable @keyup.enter="search" @clear="search" />
        </div>
        <div class="filter-item">
          <span class="filter-label">动作</span>
          <el-select
            v-model="action"
            placeholder="全部"
            clearable
            filterable
            allow-create
            default-first-option
            style="width: 180px"
          >
            <el-option v-for="item in actionOptions" :key="item.value" :label="item.label" :value="item.value" />
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
        empty-text="没有符合条件的审计记录"
        :skeleton-rows="6"
        @retry="retry"
      >
        <el-table :data="rows" row-key="id">
          <el-table-column label="时间" width="170">
            <template #default="scope">{{ formatDateTime(scope.row.created_at) }}</template>
          </el-table-column>

          <el-table-column label="管理员" width="140">
            <template #default="scope">{{ scope.row.username || '—' }}</template>
          </el-table-column>

          <el-table-column label="动作" width="170">
            <template #default="scope">
              <el-tooltip :content="`原始动作：${scope.row.action}`" placement="top" :show-after="200">
                <span>{{ auditActionLabel(scope.row.action) }}</span>
              </el-tooltip>
            </template>
          </el-table-column>

          <el-table-column label="对象" width="200">
            <template #default="scope">
              <span>{{ auditTargetLabel(scope.row.target_type) }}</span>
              <span v-if="scope.row.target_id" class="mono text-muted"> #{{ scope.row.target_id }}</span>
            </template>
          </el-table-column>

          <el-table-column label="详情" min-width="260">
            <template #default="scope">
              <el-tooltip v-if="scope.row.detail" :content="scope.row.detail" placement="top" :show-after="200">
                <span class="truncate">{{ scope.row.detail }}</span>
              </el-tooltip>
              <span v-else class="text-muted">—</span>
            </template>
          </el-table-column>

          <el-table-column label="IP" width="130">
            <template #default="scope">{{ scope.row.ip || '—' }}</template>
          </el-table-column>

          <template #empty>
            <el-empty description="没有符合条件的审计记录" />
          </template>
        </el-table>

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
  flex: 1 1 200px;
  min-width: 180px;
}
</style>
