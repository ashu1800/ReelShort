<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, onMounted, ref } from 'vue'
import {
  approveWithdrawal,
  batchApproveWithdrawals,
  batchPreviewWithdrawals,
  fetchWithdrawals,
  get2faStatus,
  rejectWithdrawal,
} from '../services/adminApi'
import type { BatchWithdrawalPreview, BatchWithdrawalResult, WithdrawalRequest, WithdrawalStatus } from '../services/adminApi'
import { backendErrorMessage } from '../services/http'

const loading = ref(false)
const operationLoading = ref(false)
const error = ref('')
const withdrawals = ref<WithdrawalRequest[]>([])
const selectedRows = ref<WithdrawalRequest[]>([])

// Batch payout dialog state
const batchDialogVisible = ref(false)
const batchStep = ref<'input-key' | 'preview' | 'totp'>('input-key')
const hotWalletPrivateKey = ref('')
const preview = ref<BatchWithdrawalPreview | null>(null)
const totpCode = ref('')
const batchResult = ref<BatchWithdrawalResult | null>(null)
const totpEnabled = ref(false)

const selectedPendingIds = computed(() =>
  selectedRows.value.filter((w) => w.status === 'PENDING').map((w) => w.id),
)

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

async function loadTotpStatus() {
  try {
    const status = await get2faStatus()
    totpEnabled.value = status.enabled
  } catch {
    // ignore
  }
}

function handleSelectionChange(rows: WithdrawalRequest[]) {
  selectedRows.value = rows
}

async function openBatchDialog() {
  if (selectedPendingIds.value.length === 0) {
    ElMessage.warning('请先勾选待处理的提现申请')
    return
  }
  if (!totpEnabled.value) {
    ElMessage.warning('请先在「两步验证」页面绑定 2FA')
    return
  }
  hotWalletPrivateKey.value = ''
  preview.value = null
  totpCode.value = ''
  batchResult.value = null
  batchStep.value = 'input-key'
  batchDialogVisible.value = true
}

async function doPreview() {
  if (!hotWalletPrivateKey.value.trim()) {
    ElMessage.warning('请输入热钱包私钥')
    return
  }
  operationLoading.value = true
  try {
    preview.value = await batchPreviewWithdrawals(selectedPendingIds.value, hotWalletPrivateKey.value.trim())
    batchStep.value = 'preview'
  } catch (error) {
    ElMessage.error(backendErrorMessage(error, '预览失败，请检查私钥'))
  } finally {
    operationLoading.value = false
  }
}

async function doBatchApprove() {
  if (totpCode.value.length !== 6) {
    ElMessage.warning('请输入 6 位 2FA 验证码')
    return
  }
  operationLoading.value = true
  try {
    batchResult.value = await batchApproveWithdrawals(
      selectedPendingIds.value,
      hotWalletPrivateKey.value.trim(),
      totpCode.value.trim(),
    )
    if (batchResult.value.succeeded > 0) {
      ElMessage.success(`成功打款 ${batchResult.value.succeeded} 笔`)
    }
    if (batchResult.value.stoppedAtIndex >= 0) {
      ElMessage.error(`第 ${batchResult.value.stoppedAtIndex + 1} 笔失败：${batchResult.value.errorMessage}`)
    }
    batchStep.value = 'totp'
    await loadWithdrawals()
    // Clear private key from memory immediately after use
    hotWalletPrivateKey.value = ''
  } catch (error) {
    ElMessage.error(backendErrorMessage(error, '批量打款失败'))
  } finally {
    operationLoading.value = false
  }
}

function closeBatchDialog() {
  batchDialogVisible.value = false
  // Clear sensitive data from memory
  hotWalletPrivateKey.value = ''
  totpCode.value = ''
}

