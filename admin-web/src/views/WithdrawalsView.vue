<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, onMounted, ref } from 'vue'
import {
  Tickets,
  Clock,
  Check,
  Lock,
  Search,
  Refresh,
  CopyDocument,
  DataAnalysis,
} from '@element-plus/icons-vue'
import {
  fetchWithdrawalStats,
  fetchWithdrawals,
  manualConfirmWithdrawal,
  rejectWithdrawal,
} from '../services/adminApi'
import type {
  WithdrawalRequest,
  WithdrawalStatsCustomRange,
  WithdrawalStats,
  WithdrawalStatsRange,
} from '../services/adminApi'
import { backendErrorMessage } from '../services/http'

const loading = ref(false)
const operationLoading = ref(false)
const error = ref('')
const withdrawals = ref<WithdrawalRequest[]>([])
const stats = ref<WithdrawalStats | null>(null)
const statsLoading = ref(false)
const statsError = ref('')
const statsRange = ref<WithdrawalStatsRange>('TODAY')
const customStatsDates = ref<[string, string] | null>(null)
const confirmDialogVisible = ref(false)
const confirmingWithdrawal = ref<WithdrawalRequest | null>(null)
const confirmError = ref('')

// 搜索、过滤与分页
const searchQuery = ref('')
const statusFilter = ref<string>('ALL')
const currentPage = ref(1)
const pageSize = ref(15)

const statRanges: Array<{ label: string; value: WithdrawalStatsRange }> = [
  { label: '今天', value: 'TODAY' },
  { label: '昨天', value: 'YESTERDAY' },
  { label: '本周', value: 'THIS_WEEK' },
  { label: '本月', value: 'THIS_MONTH' },
  { label: '上月', value: 'LAST_MONTH' },
]

const pendingCount = computed(() =>
  withdrawals.value.filter((withdrawal) => withdrawal.status === 'PENDING').length,
)
const approvedCount = computed(() =>
  withdrawals.value.filter((withdrawal) => withdrawal.status === 'APPROVED').length,
)
const totalPendingPoints = computed(() =>
  withdrawals.value
    .filter((withdrawal) => withdrawal.status === 'PENDING')
    .reduce((total, withdrawal) => total + withdrawal.pointAmount, 0),
)

// 过滤后的提现列表
const filteredWithdrawals = computed(() => {
  let list = withdrawals.value
  const q = searchQuery.value.trim().toLowerCase()
  if (q) {
    list = list.filter(
      (w) =>
        w.id.toLowerCase().includes(q) ||
        (w.userAccount && w.userAccount.toLowerCase().includes(q)) ||
        w.walletAddress.toLowerCase().includes(q),
    )
  }
  if (statusFilter.value !== 'ALL') {
    list = list.filter((w) => w.status === statusFilter.value)
  }
  return list
})

// 分页列表
const pagedWithdrawals = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return filteredWithdrawals.value.slice(start, start + pageSize.value)
})

function resetFilters() {
  searchQuery.value = ''
  statusFilter.value = 'ALL'
  currentPage.value = 1
}

function formatNumber(num?: number) {
  if (num === undefined || num === null) return '0'
  return num.toLocaleString()
}

function formatDateTime(val: string | null) {
  if (!val) return '-'
  try {
    const d = new Date(val)
    if (isNaN(d.getTime())) return val
    const y = d.getFullYear()
    const m = String(d.getMonth() + 1).padStart(2, '0')
    const day = String(d.getDate()).padStart(2, '0')
    const h = String(d.getHours()).padStart(2, '0')
    const min = String(d.getMinutes()).padStart(2, '0')
    const s = String(d.getSeconds()).padStart(2, '0')
    return `${y}-${m}-${day} ${h}:${min}:${s}`
  } catch {
    return val
  }
}

function formatPeriodText(from?: string, to?: string) {
  if (!from || !to) return '-'
  const cleanFrom = from.split('T')[0] || from
  const cleanTo = to.split('T')[0] || to
  return `${cleanFrom} 至 ${cleanTo}`
}

async function loadWithdrawals() {
  loading.value = true
  error.value = ''
  try {
    withdrawals.value = await fetchWithdrawals()
  } catch {
    error.value = '提现申请加载失败'
  } finally {
    loading.value = false
  }
}

