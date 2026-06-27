<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { fetchSystemLogs } from '../services/adminApi'
import type { SystemLogResponse } from '../services/adminApi'

const loading = ref(false)
const error = ref('')
const selectedFile = ref('')
const requestedLines = ref(200)
const logs = ref<SystemLogResponse | null>(null)

const logText = computed(() => logs.value?.lines.join('\n') || '')

async function loadLogs(file = selectedFile.value) {
  loading.value = true
  error.value = ''
  try {
    const response = await fetchSystemLogs(file || undefined, requestedLines.value)
    logs.value = response
    selectedFile.value = response.selectedFile || ''
  } catch {
    error.value = '系统日志加载失败'
  } finally {
    loading.value = false
  }
}

function handleFileChange(file: string) {
  selectedFile.value = file
  loadLogs(file)
}

onMounted(() => loadLogs())
</script>

<template>
  <section v-loading="loading" class="page-section">
    <div class="page-header">
      <div>
        <h1>系统日志</h1>
        <p>查看后端应用日志的最近内容</p>
      </div>
      <el-button @click="loadLogs()">刷新</el-button>
    </div>

    <el-alert v-if="error" :title="error" show-icon type="error" />

    <div class="panel log-toolbar">
      <el-select
        v-model="selectedFile"
        :disabled="!logs?.files.length"
        placeholder="选择日志文件"
        @change="handleFileChange"
      >
        <el-option
          v-for="file in logs?.files || []"
          :key="file"
          :label="file"
          :value="file"
        />
      </el-select>
      <el-input-number v-model="requestedLines" :max="500" :min="1" :step="50" />
      <el-button @click="loadLogs()">读取</el-button>
      <el-tag v-if="logs?.truncated" type="warning" effect="plain">已截取最近 {{ logs.lineCount }} 行</el-tag>
      <el-tag v-else type="info" effect="plain">{{ logs?.lineCount || 0 }} 行</el-tag>
    </div>

    <el-empty v-if="logs && logs.files.length === 0" description="暂无可读取的日志文件" />

    <div v-else class="panel log-panel">
      <div class="log-meta">
        <span>{{ logs?.selectedFile || '-' }}</span>
        <span>{{ logs?.updatedAt || '-' }}</span>
      </div>
      <pre class="log-content">{{ logText }}</pre>
    </div>
  </section>
</template>

<style scoped>
.log-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
}

.log-toolbar :deep(.el-select) {
  width: 220px;
}

.log-panel {
  padding: 0;
  overflow: hidden;
}

.log-meta {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding: 12px 14px;
  border-bottom: 1px solid #e5e7eb;
  color: #64748b;
  font-size: 13px;
}

.log-content {
  min-height: 420px;
  max-height: 620px;
  margin: 0;
  padding: 16px;
  overflow: auto;
  background: #0f172a;
  color: #e2e8f0;
  font-family: ui-monospace, SFMono-Regular, Consolas, 'Liberation Mono', monospace;
  font-size: 12px;
  line-height: 1.7;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
</style>
