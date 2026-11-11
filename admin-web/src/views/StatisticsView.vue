<script setup lang="ts">
import {
  ChatDotRound,
  Coin,
  Collection,
  DataLine,
  Refresh,
  Star,
  Timer,
  TrendCharts,
  VideoPlay,
} from '@element-plus/icons-vue'
import { computed, onMounted, ref, watch } from 'vue'

import {
  fetchAdminStatistics,
  type AdminStatistics,
  type StatisticsRange,
} from '../services/adminApi'

const rangeOptions: { label: string; value: StatisticsRange }[] = [
  { label: '今日', value: 'today' },
  { label: '近 7 天', value: '7d' },
  { label: '近 30 天', value: '30d' },
  { label: '累计', value: 'all' },
]

const range = ref<StatisticsRange>('7d')
const stats = ref<AdminStatistics | null>(null)
const loading = ref(false)
const error = ref('')

const rangeHint = computed(() => {
  if (range.value === 'all') {
    return '统计窗口：自上线以来的全部数据'
  }
  if (!stats.value?.since) {
    return '统计窗口：加载中'
  }
  return `统计窗口：${formatDateTime(stats.value.since)} 起（Asia/Shanghai）`
})

// 后台统一按上海时区展示（与自然日口径一致），不受浏览器时区影响
function formatDateTime(iso: string) {
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) {
    return iso
  }
  return date.toLocaleString('zh-CN', {
    timeZone: 'Asia/Shanghai',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  })
}

function formatCount(value: number | undefined) {
  return (value ?? 0).toLocaleString('zh-CN')
}

