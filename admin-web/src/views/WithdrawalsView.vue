<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import {
  approveWithdrawal,
  batchApproveWithdrawals,
  batchPreviewWithdrawals,
  fetchWithdrawals,
  get2faStatus,
  rejectWithdrawal,
} from '../services/adminApi'
import type { BatchWithdrawalPreview, BatchWithdrawalResult, WithdrawalRequest, WithdrawalStatus } from '../services/adminApi'
import { backendErrorMessage, isRequestTimeout } from '../services/http'
import { buildSinglePayoutResult } from '../services/payoutOutcome.js'
import { clearWithdrawalSecrets } from '../services/withdrawalSecrets.js'

const loading = ref(false)
const operationLoading = ref(false)
const error = ref('')
const withdrawals = ref<WithdrawalRequest[]>([])
const selectedRows = ref<WithdrawalRequest[]>([])
const activePayoutIds = ref<string[]>([])

const batchDialogVisible = ref(false)
const batchStep = ref<'preview' | 'credentials' | 'result'>('preview')
const credentials = reactive({ tronPrivateKey: '', ethPrivateKey: '', totpCode: '' })
const preview = ref<BatchWithdrawalPreview | null>(null)
const batchResult = ref<BatchWithdrawalResult | null>(null)
const totpEnabled = ref(false)

