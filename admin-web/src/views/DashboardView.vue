<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  User,
  GoldMedal,
  Film,
  Monitor,
  Refresh,
  ArrowRight,
  CircleCheck,
  Warning,
  Setting,
  Coin,
  Notebook,
} from '@element-plus/icons-vue'
import {
  fetchDashboardSummary,
  fetchSystemRuntime,
} from '../services/adminApi'
import type {
  AdminAuditLog,
  AdminDashboardSummary,
  SystemRuntimeResponse,
} from '../services/adminApi'

const router = useRouter()
const loading = ref(false)
const error = ref('')
const summary = ref<AdminDashboardSummary | null>(null)
const runtime = ref<SystemRuntimeResponse | null>(null)
const auditLogs = ref<AdminAuditLog[]>([])
const lastRefreshedAt = ref('')

const memoryPercent = computed(() => {
  const mem = runtime.value?.memory
  if (!mem || mem.maxBytes <= 0) return 0
  return Math.min(100, Math.round((mem.usedBytes / mem.maxBytes) * 100))
})

function formatBytes(bytes?: number) {
  if (!bytes || bytes <= 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  let val = bytes
  let idx = 0
  while (val >= 1024 && idx < units.length - 1) {
    val /= 1024
    idx++
  }
  return `${val.toFixed(1)} ${units[idx]}`
}

function formatUptime(seconds?: number) {
  if (!seconds || seconds <= 0) return '刚启动'
  const d = Math.floor(seconds / 86400)
  const h = Math.floor((seconds % 86400) / 3600)
  const m = Math.floor((seconds % 3600) / 60)
  if (d > 0) return `${d}天 ${h}小时`
  if (h > 0) return `${h}小时 ${m}分`
  return `${m}分钟`
}

function formatTimestamp(isoStr: string) {
  if (!isoStr) return '-'
  try {
    const d = new Date(isoStr)
    const y = d.getFullYear()
    const m = String(d.getMonth() + 1).padStart(2, '0')
    const day = String(d.getDate()).padStart(2, '0')
    const h = String(d.getHours()).padStart(2, '0')
    const min = String(d.getMinutes()).padStart(2, '0')
    const s = String(d.getSeconds()).padStart(2, '0')
    return `${y}-${m}-${day} ${h}:${min}:${s}`
  } catch {
    return isoStr
  }
}

function getActionTagType(action: string): 'primary' | 'success' | 'warning' | 'danger' | 'info' {
  if (action.includes('REFRESH')) return 'primary'
  if (action.includes('VIP') || action.includes('POINT')) return 'warning'
  if (action.includes('DELETE') || action.includes('DISABLE') || action.includes('CANCEL')) return 'danger'
  if (action.includes('CONFIRM') || action.includes('SET') || action.includes('SUCCESS')) return 'success'
  return 'info'
}

function getActionLabel(action: string) {
  const map: Record<string, string> = {
    POINTS_ADJUSTED: '积分调整',
    USER_VIP_SET: 'VIP 授权',
    USER_VIP_CANCELLED: 'VIP 取消',
    CONTENT_CACHE_REFRESHED: '内容抓取',
    CONTENT_CACHE_REFRESHED_LOCALES: '全语言刷新',
    USER_STATUS_UPDATED: '状态变更',
    WITHDRAWAL_CONFIRMED: '提现确认',
    WITHDRAWAL_REJECTED: '提现拒绝',
  }
  return map[action] || action
}

async function loadDashboard() {
  loading.value = true
  error.value = ''
  try {
    const [summaryData, runtimeData] = await Promise.all([
      fetchDashboardSummary(),
      fetchSystemRuntime().catch(() => null),
    ])
    summary.value = summaryData
    runtime.value = runtimeData
    auditLogs.value = summaryData.auditLogs.latest
    const now = new Date()
    lastRefreshedAt.value = `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}:${String(now.getSeconds()).padStart(2, '0')}`
  } catch {
    error.value = '控制台数据加载失败'
  } finally {
    loading.value = false
  }
}

function navigateTo(path: string) {
  router.push(path)
}

onMounted(loadDashboard)
</script>

<template>
  <section v-loading="loading" class="dashboard-container">
    <!-- 顶部标题与快速刷新 -->
    <div class="dashboard-header">
      <div>
        <div class="title-row">
          <h1>控制台</h1>
          <el-tag effect="plain" type="success" size="small" class="status-pill">
            <span class="pulse-dot"></span> 系统在线
          </el-tag>
        </div>
        <p class="subtitle">
          运营业务大盘、流媒体短剧资产、节点基础设施与审计日志实时透视
        </p>
      </div>
      <div class="header-actions">
        <span v-if="lastRefreshedAt" class="refreshed-time">
          最后更新: {{ lastRefreshedAt }}
        </span>
        <el-button type="primary" :icon="Refresh" @click="loadDashboard">
          刷新大盘
        </el-button>
      </div>
    </div>

    <el-alert v-if="error" :title="error" show-icon type="error" />

    <!-- 第一分区：对称型业务 KPI Hero 矩阵 (4 组核心业务领域) -->
    <div class="kpi-grid">
      <!-- 1. 用户大盘 -->
      <div class="kpi-card kpi-user" @click="navigateTo('/users')">
        <div class="kpi-watermark">
          <el-icon><User /></el-icon>
        </div>
        <div class="kpi-header">
          <div class="kpi-category">用户运营大盘</div>
          <el-icon class="kpi-arrow"><ArrowRight /></el-icon>
        </div>
        <div class="kpi-main">
          <div class="kpi-number mono">{{ summary?.users.total ?? 0 }}</div>
          <div class="kpi-unit">注册用户数</div>
        </div>
        <div class="kpi-footer">
          <div class="kpi-sub-item">
            <span class="sub-label">正常活跃:</span>
            <span class="sub-val mono">{{ (summary?.users.total ?? 0) - (summary?.users.disabled ?? 0) }}</span>
          </div>
          <div class="kpi-divider"></div>
          <div class="kpi-sub-item">
            <span class="sub-label">禁用风控:</span>
            <span class="sub-val sub-danger mono">{{ summary?.users.disabled ?? 0 }}</span>
          </div>
        </div>
      </div>

      <!-- 2. 商业化充值 -->
      <div class="kpi-card kpi-vip" @click="navigateTo('/vip-orders')">
        <div class="kpi-watermark">
          <el-icon><GoldMedal /></el-icon>
        </div>
        <div class="kpi-header">
          <div class="kpi-category">会员与商业化营收</div>
          <el-icon class="kpi-arrow"><ArrowRight /></el-icon>
        </div>
        <div class="kpi-main">
          <div class="kpi-number mono">{{ summary?.vipOrders.totalUsdt ?? '0' }}</div>
          <div class="kpi-unit">累计充值 (USDT)</div>
        </div>
        <div class="kpi-footer">
          <div class="kpi-sub-item">
            <span class="sub-label">付费订单数:</span>
            <span class="sub-val mono">{{ summary?.vipOrders.total ?? 0 }} 笔</span>
          </div>
          <div class="kpi-divider"></div>
          <div class="kpi-sub-item">
            <span class="sub-label">支付渠道:</span>
            <span class="sub-val mono">TRC-20 USDT</span>
          </div>
        </div>
      </div>

      <!-- 3. 积分获取效率 -->
      <div class="kpi-card kpi-points" @click="navigateTo('/users')">
        <div class="kpi-watermark">
          <el-icon><Coin /></el-icon>
        </div>
        <div class="kpi-header">
          <div class="kpi-category">积分获取效率 · 近 {{ summary?.points?.periodDays ?? 30 }} 天</div>
          <el-icon class="kpi-arrow"><ArrowRight /></el-icon>
        </div>
        <div class="kpi-main">
          <div class="kpi-number mono">{{ summary?.points?.averagePointsPerUserPerDay ?? '0' }}</div>
          <div class="kpi-unit">人均每日观看奖励积分（全部注册用户）</div>
        </div>
        <div class="kpi-footer">
          <div class="kpi-sub-item">
            <span class="sub-label">人均每小时:</span>
            <span class="sub-val mono">{{ summary?.points?.averagePointsPerUserPerHour ?? '0' }}</span>
          </div>
          <div class="kpi-divider"></div>
          <div class="kpi-sub-item">
            <span class="sub-label">30天奖励:</span>
            <span class="sub-val mono">{{ (summary?.points?.totalWatchRewardPoints ?? 0).toLocaleString() }}</span>
          </div>
        </div>
      </div>

      <!-- 4. 流媒体内容资产 -->
      <div class="kpi-card kpi-content" @click="navigateTo('/content-cache')">
        <div class="kpi-watermark">
          <el-icon><Film /></el-icon>
        </div>
        <div class="kpi-header">
          <div class="kpi-category">短剧与分集资产</div>
          <el-icon class="kpi-arrow"><ArrowRight /></el-icon>
        </div>
        <div class="kpi-main">
          <div class="kpi-number mono">{{ summary?.content.bookCount ?? 0 }}</div>
          <div class="kpi-unit">在架短剧总目</div>
        </div>
        <div class="kpi-footer">
          <div class="kpi-sub-item">
            <span class="sub-label">分集索引:</span>
            <span class="sub-val mono">{{ summary?.content.episodeCacheCount ?? 0 }} 集</span>
          </div>
          <div class="kpi-divider"></div>
          <div class="kpi-sub-item">
            <span class="sub-label">抓取进度:</span>
            <span class="sub-val mono">
              {{ summary?.content.crawlPage ?? 0 }} / {{ summary?.content.totalPages ?? 0 }} 页
            </span>
          </div>
        </div>
      </div>
    </div>

    <!-- 第二分区：系统底座雷达与快速通道 -->
    <div class="middle-section">
      <!-- 左栏：系统底座与依赖健康 -->
      <el-card shadow="never" class="panel-box system-radar-box">
        <template #header>
          <div class="panel-header">
            <div class="panel-header-title">
              <el-icon><Monitor /></el-icon>
              <span>系统运行健康度</span>
            </div>
            <span class="uptime-badge" v-if="runtime?.application.uptimeSeconds">
              持续运行: {{ formatUptime(runtime.application.uptimeSeconds) }}
            </span>
          </div>
        </template>

        <!-- JVM 内存 -->
        <div class="radar-metric-block">
          <div class="radar-metric-header">
            <span class="metric-title">JVM 内存占用比</span>
            <span class="metric-detail mono">
              {{ formatBytes(runtime?.memory.usedBytes) }} / {{ formatBytes(runtime?.memory.maxBytes) }} ({{ memoryPercent }}%)
            </span>
          </div>
          <el-progress
            :percentage="memoryPercent"
            :stroke-width="10"
            :color="memoryPercent > 85 ? '#f56c6c' : (memoryPercent > 65 ? '#e6a23c' : '#409eff')"
            :show-text="false"
          />
        </div>

        <!-- 依赖服务连通性 -->
        <div class="dependencies-container">
          <div class="dep-title">核心底层依赖状态</div>
          <div class="dep-grid">
            <div
              v-for="dep in (runtime?.dependencies ?? [
                { name: 'PostgreSQL', status: 'UP', detail: '127.0.0.1:5432' },
                { name: 'Redis', status: 'UP', detail: '127.0.0.1:6379' },
                { name: 'ContentProvider', status: 'UP', detail: '127.0.0.1:5000' }
              ])"
              :key="dep.name"
              class="dep-card"
            >
              <div class="dep-info">
                <span class="dep-name">{{ dep.name }}</span>
                <span class="dep-detail mono">{{ dep.detail }}</span>
              </div>
              <el-tag
                size="small"
                :type="dep.status === 'UP' ? 'success' : 'danger'"
                effect="plain"
                class="dep-tag"
              >
                <el-icon><CircleCheck v-if="dep.status === 'UP'" /><Warning v-else /></el-icon>
                {{ dep.status }}
              </el-tag>
            </div>
          </div>
        </div>
      </el-card>

      <!-- 右栏：核心运营快捷矩阵 -->
      <el-card shadow="never" class="panel-box quick-links-box">
        <template #header>
          <div class="panel-header">
            <div class="panel-header-title">
              <el-icon><Setting /></el-icon>
              <span>运营快捷工作台</span>
            </div>
            <span class="panel-hint">一键直达核心业务</span>
          </div>
        </template>

        <div class="quick-grid">
          <div class="quick-item" @click="navigateTo('/content-cache')">
            <div class="quick-icon-wrap icon-film"><el-icon><Film /></el-icon></div>
            <div class="quick-text">
              <div class="quick-title">视频库管理</div>
              <div class="quick-desc">海报资产 · 分集探针 · 全季巡检</div>
            </div>
            <el-icon class="quick-arrow"><ArrowRight /></el-icon>
          </div>

          <div class="quick-item" @click="navigateTo('/users')">
            <div class="quick-icon-wrap icon-user"><el-icon><User /></el-icon></div>
            <div class="quick-text">
              <div class="quick-title">用户与风控治理</div>
              <div class="quick-desc">封禁管控 · 积分调节 · VIP 授权</div>
            </div>
            <el-icon class="quick-arrow"><ArrowRight /></el-icon>
          </div>

          <div class="quick-item" @click="navigateTo('/withdrawals')">
            <div class="quick-icon-wrap icon-coin"><el-icon><Coin /></el-icon></div>
            <div class="quick-text">
              <div class="quick-title">提现审批打款</div>
              <div class="quick-desc">ERC-20 积分兑付 · 地址单笔确认</div>
            </div>
            <el-icon class="quick-arrow"><ArrowRight /></el-icon>
          </div>

          <div class="quick-item" @click="navigateTo('/vip-orders')">
            <div class="quick-icon-wrap icon-vip"><el-icon><GoldMedal /></el-icon></div>
            <div class="quick-text">
              <div class="quick-title">VIP 订单对账</div>
              <div class="quick-desc">TRC-20 链上流水 · 自动 / 手工核验</div>
            </div>
            <el-icon class="quick-arrow"><ArrowRight /></el-icon>
          </div>

          <div class="quick-item" @click="navigateTo('/system-configs')">
            <div class="quick-icon-wrap icon-config"><el-icon><Setting /></el-icon></div>
            <div class="quick-text">
              <div class="quick-title">平台资金参数</div>
              <div class="quick-desc">收款地址 · 积分比例 · 提现手续费</div>
            </div>
            <el-icon class="quick-arrow"><ArrowRight /></el-icon>
          </div>

          <div class="quick-item" @click="navigateTo('/system-runtime')">
            <div class="quick-icon-wrap icon-runtime"><el-icon><Notebook /></el-icon></div>
            <div class="quick-text">
              <div class="quick-title">系统日志与诊断</div>
              <div class="quick-desc">上游异常抓取 · 结构化诊断事件</div>
            </div>
            <el-icon class="quick-arrow"><ArrowRight /></el-icon>
          </div>
        </div>
      </el-card>
    </div>

    <!-- 第三分区：结构化审计追踪流 -->
    <el-card shadow="never" class="panel-box audit-feed-box">
      <template #header>
        <div class="panel-header">
          <div class="panel-header-title">
            <el-icon><Notebook /></el-icon>
            <span>最近操作审计日志</span>
          </div>
          <el-button link type="primary" :icon="ArrowRight" @click="navigateTo('/audit-logs')">
            查看全部审计日志
          </el-button>
        </div>
      </template>

      <el-empty v-if="!auditLogs.length && !loading" description="暂无操作审计日志记录" />
      <div v-else class="audit-list">
        <div v-for="log in auditLogs" :key="log.id" class="audit-row">
          <div class="audit-time mono">{{ formatTimestamp(log.createdAt) }}</div>
          <div class="audit-actor">
            <el-tag size="small" effect="plain" type="info">{{ log.adminUsername }}</el-tag>
          </div>
          <div class="audit-action">
            <el-tag size="small" :type="getActionTagType(log.action)" effect="light">
              {{ getActionLabel(log.action) }}
            </el-tag>
          </div>
          <div class="audit-summary mono" :title="log.summary">
            {{ log.summary }}
          </div>
        </div>
      </div>
    </el-card>
  </section>
