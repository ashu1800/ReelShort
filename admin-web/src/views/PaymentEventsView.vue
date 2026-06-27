<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { fetchPaymentEvents } from '../services/adminApi'
import type { PaymentEvent, PaymentEventStatus } from '../services/adminApi'

const loading = ref(false)
const error = ref('')
const events = ref<PaymentEvent[]>([])
const filters = reactive<{
  status: PaymentEventStatus | ''
  orderNo: string
  paymentChannel: string
}>({
  status: '',
  orderNo: '',
  paymentChannel: '',
})

const processedCount = computed(() => events.value.filter((event) => event.status === 'PROCESSED').length)
const rejectedCount = computed(() => events.value.filter((event) => event.status === 'REJECTED').length)
const totalAmountCents = computed(() =>
  events.value
    .filter((event) => event.status === 'PROCESSED')
    .reduce((total, event) => total + event.amountCents, 0),
)

const metrics = computed(() => [
  { label: '事件总数', value: events.value.length },
  { label: '已处理', value: processedCount.value },
  { label: '已拒绝', value: rejectedCount.value },
  { label: '成功金额', value: formatMoney(totalAmountCents.value) },
])

async function loadEvents() {
  loading.value = true
  error.value = ''
  try {
    events.value = await fetchPaymentEvents({
      status: filters.status || undefined,
      orderNo: filters.orderNo.trim() || undefined,
      paymentChannel: filters.paymentChannel.trim() || undefined,
    })
  } catch {
    error.value = '支付事件加载失败'
  } finally {
    loading.value = false
  }
}

function resetFilters() {
  filters.status = ''
  filters.orderNo = ''
  filters.paymentChannel = ''
  loadEvents()
}

function formatMoney(amountCents: number) {
  return `¥${(amountCents / 100).toFixed(2)}`
}

function statusType(status: PaymentEventStatus) {
  const types: Record<PaymentEventStatus, 'success' | 'danger'> = {
    PROCESSED: 'success',
    REJECTED: 'danger',
  }
  return types[status]
}

onMounted(loadEvents)
</script>

<template>
  <section v-loading="loading" class="page-section">
    <div class="page-header">
      <div>
        <h1>支付事件</h1>
        <p>查看内部模拟支付回调和业务拒绝记录</p>
      </div>
      <el-button @click="loadEvents">刷新</el-button>
    </div>
    <el-alert v-if="error" :title="error" show-icon type="error" />
    <div class="toolbar-row">
      <el-select v-model="filters.status" class="filter-control" clearable placeholder="状态">
        <el-option label="已处理" value="PROCESSED" />
        <el-option label="已拒绝" value="REJECTED" />
      </el-select>
      <el-input v-model="filters.orderNo" class="filter-control wide" clearable placeholder="订单号" />
      <el-input v-model="filters.paymentChannel" class="filter-control" clearable placeholder="支付渠道" />
      <el-button type="primary" @click="loadEvents">查询</el-button>
      <el-button @click="resetFilters">重置</el-button>
    </div>
    <div class="metric-grid">
      <div v-for="metric in metrics" :key="metric.label" class="metric">
        <div class="metric-label">{{ metric.label }}</div>
        <div class="metric-value">{{ metric.value }}</div>
      </div>
    </div>
    <el-table :data="events" border>
      <el-table-column label="事件 ID" min-width="220">
        <template #default="{ row }">
          <span class="mono">{{ row.providerEventId }}</span>
        </template>
      </el-table-column>
      <el-table-column label="订单号" min-width="250">
        <template #default="{ row }">
          <span class="mono">{{ row.orderNo }}</span>
        </template>
      </el-table-column>
      <el-table-column label="渠道" prop="paymentChannel" width="130" />
      <el-table-column align="right" label="金额" width="120">
        <template #default="{ row }">{{ formatMoney(row.amountCents) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="120">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)" effect="plain">{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="失败原因" min-width="220">
        <template #default="{ row }">{{ row.failureReason || '-' }}</template>
      </el-table-column>
      <el-table-column label="处理时间" min-width="220" prop="processedAt" />
    </el-table>
  </section>
</template>
