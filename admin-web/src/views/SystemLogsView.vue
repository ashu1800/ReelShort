<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  Notebook,
  Refresh,
  Search,
  CopyDocument,
  Bottom,
} from '@element-plus/icons-vue'
import { fetchSystemLogs } from '../services/adminApi'
import type { SystemLogResponse } from '../services/adminApi'

const loading = ref(false)
const error = ref('')
const selectedFile = ref('')
const requestedLines = ref(200)
const logs = ref<SystemLogResponse | null>(null)
const logSearch = ref('')
const terminalRef = ref<HTMLElement | null>(null)
const isWrap = ref(false)

// 过滤后的日志行
const displayedLines = computed(() => {
  const lines = logs.value?.lines || []
  const q = logSearch.value.trim().toLowerCase()
  if (!q) return lines
  return lines.filter((line) => line.toLowerCase().includes(q))
})

const logText = computed(() => displayedLines.value.join('\n'))

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

function setPresetLines(lines: number) {
  requestedLines.value = lines
  loadLogs()
}

async function copyAllLogs() {
  if (!logText.value) return
  try {
    await navigator.clipboard.writeText(logText.value)
    ElMessage.success('全部日志内容已复制到剪贴板')
  } catch {
    ElMessage.warning('复制失败，请手动选中文本复制')
  }
}

function scrollToBottom() {
  nextTick(() => {
    if (terminalRef.value) {
      terminalRef.value.scrollTop = terminalRef.value.scrollHeight
    }
  })
}

function getLineClass(line: string) {
  if (line.includes('ERROR') || line.includes('Exception') || line.includes('Failed')) {
    return 'line-error'
  }
  if (line.includes('WARN')) {
    return 'line-warn'
  }
  if (line.includes('INFO')) {
    return 'line-info'
  }
  return 'line-default'
}

onMounted(() => loadLogs())
</script>

<template>
  <section v-loading="loading" class="page-section">
    <!-- 顶部标题栏 -->
    <div class="page-header">
      <div>
        <div class="title-row">
          <h1>系统日志</h1>
          <el-tag effect="plain" type="primary" size="small" class="count-pill">
            <el-icon><Notebook /></el-icon>
            {{ logs?.files.length || 0 }} 个日志源
          </el-tag>
          <el-tag v-if="logs?.truncated" effect="plain" type="warning" size="small">
            截取末尾 {{ logs.lineCount }} 行
          </el-tag>
        </div>
        <p class="subtitle">查看 Spring Boot 应用运行实时日志、错误栈堆及组件调度输出</p>
      </div>
      <div class="header-actions">
        <el-button type="primary" :icon="Refresh" @click="loadLogs()">刷新日志</el-button>
      </div>
    </div>

    <el-alert v-if="error" :title="error" show-icon type="error" />

    <!-- 极客终端卡片 -->
    <el-card shadow="never" class="terminal-card">
      <!-- 终端控制台工具栏 -->
      <div class="terminal-toolbar">
        <div class="left-controls">
          <el-select
            v-model="selectedFile"
            :disabled="!logs?.files.length"
            placeholder="选择日志文件"
            class="file-select"
            @change="handleFileChange"
          >
            <el-option
              v-for="file in logs?.files || []"
              :key="file"
              :label="file"
              :value="file"
            />
          </el-select>

          <!-- 快捷行数胶囊 -->
          <div class="preset-lines-chips">
            <button
              type="button"
              class="line-chip"
              :class="{ active: requestedLines === 100 }"
              @click="setPresetLines(100)"
            >
              100行
            </button>
            <button
              type="button"
              class="line-chip"
              :class="{ active: requestedLines === 200 }"
              @click="setPresetLines(200)"
            >
              200行
            </button>
            <button
              type="button"
              class="line-chip"
              :class="{ active: requestedLines === 500 }"
              @click="setPresetLines(500)"
            >
              500行
            </button>
          </div>

          <el-input-number
            v-model="requestedLines"
            :max="500"
            :min="1"
            :step="50"
            size="small"
            class="lines-input"
            @change="() => loadLogs()"
          />
        </div>

        <div class="right-controls">
          <el-input
            v-model="logSearch"
            placeholder="在当前日志中过滤关键词..."
            :prefix-icon="Search"
            clearable
            size="small"
            class="search-log-input"
          />

          <el-button size="small" :icon="CopyDocument" @click="copyAllLogs">
            复制全部
          </el-button>
          <el-button size="small" :icon="Bottom" @click="scrollToBottom">
            滚至底部
          </el-button>
        </div>
      </div>

      <!-- 终端主显示视窗 -->
      <div class="terminal-window">
        <!-- 终端顶边装饰条 (macOS 风格三色圆点) -->
        <div class="terminal-topbar">
          <div class="window-dots">
            <span class="dot red"></span>
            <span class="dot yellow"></span>
            <span class="dot green"></span>
          </div>
          <div class="window-title mono">
            {{ logs?.selectedFile || 'app.log' }} · {{ logs?.updatedAt || '实时探针' }}
          </div>
          <div class="window-meta mono">
            共 {{ displayedLines.length }} 行
          </div>
        </div>

        <!-- 终端内容区 -->
        <div
          ref="terminalRef"
          class="terminal-content mono"
          :class="{ 'wrap-text': isWrap }"
        >
          <el-empty
            v-if="logs && logs.files.length === 0"
            description="暂无可读取的日志文件"
            class="terminal-empty"
          />
          <el-empty
            v-else-if="displayedLines.length === 0"
            description="无匹配当前搜索关键词的日志行"
            class="terminal-empty"
          />
          <div
            v-for="(line, idx) in displayedLines"
            :key="idx"
            class="terminal-row"
            :class="getLineClass(line)"
          >
            <span class="row-num mono">{{ idx + 1 }}</span>
            <span class="row-text mono">{{ line }}</span>
          </div>
        </div>
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