</template>

<style scoped>
.dashboard-container {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.dashboard-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
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

.header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.refreshed-time {
  font-size: 12px;
  color: #94a3b8;
  font-family: 'SFMono-Regular', Consolas, monospace;
}

.status-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-weight: 500;
  border-radius: 9999px;
  padding: 0 10px;
}

.pulse-dot {
  width: 6px;
  height: 6px;
  background-color: #67c23a;
  border-radius: 50%;
  box-shadow: 0 0 0 0 rgba(103, 194, 58, 0.7);
  animation: pulse 2s infinite;
}

@keyframes pulse {
  0% {
    transform: scale(0.95);
    box-shadow: 0 0 0 0 rgba(103, 194, 58, 0.7);
  }
  70% {
    transform: scale(1);
    box-shadow: 0 0 0 6px rgba(103, 194, 58, 0);
  }
  100% {
    transform: scale(0.95);
    box-shadow: 0 0 0 0 rgba(103, 194, 58, 0);
  }
}

/* KPI 卡片网格 (4列自适应) */
.kpi-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 18px;
}

@media (max-width: 1280px) {
  .kpi-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 720px) {
  .kpi-grid {
    grid-template-columns: 1fr;
  }
}

.kpi-card {
  position: relative;
  background: #ffffff;
  border-radius: 12px;
  border: 1px solid #e2e8f0;
  padding: 20px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  cursor: pointer;
  overflow: hidden;
  transition: all 0.25s ease-in-out;
}

