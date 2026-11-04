<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  Warning,
  CircleClose,
  Check,
  Refresh,
  Search,
  Bell,
  Clock,
  User,
  CopyDocument,
  DataAnalysis,
} from '@element-plus/icons-vue'
import {
  acknowledgeSystemAlert,
  evaluateSystemAlerts,
  fetchSystemAlerts,
} from '../services/adminApi'
import type { SystemAlert, SystemAlertStatus } from '../services/adminApi'

const loading = ref(false)
const evaluating = ref(false)
const error = ref('')
const selectedStatus = ref<SystemAlertStatus | ''>('')
const selectedSeverity = ref<'ALL' | 'CRITICAL' | 'WARNING'>('ALL')
const searchQuery = ref('')
const alerts = ref<SystemAlert[]>([])

const openCount = computed(() => alerts.value.filter((alert) => alert.status === 'OPEN').length)
const acknowledgedCount = computed(
  () => alerts.value.filter((alert) => alert.status === 'ACKNOWLEDGED').length,
)
const resolvedCount = computed(
  () => alerts.value.filter((alert) => alert.status === 'RESOLVED').length,
)
const criticalCount = computed(
  () => alerts.value.filter((alert) => alert.severity === 'CRITICAL' && alert.status !== 'RESOLVED').length,
)

// 过滤后的告警列表
const filteredAlerts = computed(() => {
  let list = alerts.value
  const q = searchQuery.value.trim().toLowerCase()
  if (q) {
    list = list.filter(
      (a) =>
        a.title.toLowerCase().includes(q) ||
        a.detail.toLowerCase().includes(q) ||
        (a.acknowledgedBy && a.acknowledgedBy.toLowerCase().includes(q)),
    )
  }
  if (selectedStatus.value) {
    list = list.filter((a) => a.status === selectedStatus.value)
  }
  if (selectedSeverity.value !== 'ALL') {
    list = list.filter((a) => a.severity === selectedSeverity.value)
  }
  return list
})

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

async function copyText(text: string, label = '内容') {
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success(`${label}已复制到剪贴板`)
  } catch {
    ElMessage.warning('复制失败，请手动选中文本复制')
  }
}

async function loadAlerts() {
  loading.value = true
  error.value = ''
  try {
    alerts.value = await fetchSystemAlerts(selectedStatus.value || undefined)
  } catch {
    error.value = '异常告警加载失败'
  } finally {
    loading.value = false
  }
}

async function evaluateAlerts() {
  evaluating.value = true
  error.value = ''
  try {
    alerts.value = await evaluateSystemAlerts()
    selectedStatus.value = ''
    ElMessage.success('运行诊断已刷新，告警水位线已重新评估')
  } catch {
    error.value = '异常评估失败'
  } finally {
    evaluating.value = false
  }
}

async function acknowledgeAlert(alert: SystemAlert) {
  loading.value = true
  error.value = ''
  try {
    await acknowledgeSystemAlert(alert.id)
    ElMessage.success(`告警 "${alert.title}" 已确认处理`)
    await loadAlerts()
  } catch {
    error.value = '告警确认失败'
    loading.value = false
  }
}

onMounted(loadAlerts)
</script>

