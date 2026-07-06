<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
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

function formatBytes(bytes: number) {
  if (bytes < 1024) {
    return `${bytes} B`
  }
  const units = ['KB', 'MB', 'GB']
  let value = bytes / 1024
  let unitIndex = 0
  while (value >= 1024 && unitIndex < units.length - 1) {
    value /= 1024
    unitIndex += 1
  }
  return `${value.toFixed(1)} ${units[unitIndex]}`
}

function formatDuration(seconds: number) {
  const days = Math.floor(seconds / 86400)
  const hours = Math.floor((seconds % 86400) / 3600)
  const minutes = Math.floor((seconds % 3600) / 60)
  if (days > 0) {
    return `${days}天 ${hours}小时`
  }
  if (hours > 0) {
    return `${hours}小时 ${minutes}分钟`
  }
  if (minutes === 0) {
    return '小于 1 分钟'
  }
  return `${minutes}分钟`
}

function formatContext(context: Record<string, string>) {
  const entries = Object.entries(context)
  if (entries.length === 0) {
    return '-'
  }
  return entries.map(([key, value]) => `${key}: ${value}`).join(' / ')
}

function statusType(status: string) {
  if (status === 'UP') {
    return 'success'
  }
  if (status === 'DEGRADED') {
    return 'warning'
  }
  return 'danger'
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
    <div class="page-header">
      <div>
        <h1>运行诊断</h1>
        <p>查看后端运行状态、JVM 内存和内部依赖连通性</p>
      </div>
      <el-button @click="loadRuntime">刷新</el-button>
    </div>

    <el-alert v-if="error" :title="error" show-icon type="error" />

    <template v-if="runtime">
      <div class="metric-grid">
        <div class="metric">
          <div class="metric-label">总体状态</div>
          <div class="metric-value">
            <el-tag :type="statusType(runtime.status)" size="large">{{ runtime.status }}</el-tag>
          </div>
        </div>
        <div class="metric">
          <div class="metric-label">服务</div>
          <div class="metric-value runtime-metric-text">{{ runtime.application.service }}</div>
        </div>
        <div class="metric">
          <div class="metric-label">运行时间</div>
          <div class="metric-value runtime-metric-text">
            {{ formatDuration(runtime.application.uptimeSeconds) }}
          </div>
        </div>
        <div class="metric">
          <div class="metric-label">检查时间</div>
          <div class="metric-value runtime-metric-text mono">{{ runtime.checkedAt }}</div>
        </div>
      </div>

      <div class="panel">
        <div class="panel-title">JVM 与内存</div>
        <div class="runtime-grid">
          <div>
            <div class="muted">Java 版本</div>
            <div class="mono">{{ runtime.application.javaVersion }}</div>
          </div>
          <div>
            <div class="muted">应用版本</div>
            <div class="mono">{{ runtime.application.version }}</div>
          </div>
          <div>
            <div class="muted">内存使用</div>
            <div class="mono">
              {{ formatBytes(runtime.memory.usedBytes) }} / {{ formatBytes(runtime.memory.maxBytes) }}
            </div>
          </div>
        </div>
        <el-progress :percentage="memoryUsagePercent" :status="memoryUsagePercent > 85 ? 'warning' : undefined" />
      </div>

      <div class="panel">
        <div class="panel-title">内容源诊断事件</div>
        <template v-if="contentProviderDiagnostics">
          <div class="diagnostics-summary">
            <div>
              <div class="muted">事件总数</div>
              <div class="diagnostics-total">{{ contentProviderDiagnostics.totalEvents }}</div>
            </div>
            <div class="diagnostics-counters">
              <el-tag
                v-for="counter in diagnosticCounters"
                :key="counter.eventType"
                effect="plain"
                type="warning"
              >
                {{ counter.eventType }}: {{ counter.count }}
              </el-tag>
              <el-tag v-if="diagnosticCounters.length === 0 && diagnosticsAreClean" effect="plain" type="success">
                diagnostics clean
              </el-tag>
              <el-tag v-else-if="diagnosticCounters.length === 0" effect="plain" type="warning">
                未提供类型计数
              </el-tag>
            </div>
          </div>

          <el-table
            v-if="contentProviderDiagnostics.recentEvents.length > 0"
            :data="contentProviderDiagnostics.recentEvents"
            border
          >
            <el-table-column label="事件类型" min-width="180" prop="eventType" />
            <el-table-column label="观察时间" min-width="190" prop="observedAt" />
            <el-table-column label="上下文" min-width="260">
              <template #default="{ row }">
                <span class="mono context-text">{{ formatContext(row.context) }}</span>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-else description="暂无内容源异常事件" />
        </template>
        <el-empty v-else description="内容源诊断暂不可用" />
      </div>

      <el-table :data="runtime.dependencies" border>
        <el-table-column label="依赖" min-width="180" prop="name" />
        <el-table-column label="状态" width="140">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="说明" min-width="260" prop="detail" />
      </el-table>
    </template>
  </section>
</template>

<style scoped>
.runtime-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 16px;
  margin-bottom: 16px;
}

.runtime-metric-text {
  font-size: 18px;
  overflow-wrap: anywhere;
}

.diagnostics-summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 16px;
}

.diagnostics-total {
  font-size: 28px;
  font-weight: 700;
  line-height: 1.2;
}

.diagnostics-counters {
  display: flex;
  flex: 1;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}

.context-text {
  overflow-wrap: anywhere;
}

@media (max-width: 720px) {
  .diagnostics-summary {
    align-items: flex-start;
    flex-direction: column;
  }

  .diagnostics-counters {
    justify-content: flex-start;
  }
}
</style>