.kpi-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 12px 24px -6px rgba(0, 0, 0, 0.08);
}

.kpi-card:hover .kpi-arrow {
  transform: translateX(4px);
  color: #0f172a;
}

.kpi-watermark {
  position: absolute;
  right: -10px;
  bottom: -15px;
  font-size: 110px;
  opacity: 0.04;
  pointer-events: none;
  transition: transform 0.3s ease;
}

.kpi-card:hover .kpi-watermark {
  transform: scale(1.08);
}

.kpi-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.kpi-category {
  font-size: 13px;
  font-weight: 600;
  color: #64748b;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.kpi-arrow {
  color: #94a3b8;
  font-size: 16px;
  transition: transform 0.2s ease;
}

.kpi-main {
  margin: 14px 0 16px;
}

.kpi-number {
  font-size: 34px;
  font-weight: 800;
  color: #0f172a;
  line-height: 1.1;
}

.kpi-unit {
  font-size: 12px;
  color: #94a3b8;
  margin-top: 4px;
}

.kpi-footer {
  display: flex;
  align-items: center;
  gap: 12px;
  border-top: 1px dashed #e2e8f0;
  padding-top: 12px;
  font-size: 13px;
}

.kpi-sub-item {
  display: flex;
  align-items: center;
  gap: 6px;
}

