<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, onMounted, ref } from 'vue'
import { approveWithdrawal, fetchWithdrawals, rejectWithdrawal } from '../services/adminApi'
import type { WithdrawalRequest, WithdrawalStatus } from '../services/adminApi'
import { backendErrorMessage } from '../services/http'

const loading = ref(false)
const operationLoading = ref(false)
const error = ref('')
const withdrawals = ref<WithdrawalRequest[]>([])

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
  { label: '已通过', value: approvedCount.value },
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

async function approve(row: WithdrawalRequest) {
  let txHash = ''
  try {
    const result = await ElMessageBox.prompt('请输入 TRC 链上转账 tx hash', '通过提现申请', {
      confirmButtonText: '通过',
      cancelButtonText: '取消',
      inputPattern: /\S{6,}/,
      inputErrorMessage: 'tx hash 不能为空',
    })
    txHash = result.value.trim()
  } catch {
    return
  }
  operationLoading.value = true
  try {
    await approveWithdrawal(row.id, txHash, 'manual TRC transfer confirmed')
    ElMessage.success('提现申请已通过')
    await loadWithdrawals()
  } catch (error) {
    ElMessage.error(backendErrorMessage(error, '提现审批失败'))
  } finally {
    operationLoading.value = false
  }
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
    await loadWithdrawals()
  } catch (error) {
    ElMessage.error(backendErrorMessage(error, '提现拒绝失败'))
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

onMounted(loadWithdrawals)
</script>

<template>
  <section v-loading="loading" class="page-section">
    <div class="page-header">
      <div>
        <h1>提现申请</h1>
        <p>人工核对 TRC 转账结果，审批通过后扣除冻结积分，拒绝后释放冻结积分。</p>
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

    <el-table :data="withdrawals" border>
      <el-table-column label="申请 ID" min-width="240">
        <template #default="{ row }">
          <span class="mono">{{ row.id }}</span>
        </template>
      </el-table-column>
      <el-table-column label="手机号账号" min-width="180">
        <template #default="{ row }">
          <span class="mono">{{ row.userAccount || '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="用户 ID" min-width="220">
        <template #default="{ row }">
          <span class="mono">{{ row.userId }}</span>
        </template>
      </el-table-column>
      <el-table-column align="right" label="积分" prop="pointAmount" width="100" />
      <el-table-column align="right" label="人民币价值" width="130">
        <template #default="{ row }">
          {{ row.cnyPerPoint ? `${(Number(row.pointAmount) * Number(row.cnyPerPoint)).toFixed(2)} CNY` : '-' }}
        </template>
      </el-table-column>
      <el-table-column align="right" label="USDT" width="110">
        <template #default="{ row }">{{ row.usdtAmount }}</template>
      </el-table-column>
      <el-table-column label="汇率快照" min-width="180">
        <template #default="{ row }">
          <span v-if="row.cnyPerUsd">1 USD = {{ row.cnyPerUsd }} CNY</span>
          <span v-else class="muted">历史记录</span>
        </template>
      </el-table-column>
      <el-table-column label="钱包地址" min-width="260">
        <template #default="{ row }">
          <span class="mono">{{ row.network }} · {{ row.walletAddress }}</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="120">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)" effect="plain">{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="tx hash" min-width="180">
        <template #default="{ row }">
          <span v-if="row.txHash" class="mono">{{ row.txHash }}</span>
          <span v-else class="muted">未录入</span>
        </template>
      </el-table-column>
      <el-table-column label="备注" min-width="180">
        <template #default="{ row }">{{ row.adminNote || '-' }}</template>
      </el-table-column>
      <el-table-column label="申请时间" min-width="220" prop="createdAt" />
      <el-table-column label="处理时间" min-width="220">
        <template #default="{ row }">{{ row.reviewedAt || '-' }}</template>
      </el-table-column>
      <el-table-column align="right" fixed="right" label="操作" width="150">
        <template #default="{ row }">
          <template v-if="row.status === 'PENDING'">
            <el-button :loading="operationLoading" type="primary" text @click="approve(row)">通过</el-button>
            <el-button :loading="operationLoading" type="danger" text @click="reject(row)">拒绝</el-button>
          </template>
          <span v-else class="muted">已处理</span>
        </template>
      </el-table-column>
    </el-table>
  </section>
</template>
