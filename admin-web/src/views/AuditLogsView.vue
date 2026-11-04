<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  Document,
  User,
  Search,
  Refresh,
  Lock,
  CopyDocument,
  Tickets,
} from '@element-plus/icons-vue'
import { fetchAuditLogs } from '../services/adminApi'
import type { AdminAuditLog } from '../services/adminApi'

const loading = ref(false)
const error = ref('')
const logs = ref<AdminAuditLog[]>([])

// 搜索、过滤与分页
const searchQuery = ref('')
const selectedAction = ref<string>('ALL')
const currentPage = ref(1)
const pageSize = ref(15)

// 指标统计
const totalLogsCount = computed(() => logs.value.length)
const uniqueAdminsCount = computed(() => {
  const set = new Set(logs.value.map((l) => l.adminUsername))
  return set.size
})
const fundOpsCount = computed(() => {
  return logs.value.filter(
    (l) =>
      l.action.includes('WITHDRAWAL') ||
      l.action.includes('VIP') ||
      l.action.includes('POINTS') ||
      l.targetType === 'SYSTEM_CONFIG',
  ).length
})

// 动作类型选项提取
const actionOptions = computed(() => {
  const set = new Set(logs.value.map((l) => l.action))
  return Array.from(set).sort()
})

// 过滤后的日志
const filteredLogs = computed(() => {
  let list = logs.value
  const q = searchQuery.value.trim().toLowerCase()
  if (q) {
    list = list.filter(
      (l) =>
        l.adminUsername.toLowerCase().includes(q) ||
        l.action.toLowerCase().includes(q) ||
        l.targetType.toLowerCase().includes(q) ||
        l.summary.toLowerCase().includes(q),
    )
  }
  if (selectedAction.value !== 'ALL') {
    list = list.filter((l) => l.action === selectedAction.value)
  }
  return list
})

// 分页列表
const pagedLogs = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return filteredLogs.value.slice(start, start + pageSize.value)
})

function resetFilters() {
  searchQuery.value = ''
  selectedAction.value = 'ALL'
  currentPage.value = 1
}

function formatDateTime(val: string | null) {
  if (!val) return '-'
  try {
    const d = new Date(val)
    if (isNaN(d.getTime())) return val
    const y = d.getFullYear()
    const m = String(d.getMonth() + 1).padStart(2, '0')
    const day = String(d.getDate()).padStart(2, '0')
    const h = String(d.getHours()).padStart(2, '0')
    const min = String(d.getMinutes()).padStart(2, '0')
    const s = String(d.getSeconds()).padStart(2, '0')
    return `${y}-${m}-${day} ${h}:${min}:${s}`
  } catch {
    return val
  }
}

function getAvatarBg(username: string) {
  const colors = ['#2563eb', '#7c3aed', '#db2777', '#d97706', '#059669', '#0891b2']
  let hash = 0
  for (let i = 0; i < username.length; i++) {
    hash = username.charCodeAt(i) + ((hash << 5) - hash)
  }
  return colors[Math.abs(hash) % colors.length]
}

function actionTagType(action: string) {
  if (action.includes('DELETE') || action.includes('REJECT') || action.includes('BLACKLIST')) {
    return 'danger'
  }
  if (action.includes('CONFIRM') || action.includes('APPROVE') || action.includes('SUCCESS')) {
    return 'success'
  }
  if (action.includes('UPDATE') || action.includes('CONFIG') || action.includes('ADJUST')) {
    return 'warning'
  }
  return 'info'
}

async function copyText(text: string, label = '内容') {
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success(`${label}已复制到剪贴板`)
  } catch {
    ElMessage.warning('复制失败，请手动选中文本复制')
  }
}

async function loadLogs() {
  loading.value = true
  error.value = ''
  try {
    logs.value = await fetchAuditLogs()
  } catch {
    error.value = '审计日志加载失败'
  } finally {
    loading.value = false
  }
}

onMounted(loadLogs)
</script>