.sub-label {
  color: #64748b;
}

.sub-val {
  color: #0f172a;
  font-weight: 600;
}

.sub-danger {
  color: #ef4444;
}

.kpi-divider {
  width: 1px;
  height: 14px;
  background-color: #cbd5e1;
}

/* KPI 主题色 */
.kpi-user {
  border-top: 4px solid #3b82f6;
  background: linear-gradient(180deg, rgba(239, 246, 255, 0.4) 0%, #ffffff 100%);
}

.kpi-vip {
  border-top: 4px solid #f59e0b;
  background: linear-gradient(180deg, rgba(254, 243, 199, 0.4) 0%, #ffffff 100%);
}

.kpi-points {
  border-top: 4px solid #10b981;
  background: linear-gradient(180deg, rgba(236, 253, 245, 0.5) 0%, #ffffff 100%);
}

.kpi-content {
  border-top: 4px solid #8b5cf6;
  background: linear-gradient(180deg, rgba(245, 243, 255, 0.4) 0%, #ffffff 100%);
}

/* 中段双栏 */
.middle-section {
  display: grid;
  grid-template-columns: 1fr 1.3fr;
  gap: 18px;
}

@media (max-width: 1200px) {
  .middle-section {
    grid-template-columns: 1fr;
  }
}

.panel-box {
  border-radius: 12px;
  border: 1px solid #e2e8f0;
  background: #ffffff;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.panel-header-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 600;
  color: #0f172a;
}

.uptime-badge {
  font-size: 12px;
  color: #16a34a;
  background: #f0fdf4;
  padding: 2px 8px;
  border-radius: 4px;
  border: 1px solid #bbf7d0;
  font-family: 'SFMono-Regular', Consolas, monospace;
}

.panel-hint {
  font-size: 12px;
  color: #94a3b8;
}

/* 雷达指标 */
.radar-metric-block {
  margin-bottom: 20px;
}

.radar-metric-header {
  display: flex;
  justify-content: space-between;
  margin-bottom: 8px;
  font-size: 13px;
}

.metric-title {
  color: #475569;
  font-weight: 500;
}

.metric-detail {
  color: #0f172a;
  font-weight: 600;
}

.dependencies-container {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.dep-title {
  font-size: 12px;
  font-weight: 600;
  color: #64748b;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.dep-grid {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.dep-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
}

.dep-info {
  display: flex;
  flex-direction: column;
}

.dep-name {
  font-size: 13px;
  font-weight: 600;
  color: #1e293b;
}

.dep-detail {
  font-size: 11px;
  color: #94a3b8;
}

.dep-tag {
  display: flex;
  align-items: center;
  gap: 4px;
}

/* 快捷矩阵 */
.quick-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
}

@media (max-width: 640px) {
  .quick-grid {
    grid-template-columns: 1fr;
  }
}

.quick-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s ease-in-out;
}

.quick-item:hover {
  background: #ffffff;
  border-color: #cbd5e1;
  transform: translateY(-2px);
  box-shadow: 0 4px 10px rgba(0, 0, 0, 0.05);
}

.quick-icon-wrap {
  width: 40px;
  height: 40px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  flex-shrink: 0;
}

.icon-film {
  background: #ede9fe;
  color: #7c3aed;
}

.icon-user {
  background: #dbeafe;
  color: #2563eb;
}

.icon-coin {
  background: #dcfce7;
  color: #16a34a;
}

.icon-vip {
  background: #fef3c7;
  color: #d97706;
}

.icon-config {
  background: #fee2e2;
  color: #dc2626;
}

.icon-runtime {
  background: #e0f2fe;
  color: #0284c7;
}

.quick-text {
  flex: 1;
  overflow: hidden;
}

.quick-title {
  font-size: 13px;
  font-weight: 600;
  color: #1e293b;
}

.quick-desc {
  font-size: 11px;
  color: #94a3b8;
  margin-top: 2px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.quick-arrow {
  color: #cbd5e1;
  font-size: 14px;
}

.quick-item:hover .quick-arrow {
  color: #475569;
}

/* 审计日志列表 */
.audit-list {
  display: flex;
  flex-direction: column;
}

.audit-row {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 12px 0;
  border-bottom: 1px solid #f1f5f9;
  font-size: 13px;
}

.audit-row:last-child {
  border-bottom: none;
}

.audit-time {
  font-size: 12px;
  color: #94a3b8;
  width: 155px;
  flex-shrink: 0;
}

.audit-actor {
  flex-shrink: 0;
}

.audit-action {
  flex-shrink: 0;
}

.audit-summary {
  color: #334155;
  flex: 1;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
</style>