// Manual txHash approval — requires 2FA (H1 fix: single approval also needs TOTP)
async function approve(row: WithdrawalRequest) {
  let txHash = ''
  let totpCode = ''
  try {
    const hashResult = await ElMessageBox.prompt('请输入 TRC 链上转账 tx hash（手动模式）', '通过提现申请', {
      confirmButtonText: '下一步',
      cancelButtonText: '取消',
      inputPattern: /\S{6,}/,
      inputErrorMessage: 'tx hash 不能为空',
    })
    txHash = hashResult.value.trim()
    const totpResult = await ElMessageBox.prompt('请输入 2FA 验证码', '安全验证', {
      confirmButtonText: '确认通过',
      cancelButtonText: '取消',
      inputPattern: /^\d{6}$/,
      inputErrorMessage: '请输入 6 位数字验证码',
    })
    totpCode = totpResult.value.trim()
  } catch {
    return
  }
  operationLoading.value = true
  try {
    await approveWithdrawal(row.id, txHash, 'manual TRC transfer confirmed', totpCode)
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

onMounted(() => {
  loadWithdrawals()
  loadTotpStatus()
})
</script>

<template>
  <section v-loading="loading" class="page-section">
    <div class="page-header">
      <div>
        <h1>提现申请</h1>
        <p>勾选待处理申请后批量自动打款（TRC20 USDT），需 2FA 确认。也可单笔手动录入 tx hash。</p>
      </div>
      <div style="display: flex; gap: 8px">
        <el-button
          type="primary"
          :disabled="selectedPendingIds.length === 0"
          @click="openBatchDialog"
        >
          批量打款 ({{ selectedPendingIds.length }})
        </el-button>
        <el-button @click="loadWithdrawals">刷新</el-button>
      </div>
    </div>

    <el-alert v-if="error" :title="error" show-icon type="error" />

    <div class="metric-grid">
      <div v-for="metric in metrics" :key="metric.label" class="metric">
        <div class="metric-label">{{ metric.label }}</div>
        <div class="metric-value">{{ metric.value }}</div>
      </div>
    </div>

    <el-table :data="withdrawals" border @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="45" :selectable="(row: WithdrawalRequest) => row.status === 'PENDING'" />
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
      <el-table-column align="right" label="积分" prop="pointAmount" width="100" />
      <el-table-column align="right" label="USDT" width="110">
        <template #default="{ row }">{{ row.usdtAmount }}</template>
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
      <el-table-column label="申请时间" min-width="220" prop="createdAt" />
      <el-table-column align="right" fixed="right" label="操作" width="150">
        <template #default="{ row }">
          <template v-if="row.status === 'PENDING'">
            <el-button :loading="operationLoading" type="primary" text @click="approve(row)">手动通过</el-button>
            <el-button :loading="operationLoading" type="danger" text @click="reject(row)">拒绝</el-button>
          </template>
          <span v-else class="muted">已处理</span>
        </template>
      </el-table-column>
    </el-table>

    <!-- Batch payout dialog -->
    <el-dialog v-model="batchDialogVisible" title="批量自动打款" width="600px" :close-on-click-modal="false">
      <!-- Step 1: input private key -->
      <div v-if="batchStep === 'input-key'">
        <el-alert type="warning" show-icon :closable="false" style="margin-bottom: 16px">
          私钥仅用于本次签名，不会存储在服务器。请确保在安全环境下操作。
        </el-alert>
        <p>已选 {{ selectedPendingIds.length }} 笔待处理提现申请。</p>
        <el-input
          v-model="hotWalletPrivateKey"
          type="password"
          placeholder="热钱包私钥（hex，不含 0x 前缀）"
          show-password
        />
        <div style="margin-top: 16px; text-align: right">
          <el-button @click="closeBatchDialog">取消</el-button>
          <el-button type="primary" :loading="operationLoading" @click="doPreview">下一步：预览</el-button>
        </div>
      </div>

      <!-- Step 2: preview balances + items -->
      <div v-if="batchStep === 'preview' && preview">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="热钱包地址">
            <span class="mono">{{ preview.hotWalletAddress }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="热钱包 USDT 余额">{{ preview.hotWalletUsdtBalance }}</el-descriptions-item>
          <el-descriptions-item label="热钱包 TRX 余额">{{ preview.hotWalletTrxBalance }}</el-descriptions-item>
          <el-descriptions-item label="本次提现总计 USDT">
            <strong>{{ preview.totalUsdt }}</strong>
          </el-descriptions-item>
          <el-descriptions-item label="提现笔数">{{ preview.itemCount }}</el-descriptions-item>
        </el-descriptions>
        <el-table :data="preview.items" border size="small" style="margin-top: 12px" max-height="200">
          <el-table-column label="USDT" prop="usdtAmount" width="100" />
          <el-table-column label="钱包地址" prop="walletAddress" min-width="240" />
          <el-table-column label="账号" prop="userAccount" min-width="140" />
        </el-table>
        <div style="margin-top: 16px; text-align: right">
          <el-button @click="batchStep = 'input-key'">上一步</el-button>
          <el-button type="primary" @click="batchStep = 'totp'">下一步：2FA 确认</el-button>
        </div>
      </div>

      <!-- Step 3: 2FA + result -->
      <div v-if="batchStep === 'totp'">
        <div v-if="!batchResult">
          <p>请输入 Google Authenticator 上的 6 位验证码以确认打款。</p>
          <el-input
            v-model="totpCode"
            placeholder="6 位 2FA 验证码"
            maxlength="6"
            style="max-width: 200px"
          />
          <div style="margin-top: 16px; text-align: right">
            <el-button @click="batchStep = 'preview'">上一步</el-button>
            <el-button type="danger" :loading="operationLoading" @click="doBatchApprove">确认打款</el-button>
          </div>
        </div>
        <div v-else>
          <el-alert
            :type="batchResult.succeeded > 0 && batchResult.stoppedAtIndex < 0 ? 'success' : 'error'"
            show-icon
            :closable="false"
            style="margin-bottom: 12px"
          >
            {{ batchResult.succeeded > 0 && batchResult.stoppedAtIndex < 0
              ? `全部 ${batchResult.succeeded} 笔打款成功`
              : `成功 ${batchResult.succeeded} 笔，第 ${batchResult.stoppedAtIndex + 1} 笔失败：${batchResult.errorMessage}` }}
          </el-alert>
          <el-table :data="batchResult.items" border size="small" max-height="250">
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="row.status === 'APPROVED' ? 'success' : 'danger'" effect="plain">{{ row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="tx hash" prop="txHash" min-width="200" />
            <el-table-column label="错误" prop="errorMessage" min-width="200" />
          </el-table>
          <div style="margin-top: 16px; text-align: right">
            <el-button type="primary" @click="closeBatchDialog">完成</el-button>
          </div>
        </div>
      </div>
    </el-dialog>
  </section>
</template>
