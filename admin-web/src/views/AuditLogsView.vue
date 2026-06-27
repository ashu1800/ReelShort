<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { fetchAuditLogs } from '../services/adminApi'
import type { AdminAuditLog } from '../services/adminApi'

const loading = ref(false)
const error = ref('')
const logs = ref<AdminAuditLog[]>([])

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
    <div class="page-header">
      <div>
        <h1>审计日志</h1>
        <p>查看后台关键操作记录</p>
      </div>
      <el-button @click="loadLogs">刷新</el-button>
    </div>
    <el-alert v-if="error" :title="error" show-icon type="error" />
    <el-table v-loading="loading" :data="logs" border>
      <el-table-column label="管理员" prop="adminUsername" width="140" />
      <el-table-column label="动作" prop="action" width="210" />
      <el-table-column label="目标类型" prop="targetType" width="150" />
      <el-table-column label="摘要" min-width="260" prop="summary" />
      <el-table-column label="时间" min-width="220" prop="createdAt" />
    </el-table>
  </section>
</template>
