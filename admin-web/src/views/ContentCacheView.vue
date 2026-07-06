<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { onMounted, ref } from 'vue'
import { fetchContentCacheStatus, refreshContentShelf, refreshContentShelfLocales } from '../services/adminApi'
import type { ContentCacheStatus } from '../services/adminApi'

const loading = ref(false)
const refreshing = ref(false)
const refreshingLocales = ref(false)
const error = ref('')
const status = ref<ContentCacheStatus | null>(null)
const shelfType = ref('recommend')
const locale = ref('en')
const shelfOptions = [
  { label: '推荐', value: 'recommend' },
  { label: '新剧', value: 'new-release' },
  { label: '配音', value: 'drama-dub' },
]
const localeOptions = [
  { label: 'English', value: 'en' },
  { label: '繁體中文', value: 'zh-TW' },
]

async function loadStatus() {
  loading.value = true
  error.value = ''
  try {
    status.value = await fetchContentCacheStatus()
  } catch {
    error.value = '内容缓存状态加载失败'
  } finally {
    loading.value = false
  }
}

async function refreshShelf() {
  refreshing.value = true
  try {
    await refreshContentShelf(shelfType.value, locale.value)
    ElMessage.success('内容货架刷新完成')
  } catch {
    ElMessage.error('内容货架刷新失败')
    refreshing.value = false
    return
  }
  try {
    await loadStatus()
  } finally {
    refreshing.value = false
  }
}

async function refreshShelfLocales() {
  refreshingLocales.value = true
  try {
    const results = await refreshContentShelfLocales(shelfType.value)
    const successCount = results.filter((result) => result.status === 'SUCCESS').length
    const failedCount = results.length - successCount
    if (failedCount > 0) {
      ElMessage.warning(`双语刷新完成：成功 ${successCount} / 失败 ${failedCount}`)
    } else {
      ElMessage.success(`双语刷新完成：成功 ${successCount}`)
    }
  } catch {
    ElMessage.error('双语货架刷新失败')
    refreshingLocales.value = false
    return
  }
  try {
    await loadStatus()
  } finally {
    refreshingLocales.value = false
  }
}

function shelfHealthType(health: string) {
  if (health === 'HEALTHY') {
    return 'success'
  }
  if (health === 'ERROR') {
    return 'danger'
  }
  return 'warning'
}

onMounted(loadStatus)
</script>

<template>
  <section v-loading="loading" class="page-section">
    <div class="page-header">
      <div>
        <h1>内容缓存</h1>
        <p>查看剧集索引、分集缓存和货架刷新状态</p>
      </div>
      <div class="toolbar-actions">
        <el-select v-model="shelfType" class="shelf-select" placeholder="选择货架">
          <el-option
            v-for="option in shelfOptions"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
        <el-select v-model="locale" class="locale-select" placeholder="选择语言">
          <el-option
            v-for="option in localeOptions"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
        <el-button :loading="refreshing" type="primary" @click="refreshShelf">刷新货架</el-button>
        <el-button :loading="refreshingLocales" @click="refreshShelfLocales">刷新双语货架</el-button>
        <el-button @click="loadStatus">刷新状态</el-button>
      </div>
    </div>
    <el-alert v-if="error" :title="error" show-icon type="error" />
    <div class="metric-grid">
      <div class="metric">
        <div class="metric-label">剧集索引</div>
        <div class="metric-value">{{ status?.bookCount ?? 0 }}</div>
      </div>
      <div class="metric">
        <div class="metric-label">分集缓存</div>
        <div class="metric-value">{{ status?.episodeCacheCount ?? 0 }}</div>
      </div>
      <div class="metric">
        <div class="metric-label">播放地址缓存</div>
        <div class="metric-value">{{ status?.videoCacheCount ?? 0 }}</div>
      </div>
    </div>
    <el-table :data="status?.shelves ?? []" border>
      <el-table-column label="货架" prop="shelfType" width="160" />
      <el-table-column label="语言" prop="locale" width="120" />
      <el-table-column label="健康状态" width="140">
        <template #default="{ row }">
          <el-tag :type="shelfHealthType(row.health)">{{ row.health }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column align="right" label="条目数" prop="itemCount" width="120" />
      <el-table-column label="最近刷新" min-width="220" prop="refreshedAt" />
      <el-table-column label="健康说明" min-width="240" prop="healthMessage" />
      <el-table-column label="最近错误" min-width="260" prop="lastError" />
    </el-table>
    <h2 class="section-title">最近刷新任务</h2>
    <el-table :data="status?.recentRefreshRuns ?? []" border>
      <el-table-column label="来源" prop="triggerSource" width="120" />
      <el-table-column label="货架" prop="shelfType" width="140" />
      <el-table-column label="语言" prop="locale" width="120" />
      <el-table-column label="状态" width="120">
        <template #default="{ row }">
          <el-tag :type="row.status === 'SUCCESS' ? 'success' : 'danger'">{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column align="right" label="条目数" prop="itemCount" width="100" />
      <el-table-column align="right" label="耗时(ms)" prop="durationMillis" width="120" />
      <el-table-column label="开始时间" min-width="220" prop="startedAt" />
      <el-table-column label="错误" min-width="260" prop="errorMessage" />
    </el-table>
  </section>
</template>
