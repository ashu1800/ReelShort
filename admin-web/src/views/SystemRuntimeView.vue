<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  Monitor,
  Refresh,
  Check,
  Warning,
  DataLine,
  Cpu,
  Clock,
  CopyDocument,
} from '@element-plus/icons-vue'
import { fetchSystemRuntime } from '../services/adminApi'
import type { SystemRuntimeResponse } from '../services/adminApi'

const loading = ref(false)
const error = ref('')
const runtime = ref<SystemRuntimeResponse | null>(null)

const contentProviderDiagnostics = computed(() => runtime.value?.contentProviderDiagnostics ?? null)

const memoryUsagePercent = computed(() => {
  const memory = runtime.value?.memory
  if (!memory || memory.maxBytes <= 0) {
    return 0
  }
  return Math.round((memory.usedBytes / memory.maxBytes) * 100)
})

const diagnosticCounters = computed(() => {
  const counters = contentProviderDiagnostics.value?.counters ?? {}
  return Object.entries(counters)
    .sort(([left], [right]) => left.localeCompare(right))
    .map(([eventType, count]) => ({ eventType, count }))
})

const diagnosticsAreClean = computed(() => (contentProviderDiagnostics.value?.totalEvents ?? 0) === 0)

function formatBytes(bytes?: number) {
  if (!bytes || bytes <= 0) return '0 B'
  if (bytes < 1024) return `${bytes} B`
  const units = ['KB', 'MB', 'GB']
  let value = bytes / 1024
  let unitIndex = 0
  while (value >= 1024 && unitIndex < units.length - 1) {
    value /= 1024
    unitIndex += 1
  }
  return `${value.toFixed(1)} ${units[unitIndex]}`
}

function formatDuration(seconds?: number) {
  if (!seconds || seconds <= 0) return '刚启动'
  const days = Math.floor(seconds / 86400)
  const hours = Math.floor((seconds % 86400) / 3600)
  const minutes = Math.floor((seconds % 3600) / 60)
  if (days > 0) return `${days}天 ${hours}小时`
  if (hours > 0) return `${hours}小时 ${minutes}分钟`
  if (minutes === 0) return '小于 1 分钟'
  return `${minutes}分钟`
}

function formatContext(context: Record<string, string>) {
  const entries = Object.entries(context)
  if (entries.length === 0) {
    return '-'
  }
  return entries.map(([key, value]) => `${key}: ${value}`).join(' / ')
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

async function copyText(text: string, label = '内容') {
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success(`${label}已复制到剪贴板`)
  } catch {
    ElMessage.warning('复制失败，请手动选中文本复制')
  }
}

async function loadRuntime() {
  loading.value = true
  error.value = ''
  try {
    runtime.value = await fetchSystemRuntime()
  } catch {
    error.value = '运行诊断加载失败'
  } finally {
    loading.value = false
  }
}

onMounted(loadRuntime)
</script>