async function loadStats() {
  statsLoading.value = true
  statsError.value = ''
  try {
    const customRange: WithdrawalStatsCustomRange | undefined =
      statsRange.value === 'CUSTOM' && customStatsDates.value
        ? { fromDate: customStatsDates.value[0], toDate: customStatsDates.value[1] }
        : undefined
    stats.value = await fetchWithdrawalStats(statsRange.value, customRange)
  } catch {
    statsError.value = '打款统计加载失败'
  } finally {
    statsLoading.value = false
  }
}

async function selectStatsRange(range: WithdrawalStatsRange) {
  if (statsRange.value === range && stats.value) return
  statsRange.value = range
  customStatsDates.value = null
  await loadStats()
}

async function selectCustomStatsRange(value: [string, string] | null) {
  customStatsDates.value = value
  if (!value) return
  statsRange.value = 'CUSTOM'
  await loadStats()
}

function canManuallyConfirm(withdrawal: WithdrawalRequest) {
  return (
    withdrawal.status === 'PENDING' &&
    withdrawal.network === 'ERC20' &&
    (!withdrawal.payoutStatus || withdrawal.payoutStatus === 'MANUAL_REVIEW')
  )
}

function openManualConfirm(row: WithdrawalRequest) {
  if (!canManuallyConfirm(row)) {
    ElMessage.warning('该提现当前状态不能手动确认，请刷新列表核对')
    return
  }
  confirmingWithdrawal.value = row
  confirmError.value = ''
  confirmDialogVisible.value = true
}

async function copyWalletAddress(row: WithdrawalRequest) {
  try {
    await navigator.clipboard.writeText(row.walletAddress)
    ElMessage.success('钱包地址已复制')
  } catch {
    ElMessage.error('钱包地址复制失败')
  }
}

async function copyText(text: string, label = '内容') {
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success(`${label}已复制到剪贴板`)
  } catch {
    ElMessage.warning('复制失败，请手动选中文本复制')
  }
}

async function confirmExternalPayout() {
  if (!confirmingWithdrawal.value) return
  operationLoading.value = true
  confirmError.value = ''
  try {
    await manualConfirmWithdrawal(confirmingWithdrawal.value.id)
    ElMessage.success('已确认外部打款，冻结积分已扣除')
    confirmDialogVisible.value = false
    confirmingWithdrawal.value = null
    await Promise.all([loadWithdrawals(), loadStats()])
  } catch (requestError) {
    confirmError.value = backendErrorMessage(requestError, '外部打款确认失败')
  } finally {
    operationLoading.value = false
  }
}

function closeConfirmDialog() {
  confirmDialogVisible.value = false
  confirmingWithdrawal.value = null
  confirmError.value = ''
}

async function reject(row: WithdrawalRequest) {
  let reason = ''
  try {
    const result = await ElMessageBox.prompt('请输入拒绝原因', '拒绝提现申请', {
      confirmButtonText: '拒绝',
      cancelButtonText: '取消',
      inputPattern: /\S{2,}/,
      inputErrorMessage: '拒绝原因不能为空',
      type: 'warning',
    })
    reason = result.value.trim()
  } catch {
    return
  }
  operationLoading.value = true
  try {
    await rejectWithdrawal(row.id, reason)
    ElMessage.success('提现申请已拒绝，冻结积分已释放')
    await Promise.all([loadWithdrawals(), loadStats()])
  } catch (requestError) {
    ElMessage.error(backendErrorMessage(requestError, '提现拒绝失败'))
  } finally {
    operationLoading.value = false
  }
}

onMounted(() => {
  void loadWithdrawals()
  void loadStats()
})
</script>

