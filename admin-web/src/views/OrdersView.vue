<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { fetchOrders } from '../services/adminApi'
import type { RechargeOrder, RechargeOrderStatus } from '../services/adminApi'

const loading = ref(false)
const error = ref('')
const orders = ref<RechargeOrder[]>([])

const createdOrders = computed(() => orders.value.filter((order) => order.status === 'CREATED').length)
const totalAmountCents = computed(() =>
  orders.value.reduce((total, order) => total + order.amountCents, 0),
)
const totalPointAmount = computed(() =>
  orders.value.reduce((total, order) => total + order.pointAmount, 0),
)

const metrics = computed(() => [
  { label: '订单总数', value: orders.value.length },
  { label: '创建中订单', value: createdOrders.value },
  { label: '充值金额', value: formatMoney(totalAmountCents.value) },
  { label: '计划积分', value: totalPointAmount.value },
])

async function loadOrders() {
  loading.value = true
  error.value = ''
  try {
    orders.value = await fetchOrders()
  } catch {
    error.value = '订单列表加载失败'
  } finally {
    loading.value = false
  }
}

function formatMoney(amountCents: number) {
  return `¥${(amountCents / 100).toFixed(2)}`
}

function statusType(status: RechargeOrderStatus) {
  const types: Record<RechargeOrderStatus, 'info' | 'success' | 'warning' | 'danger'> = {
    CREATED: 'info',
    PAID: 'success',
    CANCELLED: 'warning',
    FAILED: 'danger',
    REFUNDED: 'warning',
  }
  return types[status]
}

onMounted(loadOrders)
</script>

<template>
  <section v-loading="loading" class="page-section">
    <div class="page-header">
      <div>
        <h1>订单管理</h1>
        <p>查看充值订单和商业化预留数据</p>
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
      <el-table-column label="订单号" min-width="250">
        <template #default="{ row }">
          <span class="mono">{{ row.orderNo }}</span>
        </template>
      </el-table-column>
      <el-table-column label="用户 ID" min-width="240">
        <template #default="{ row }">
          <span class="mono">{{ row.userId }}</span>
        </template>
      </el-table-column>
      <el-table-column align="right" label="金额" width="120">
        <template #default="{ row }">{{ formatMoney(row.amountCents) }}</template>
      </el-table-column>
      <el-table-column align="right" label="积分" prop="pointAmount" width="100" />
      <el-table-column label="状态" width="120">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)" effect="plain">{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="支付渠道" width="130">
        <template #default="{ row }">{{ row.paymentChannel || '未接入' }}</template>
      </el-table-column>
      <el-table-column label="创建时间" min-width="220" prop="createdAt" />
      <el-table-column label="更新时间" min-width="220" prop="updatedAt" />
    </el-table>
  </section>
</template>