<template>
  <section class="page-section">
    <!-- 顶部标题栏 -->
    <div class="page-header">
      <div>
        <div class="title-row">
          <h1>异常告警</h1>
          <el-tag
            v-if="openCount > 0"
            effect="dark"
            type="danger"
            size="small"
            class="pending-pill"
          >
            <span class="pulse-dot red"></span> {{ openCount }} 起未处理告警
          </el-tag>
          <el-tag v-else effect="plain" type="success" size="small" class="pending-pill">
            当前无未处理告警
          </el-tag>
        </div>
        <p class="subtitle">
          实时监控运行诊断沉淀的内容源断流、超时与系统异常水位线，支持人工确认与自动恢复
        </p>
      </div>
      <div class="header-actions">
        <el-button
          type="primary"
          :icon="DataAnalysis"
          :loading="evaluating"
          @click="evaluateAlerts"
        >
          刷新诊断并重新评估
        </el-button>
        <el-button :icon="Refresh" @click="loadAlerts">刷新列表</el-button>
      </div>
    </div>

    <el-alert v-if="error" :title="error" show-icon type="error" />

    <!-- 顶部告警 KPI 统计卡片 (4 列) -->
    <div class="alert-kpi-grid">
      <div class="kpi-card kpi-open" :class="{ 'has-open': openCount > 0 }">
        <div class="kpi-icon-box"><el-icon><Bell /></el-icon></div>
        <div class="kpi-content">
          <div class="kpi-label">未处理待办</div>
          <div class="kpi-num mono" :class="{ 'text-red': openCount > 0 }">{{ openCount }}</div>
        </div>
      </div>

      <div class="kpi-card kpi-critical">
        <div class="kpi-icon-box"><el-icon><CircleClose /></el-icon></div>
        <div class="kpi-content">
          <div class="kpi-label">高危级告警 (CRITICAL)</div>
          <div class="kpi-num mono" :class="{ 'text-red': criticalCount > 0 }">{{ criticalCount }}</div>
        </div>
      </div>

      <div class="kpi-card kpi-acked">
        <div class="kpi-icon-box"><el-icon><Clock /></el-icon></div>
        <div class="kpi-content">
          <div class="kpi-label">已确认跟进中</div>
          <div class="kpi-num mono text-amber">{{ acknowledgedCount }}</div>
        </div>
      </div>

      <div class="kpi-card kpi-resolved">
        <div class="kpi-icon-box"><el-icon><Check /></el-icon></div>
        <div class="kpi-content">
          <div class="kpi-label">已自愈 / 恢复</div>
          <div class="kpi-num mono text-green">{{ resolvedCount }}</div>
        </div>
      </div>
    </div>

    <!-- 告警表格与多维过滤器 -->
    <el-card shadow="never" class="table-card">
      <div class="table-filter-bar">
        <el-input
          v-model="searchQuery"
          placeholder="搜索告警标题 / 详情描述 / 确认人..."
          :prefix-icon="Search"
          clearable
          class="search-input"
        />

        <div class="filter-actions">
          <el-segmented
            v-model="selectedStatus"
            :options="[
              { label: '全部状态', value: '' },
              { label: `未处理 (${openCount})`, value: 'OPEN' },
              { label: `已确认 (${acknowledgedCount})`, value: 'ACKNOWLEDGED' },
              { label: `已恢复 (${resolvedCount})`, value: 'RESOLVED' },
            ]"
            size="small"
          />

          <el-radio-group v-model="selectedSeverity" size="small">
            <el-radio-button value="ALL">全部级别</el-radio-button>
            <el-radio-button value="CRITICAL">严重 (CRITICAL)</el-radio-button>
            <el-radio-button value="WARNING">警告 (WARNING)</el-radio-button>
          </el-radio-group>
        </div>
      </div>

      <!-- 告警列表表格 -->
      <el-table
        v-loading="loading"
        :data="filteredAlerts"
        border
        stripe
        class="custom-table"
      >
        <!-- 级别 -->
        <el-table-column label="严重等级" width="130" align="center">
          <template #default="{ row }">
            <span
              class="severity-badge"
              :class="row.severity === 'CRITICAL' ? 'sev-critical' : 'sev-warning'"
            >
              <el-icon><CircleClose v-if="row.severity === 'CRITICAL'" /><Warning v-else /></el-icon>
              {{ row.severity }}
            </span>
          </template>
        </el-table-column>

        <!-- 状态 -->
        <el-table-column label="处置状态" width="130" align="center">
          <template #default="{ row }">
            <span
              class="status-pill"
              :class="{
                'pill-open': row.status === 'OPEN',
                'pill-acked': row.status === 'ACKNOWLEDGED',
                'pill-resolved': row.status === 'RESOLVED',
              }"
            >
              <span class="status-dot"></span>
              {{ row.status === 'OPEN' ? '未处理' : (row.status === 'ACKNOWLEDGED' ? '已确认' : '已恢复') }}
            </span>
          </template>
        </el-table-column>

        <!-- 标题 -->
        <el-table-column label="告警标题" min-width="220">
          <template #default="{ row }">
            <span class="alert-title font-bold">{{ row.title }}</span>
          </template>
        </el-table-column>

        <!-- 说明与上下文 -->
        <el-table-column label="异常说明与上下文" min-width="320">
          <template #default="{ row }">
            <div class="detail-cell">
              <span class="detail-text">{{ row.detail }}</span>
              <el-button
                link
                :icon="CopyDocument"
                size="small"
                @click="copyText(row.detail, '告警说明')"
              />
            </div>
          </template>
        </el-table-column>

        <!-- 最近出现时间 -->
        <el-table-column label="最近捕捉时间" min-width="180">
          <template #default="{ row }">
            <span class="mono time-text">{{ formatDateTime(row.lastSeenAt) }}</span>
          </template>
        </el-table-column>

        <!-- 确认人 -->
        <el-table-column label="确认操作人" width="130">
          <template #default="{ row }">
            <div v-if="row.acknowledgedBy" class="actor-cell">
              <el-icon><User /></el-icon>
              <span class="mono">{{ row.acknowledgedBy }}</span>
            </div>
            <span v-else class="text-dim">-</span>
          </template>
        </el-table-column>

        <!-- 操作 -->
        <el-table-column align="center" fixed="right" label="操作" width="120">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'OPEN'"
              size="small"
              type="primary"
              @click="acknowledgeAlert(row)"
            >
              确认告警
            </el-button>
            <span v-else-if="row.status === 'ACKNOWLEDGED'" class="text-amber">跟进中</span>
            <span v-else class="text-green">已恢复</span>
          </template>
        </el-table-column>

        <template #empty>
          <el-empty description="当前无匹配的系统异常告警" />
        </template>
      </el-table>
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