<template>
  <section v-loading="loading" class="page-section">
    <!-- 顶部标题栏 -->
    <div class="page-header">
      <div>
        <div class="title-row">
          <h1>提现申请</h1>
          <el-tag
            v-if="pendingCount > 0"
            effect="dark"
            type="warning"
            size="small"
            class="pending-pill"
          >
            <span class="pulse-dot"></span> {{ pendingCount }} 笔待打款
          </el-tag>
          <el-tag v-else effect="plain" type="success" size="small" class="pending-pill">
            全部已处理
          </el-tag>
        </div>
        <p class="subtitle">
          管理员完成外部 ERC20 转账后，在此确认打款。系统不签名、不广播，也不要求填写交易哈希。
        </p>
      </div>
      <el-button type="primary" :icon="Refresh" @click="() => { loadWithdrawals(); loadStats(); }">
        刷新数据
      </el-button>
    </div>

    <el-alert v-if="error" :title="error" show-icon type="error" />

    <!-- 顶部业务 KPI 统计卡片 (4 列) -->
    <div class="withdrawal-kpi-grid">
      <div class="kpi-card kpi-total">
        <div class="kpi-icon-box"><el-icon><Tickets /></el-icon></div>
        <div class="kpi-content">
          <div class="kpi-label">申请总数</div>
          <div class="kpi-num mono">{{ withdrawals.length }}</div>
        </div>
      </div>

      <div class="kpi-card kpi-pending" :class="{ 'has-pending': pendingCount > 0 }">
        <div class="kpi-icon-box"><el-icon><Clock /></el-icon></div>
        <div class="kpi-content">
          <div class="kpi-label">待人工打款</div>
          <div class="kpi-num mono" :class="{ 'text-amber': pendingCount > 0 }">
            {{ pendingCount }}
          </div>
        </div>
      </div>

      <div class="kpi-card kpi-approved">
        <div class="kpi-icon-box"><el-icon><Check /></el-icon></div>
        <div class="kpi-content">
          <div class="kpi-label">已确认出金</div>
          <div class="kpi-num mono text-green">{{ approvedCount }}</div>
        </div>
      </div>

      <div class="kpi-card kpi-frozen">
        <div class="kpi-icon-box"><el-icon><Lock /></el-icon></div>
        <div class="kpi-content">
          <div class="kpi-label">冻结保障积分</div>
          <div class="kpi-num mono">{{ formatNumber(totalPendingPoints) }}</div>
        </div>
      </div>
    </div>

    <!-- 打款统计面板 (Stats Panel) -->
    <el-card shadow="never" class="stats-card" v-loading="statsLoading">
      <div class="stats-panel-header">
        <div class="stats-title-wrap">
          <el-icon><DataAnalysis /></el-icon>
          <h2>打款统计</h2>
          <span class="period-clean-text mono">
            统计区间（上海时间）: <b>{{ formatPeriodText(stats?.from, stats?.to) }}</b>
          </span>
        </div>

        <div class="stats-controls">
          <el-radio-group :model-value="statsRange" size="small" @change="selectStatsRange">
            <el-radio-button v-for="range in statRanges" :key="range.value" :value="range.value">
              {{ range.label }}
            </el-radio-button>
          </el-radio-group>
          <el-date-picker
            v-model="customStatsDates"
            end-placeholder="结束日期"
            range-separator="至"
            size="small"
            start-placeholder="开始日期"
            type="daterange"
            unlink-panels
            value-format="YYYY-MM-DD"
            @change="selectCustomStatsRange"
          />
        </div>
      </div>

      <el-alert v-if="statsError" :title="statsError" show-icon type="error" :closable="false" />
      <div v-else class="stats-kpi-row">
        <div class="stat-block">
          <div class="stat-label">打款笔数</div>
          <div class="stat-value mono">{{ stats?.payoutCount ?? 0 }} <span class="stat-unit">笔</span></div>
        </div>
        <div class="stat-divider"></div>
        <div class="stat-block">
          <div class="stat-label">打款金额（USDT）</div>
          <div class="stat-value mono text-green">{{ stats?.totalUsdt ?? '0' }} <span class="stat-unit">USDT</span></div>
        </div>
        <div class="stat-divider"></div>
        <div class="stat-block">
          <div class="stat-label">出金结算网络</div>
          <div class="stat-value mono">ERC20 <span class="stat-unit">(Ethereum USDT)</span></div>
        </div>
      </div>
    </el-card>

    <!-- 提现申请表格卡片 -->
    <el-card shadow="never" class="table-card">
      <!-- 搜索与状态过滤器 -->
      <div class="table-filter-bar">
        <el-input
          v-model="searchQuery"
          placeholder="搜索申请 ID / 账号 / 钱包地址..."
          :prefix-icon="Search"
          clearable
          class="search-input"
        />

        <div class="filter-group">
          <el-radio-group v-model="statusFilter" size="small">
            <el-radio-button value="ALL">全部申请 ({{ withdrawals.length }})</el-radio-button>
            <el-radio-button value="PENDING">待处理 ({{ pendingCount }})</el-radio-button>
            <el-radio-button value="APPROVED">已确认 ({{ approvedCount }})</el-radio-button>
            <el-radio-button value="REJECTED">已拒绝</el-radio-button>
          </el-radio-group>
          <el-button link type="info" size="small" @click="resetFilters">重置</el-button>
        </div>
      </div>

      <!-- 提现表格 -->
      <el-table :data="pagedWithdrawals" border stripe style="width: 100%" class="custom-table">
        <!-- 申请 ID -->
        <el-table-column label="申请 ID" min-width="170">
          <template #default="{ row }">
            <div class="copyable-cell">
              <span class="mono id-text" :title="row.id">{{ row.id.substring(0, 12) }}...</span>
              <el-button link :icon="CopyDocument" size="small" @click="copyText(row.id, '申请 ID')" />
            </div>
          </template>
        </el-table-column>

        <!-- 账号 -->
        <el-table-column label="账号" min-width="140">
          <template #default="{ row }">
            <div class="account-cell">
              <span class="mono font-bold">{{ row.userAccount || '-' }}</span>
            </div>
          </template>
        </el-table-column>

        <!-- 积分 -->
        <el-table-column align="right" label="消耗积分" prop="pointAmount" width="120">
          <template #default="{ row }">
            <span class="mono font-bold">{{ formatNumber(row.pointAmount) }}</span>
          </template>
        </el-table-column>

        <!-- USDT 应付金额 -->
        <el-table-column align="right" label="应付 USDT" width="130">
          <template #default="{ row }">
            <span class="mono usdt-amount font-bold">{{ row.usdtAmount }}</span>
          </template>
        </el-table-column>

        <!-- 收款钱包地址 -->
        <el-table-column label="收款钱包地址" min-width="260">
          <template #default="{ row }">
            <div class="wallet-cell">
              <el-tag size="small" effect="plain" type="info" class="network-tag">{{ row.network }}</el-tag>
              <span class="mono wallet-addr" :title="row.walletAddress">{{ row.walletAddress }}</span>
              <el-button link :icon="CopyDocument" size="small" @click="copyWalletAddress(row)">复制钱包地址</el-button>
            </div>
          </template>
        </el-table-column>

        <!-- 状态 -->
        <el-table-column label="审核状态" width="120" align="center">
          <template #default="{ row }">
            <span
              class="status-pill"
              :class="{
                'pill-pending': row.status === 'PENDING',
                'pill-approved': row.status === 'APPROVED',
                'pill-rejected': row.status === 'REJECTED',
              }"
            >
              <span class="status-dot"></span>
              {{ row.status === 'PENDING' ? '待打款' : (row.status === 'APPROVED' ? '已确认' : '已拒绝') }}
            </span>
          </template>
        </el-table-column>

        <!-- 打款状态 -->
        <el-table-column label="打款跟踪" width="140" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.payoutStatus" :type="row.manualReview ? 'warning' : 'info'" effect="plain" size="small">
              {{ row.payoutStatus }}
            </el-tag>
            <span v-else class="text-dim">无需链上状态</span>
          </template>
        </el-table-column>

        <!-- 备注 -->
        <el-table-column label="备注说明" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">
            <span>{{ row.failureReason || row.adminNote || '-' }}</span>
          </template>
        </el-table-column>

        <!-- 申请时间 -->
        <el-table-column label="申请时间" min-width="170">
          <template #default="{ row }">
            <span class="mono time-text">{{ formatDateTime(row.createdAt) }}</span>
          </template>
        </el-table-column>

        <!-- 确认时间 -->
        <el-table-column label="处理时间" min-width="170">
          <template #default="{ row }">
            <span class="mono time-text">{{ formatDateTime(row.reviewedAt) }}</span>
          </template>
        </el-table-column>

        <!-- 操作 -->
        <el-table-column align="center" fixed="right" label="操作" width="220">
          <template #default="{ row }">
            <div class="operation-actions">
              <template v-if="row.status === 'PENDING'">
                <el-button
                  v-if="canManuallyConfirm(row)"
                  :loading="operationLoading"
                  type="success"
                  size="small"
                  @click="openManualConfirm(row)"
                >
                  确认已外部打款
                </el-button>
                <el-button
                  :loading="operationLoading"
                  type="danger"
                  size="small"
                  plain
                  @click="reject(row)"
                >
                  拒绝
                </el-button>
              </template>
              <span v-else class="text-dim">已办结</span>
            </div>
          </template>
        </el-table-column>

        <template #empty>
          <el-empty description="暂无符合条件的提现申请记录" />
        </template>
      </el-table>

      <!-- 底部分页 -->
      <div class="pagination-footer">
        <span class="page-count-text">
          展示 <b>{{ pagedWithdrawals.length }}</b> / 共 <b>{{ filteredWithdrawals.length }}</b> 条申请
        </span>
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[10, 15, 30, 50]"
          layout="sizes, prev, pager, next"
          :total="filteredWithdrawals.length"
          size="small"
        />
      </div>
    </el-card>

    <!-- 确认外部打款对话框 -->
    <el-dialog
      v-model="confirmDialogVisible"
      v-loading="operationLoading"
      title="确认外部 ERC20 打款"
      width="540px"
      :close-on-click-modal="false"
      @close="closeConfirmDialog"
    >
      <template v-if="confirmingWithdrawal">
        <el-alert type="warning" show-icon :closable="false" style="margin-bottom: 16px">
          请先在外部钱包完成 ERC20 转账。确认后系统立即扣除该申请冻结的积分，且不会广播或核验链上交易。
        </el-alert>

        <div class="confirm-payout-box">
          <div class="payout-row">
            <span class="payout-label">打款金额（USDT）:</span>
            <span class="payout-amount mono">{{ confirmingWithdrawal.usdtAmount }} USDT</span>
          </div>
          <div class="payout-row">
            <span class="payout-label">对应积分扣除:</span>
            <span class="payout-points mono">-{{ formatNumber(confirmingWithdrawal.pointAmount) }} pt</span>
          </div>
          <div class="payout-row full">
            <span class="payout-label">收款钱包地址:</span>
            <div class="wallet-copy-wrap">
              <span class="mono wallet-bold">{{ confirmingWithdrawal.walletAddress }}</span>
              <el-button
                link
                :icon="CopyDocument"
                size="small"
                @click="copyText(confirmingWithdrawal.walletAddress, '钱包地址')"
              >
                复制
              </el-button>
            </div>
          </div>
        </div>

        <el-alert
          v-if="confirmError"
          title="确认未完成"
          :description="confirmError"
          type="error"
          show-icon
          :closable="false"
          style="margin-top: 16px"
        />
      </template>
      <template #footer>
        <el-button @click="closeConfirmDialog">取消</el-button>
        <el-button type="primary" :loading="operationLoading" @click="confirmExternalPayout">
          确认已外部打款
        </el-button>
      </template>
    </el-dialog>
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

