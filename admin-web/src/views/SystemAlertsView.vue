<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
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
const alerts = ref<SystemAlert[]>([])

const openCount = computed(() => alerts.value.filter((alert) => alert.status === 'OPEN').length)
const acknowledgedCount = computed(() => alerts.value.filter((alert) => alert.status === 'ACKNOWLEDGED').length)
const resolvedCount = computed(() => alerts.value.filter((alert) => alert.status === 'RESOLVED').length)

function severityType(severity: string) {
  return severity === 'CRITICAL' ? 'danger' : 'warning'
}

function statusType(status: string) {
  if (status === 'OPEN') {
    return 'danger'
  }
  if (status === 'ACKNOWLEDGED') {
    return 'warning'
  }
  return 'success'
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
    <div class="page-header">
      <div>
        <h1>异常告警</h1>
        <p>查看运行诊断沉淀的异常记录并确认处理状态</p>
      </div>
      <div class="header-actions">
        <el-button :loading="evaluating" @click="evaluateAlerts">刷新诊断并评估</el-button>
        <el-button @click="loadAlerts">刷新列表</el-button>
      </div>
    </div>

    <el-alert v-if="error" :title="error" show-icon type="error" />

    <div class="metric-grid">
      <div class="metric">
        <div class="metric-label">未处理</div>
        <div class="metric-value">{{ openCount }}</div>
      </div>
      <div class="metric">
        <div class="metric-label">已确认</div>
        <div class="metric-value">{{ acknowledgedCount }}</div>
      </div>
      <div class="metric">
        <div class="metric-label">已恢复</div>
        <div class="metric-value">{{ resolvedCount }}</div>
      </div>
    </div>

    <div class="panel alert-toolbar">
      <el-segmented
        v-model="selectedStatus"
        :options="[
          { label: '全部', value: '' },
          { label: '未处理', value: 'OPEN' },
          { label: '已确认', value: 'ACKNOWLEDGED' },
          { label: '已恢复', value: 'RESOLVED' },
        ]"
        @change="loadAlerts"
      />
    </div>

    <el-table v-loading="loading" :data="alerts" border>
      <el-table-column label="级别" width="120">
        <template #default="{ row }">
          <el-tag :type="severityType(row.severity)">{{ row.severity }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="130">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)">{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="标题" min-width="220" prop="title" />
      <el-table-column label="说明" min-width="220" prop="detail" />
      <el-table-column label="最近出现" min-width="210" prop="lastSeenAt" />
      <el-table-column label="确认人" width="120">
        <template #default="{ row }">
          {{ row.acknowledgedBy || '-' }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="120">
        <template #default="{ row }">
          <el-button
            v-if="row.status === 'OPEN'"
            size="small"
            type="primary"
            @click="acknowledgeAlert(row)"
          >
            确认
          </el-button>
          <span v-else class="muted">-</span>
        </template>
      </el-table-column>
    </el-table>
  </section>
</template>

<style scoped>
.header-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.alert-toolbar {
  display: flex;
  align-items: center;
}
</style>
