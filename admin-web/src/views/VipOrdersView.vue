<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, onMounted, ref } from 'vue'
import {
  GoldMedal,
  Tickets,
  Clock,
  Check,
  Close,
  Timer,
  Search,
  Refresh,
  CopyDocument,
  TopRight,
} from '@element-plus/icons-vue'
import { confirmVipOrder, fetchVipOrders, rejectVipOrder } from '../services/adminApi'
import type { VipOrder, VipOrderStatus } from '../services/adminApi'
import { backendErrorMessage } from '../services/http'

const loading = ref(false)
const operationLoading = ref(false)
const error = ref('')
const orders = ref<VipOrder[]>([])

// 搜索、过滤与分页
const searchQuery = ref('')
const statusFilter = ref<string>('ALL')
const currentPage = ref(1)
const pageSize = ref(15)

// 人工核销对账弹窗
const confirmDialogVisible = ref(false)
const confirmingOrder = ref<VipOrder | null>(null)
const inputTxHash = ref('')
const confirmError = ref('')

const isTxHashValid = computed(() => {
  return /^[0-9a-fA-F]{64}$/.test(inputTxHash.value.trim())
})

const pendingCount = computed(
  () => orders.value.filter((order) => order.status === 'PENDING').length,
)
const confirmedCount = computed(
  () => orders.value.filter((order) => order.status === 'CONFIRMED').length,
)
const rejectedCount = computed(
  () => orders.value.filter((order) => order.status === 'REJECTED').length,
)
const expiredCount = computed(
  () => orders.value.filter((order) => order.status === 'EXPIRED').length,
)

const totalConfirmedUsdt = computed(() => {
  const sum = orders.value
    .filter((o) => o.status === 'CONFIRMED')
    .reduce((acc, cur) => acc + parseFloat(cur.payableAmount || cur.usdtAmount || '0'), 0)
  return sum.toFixed(2)
})

// 过滤后的订单列表
const filteredOrders = computed(() => {
  let list = orders.value
  const q = searchQuery.value.trim().toLowerCase()
  if (q) {
    list = list.filter(
      (o) =>
        o.orderNo.toLowerCase().includes(q) ||
        o.userId.toLowerCase().includes(q) ||
        (o.txHash && o.txHash.toLowerCase().includes(q)) ||
        (o.receivingAddress && o.receivingAddress.toLowerCase().includes(q)),
    )
  }
  if (statusFilter.value !== 'ALL') {
    list = list.filter((o) => o.status === statusFilter.value)
  }
  return list
})

// 分页列表
const pagedOrders = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return filteredOrders.value.slice(start, start + pageSize.value)
})

function resetFilters() {
  searchQuery.value = ''
  statusFilter.value = 'ALL'
  currentPage.value = 1
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

function truncateHash(hash: string, lead = 8, tail = 8) {
  if (!hash) return '-'
  if (hash.length <= lead + tail) return hash
  return `${hash.substring(0, lead)}...${hash.substring(hash.length - tail)}`
}

async function copyText(text: string, label = '内容') {
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success(`${label}已复制到剪贴板`)
  } catch {
    ElMessage.warning('复制失败，请手动选中文本复制')
  }
}

async function loadOrders() {
  loading.value = true
  error.value = ''
  try {
    orders.value = await fetchVipOrders()
  } catch {
    error.value = 'VIP 订单加载失败'
  } finally {
    loading.value = false
  }
}

function openConfirmModal(row: VipOrder) {
  confirmingOrder.value = row
  inputTxHash.value = ''
  confirmError.value = ''
  confirmDialogVisible.value = true
}

function closeConfirmModal() {
  confirmDialogVisible.value = false
  confirmingOrder.value = null
  inputTxHash.value = ''
  confirmError.value = ''
}

async function submitConfirm() {
  if (!confirmingOrder.value || !isTxHashValid.value) return
  operationLoading.value = true
  confirmError.value = ''
  try {
    await confirmVipOrder(confirmingOrder.value.id, inputTxHash.value.trim())
    ElMessage.success('VIP 订单已确认，权益已即时为用户开通')
    closeConfirmModal()
    await loadOrders()
  } catch (err) {
    confirmError.value = backendErrorMessage(err, 'VIP 订单确认失败')
  } finally {
    operationLoading.value = false
  }
}

