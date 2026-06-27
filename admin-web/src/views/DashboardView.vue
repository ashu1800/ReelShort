<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { fetchDashboardSummary } from '../services/adminApi'
import type { AdminAuditLog, AdminDashboardSummary } from '../services/adminApi'

const loading = ref(false)
const error = ref('')
const summary = ref<AdminDashboardSummary | null>(null)
const auditLogs = ref<AdminAuditLog[]>([])

function formatMoney(cents: number) {
  return `¥${(cents / 100).toFixed(2)}`
}

const metrics = computed(() => [
  { label: '用户总数', value: summary.value?.users.total ?? 0 },
  { label: '禁用用户', value: summary.value?.users.disabled ?? 0 },
  { label: '充值订单', value: summary.value?.orders.total ?? 0 },
  { label: '已支付订单', value: summary.value?.orders.paid ?? 0 },
  { label: '充值金额', value: formatMoney(summary.value?.orders.totalAmountCents ?? 0) },
  { label: '支付事件', value: summary.value?.payments.total ?? 0 },
  { label: '拒绝支付', value: summary.value?.payments.rejected ?? 0 },
  { label: '剧集缓存', value: summary.value?.content.bookCount ?? 0 },
  { label: '分集缓存', value: summary.value?.content.episodeCacheCount ?? 0 },
])

async function loadDashboard() {
  loading.value = true
  error.value = ''
  try {
    const data = await fetchDashboardSummary()
    summary.value = data
    auditLogs.value = data.auditLogs.latest
  } catch {
    error.value = '控制台数据加载失败'
  } finally {
    loading.value = false
  }
}

onMounted(loadDashboard)
</script>

<template>
  <section v-loading="loading" class="page-section">
    <div class="page-header">
      <div>
        <h1>控制台</h1>
        <p>运营数据和系统缓存状态概览</p>
      </div>
      <el-button @click="loadDashboard">刷新</el-button>
    </div>
    <el-alert v-if="error" :title="error" show-icon type="error" />
    <div class="metric-grid">
      <div v-for="metric in metrics" :key="metric.label" class="metric">
        <div class="metric-label">{{ metric.label }}</div>
        <div class="metric-value">{{ metric.value }}</div>
      </div>
    </div>
    <section class="panel">
      <div class="panel-title">最近审计日志</div>
      <el-empty v-if="!auditLogs.length && !loading" description="暂无审计日志" />
      <el-timeline v-else>
        <el-timeline-item
          v-for="log in auditLogs"
          :key="log.id"
          :timestamp="log.createdAt"
        >
          {{ log.adminUsername }} · {{ log.action }} · {{ log.summary }}
        </el-timeline-item>
      </el-timeline>
    </section>
  </section>
</template>
