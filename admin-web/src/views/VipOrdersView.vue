<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, onMounted, ref } from 'vue'
import { confirmVipOrder, fetchVipOrders, rejectVipOrder } from '../services/adminApi'
import type { VipOrder, VipOrderStatus } from '../services/adminApi'
import { backendErrorMessage } from '../services/http'

const loading = ref(false)
const operationLoading = ref(false)
const error = ref('')
const orders = ref<VipOrder[]>([])

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

const metrics = computed(() => [
  { label: '订单总数', value: orders.value.length },
  { label: '待处理', value: pendingCount.value },
  { label: '已确认', value: confirmedCount.value },
  { label: '已拒绝', value: rejectedCount.value },
  { label: '已过期', value: expiredCount.value },
])

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

async function confirm(row: VipOrder) {
  let txHash = ''
  let totpCode = ''
  try {
    const result = await ElMessageBox.prompt('请输入链上转账 tx hash', '确认 VIP 订单', {
      confirmButtonText: '确认',
      cancelButtonText: '取消',
      inputPattern: /^[0-9a-fA-F]{64}$/,
      inputErrorMessage: '请输入 64 位十六进制 tx hash',
    })
    txHash = result.value.trim()
    const totpResult = await ElMessageBox.prompt('请输入管理员 6 位动态验证码', '二次验证', {
      confirmButtonText: '验证并确认',
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
    await confirmVipOrder(row.id, txHash, totpCode)
    ElMessage.success('VIP 订单已确认')
    await loadOrders()
  } catch (error) {
    ElMessage.error(backendErrorMessage(error, 'VIP 订单确认失败'))
  } finally {
    operationLoading.value = false
  }
}

async function reject(row: VipOrder) {
  try {
    await ElMessageBox.confirm('确定要拒绝该 VIP 订单吗？', '拒绝 VIP 订单', {
      confirmButtonText: '拒绝',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return
  }
  operationLoading.value = true
  try {
    await rejectVipOrder(row.id)
    ElMessage.success('VIP 订单已拒绝')
    await loadOrders()
  } catch (error) {
    ElMessage.error(backendErrorMessage(error, 'VIP 订单拒绝失败'))
  } finally {
    operationLoading.value = false
  }
}

function statusType(status: VipOrderStatus) {
  const types: Record<VipOrderStatus, 'info' | 'success' | 'warning' | 'danger'> = {
    PENDING: 'warning',
    CONFIRMED: 'success',
    REJECTED: 'danger',
    EXPIRED: 'info',
  }
  return types[status]
}

onMounted(loadOrders)
</script>

<template>
  <section v-loading="loading" class="page-section">
    <div class="page-header">
      <div>
        <h1>VIP 订单</h1>
        <p>处理 VIP 订阅的 USDT 充值订单，确认后录入 tx hash。</p>
      </div>
      <el-button @click="loadOrders">刷新</el-button>
    </div>

    <el-alert v-if="error" :title="error" show-icon type="error" />

    <div class="metric-grid">
      <div v-for="metric in metrics" :key="metric.label" class="metric">
        <div class="metric-label">{{ metric.label }}</div>
        <div class="metric-value">{{ metric.value }}</div>
      </div>
    </div>

    <el-table :data="orders" border>
      <el-table-column label="订单号" min-width="220">
        <template #default="{ row }">
          <span class="mono">{{ row.orderNo }}</span>
        </template>
      </el-table-column>
      <el-table-column label="用户 ID" min-width="220">
        <template #default="{ row }">
          <span class="mono">{{ row.userId }}</span>
        </template>
      </el-table-column>
      <el-table-column align="right" label="应付 USDT" width="130">
        <template #default="{ row }">{{ row.payableAmount }}</template>
      </el-table-column>
      <el-table-column label="支付方式" min-width="140">
        <template #default="{ row }">{{ row.paymentMethod || '-' }}</template>
      </el-table-column>
      <el-table-column label="状态" width="120">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)" effect="plain">{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="tx hash" min-width="200">
        <template #default="{ row }">
          <span v-if="row.txHash" class="mono">{{ row.txHash }}</span>
          <span v-else class="muted">未录入</span>
        </template>
      </el-table-column>
      <el-table-column label="确认人" width="140">
        <template #default="{ row }">{{ row.confirmedBy || '-' }}</template>
      </el-table-column>
      <el-table-column align="right" label="确认数" width="90" prop="confirmationCount" />
      <el-table-column label="收款地址" min-width="220">
        <template #default="{ row }">
          <span v-if="row.receivingAddress" class="mono">{{ row.receivingAddress }}</span>
          <span v-else class="muted">旧订单无快照</span>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" min-width="220" prop="createdAt" />
      <el-table-column label="确认时间" min-width="220">
        <template #default="{ row }">{{ row.confirmedAt || '-' }}</template>
      </el-table-column>
      <el-table-column align="right" fixed="right" label="操作" width="150">
        <template #default="{ row }">
          <template v-if="row.status === 'PENDING'">
            <el-button :loading="operationLoading" type="primary" text @click="confirm(row)">确认</el-button>
            <el-button :loading="operationLoading" type="danger" text @click="reject(row)">拒绝</el-button>
          </template>
          <span v-else class="muted">已处理</span>
        </template>
      </el-table-column>
    </el-table>
  </section>
</template>