async function reject(row: VipOrder) {
  try {
    await ElMessageBox.confirm(
      `确定要拒绝订单号为 ${row.orderNo} 的 VIP 充值订单吗？此操作不可逆。`,
      '拒绝 VIP 订单',
      {
        confirmButtonText: '确定拒绝',
        cancelButtonText: '取消',
        type: 'warning',
      },
    )
  } catch {
    return
  }
  operationLoading.value = true
  try {
    await rejectVipOrder(row.id)
    ElMessage.success('VIP 订单已拒绝')
    await loadOrders()
  } catch (err) {
    ElMessage.error(backendErrorMessage(err, 'VIP 订单拒绝失败'))
  } finally {
    operationLoading.value = false
  }
}

function statusTagText(status: VipOrderStatus) {
  const map: Record<VipOrderStatus, string> = {
    PENDING: '待处理',
    CONFIRMED: '已确认',
    REJECTED: '已拒绝',
    EXPIRED: '已过期',
  }
  return map[status] || status
}

onMounted(loadOrders)
</script>

<template>
  <section v-loading="loading" class="page-section">
    <!-- 顶部标题栏 -->
    <div class="page-header">
      <div>
        <div class="title-row">
          <h1>VIP 订单</h1>
          <el-tag
            v-if="pendingCount > 0"
            effect="dark"
            type="warning"
            size="small"
            class="pending-pill"
          >
            <span class="pulse-dot"></span> {{ pendingCount }} 笔待对账
          </el-tag>
          <el-tag v-else effect="plain" type="success" size="small" class="pending-pill">
            订单处理完毕
          </el-tag>
        </div>
        <p class="subtitle">
          处理 VIP 订阅的 USDT 充值订单，核验链上转账流水并人工确认开通权益。
        </p>
      </div>
      <el-button type="primary" :icon="Refresh" @click="loadOrders">刷新列表</el-button>
    </div>

    <el-alert v-if="error" :title="error" show-icon type="error" />

    <!-- 顶部业务 KPI 统计卡片 (6 组卡片) -->
    <div class="vip-kpi-grid">
      <div class="kpi-card kpi-total">
        <div class="kpi-icon-box"><el-icon><Tickets /></el-icon></div>
        <div class="kpi-content">
          <div class="kpi-label">充值订单总数</div>
          <div class="kpi-num mono">{{ orders.length }}</div>
        </div>
      </div>

      <div class="kpi-card kpi-pending" :class="{ 'has-pending': pendingCount > 0 }">
        <div class="kpi-icon-box"><el-icon><Clock /></el-icon></div>
        <div class="kpi-content">
          <div class="kpi-label">待人工对账</div>
          <div class="kpi-num mono" :class="{ 'text-amber': pendingCount > 0 }">
            {{ pendingCount }}
          </div>
        </div>
      </div>

      <div class="kpi-card kpi-confirmed">
        <div class="kpi-icon-box"><el-icon><Check /></el-icon></div>
        <div class="kpi-content">
          <div class="kpi-label">已确认履约</div>
          <div class="kpi-num mono text-green">{{ confirmedCount }}</div>
        </div>
      </div>

      <div class="kpi-card kpi-volume">
        <div class="kpi-icon-box"><el-icon><GoldMedal /></el-icon></div>
        <div class="kpi-content">
          <div class="kpi-label">累计入账金额</div>
          <div class="kpi-num mono text-gold">{{ totalConfirmedUsdt }} <span class="kpi-unit">USDT</span></div>
        </div>
      </div>

      <div class="kpi-card kpi-rejected">
        <div class="kpi-icon-box"><el-icon><Close /></el-icon></div>
        <div class="kpi-content">
          <div class="kpi-label">已拒绝订单</div>
          <div class="kpi-num mono text-red">{{ rejectedCount }}</div>
        </div>
      </div>

      <div class="kpi-card kpi-expired">
        <div class="kpi-icon-box"><el-icon><Timer /></el-icon></div>
        <div class="kpi-content">
          <div class="kpi-label">超时已失效</div>
          <div class="kpi-num mono text-dim">{{ expiredCount }}</div>
        </div>
      </div>
    </div>

    <!-- 订单表格与过滤器 -->
    <el-card shadow="never" class="table-card">
      <div class="table-filter-bar">
        <el-input
          v-model="searchQuery"
          placeholder="搜索订单号 / 用户 ID / txHash / 收款地址..."
          :prefix-icon="Search"
          clearable
          class="search-input"
        />

        <div class="filter-group">
          <el-radio-group v-model="statusFilter" size="small">
            <el-radio-button value="ALL">全部 ({{ orders.length }})</el-radio-button>
            <el-radio-button value="PENDING">待对账 ({{ pendingCount }})</el-radio-button>
            <el-radio-button value="CONFIRMED">已确认 ({{ confirmedCount }})</el-radio-button>
            <el-radio-button value="REJECTED">已拒绝</el-radio-button>
            <el-radio-button value="EXPIRED">已过期</el-radio-button>
          </el-radio-group>
          <el-button link type="info" size="small" @click="resetFilters">重置</el-button>
        </div>
      </div>

      <!-- 订单表格 -->
      <el-table :data="pagedOrders" border stripe style="width: 100%" class="custom-table">
        <!-- 订单号 -->
        <el-table-column label="订单号" min-width="190">
          <template #default="{ row }">
            <div class="copyable-cell">
              <span class="mono order-no-text">{{ row.orderNo }}</span>
              <el-button link :icon="CopyDocument" size="small" @click="copyText(row.orderNo, '订单号')" />
            </div>
          </template>
        </el-table-column>

        <!-- 用户 ID -->
        <el-table-column label="用户 ID" min-width="170">
          <template #default="{ row }">
            <div class="copyable-cell">
              <span class="mono user-id-text" :title="row.userId">{{ row.userId.substring(0, 10) }}...</span>
              <el-button link :icon="CopyDocument" size="small" @click="copyText(row.userId, '用户 ID')" />
            </div>
          </template>
        </el-table-column>

        <!-- 应付金额 -->
        <el-table-column align="right" label="应付金额 (USDT)" width="140">
          <template #default="{ row }">
            <span class="mono amount-text font-bold">{{ row.payableAmount || row.usdtAmount }} USDT</span>
          </template>
        </el-table-column>

        <!-- 支付网络 -->
        <el-table-column label="支付渠道" width="130" align="center">
          <template #default="{ row }">
            <el-tag size="small" type="info" effect="plain">
              {{ row.paymentMethod || 'TRC20 USDT' }}
            </el-tag>
          </template>
        </el-table-column>

        <!-- 状态 -->
        <el-table-column label="订单状态" width="120" align="center">
          <template #default="{ row }">
            <span
              class="status-pill"
              :class="{
                'pill-pending': row.status === 'PENDING',
                'pill-confirmed': row.status === 'CONFIRMED',
                'pill-rejected': row.status === 'REJECTED',
                'pill-expired': row.status === 'EXPIRED',
              }"
            >
              <span class="status-dot"></span>
              {{ statusTagText(row.status) }}
            </span>
          </template>
        </el-table-column>

        <!-- 链上 tx hash -->
        <el-table-column label="链上 tx hash" min-width="210">
          <template #default="{ row }">
            <div v-if="row.txHash" class="tx-cell">
              <span class="mono tx-text" :title="row.txHash">{{ truncateHash(row.txHash, 8, 6) }}</span>
              <el-button link :icon="CopyDocument" size="small" @click="copyText(row.txHash, 'txHash')" />
              <a
                :href="`https://tronscan.org/#/transaction/${row.txHash}`"
                target="_blank"
                rel="noopener noreferrer"
                class="external-link"
                title="前往 TronScan 浏览器查看"
              >
                <el-icon><TopRight /></el-icon>
              </a>
            </div>
            <span v-else class="text-dim">未录入</span>
          </template>
        </el-table-column>

        <!-- 确认人 & 确认数 -->
        <el-table-column label="确认操作人" width="130">
          <template #default="{ row }">
            <span v-if="row.confirmedBy" class="mono text-actor">{{ row.confirmedBy }}</span>
            <span v-else class="text-dim">-</span>
          </template>
        </el-table-column>

        <el-table-column align="right" label="确认数" width="90" prop="confirmationCount" />

        <!-- 收款地址 -->
        <el-table-column label="收款地址" min-width="220">
          <template #default="{ row }">
            <div v-if="row.receivingAddress" class="copyable-cell">
              <span class="mono address-text" :title="row.receivingAddress">
                {{ truncateHash(row.receivingAddress, 6, 6) }}
              </span>
              <el-button link :icon="CopyDocument" size="small" @click="copyText(row.receivingAddress, '收款地址')" />
            </div>
            <span v-else class="text-dim">旧订单无快照</span>
          </template>
        </el-table-column>

        <!-- 创建时间 -->
        <el-table-column label="创建时间" min-width="170">
          <template #default="{ row }">
            <span class="mono time-text">{{ formatDateTime(row.createdAt) }}</span>
          </template>
        </el-table-column>

        <!-- 确认时间 -->
        <el-table-column label="核销时间" min-width="170">
          <template #default="{ row }">
            <span class="mono time-text">{{ formatDateTime(row.confirmedAt) }}</span>
          </template>
        </el-table-column>

        <!-- 操作 -->
        <el-table-column align="center" fixed="right" label="操作" width="160">
          <template #default="{ row }">
            <div v-if="row.status === 'PENDING'" class="operation-actions">
              <el-button
                :loading="operationLoading"
                type="primary"
                size="small"
                @click="openConfirmModal(row)"
              >
                确认到账
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
            </div>
            <span v-else class="text-dim">已办结</span>
          </template>
        </el-table-column>

        <template #empty>
          <el-empty description="暂无符合条件的 VIP 充值订单" />
        </template>
      </el-table>

      <!-- 底部分页 -->
      <div class="pagination-footer">
        <span class="page-count-text">
          展示 <b>{{ pagedOrders.length }}</b> / 共 <b>{{ filteredOrders.length }}</b> 笔订单
        </span>
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[10, 15, 30, 50]"
          layout="sizes, prev, pager, next"
          :total="filteredOrders.length"
          size="small"
        />
      </div>
    </el-card>

    <!-- 确认入账对账核销对话框 -->
    <el-dialog
      v-model="confirmDialogVisible"
      title="核对并确认 VIP 充值订单"
      width="540px"
      :close-on-click-modal="false"
      @close="closeConfirmModal"
    >
      <div v-if="confirmingOrder" class="confirm-dialog-content">
        <el-alert
          type="info"
          show-icon
          :closable="false"
          style="margin-bottom: 16px;"
        >
          请核对链上转账真实到账情况。录入 64 位 tx hash 并确认后，系统将自动为该用户开通/续期 VIP 会员权益。
        </el-alert>

        <div class="reconciliation-slip">
          <div class="slip-row">
            <span class="slip-label">充值订单号:</span>
            <span class="slip-val mono font-bold">{{ confirmingOrder.orderNo }}</span>
          </div>
          <div class="slip-row">
            <span class="slip-label">用户标识:</span>
            <span class="slip-val mono">{{ confirmingOrder.userId }}</span>
          </div>
          <div class="slip-row">
            <span class="slip-label">应付金额:</span>
            <span class="slip-val mono text-gold font-bold">
              {{ confirmingOrder.payableAmount || confirmingOrder.usdtAmount }} USDT
            </span>
          </div>
          <div class="slip-row" v-if="confirmingOrder.receivingAddress">
            <span class="slip-label">收款地址:</span>
            <div class="slip-address-wrap">
              <span class="mono slip-address">{{ confirmingOrder.receivingAddress }}</span>
              <a
                :href="`https://tronscan.org/#/address/${confirmingOrder.receivingAddress}`"
                target="_blank"
                rel="noopener noreferrer"
                class="external-link"
              >
                在 TronScan 查看 <el-icon><TopRight /></el-icon>
              </a>
            </div>
          </div>
        </div>

        <div class="tx-input-block">
          <label class="tx-label">
            链上交易哈希 (tx hash): <span class="req">*</span>
          </label>
          <el-input
            v-model="inputTxHash"
            placeholder="请输入 64 位十六进制交易哈希（如：a1b2c3d4...）"
            clearable
            maxlength="64"
            show-word-limit
            class="tx-hash-input"
          />
          <div v-if="inputTxHash && !isTxHashValid" class="tx-error-tip">
            必须是 64 位的合法十六进制字符（0-9, a-f）
          </div>
        </div>

        <el-alert
          v-if="confirmError"
          title="确认失败"
          :description="confirmError"
          type="error"
          show-icon
          :closable="false"
          style="margin-top: 14px;"
        />
      </div>

      <template #footer>
        <el-button @click="closeConfirmModal">取消</el-button>
        <el-button
          type="primary"
          :loading="operationLoading"
          :disabled="!isTxHashValid"
          @click="submitConfirm"
        >
          确认到账并开通权益
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

