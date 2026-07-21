<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, onMounted, ref } from 'vue'
import {
  fetchWithdrawalStats,
  fetchWithdrawals,
  manualConfirmWithdrawal,
  rejectWithdrawal,
} from '../services/adminApi'
import type {
  WithdrawalRequest,
  WithdrawalStats,
  WithdrawalStatsRange,
  WithdrawalStatus,
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
const confirmDialogVisible = ref(false)
const confirmingWithdrawal = ref<WithdrawalRequest | null>(null)
const confirmError = ref('')

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

const metrics = computed(() => [
  { label: '申请总数', value: withdrawals.value.length },
  { label: '待处理', value: pendingCount.value },
  { label: '已确认', value: approvedCount.value },
  { label: '冻结积分', value: totalPendingPoints.value },
])

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
    stats.value = await fetchWithdrawalStats(statsRange.value)
  } catch {
    statsError.value = '打款统计加载失败'
  } finally {
    statsLoading.value = false
  }
}

async function selectStatsRange(range: WithdrawalStatsRange) {
  if (statsRange.value === range && stats.value) return
  statsRange.value = range
  await loadStats()
}

function canManuallyConfirm(withdrawal: WithdrawalRequest) {
  return withdrawal.status === 'PENDING'
    && withdrawal.network === 'ERC20'
    && (!withdrawal.payoutStatus || withdrawal.payoutStatus === 'MANUAL_REVIEW')
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

function statusType(status: WithdrawalStatus) {
  const types: Record<WithdrawalStatus, 'info' | 'success' | 'warning' | 'danger'> = {
    PENDING: 'warning',
    APPROVED: 'success',
    REJECTED: 'danger',
  }
  return types[status]
}

onMounted(() => {
  void loadWithdrawals()
  void loadStats()
})
</script>

<template>
  <section v-loading="loading" class="page-section">
    <div class="page-header">
      <div>
        <h1>提现申请</h1>
        <p>管理员完成外部 ERC20 转账后，在此确认打款。系统不签名、不广播，也不要求填写交易哈希。</p>
      </div>
      <el-button @click="loadWithdrawals">刷新</el-button>
    </div>

    <el-alert v-if="error" :title="error" show-icon type="error" />

    <div class="metric-grid">
      <div v-for="metric in metrics" :key="metric.label" class="metric">
        <div class="metric-label">{{ metric.label }}</div>
        <div class="metric-value">{{ metric.value }}</div>
      </div>
    </div>

    <div class="stats-panel" v-loading="statsLoading">
      <div class="stats-header">
        <h2>打款统计</h2>
        <el-radio-group :model-value="statsRange" size="small" @change="selectStatsRange">
          <el-radio-button v-for="range in statRanges" :key="range.value" :value="range.value">
            {{ range.label }}
          </el-radio-button>
        </el-radio-group>
      </div>
      <el-alert v-if="statsError" :title="statsError" show-icon type="error" :closable="false" />
      <div v-else class="stats-values">
        <div>
          <div class="metric-label">打款笔数</div>
          <div class="metric-value">{{ stats?.payoutCount ?? 0 }}</div>
        </div>
        <div>
          <div class="metric-label">打款金额（USDT）</div>
          <div class="metric-value">{{ stats?.totalUsdt ?? '0' }}</div>
        </div>
        <div class="stats-period">
          <div class="metric-label">统计区间（上海时间）</div>
          <div>{{ stats ? `${stats.from} 至 ${stats.to}` : '-' }}</div>
        </div>
      </div>
    </div>

    <el-table :data="withdrawals" border>
      <el-table-column label="申请 ID" min-width="240">
        <template #default="{ row }"><span class="mono">{{ row.id }}</span></template>
      </el-table-column>
      <el-table-column label="手机号账号" min-width="180">
        <template #default="{ row }"><span class="mono">{{ row.userAccount || '-' }}</span></template>
      </el-table-column>
      <el-table-column align="right" label="积分" prop="pointAmount" width="100" />
      <el-table-column align="right" label="USDT" width="110">
        <template #default="{ row }">{{ row.usdtAmount }}</template>
      </el-table-column>
      <el-table-column label="钱包地址" min-width="280">
        <template #default="{ row }"><span class="mono">{{ row.network }} · {{ row.walletAddress }}</span></template>
      </el-table-column>
      <el-table-column label="状态" width="120">
        <template #default="{ row }"><el-tag :type="statusType(row.status)" effect="plain">{{ row.status }}</el-tag></template>
      </el-table-column>
      <el-table-column label="打款状态" width="170">
        <template #default="{ row }">
          <el-tag v-if="row.payoutStatus" :type="row.manualReview ? 'warning' : 'info'" effect="plain">
            {{ row.payoutStatus }}
          </el-tag>
          <span v-else class="muted">尚未确认</span>
        </template>
      </el-table-column>
      <el-table-column label="备注" min-width="220">
        <template #default="{ row }"><span>{{ row.failureReason || row.adminNote || '-' }}</span></template>
      </el-table-column>
      <el-table-column label="申请时间" min-width="220" prop="createdAt" />
      <el-table-column label="确认时间" min-width="220">
        <template #default="{ row }">{{ row.reviewedAt || '-' }}</template>
      </el-table-column>
      <el-table-column align="right" fixed="right" label="操作" width="190">
        <template #default="{ row }">
          <template v-if="row.status === 'PENDING'">
            <el-button
              v-if="canManuallyConfirm(row)"
              :loading="operationLoading"
              type="primary"
              text
              @click="openManualConfirm(row)"
            >确认已外部打款</el-button>
            <el-button :loading="operationLoading" type="danger" text @click="reject(row)">拒绝</el-button>
          </template>
          <span v-else class="muted">已处理</span>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog
      v-model="confirmDialogVisible"
      v-loading="operationLoading"
      title="确认外部 ERC20 打款"
      width="520px"
      :close-on-click-modal="false"
      @close="closeConfirmDialog"
    >
      <template v-if="confirmingWithdrawal">
        <el-alert type="warning" show-icon :closable="false" style="margin-bottom: 16px">
          请先在外部钱包完成 ERC20 转账。确认后系统立即扣除该申请冻结的积分，且不会广播或核验链上交易。
        </el-alert>
        <el-descriptions :column="1" border>
          <el-descriptions-item label="打款金额">{{ confirmingWithdrawal.usdtAmount }} USDT</el-descriptions-item>
          <el-descriptions-item label="收款地址"><span class="mono">{{ confirmingWithdrawal.walletAddress }}</span></el-descriptions-item>
        </el-descriptions>
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
        <el-button type="primary" :loading="operationLoading" @click="confirmExternalPayout">确认已外部打款</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.stats-panel {
  margin-bottom: 16px;
  padding: 16px;
  border: 1px solid var(--el-border-color-light);
}

.stats-header,
.stats-values {
  display: flex;
  align-items: center;
  gap: 28px;
}

.stats-header {
  justify-content: space-between;
  margin-bottom: 16px;
}

.stats-header h2 {
  margin: 0;
  font-size: 16px;
}

.stats-period {
  min-width: 260px;
}
</style>