<template>
  <section class="page-section">
    <!-- 顶部标题栏 -->
    <div class="page-header">
      <div>
        <div class="title-row">
          <h1>审计日志</h1>
          <el-tag effect="plain" type="primary" size="small" class="count-pill">
            <el-icon><Document /></el-icon>
            共 {{ totalLogsCount }} 条操作存证
          </el-tag>
        </div>
        <p class="subtitle">查看管理员账号敏感操作、资金变动、风控处置与配置变更的不可篡改审计记录</p>
      </div>
      <el-button type="primary" :icon="Refresh" @click="loadLogs">刷新日志</el-button>
    </div>

    <el-alert v-if="error" :title="error" show-icon type="error" />

    <!-- 顶部审计 KPI 统计卡片 (3 列) -->
    <div class="audit-kpi-grid">
      <div class="kpi-card kpi-total">
        <div class="kpi-icon-box"><el-icon><Tickets /></el-icon></div>
        <div class="kpi-content">
          <div class="kpi-label">累计审计记录</div>
          <div class="kpi-num mono">{{ totalLogsCount }}</div>
        </div>
      </div>

      <div class="kpi-card kpi-admins">
        <div class="kpi-icon-box"><el-icon><User /></el-icon></div>
        <div class="kpi-content">
          <div class="kpi-label">活跃操作管理员</div>
          <div class="kpi-num mono text-primary">{{ uniqueAdminsCount }}</div>
        </div>
      </div>

      <div class="kpi-card kpi-fund">
        <div class="kpi-icon-box"><el-icon><Lock /></el-icon></div>
        <div class="kpi-content">
          <div class="kpi-label">资金与风控核心操作</div>
          <div class="kpi-num mono text-amber">{{ fundOpsCount }}</div>
        </div>
      </div>
    </div>

    <!-- 表格与过滤卡片 -->
    <el-card shadow="never" class="table-card">
      <div class="table-filter-bar">
        <el-input
          v-model="searchQuery"
          placeholder="搜索管理员 / 动作 / 目标类型 / 摘要..."
          :prefix-icon="Search"
          clearable
          class="search-input"
        />

        <div class="filter-actions">
          <el-select
            v-model="selectedAction"
            placeholder="筛选操作动作"
            clearable
            style="width: 220px;"
          >
            <el-option label="全部动作类型" value="ALL" />
            <el-option
              v-for="act in actionOptions"
              :key="act"
              :label="act"
              :value="act"
            />
          </el-select>

          <el-button link type="info" size="small" @click="resetFilters">重置筛选</el-button>
        </div>
      </div>

      <!-- 审计日志表格 -->
      <el-table
        v-loading="loading"
        :data="pagedLogs"
        border
        stripe
        class="custom-table"
      >
        <!-- 管理员 -->
        <el-table-column label="操作管理员" min-width="160">
          <template #default="{ row }">
            <div class="admin-cell">
              <div class="admin-avatar" :style="{ backgroundColor: getAvatarBg(row.adminUsername) }">
                {{ row.adminUsername.charAt(0).toUpperCase() }}
              </div>
              <span class="mono admin-name">{{ row.adminUsername }}</span>
            </div>
          </template>
        </el-table-column>

        <!-- 动作 -->
        <el-table-column label="操作动作" min-width="210">
          <template #default="{ row }">
            <el-tag :type="actionTagType(row.action)" size="small" effect="plain" class="action-tag">
              {{ row.action }}
            </el-tag>
          </template>
        </el-table-column>

        <!-- 目标类型 -->
        <el-table-column label="目标主体类型" width="160" align="center">
          <template #default="{ row }">
            <el-tag size="small" type="info" effect="light">
              {{ row.targetType }}
            </el-tag>
          </template>
        </el-table-column>

        <!-- 摘要说明 -->
        <el-table-column label="操作详细摘要" min-width="320">
          <template #default="{ row }">
            <div class="summary-cell">
              <span class="summary-text">{{ row.summary }}</span>
              <el-button
                link
                :icon="CopyDocument"
                size="small"
                @click="copyText(row.summary, '摘要')"
              />
            </div>
          </template>
        </el-table-column>

        <!-- 时间 -->
        <el-table-column label="记录时间" min-width="180">
          <template #default="{ row }">
            <span class="mono time-text">{{ formatDateTime(row.createdAt) }}</span>
          </template>
        </el-table-column>

        <template #empty>
          <el-empty description="暂无符合条件的审计操作记录" />
        </template>
      </el-table>

      <!-- 底部分页 -->
      <div class="pagination-footer">
        <span class="page-count-text">
          展示 <b>{{ pagedLogs.length }}</b> / 共 <b>{{ filteredLogs.length }}</b> 条操作记录
        </span>
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[10, 15, 30, 50]"
          layout="sizes, prev, pager, next"
          :total="filteredLogs.length"
          size="small"
        />
      </div>
    </el-card>
  </section>
</template>

<style scoped>
.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;
}

.title-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.title-row h1 {
  margin: 0;
  font-size: 24px;
  font-weight: 700;
  color: #0f172a;
}

.subtitle {
  margin: 6px 0 0;
  color: #64748b;
  font-size: 14px;
}

.count-pill {
  font-weight: 600;
  border-radius: 9999px;
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

/* 顶部 KPI 卡片网格 (3列) */
.audit-kpi-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
  margin-bottom: 18px;
}

@media (max-width: 900px) {
  .audit-kpi-grid {
    grid-template-columns: 1fr;
  }
}

.kpi-card {
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  padding: 16px;
  display: flex;
  align-items: center;
  gap: 14px;
  transition: all 0.2s ease-in-out;
}

.kpi-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 14px -2px rgba(0, 0, 0, 0.06);
}

.kpi-icon-box {
  width: 44px;
  height: 44px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  flex-shrink: 0;
}

.kpi-total .kpi-icon-box { background: #eff6ff; color: #2563eb; }
.kpi-admins .kpi-icon-box { background: #f5f3ff; color: #7c3aed; }
.kpi-fund .kpi-icon-box { background: #fef3c7; color: #b45309; }

.kpi-content {
  display: flex;
  flex-direction: column;
}

.kpi-label {
  font-size: 12px;
  color: #64748b;
  font-weight: 500;
}

.kpi-num {
  font-size: 22px;
  font-weight: 700;
  color: #0f172a;
  margin-top: 2px;
}

.text-primary { color: #2563eb; }
.text-amber { color: #d97706; }

/* 表格卡片与过滤器 */
.table-card {
  border-radius: 10px;
  border: 1px solid #e2e8f0;
}

.table-filter-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
  gap: 12px;
  flex-wrap: wrap;
}

.search-input {
  max-width: 320px;
}

.filter-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.admin-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.admin-avatar {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  color: #ffffff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 12px;
  flex-shrink: 0;
}

.admin-name {
  font-size: 13px;
  color: #1e293b;
  font-weight: 600;
}

.action-tag {
  font-family: 'SFMono-Regular', Consolas, monospace;
}

.summary-cell {
  display: flex;
  align-items: center;
  gap: 6px;
}

.summary-text {
  font-size: 12px;
  color: #334155;
  line-height: 1.4;
}

.time-text {
  font-size: 12px;
  color: #64748b;
}

.pagination-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px solid #f1f5f9;
}

.page-count-text {
  font-size: 12px;
  color: #64748b;
}
</style>