/* 顶部 KPI 卡片网格 (6列) */
.vip-kpi-grid {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 14px;
  margin-bottom: 18px;
}

@media (max-width: 1400px) {
  .vip-kpi-grid {
    grid-template-columns: repeat(3, 1fr);
  }
}

@media (max-width: 768px) {
  .vip-kpi-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

.kpi-card {
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  padding: 14px 16px;
  display: flex;
  align-items: center;
  gap: 12px;
  transition: all 0.2s ease-in-out;
}

.kpi-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 14px -2px rgba(0, 0, 0, 0.06);
}

.kpi-icon-box {
  width: 40px;
  height: 40px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  flex-shrink: 0;
}

.kpi-total .kpi-icon-box { background: #eff6ff; color: #2563eb; }
.kpi-pending .kpi-icon-box { background: #fffbeb; color: #d97706; }
.kpi-confirmed .kpi-icon-box { background: #f0fdf4; color: #16a34a; }
.kpi-volume .kpi-icon-box { background: #fef3c7; color: #b45309; }
.kpi-rejected .kpi-icon-box { background: #fef2f2; color: #dc2626; }
.kpi-expired .kpi-icon-box { background: #f1f5f9; color: #64748b; }

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
  font-size: 20px;
  font-weight: 700;
  color: #0f172a;
  margin-top: 2px;
}

.kpi-unit {
  font-size: 11px;
  color: #94a3b8;
  font-weight: normal;
}

.text-amber { color: #d97706; }
.text-green { color: #16a34a; }
.text-gold { color: #b45309; }
.text-red { color: #dc2626; }
.text-dim { color: #94a3b8; }

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
  max-width: 360px;
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
  gap: 6px;
}

.order-no-text {
  font-weight: 600;
  color: #1e293b;
  font-size: 13px;
}

.user-id-text {
  font-size: 12px;
  color: #64748b;
}

.amount-text {
  color: #b45309;
  font-size: 14px;
}

.tx-cell {
  display: flex;
  align-items: center;
  gap: 6px;
}

.tx-text {
  font-size: 12px;
  color: #2563eb;
}

.external-link {
  color: #94a3b8;
  display: inline-flex;
  align-items: center;
  transition: color 0.15s ease;
}

.external-link:hover {
  color: #2563eb;
}

.address-text {
  font-size: 12px;
  color: #64748b;
}

.text-actor {
  font-size: 12px;
  color: #334155;
  font-weight: 500;
}

.time-text {
  font-size: 12px;
  color: #64748b;
}

/* 状态药丸胶囊 */
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
.pill-pending .status-dot { background: #f59e0b; }

.pill-confirmed {
  background: #f0fdf4;
  color: #15803d;
  border: 1px solid #bbf7d0;
}
.pill-confirmed .status-dot { background: #22c55e; }

.pill-rejected {
  background: #fef2f2;
  color: #b91c1c;
  border: 1px solid #fecaca;
}
.pill-rejected .status-dot { background: #ef4444; }

.pill-expired {
  background: #f8fafc;
  color: #64748b;
  border: 1px solid #e2e8f0;
}
.pill-expired .status-dot { background: #94a3b8; }

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

/* 对账核销单对话框 */
.confirm-dialog-content {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.reconciliation-slip {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 14px 16px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.slip-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.slip-label {
  font-size: 13px;
  color: #64748b;
}

.slip-val {
  font-size: 14px;
  color: #0f172a;
}

.slip-address-wrap {
  display: flex;
  align-items: center;
  gap: 8px;
}

.slip-address {
  font-size: 12px;
  color: #2563eb;
  word-break: break-all;
}

.tx-input-block {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-top: 4px;
}

.tx-label {
  font-size: 13px;
  font-weight: 600;
  color: #1e293b;
}

.tx-label .req {
  color: #ef4444;
}

.tx-hash-input {
  font-family: 'SFMono-Regular', Consolas, monospace;
}

.tx-error-tip {
  font-size: 12px;
  color: #ef4444;
}
</style>