<template>
  <section v-loading="loading" class="page-section">
    <!-- 顶部标题栏 -->
    <div class="page-header">
      <div>
        <div class="title-row">
          <h1>运行诊断</h1>
          <el-tag
            v-if="runtime?.status === 'UP'"
            effect="plain"
            type="success"
            size="small"
            class="status-pill"
          >
            <span class="pulse-dot green"></span> 服务健康 (UP)
          </el-tag>
          <el-tag
            v-else-if="runtime"
            effect="plain"
            type="warning"
            size="small"
            class="status-pill"
          >
            <span class="pulse-dot amber"></span> 状态降级 ({{ runtime.status }})
          </el-tag>
        </div>
        <p class="subtitle">查看后端 JVM 内存占用、底层数据库/内容源连通性及上游结构化诊断事件</p>
      </div>
      <el-button type="primary" :icon="Refresh" @click="loadRuntime">刷新探针</el-button>
    </div>

    <el-alert v-if="error" :title="error" show-icon type="error" />

    <template v-if="runtime">
      <!-- 顶部系统 KPI 统计卡片 (4 列) -->
      <div class="runtime-kpi-grid">
        <div class="kpi-card kpi-status">
          <div class="kpi-icon-box"><el-icon><Monitor /></el-icon></div>
          <div class="kpi-content">
            <div class="kpi-label">应用总体状态</div>
            <div class="kpi-num mono" :class="runtime.status === 'UP' ? 'text-green' : 'text-amber'">
              {{ runtime.status }}
            </div>
          </div>
        </div>

        <div class="kpi-card kpi-uptime">
          <div class="kpi-icon-box"><el-icon><Clock /></el-icon></div>
          <div class="kpi-content">
            <div class="kpi-label">已持续运行</div>
            <div class="kpi-num mono">{{ formatDuration(runtime.application.uptimeSeconds) }}</div>
          </div>
        </div>

        <div class="kpi-card kpi-service">
          <div class="kpi-icon-box"><el-icon><Cpu /></el-icon></div>
          <div class="kpi-content">
            <div class="kpi-label">后端应用版本</div>
            <div class="kpi-num mono">{{ runtime.application.service }} v{{ runtime.application.version }}</div>
          </div>
        </div>

        <div class="kpi-card kpi-probe">
          <div class="kpi-icon-box"><el-icon><Check /></el-icon></div>
          <div class="kpi-content">
            <div class="kpi-label">最近探针检测</div>
            <div class="kpi-num mono time-tiny">{{ formatDateTime(runtime.checkedAt) }}</div>
          </div>
        </div>
      </div>

      <!-- 中段：双栏（左：JVM 与内存 + 基础设施连通性；右：诊断事件概览） -->
      <div class="runtime-middle-grid">
        <!-- 左侧：JVM 内存与进程信息 -->
        <el-card shadow="never" class="panel-box">
          <template #header>
            <div class="panel-header">
              <div class="panel-header-title">
                <el-icon><Cpu /></el-icon>
                <span>JVM 堆内存与进程</span>
              </div>
              <span class="mono java-tag">Java {{ runtime.application.javaVersion }}</span>
            </div>
          </template>

          <div class="memory-block">
            <div class="memory-meta">
              <span class="mem-title">堆内存占用比</span>
              <span class="mem-val mono">
                {{ formatBytes(runtime.memory.usedBytes) }} / {{ formatBytes(runtime.memory.maxBytes) }}
                <b>({{ memoryUsagePercent }}%)</b>
              </span>
            </div>
            <el-progress
              :percentage="memoryUsagePercent"
              :stroke-width="12"
              :color="memoryUsagePercent > 85 ? '#ef4444' : (memoryUsagePercent > 65 ? '#f59e0b' : '#3b82f6')"
              :show-text="false"
            />
          </div>

          <!-- 核心依赖网格 -->
          <div class="dep-section">
            <div class="dep-title">底层核心依赖服务</div>
            <div class="dep-list">
              <div
                v-for="dep in runtime.dependencies"
                :key="dep.name"
                class="dep-item"
              >
                <div class="dep-info">
                  <span class="dep-name font-bold">{{ dep.name }}</span>
                  <span class="dep-detail mono">{{ dep.detail }}</span>
                </div>
                <el-tag
                  size="small"
                  :type="dep.status === 'UP' ? 'success' : 'danger'"
                  effect="plain"
                  class="dep-tag"
                >
                  <el-icon><Check v-if="dep.status === 'UP'" /><Warning v-else /></el-icon>
                  {{ dep.status }}
                </el-tag>
              </div>
            </div>
          </div>
        </el-card>

        <!-- 右侧：内容源结构化诊断事件 -->
        <el-card shadow="never" class="panel-box">
          <template #header>
            <div class="panel-header">
              <div class="panel-header-title">
                <el-icon><DataLine /></el-icon>
                <span>内容源结构化诊断</span>
              </div>
              <el-tag
                v-if="diagnosticsAreClean"
                type="success"
                size="small"
                effect="plain"
              >
                诊断干净 (Clean)
              </el-tag>
              <el-tag
                v-else
                type="warning"
                size="small"
                effect="dark"
              >
                {{ contentProviderDiagnostics?.totalEvents ?? 0 }} 起异常事件
              </el-tag>
            </div>
          </template>

          <template v-if="contentProviderDiagnostics">
            <div class="diag-counters-row">
              <span class="counter-label">事件类型计数:</span>
              <div class="counter-chips">
                <span
                  v-for="counter in diagnosticCounters"
                  :key="counter.eventType"
                  class="diag-chip"
                >
                  <span class="chip-name">{{ counter.eventType }}:</span>
                  <span class="chip-count mono">{{ counter.count }}</span>
                </span>
                <span v-if="diagnosticCounters.length === 0 && diagnosticsAreClean" class="clean-text">
                  无任何内容源故障或爬虫结构异常记录
                </span>
              </div>
            </div>

            <!-- 最近异常事件列表 -->
            <div class="recent-events-wrapper">
              <div class="events-title">最近捕捉事件流</div>
              <el-empty
                v-if="contentProviderDiagnostics.recentEvents.length === 0"
                description="暂无内容源异常事件"
              />
              <el-table
                v-else
                :data="contentProviderDiagnostics.recentEvents"
                border
                size="small"
                class="events-table"
              >
                <el-table-column label="事件类型" width="180" prop="eventType">
                  <template #default="{ row }">
                    <span class="mono font-bold text-amber">{{ row.eventType }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="观察时间" width="180">
                  <template #default="{ row }">
                    <span class="mono">{{ formatDateTime(row.observedAt) }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="排查上下文" min-width="260">
                  <template #default="{ row }">
                    <div class="context-cell">
                      <span class="mono context-text" :title="formatContext(row.context)">
                        {{ formatContext(row.context) }}
                      </span>
                      <el-button
                        link
                        :icon="CopyDocument"
                        size="small"
                        @click="copyText(formatContext(row.context), '上下文')"
                      />
                    </div>
                  </template>
                </el-table-column>
              </el-table>
            </div>
          </template>
          <el-empty v-else description="内容源诊断暂不可用" />
        </el-card>
      </div>
    </template>
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

.status-pill {
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
.pulse-dot.green {
  background-color: #22c55e;
  box-shadow: 0 0 0 0 rgba(34, 197, 94, 0.7);
  animation: pulse 2s infinite;
}
.pulse-dot.amber {
  background-color: #f59e0b;
  box-shadow: 0 0 0 0 rgba(245, 158, 11, 0.7);
  animation: pulse 2s infinite;
}

@keyframes pulse {
  0% { transform: scale(0.95); box-shadow: 0 0 0 0 rgba(34, 197, 94, 0.7); }
  70% { transform: scale(1); box-shadow: 0 0 0 6px rgba(34, 197, 94, 0); }
  100% { transform: scale(0.95); box-shadow: 0 0 0 0 rgba(34, 197, 94, 0); }
}

/* 顶部 KPI 卡片网格 (4列) */
.runtime-kpi-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 18px;
}

@media (max-width: 1024px) {
  .runtime-kpi-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 640px) {
  .runtime-kpi-grid {
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

.kpi-status .kpi-icon-box { background: #f0fdf4; color: #16a34a; }
.kpi-uptime .kpi-icon-box { background: #eff6ff; color: #2563eb; }
.kpi-service .kpi-icon-box { background: #f5f3ff; color: #7c3aed; }
.kpi-probe .kpi-icon-box { background: #ecfdf5; color: #059669; }

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
  font-size: 19px;
  font-weight: 700;
  color: #0f172a;
  margin-top: 2px;
}

.time-tiny {
  font-size: 13px;
}

.text-green { color: #16a34a; }
.text-amber { color: #d97706; }

/* 中段布局 */
.runtime-middle-grid {
  display: grid;
  grid-template-columns: 1fr 1.3fr;
  gap: 18px;
}

@media (max-width: 1200px) {
  .runtime-middle-grid {
    grid-template-columns: 1fr;
  }
}

.panel-box {
  border-radius: 10px;
  border: 1px solid #e2e8f0;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.panel-header-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 700;
  color: #0f172a;
}

.java-tag {
  font-size: 12px;
  color: #64748b;
  background: #f1f5f9;
  padding: 2px 8px;
  border-radius: 4px;
}

/* 内存卡片 */
.memory-block {
  margin-bottom: 20px;
}

.memory-meta {
  display: flex;
  justify-content: space-between;
  margin-bottom: 8px;
  font-size: 13px;
}

.mem-title {
  color: #475569;
  font-weight: 500;
}

.mem-val {
  color: #0f172a;
}

/* 依赖列表 */
.dep-section {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.dep-title {
  font-size: 12px;
  font-weight: 600;
  color: #64748b;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.dep-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.dep-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
}

.dep-info {
  display: flex;
  flex-direction: column;
}

.dep-name {
  font-size: 13px;
  color: #0f172a;
}

.dep-detail {
  font-size: 11px;
  color: #94a3b8;
}

.dep-tag {
  display: flex;
  align-items: center;
  gap: 4px;
}

/* 诊断部分 */
.diag-counters-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.counter-label {
  font-size: 12px;
  color: #64748b;
  font-weight: 500;
}

.counter-chips {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.diag-chip {
  background: #fffbeb;
  border: 1px solid #fde68a;
  border-radius: 6px;
  padding: 3px 8px;
  font-size: 12px;
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.chip-name {
  color: #92400e;
}

.chip-count {
  color: #b45309;
  font-weight: 700;
}

.clean-text {
  font-size: 12px;
  color: #16a34a;
}

.recent-events-wrapper {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.events-title {
  font-size: 12px;
  font-weight: 600;
  color: #64748b;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.context-cell {
  display: flex;
  align-items: center;
  gap: 6px;
}

.context-text {
  font-size: 11px;
  color: #475569;
  word-break: break-all;
}
</style>