.pending-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-weight: 600;
  border-radius: 9999px;
  padding: 0 10px;
}

.pulse-dot {
  width: 6px;
  height: 6px;
  background-color: #f59e0b;
  border-radius: 50%;
  box-shadow: 0 0 0 0 rgba(245, 158, 11, 0.7);
  animation: pulse 2s infinite;
}

@keyframes pulse {
  0% { transform: scale(0.95); box-shadow: 0 0 0 0 rgba(245, 158, 11, 0.7); }
  70% { transform: scale(1); box-shadow: 0 0 0 6px rgba(245, 158, 11, 0); }
  100% { transform: scale(0.95); box-shadow: 0 0 0 0 rgba(245, 158, 11, 0); }
}

/* 顶部 KPI 卡片网格 (4列) */
.withdrawal-kpi-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 18px;
}

@media (max-width: 1024px) {
  .withdrawal-kpi-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 640px) {
  .withdrawal-kpi-grid {
    grid-template-columns: 1fr;
  }
}

.kpi-card {
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  padding: 16px;
  display: flex;
  align-items: center;
  gap: 14px;
  transition: all 0.2s ease-in-out;
}

.kpi-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 14px -2px rgba(0, 0, 0, 0.06);
}

.kpi-icon-box {
  width: 44px;
  height: 44px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  flex-shrink: 0;
}

