<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { fetchContentCacheStatus } from '../services/adminApi'
import type { ContentCacheStatus } from '../services/adminApi'

const loading = ref(false)
const error = ref('')
const status = ref<ContentCacheStatus | null>(null)

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

onMounted(loadStatus)
</script>

<template>
  <section v-loading="loading" class="page-section">
    <div class="page-header">
      <div>
        <h1>内容缓存</h1>
        <p>查看剧集索引、分集缓存和货架刷新状态</p>
      </div>
      <el-button @click="loadStatus">刷新</el-button>
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
    </div>
    <el-table :data="status?.shelves ?? []" border>
      <el-table-column label="货架" prop="shelfType" width="160" />
      <el-table-column align="right" label="条目数" prop="itemCount" width="120" />
      <el-table-column label="最近刷新" min-width="220" prop="refreshedAt" />
      <el-table-column label="最近错误" min-width="260" prop="lastError" />
    </el-table>
  </section>
</template>