.pending-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-weight: 600;
  border-radius: 9999px;
  padding: 0 10px;
}

.pulse-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
}
.pulse-dot.red {
  background-color: #ef4444;
  box-shadow: 0 0 0 0 rgba(239, 68, 68, 0.7);
  animation: pulse 2s infinite;
}

@keyframes pulse {
  0% { transform: scale(0.95); box-shadow: 0 0 0 0 rgba(239, 68, 68, 0.7); }
  70% { transform: scale(1); box-shadow: 0 0 0 6px rgba(239, 68, 68, 0); }
  100% { transform: scale(0.95); box-shadow: 0 0 0 0 rgba(239, 68, 68, 0); }
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

/* 顶部 KPI 卡片网格 (4列) */
.alert-kpi-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 18px;
}

@media (max-width: 1024px) {
  .alert-kpi-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 640px) {
  .alert-kpi-grid {
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

.kpi-open .kpi-icon-box { background: #fee2e2; color: #dc2626; }
.kpi-critical .kpi-icon-box { background: #fef2f2; color: #b91c1c; }
.kpi-acked .kpi-icon-box { background: #fffbeb; color: #d97706; }
.kpi-resolved .kpi-icon-box { background: #f0fdf4; color: #16a34a; }

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

.text-red { color: #dc2626; }
.text-amber { color: #d97706; }
.text-green { color: #16a34a; }
.text-dim { color: #94a3b8; }

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

/* 严重等级 Badge */
.severity-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 8px;
  border-radius: 6px;
  font-size: 11px;
  font-weight: 700;
}

.sev-critical {
  background: #fef2f2;
  color: #b91c1c;
  border: 1px solid #fecaca;
}

.sev-warning {
  background: #fffbeb;
  color: #b45309;
  border: 1px solid #fde68a;
}

/* 状态药丸 */
.status-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 3px 10px;
  border-radius: 9999px;
  font-size: 12px;
  font-weight: 600;
}

.status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
}

.pill-open {
  background: #fef2f2;
  color: #b91c1c;
  border: 1px solid #fecaca;
}
.pill-open .status-dot { background: #ef4444; }

.pill-acked {
  background: #fffbeb;
  color: #b45309;
  border: 1px solid #fde68a;
}
.pill-acked .status-dot { background: #f59e0b; }

.pill-resolved {
  background: #f0fdf4;
  color: #15803d;
  border: 1px solid #bbf7d0;
}
.pill-resolved .status-dot { background: #22c55e; }

.alert-title {
  color: #0f172a;
  font-size: 13px;
}

.detail-cell {
  display: flex;
  align-items: center;
  gap: 6px;
}

.detail-text {
  font-size: 12px;
  color: #475569;
  line-height: 1.4;
}

.actor-cell {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #334155;
}

.time-text {
  font-size: 12px;
  color: #64748b;
}
</style>