async function loadStatistics() {
  loading.value = true
  error.value = ''
  try {
    stats.value = await fetchAdminStatistics(range.value)
  } catch {
    error.value = '统计数据加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

// 切换时间范围立即重新统计，避免页面上的数字与所选窗口不一致
watch(range, loadStatistics)

onMounted(loadStatistics)
</script>

<template>
  <section v-loading="loading" class="stats-container">
    <div class="stats-header">
      <div>
        <div class="title-row">
          <h1>数据统计</h1>
          <el-tag effect="plain" type="info" size="small">{{ rangeHint }}</el-tag>
        </div>
        <p class="subtitle">
          App 内播放与互动、视频观看任务（刷积分）收益，以及全站用户当前积分总量
        </p>
      </div>
      <div class="header-actions">
        <el-radio-group v-model="range" size="default">
          <el-radio-button v-for="option in rangeOptions" :key="option.value" :value="option.value">
            {{ option.label }}
          </el-radio-button>
        </el-radio-group>
        <el-button :icon="Refresh" @click="loadStatistics">刷新</el-button>
      </div>
    </div>

    <el-alert v-if="error" :title="error" show-icon type="error" />

    <!-- 全站总积分是当前快照，不随时间范围变化 -->
    <div class="total-points-card">
      <div class="total-points-icon">
        <el-icon><Coin /></el-icon>
      </div>
      <div class="total-points-body">
        <div class="total-points-label">全站用户总积分</div>
        <div class="total-points-value mono">{{ stats?.totalPoints ?? '0' }}</div>
        <div class="total-points-hint">所有用户当前积分余额合计（含十分位），与所选时间范围无关</div>
      </div>
    </div>

    <div class="stats-section">
      <div class="section-head">
        <h2><el-icon><DataLine /></el-icon> App 内统计</h2>
        <span class="section-desc">用户自然播放与社交互动，播放量按完成上报的分集次数计（重看不重复计）</span>
      </div>
      <div class="metric-grid">
        <div class="metric-card accent-blue">
          <div class="metric-icon"><el-icon><VideoPlay /></el-icon></div>
          <div class="metric-label">用户播放量</div>
          <div class="metric-value mono">{{ formatCount(stats?.app.plays) }}</div>
          <div class="metric-unit">分集播放</div>
        </div>
        <div class="metric-card accent-rose">
          <div class="metric-icon"><el-icon><Star /></el-icon></div>
          <div class="metric-label">点赞数量</div>
          <div class="metric-value mono">{{ formatCount(stats?.app.likes) }}</div>
          <div class="metric-unit">次点赞</div>
        </div>
        <div class="metric-card accent-amber">
          <div class="metric-icon"><el-icon><Collection /></el-icon></div>
          <div class="metric-label">收藏数量</div>
          <div class="metric-value mono">{{ formatCount(stats?.app.favorites) }}</div>
          <div class="metric-unit">次收藏</div>
        </div>
        <div class="metric-card accent-emerald">
          <div class="metric-icon"><el-icon><ChatDotRound /></el-icon></div>
          <div class="metric-label">评论数量</div>
          <div class="metric-value mono">{{ formatCount(stats?.app.comments) }}</div>
          <div class="metric-unit">条评论</div>
        </div>
      </div>
    </div>

    <div class="stats-section">
      <div class="section-head">
        <h2><el-icon><TrendCharts /></el-icon> 视频观看任务（刷积分）</h2>
        <span class="section-desc">运营任务接口模拟完成的观看，与 App 自然观看分开统计</span>
      </div>
      <div class="metric-grid metric-grid-3">
        <div class="metric-card accent-violet">
          <div class="metric-icon"><el-icon><Timer /></el-icon></div>
          <div class="metric-label">任务播放量</div>
          <div class="metric-value mono">{{ formatCount(stats?.watchTask.plays) }}</div>
          <div class="metric-unit">分集任务</div>
        </div>
        <div class="metric-card accent-teal">
          <div class="metric-icon"><el-icon><Coin /></el-icon></div>
          <div class="metric-label">积分总获取数量</div>
          <div class="metric-value mono">{{ stats?.watchTask.points?.toLocaleString('zh-CN') ?? '0' }}</div>
          <div class="metric-unit">任务奖励积分</div>
        </div>
        <div class="metric-card accent-slate">
          <div class="metric-icon"><el-icon><DataLine /></el-icon></div>
          <div class="metric-label">积分平均获取数量</div>
          <div class="metric-value mono">{{ stats?.watchTask.averagePoints ?? '0.00' }}</div>
          <div class="metric-unit">平均每次任务</div>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.stats-container {
  display: flex;
  flex-direction: column;
  gap: 22px;
}

.stats-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}

.title-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.title-row h1 {
  margin: 0;
  font-size: 24px;
  font-weight: 700;
  color: #0f172a;
}

.subtitle {
  margin: 6px 0 0;
  font-size: 13px;
  color: #64748b;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.total-points-card {
  display: flex;
  align-items: center;
  gap: 18px;
  padding: 22px 26px;
  border-radius: 16px;
  color: #fff;
  background: linear-gradient(120deg, #312e81 0%, #4f46e5 55%, #6366f1 100%);
  box-shadow: 0 12px 28px rgba(79, 70, 229, 0.22);
}

.total-points-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 54px;
  height: 54px;
  border-radius: 14px;
  font-size: 26px;
  background: rgba(255, 255, 255, 0.16);
}

.total-points-label {
  font-size: 13px;
  letter-spacing: 0.04em;
  opacity: 0.85;
}

.total-points-value {
  margin-top: 4px;
  font-size: 38px;
  font-weight: 700;
  line-height: 1.1;
}

.total-points-hint {
  margin-top: 6px;
  font-size: 12px;
  opacity: 0.75;
}

.stats-section {
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 16px;
  padding: 20px 22px 22px;
}

.section-head {
  display: flex;
  align-items: baseline;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 16px;
}

.section-head h2 {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0;
  font-size: 16px;
  font-weight: 650;
  color: #0f172a;
}

.section-desc {
  font-size: 12px;
  color: #94a3b8;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 14px;
}

.metric-grid-3 {
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
}

.metric-card {
  position: relative;
  padding: 16px 18px;
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  background: #f8fafc;
}

.metric-card::before {
  content: '';
  position: absolute;
  left: 0;
  top: 14px;
  bottom: 14px;
  width: 3px;
  border-radius: 3px;
  background: currentColor;
}

.metric-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border-radius: 10px;
  font-size: 18px;
  background: #fff;
  color: inherit;
  box-shadow: inset 0 0 0 1px rgba(15, 23, 42, 0.06);
}

.metric-label {
  margin-top: 12px;
  font-size: 13px;
  color: #475569;
}

.metric-value {
  margin-top: 4px;
  font-size: 28px;
  font-weight: 700;
  line-height: 1.15;
  color: #0f172a;
  word-break: break-all;
}

.metric-unit {
  margin-top: 2px;
  font-size: 12px;
  color: #94a3b8;
}

.accent-blue { color: #2563eb; }
.accent-rose { color: #e11d48; }
.accent-amber { color: #d97706; }
.accent-emerald { color: #059669; }
.accent-violet { color: #7c3aed; }
.accent-teal { color: #0d9488; }
.accent-slate { color: #475569; }

.mono {
  font-variant-numeric: tabular-nums;
}
</style>