/* 终端卡片 */
.terminal-card {
  border-radius: 10px;
  border: 1px solid #e2e8f0;
}

.terminal-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
  gap: 12px;
  flex-wrap: wrap;
}

.left-controls {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.file-select {
  width: 220px;
}

.preset-lines-chips {
  display: flex;
  align-items: center;
  gap: 6px;
}

.line-chip {
  border: 1px solid #e2e8f0;
  background: #f8fafc;
  color: #475569;
  font-size: 12px;
  padding: 3px 8px;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.15s ease;
}

.line-chip:hover {
  background: #ffffff;
  border-color: #cbd5e1;
}

.line-chip.active {
  background: #eff6ff;
  border-color: #3b82f6;
  color: #1d4ed8;
  font-weight: 600;
}

.lines-input {
  width: 120px;
}

.right-controls {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.search-log-input {
  width: 240px;
}

/* 终端视窗 */
.terminal-window {
  background: #0b0f19;
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid #1e293b;
  box-shadow: 0 10px 25px -5px rgba(0, 0, 0, 0.4);
}

.terminal-topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 14px;
  background: #111827;
  border-bottom: 1px solid #1f2937;
}

.window-dots {
  display: flex;
  align-items: center;
  gap: 6px;
}

.dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
}
.dot.red { background: #ef4444; }
.dot.yellow { background: #f59e0b; }
.dot.green { background: #10b981; }

.window-title {
  font-size: 12px;
  color: #94a3b8;
}

.window-meta {
  font-size: 11px;
  color: #64748b;
}

/* 终端滚动体 */
.terminal-content {
  padding: 12px 14px;
  height: 600px;
  overflow-y: auto;
  font-size: 12px;
  line-height: 1.6;
  color: #e2e8f0;
  display: flex;
  flex-direction: column;
}

.terminal-row {
  display: flex;
  gap: 12px;
  padding: 1px 0;
  white-space: pre;
}

.wrap-text .terminal-row {
  white-space: pre-wrap;
  word-break: break-all;
}

.row-num {
  color: #475569;
  width: 36px;
  text-align: right;
  flex-shrink: 0;
  user-select: none;
}

.row-text {
  flex: 1;
}

.line-error {
  color: #f87171;
  background: rgba(239, 68, 68, 0.08);
}
.line-warn {
  color: #fbbf24;
}
.line-info {
  color: #38bdf8;
}
.line-default {
  color: #cbd5e1;
}

.terminal-empty {
  margin: auto;
}
</style>