.kpi-total .kpi-icon-box { background: #eff6ff; color: #2563eb; }
.kpi-pending .kpi-icon-box { background: #fffbeb; color: #d97706; }
.kpi-approved .kpi-icon-box { background: #f0fdf4; color: #16a34a; }
.kpi-frozen .kpi-icon-box { background: #f5f3ff; color: #7c3aed; }

.kpi-content {
  display: flex;
  flex-direction: column;
}

.kpi-label {
  font-size: 12px;
  color: #64748b;
  font-weight: 500;
}

.kpi-num {
  font-size: 22px;
  font-weight: 700;
  color: #0f172a;
  margin-top: 2px;
}

.text-amber { color: #d97706; }
.text-green { color: #16a34a; }

/* 统计面板 */
.stats-card {
  border-radius: 10px;
  border: 1px solid #e2e8f0;
  margin-bottom: 18px;
}

.stats-panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
  flex-wrap: wrap;
  gap: 12px;
}

.stats-title-wrap {
  display: flex;
  align-items: center;
  gap: 8px;
}

.stats-title-wrap h2 {
  margin: 0;
  font-size: 16px;
  font-weight: 700;
  color: #0f172a;
}

.period-clean-text {
  font-size: 12px;
  color: #64748b;
  background: #f1f5f9;
  padding: 3px 8px;
  border-radius: 4px;
  margin-left: 8px;
}

.stats-controls {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.stats-kpi-row {
  display: flex;
  align-items: center;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 14px 20px;
  gap: 32px;
  flex-wrap: wrap;
}

.stat-block {
  display: flex;
  flex-direction: column;
}

.stat-label {
  font-size: 12px;
  color: #64748b;
  margin-bottom: 4px;
}

.stat-value {
  font-size: 22px;
  font-weight: 700;
  color: #0f172a;
}

.stat-unit {
  font-size: 12px;
  color: #94a3b8;
  font-weight: normal;
}

.stat-divider {
  width: 1px;
  height: 36px;
  background: #cbd5e1;
}

@media (max-width: 640px) {
  .stat-divider {
    display: none;
  }
}

/* 表格卡片与过滤器 */
.table-card {
  border-radius: 10px;
  border: 1px solid #e2e8f0;
}

.table-filter-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
  gap: 12px;
  flex-wrap: wrap;
}

.search-input {
  max-width: 320px;
}

.filter-group {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.copyable-cell {
  display: flex;
  align-items: center;
  gap: 4px;
}

.id-text {
  color: #64748b;
  font-size: 12px;
}

.usdt-amount {
  color: #16a34a;
  font-size: 14px;
}

.wallet-cell {
  display: flex;
  align-items: center;
  gap: 6px;
}

.network-tag {
  font-size: 10px;
  padding: 0 4px;
}

.wallet-addr {
  font-size: 12px;
  color: #2563eb;
  word-break: break-all;
}

/* 状态药丸 */
.status-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 3px 10px;
  border-radius: 9999px;
  font-size: 12px;
  font-weight: 600;
}

.status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
}

.pill-pending {
  background: #fffbeb;
  color: #b45309;
  border: 1px solid #fde68a;
}
.pill-pending .status-dot {
  background: #f59e0b;
}

.pill-approved {
  background: #f0fdf4;
  color: #15803d;
  border: 1px solid #bbf7d0;
}
.pill-approved .status-dot {
  background: #22c55e;
}

.pill-rejected {
  background: #fef2f2;
  color: #b91c1c;
  border: 1px solid #fecaca;
}
.pill-rejected .status-dot {
  background: #ef4444;
}

.time-text {
  font-size: 12px;
  color: #64748b;
}

.operation-actions {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.pagination-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px solid #f1f5f9;
}

.page-count-text {
  font-size: 12px;
  color: #64748b;
}

.text-dim {
  color: #94a3b8;
  font-size: 12px;
}

/* 确认打款弹窗 */
.confirm-payout-box {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.payout-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.payout-row.full {
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
}

.payout-label {
  font-size: 13px;
  color: #64748b;
}

.payout-amount {
  font-size: 18px;
  font-weight: 700;
  color: #16a34a;
}

.payout-points {
  font-size: 14px;
  color: #d97706;
  font-weight: 600;
}

.wallet-copy-wrap {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
}

.wallet-bold {
  font-size: 13px;
  color: #0f172a;
  word-break: break-all;
  flex: 1;
}
</style>
