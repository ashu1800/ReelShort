<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { fetchAuditLogs, fetchContentCacheStatus, fetchUsers } from '../services/adminApi'
import type { AdminAuditLog, AdminUserSummary, ContentCacheStatus } from '../services/adminApi'

const loading = ref(false)
const error = ref('')
const users = ref<AdminUserSummary[]>([])
const cacheStatus = ref<ContentCacheStatus | null>(null)
const auditLogs = ref<AdminAuditLog[]>([])

const disabledUsers = computed(() => users.value.filter((user) => user.status === 'DISABLED').length)

const metrics = computed(() => [
  { label: '用户总数', value: users.value.length },
  { label: '禁用用户', value: disabledUsers.value },
  { label: '剧集缓存', value: cacheStatus.value?.bookCount ?? 0 },
  { label: '分集缓存', value: cacheStatus.value?.episodeCacheCount ?? 0 },
])

async function loadDashboard() {
  loading.value = true
  error.value = ''
  try {
    const [userData, cacheData, auditData] = await Promise.all([
      fetchUsers(),
      fetchContentCacheStatus(),
      fetchAuditLogs(),
    ])
    users.value = userData
    cacheStatus.value = cacheData
    auditLogs.value = auditData.slice(0, 5)
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