const selectedPendingIds = computed(() =>
  selectedRows.value.filter((w) => w.status === 'PENDING').map((w) => w.id),
)
const needsTronKey = computed(() => preview.value?.items.some((item) => item.network === 'TRC20') ?? false)
const needsEthKey = computed(() => preview.value?.items.some((item) => item.network === 'ERC20') ?? false)
const payoutDialogTitle = computed(() =>
  activePayoutIds.value.length === 1 ? '执行提现打款' : `批量执行 ${activePayoutIds.value.length} 笔提现`,
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

function clearSecrets() {
  clearWithdrawalSecrets(credentials)
}

async function openPayoutDialog(row?: WithdrawalRequest) {
  activePayoutIds.value = row ? [row.id] : [...selectedPendingIds.value]
  if (activePayoutIds.value.length === 0) {
    ElMessage.warning('请先勾选待处理的提现申请')
    return
  }
  if (!totpEnabled.value) {
    ElMessage.warning('请先在「两步验证」页面绑定 2FA')
    return
  }
  clearSecrets()
  preview.value = null
  batchResult.value = null
  batchStep.value = 'preview'
  batchDialogVisible.value = true
  await doPreview()
}

async function doPreview() {
  operationLoading.value = true
  try {
    preview.value = await batchPreviewWithdrawals(activePayoutIds.value)
    batchStep.value = 'preview'
  } catch (error) {
    ElMessage.error(backendErrorMessage(error, '提现预览失败'))
    batchDialogVisible.value = false
  } finally {
    operationLoading.value = false
  }
}

async function doPayout() {
  if (needsTronKey.value && !credentials.tronPrivateKey.trim()) {
    ElMessage.warning('请输入 TRC20 热钱包私钥')
    return
  }
  if (needsEthKey.value && !credentials.ethPrivateKey.trim()) {
    ElMessage.warning('请输入 ERC20 热钱包私钥')
    return
  }
  if (!/^\d{6}$/.test(credentials.totpCode)) {
    ElMessage.warning('请输入 6 位 2FA 验证码')
    return
  }
  operationLoading.value = true
  try {
    const tronKey = credentials.tronPrivateKey.trim() || undefined
    const ethKey = credentials.ethPrivateKey.trim() || undefined
    if (activePayoutIds.value.length === 1) {
      const withdrawal = await approveWithdrawal(
        activePayoutIds.value[0],
        tronKey,
        ethKey,
        credentials.totpCode,
      )
      batchResult.value = buildSinglePayoutResult(withdrawal)
    } else {
      batchResult.value = await batchApproveWithdrawals(
        activePayoutIds.value,
        tronKey,
        ethKey,
        credentials.totpCode,
      )
    }
    if (batchResult.value.succeeded > 0) {
      ElMessage.success(`已提交 ${batchResult.value.succeeded} 笔打款`)
    }
    if (batchResult.value.items.some((item) => item.manualReview)) {
      ElMessage.warning('打款状态需要人工核对，请勿重复生成交易')
    } else if (batchResult.value.pending > 0) {
      ElMessage.warning(`${batchResult.value.pending} 笔已签名，等待广播确认，请刷新列表核对状态`)
    } else if (batchResult.value.failed > 0) {
      ElMessage.error(`${batchResult.value.failed} 笔提交失败，请查看逐笔结果`)
    }
    batchStep.value = 'result'
    await loadWithdrawals()
  } catch (error) {
    if (isRequestTimeout(error)) {
      await loadWithdrawals()
      batchDialogVisible.value = false
      ElMessage.warning('请求超时，后台可能仍在处理，请刷新列表核对打款状态')
    } else {
      ElMessage.error(backendErrorMessage(error, '打款提交失败'))
    }
  } finally {
    operationLoading.value = false
    clearSecrets()
  }
}

function closeBatchDialog() {
  batchDialogVisible.value = false
  clearSecrets()
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

function resultAlertType(result: BatchWithdrawalResult) {
  if (result.items.some((item) => item.manualReview)) return 'warning'
  if (result.failed > 0) return 'error'
  if (result.pending > 0) return 'warning'
  return 'success'
}

function resultSummary(result: BatchWithdrawalResult) {
  const summary = `已提交 ${result.succeeded} 笔，待广播 ${result.pending} 笔，失败 ${result.failed} 笔`
  return result.items.some((item) => item.manualReview)
    ? `${summary}；含需人工核对项，请勿重复生成交易`
    : summary
}

onMounted(() => {
  loadWithdrawals()
  loadTotpStatus()
})

onUnmounted(clearSecrets)
onBeforeRouteLeave(() => clearSecrets())
</script>

<template>
  <section v-loading="loading" class="page-section">
    <div class="page-header">
      <div>
        <h1>提现申请</h1>
        <p>预览收款地址和金额后，由本次请求提交对应链私钥与 2FA，链上确认进度会持续回写。</p>
      </div>
      <div style="display: flex; gap: 8px">
        <el-button
          type="primary"
          :disabled="selectedPendingIds.length === 0"
          @click="openPayoutDialog()"
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
      <el-table-column label="打款状态" width="150">
        <template #default="{ row }">
          <el-tag v-if="row.payoutStatus" :type="row.manualReview ? 'danger' : 'info'" effect="plain">
            {{ row.payoutStatus }}
          </el-tag>
          <span v-else class="muted">尚未执行</span>
        </template>
      </el-table-column>
      <el-table-column align="right" label="确认数" width="90">
        <template #default="{ row }">{{ row.confirmationCount }}</template>
      </el-table-column>
      <el-table-column label="链上 tx hash" min-width="210">
        <template #default="{ row }">
          <span v-if="row.payoutTxHash || row.txHash" class="mono">{{ row.payoutTxHash || row.txHash }}</span>
          <span v-else class="muted">尚未生成</span>
        </template>
      </el-table-column>
      <el-table-column label="失败 / 人工核对" min-width="190">
        <template #default="{ row }">
          <span v-if="row.manualReview" class="muted">需要人工核对</span>
          <span v-else-if="row.failureReason">{{ row.failureReason }}</span>
          <span v-else class="muted">-</span>
        </template>
      </el-table-column>
      <el-table-column label="申请时间" min-width="220" prop="createdAt" />
      <el-table-column align="right" fixed="right" label="操作" width="150">
        <template #default="{ row }">
          <template v-if="row.status === 'PENDING'">
            <el-button :loading="operationLoading" type="primary" text @click="openPayoutDialog(row)">执行打款</el-button>
            <el-button :loading="operationLoading" type="danger" text @click="reject(row)">拒绝</el-button>
          </template>
          <span v-else class="muted">已处理</span>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog
      v-model="batchDialogVisible"
      v-loading="operationLoading"
      :title="payoutDialogTitle"
      width="640px"
      :close-on-click-modal="false"
      @close="closeBatchDialog"
    >
      <div v-if="batchStep === 'preview' && preview">
        <el-descriptions :column="1" border>
          <el-descriptions-item v-if="preview.tronHotWalletAddress" label="TRC20 热钱包地址">
            <span class="mono">{{ preview.tronHotWalletAddress }}</span>
          </el-descriptions-item>
          <el-descriptions-item v-if="preview.ethHotWalletAddress" label="ERC20 热钱包地址">
            <span class="mono">{{ preview.ethHotWalletAddress }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="本次提现总计 USDT">
            <strong>{{ preview.totalUsdt }}</strong>
          </el-descriptions-item>
          <el-descriptions-item label="提现笔数">{{ preview.itemCount }}</el-descriptions-item>
        </el-descriptions>
        <el-table :data="preview.items" border size="small" style="margin-top: 12px" max-height="200">
          <el-table-column label="链" prop="network" width="70" />
          <el-table-column label="USDT" prop="usdtAmount" width="100" />
          <el-table-column label="钱包地址" prop="walletAddress" min-width="240" />
          <el-table-column label="账号" prop="userAccount" min-width="140" />
          <el-table-column label="状态" prop="status" width="100" />
        </el-table>
        <div style="margin-top: 16px; text-align: right">
          <el-button @click="closeBatchDialog">取消</el-button>
          <el-button type="primary" @click="batchStep = 'credentials'">下一步：安全确认</el-button>
        </div>
      </div>

      <div v-if="batchStep === 'credentials'">
        <el-alert type="warning" show-icon :closable="false" style="margin-bottom: 16px">
          私钥只随本次执行请求发送，不用于预览，不会出现在响应或审计日志中。
        </el-alert>
        <div v-if="needsTronKey" style="margin-bottom: 12px">
          <div style="margin-bottom: 4px; font-weight: 600">TRC20 热钱包私钥</div>
          <el-input
            v-model="credentials.tronPrivateKey"
            type="password"
            maxlength="66"
            placeholder="Tron 私钥（hex，不含 0x 前缀）"
            show-password
            autocomplete="off"
          />
        </div>
        <div v-if="needsEthKey" style="margin-bottom: 12px">
          <div style="margin-bottom: 4px; font-weight: 600">ERC20 热钱包私钥</div>
          <el-input
            v-model="credentials.ethPrivateKey"
            type="password"
            maxlength="66"
            placeholder="Ethereum 私钥（hex，不含 0x 前缀）"
            show-password
            autocomplete="off"
          />
        </div>
        <div style="margin-bottom: 12px">
          <div style="margin-bottom: 4px; font-weight: 600">2FA 验证码</div>
          <el-input
            v-model="credentials.totpCode"
            placeholder="6 位 2FA 验证码"
            maxlength="6"
            inputmode="numeric"
            autocomplete="one-time-code"
            style="max-width: 200px"
          />
        </div>
        <div style="margin-top: 16px; text-align: right">
          <el-button @click="batchStep = 'preview'">上一步</el-button>
          <el-button type="danger" :loading="operationLoading" @click="doPayout">确认并签名打款</el-button>
        </div>
      </div>

      <div v-if="batchStep === 'result' && batchResult">
          <el-alert
            :type="resultAlertType(batchResult)"
            show-icon
            :closable="false"
            style="margin-bottom: 12px"
          >
            {{ resultSummary(batchResult) }}
          </el-alert>
          <el-table :data="batchResult.items" border size="small" max-height="250">
            <el-table-column label="打款状态" width="150">
              <template #default="{ row }">
                <el-tag :type="row.manualReview || row.payoutStatus === 'FAILED' ? 'danger' : 'info'" effect="plain">
                  {{ row.payoutStatus }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="确认数" prop="confirmationCount" width="80" />
            <el-table-column label="tx hash" prop="txHash" min-width="200" />
            <el-table-column label="失败 / 人工核对" min-width="220">
              <template #default="{ row }">
                {{ row.manualReview ? '需要人工核对' : row.failureReason || row.errorMessage || '-' }}
              </template>
            </el-table-column>
          </el-table>
          <div style="margin-top: 16px; text-align: right">
            <el-button type="primary" @click="closeBatchDialog">完成</el-button>
          </div>
      </div>
    </el-dialog>
  </section>
</template>
