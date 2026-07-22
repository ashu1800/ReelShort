<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { onMounted, reactive, ref, watch } from 'vue'
import {
  adjustUserPoints,
  cancelUserVip,
  fetchUserDetail,
  fetchUserPointRecords,
  fetchUserWithdrawals,
  fetchUsers,
  fetchUserWatchRecords,
  setUserVip,
  updateUserStatus,
} from '../services/adminApi'
import type {
  AdminUserDetail,
  AdminUserSummary,
  PointRecord,
  UserStatus,
  WatchRecord,
  WithdrawalRequest,
  WithdrawalStatus,
} from '../services/adminApi'

const loading = ref(false)
const error = ref('')
const users = ref<AdminUserSummary[]>([])
const detailOpen = ref(false)
const detailLoading = ref(false)
const operationLoading = ref(false)
const selectedUser = ref<AdminUserDetail | null>(null)
const watchRecords = ref<WatchRecord[]>([])
const pointRecords = ref<PointRecord[]>([])
const withdrawals = ref<WithdrawalRequest[]>([])
const pointsForm = reactive({
  amount: 0,
  reason: '',
  idempotencyKey: '',
})
const vipForm = reactive<{ vipUntil: Date | null }>({
  vipUntil: null,
})

watch(
  () => [pointsForm.amount, pointsForm.reason],
  () => {
    pointsForm.idempotencyKey = ''
  },
)

const statusOptions: UserStatus[] = ['ACTIVE', 'DISABLED', 'BLACKLISTED']

async function loadUsers() {
  loading.value = true
  error.value = ''
  try {
    users.value = await fetchUsers()
  } catch {
    error.value = '用户列表加载失败'
  } finally {
    loading.value = false
  }
}

async function openDetail(user: AdminUserSummary) {
  detailOpen.value = true
  await loadUserDetail(user.id)
}

async function loadUserDetail(userId: string) {
  detailLoading.value = true
  selectedUser.value = null
  watchRecords.value = []
  pointRecords.value = []
  withdrawals.value = []
  try {
    const [detail, watches, points, userWithdrawals] = await Promise.all([
      fetchUserDetail(userId),
      fetchUserWatchRecords(userId),
      fetchUserPointRecords(userId),
      fetchUserWithdrawals(userId),
    ])
    selectedUser.value = detail
    watchRecords.value = watches
    pointRecords.value = points
    withdrawals.value = userWithdrawals
  } catch {
    ElMessage.error('用户详情加载失败')
  } finally {
    detailLoading.value = false
  }
}

async function changeSelectedUserStatus(status: UserStatus) {
  if (!selectedUser.value) return
  if (status === selectedUser.value.status) return
  try {
    await ElMessageBox.confirm(`确认将用户 ${selectedUser.value.username} 状态改为 ${status}？`, '变更用户状态', {
      confirmButtonText: '确认',
      cancelButtonText: '取消',
      type: status === 'ACTIVE' ? 'info' : 'warning',
    })
  } catch {
    return
  }
  operationLoading.value = true
  try {
    selectedUser.value = await updateUserStatus(selectedUser.value.id, status)
    await loadUsers()
    ElMessage.success('用户状态已更新')
  } catch {
    ElMessage.error('用户状态更新失败')
  } finally {
    operationLoading.value = false
  }
}

function formatShanghaiDateTime(value: string | null) {
  if (!value) return '未开通'
  return new Intl.DateTimeFormat('zh-CN', {
    timeZone: 'Asia/Shanghai',
    dateStyle: 'medium',
    timeStyle: 'short',
    hour12: false,
  }).format(new Date(value))
}

async function submitVip() {
  if (!selectedUser.value || !vipForm.vipUntil || vipForm.vipUntil <= new Date()) {
    ElMessage.warning('请选择未来的 VIP 到期时间')
    return
  }
  const userId = selectedUser.value.id
  try {
    await ElMessageBox.confirm(
      `确认将 ${selectedUser.value.username} 的 VIP 设置至 ${formatShanghaiDateTime(vipForm.vipUntil.toISOString())}？`,
      '设置 VIP',
      { confirmButtonText: '确认设置', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }
  operationLoading.value = true
  try {
    selectedUser.value = await setUserVip(userId, vipForm.vipUntil.toISOString())
    vipForm.vipUntil = null
    ElMessage.success('VIP 已设置')
    await loadUsers()
  } catch {
    ElMessage.error('VIP 设置失败')
  } finally {
    operationLoading.value = false
  }
}

async function cancelVip() {
  if (!selectedUser.value || !selectedUser.value.vipUntil) return
  const userId = selectedUser.value.id
  try {
    await ElMessageBox.confirm(
      `确认取消 ${selectedUser.value.username} 当前的 VIP 权益？此操作不会修改任何 VIP 订单。`,
      '取消 VIP',
      { confirmButtonText: '确认取消', cancelButtonText: '返回', type: 'warning' },
    )
  } catch {
    return
  }
  operationLoading.value = true
  try {
    selectedUser.value = await cancelUserVip(userId)
    ElMessage.success('VIP 已取消')
    await loadUsers()
  } catch {
    ElMessage.error('VIP 取消失败')
  } finally {
    operationLoading.value = false
  }
}

async function submitPointAdjustment() {
  if (!selectedUser.value) return
  const userId = selectedUser.value.id
  const reason = pointsForm.reason.trim()
  if (!pointsForm.amount || !reason) {
    ElMessage.warning('请输入非 0 积分金额和调整原因')
    return
  }
  let adjusted = false
  if (!pointsForm.idempotencyKey) {
    pointsForm.idempotencyKey = crypto.randomUUID()
  }
  operationLoading.value = true
  try {
    selectedUser.value = await adjustUserPoints(userId, pointsForm.amount, reason, pointsForm.idempotencyKey)
    pointsForm.amount = 0
    pointsForm.reason = ''
    pointsForm.idempotencyKey = ''
    adjusted = true
    ElMessage.success('积分调整已提交')
  } catch {
    ElMessage.error('积分调整失败')
  } finally {
    operationLoading.value = false
  }
  if (adjusted) {
    await Promise.all([loadUsers(), loadUserDetail(userId)])
  }
}

function statusTagType(status: UserStatus) {
  const types: Record<UserStatus, 'success' | 'warning' | 'danger'> = {
    ACTIVE: 'success',
    DISABLED: 'warning',
    BLACKLISTED: 'danger',
  }
  return types[status]
}

function withdrawalStatusType(status: WithdrawalStatus) {
  const types: Record<WithdrawalStatus, 'success' | 'warning' | 'danger'> = {
    PENDING: 'warning',
    APPROVED: 'success',
    REJECTED: 'danger',
  }
  return types[status]
}

onMounted(loadUsers)
</script>

<template>
  <section class="page-section">
    <div class="page-header">
      <div>
        <h1>用户管理</h1>
        <p>查看用户状态、积分余额和创建时间</p>
      </div>
      <el-button @click="loadUsers">刷新</el-button>
    </div>
    <el-alert v-if="error" :title="error" show-icon type="error" />
    <el-table v-loading="loading" :data="users" border>
      <el-table-column label="账号" min-width="190">
        <template #default="{ row }">
          <div class="mono">{{ row.phoneE164 || row.username }}</div>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="120">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)" effect="plain">
            {{ row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="VIP" min-width="180">
        <template #default="{ row }">
          <el-tag :type="row.vip ? 'warning' : 'info'" effect="plain">
            {{ row.vip ? 'VIP' : '非 VIP' }}
          </el-tag>
          <div v-if="row.vipUntil" class="muted">{{ formatShanghaiDateTime(row.vipUntil) }}</div>
        </template>
      </el-table-column>
      <el-table-column align="right" label="总积分" prop="pointBalance" width="110" />
      <el-table-column align="right" label="冻结" prop="frozenPoints" width="100" />
      <el-table-column align="right" label="可用" prop="availablePoints" width="100" />
      <el-table-column label="创建时间" min-width="220" prop="createdAt" />
      <el-table-column align="right" label="操作" width="120">
        <template #default="{ row }">
          <el-button type="primary" text @click="openDetail(row)">详情</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-drawer v-model="detailOpen" size="720px" title="用户详情">
      <div v-loading="detailLoading" class="drawer-body">
        <el-empty v-if="!selectedUser && !detailLoading" description="未选择用户" />
        <template v-if="selectedUser">
          <div class="detail-summary">
            <div>
              <div class="muted">手机号账号</div>
              <strong>{{ selectedUser.phoneE164 || selectedUser.username }}</strong>
            </div>
            <div>
              <div class="muted">状态</div>
              <el-tag :type="statusTagType(selectedUser.status)" effect="plain">
                {{ selectedUser.status }}
              </el-tag>
            </div>
            <div>
              <div class="muted">VIP 权益</div>
              <el-tag :type="selectedUser.vip ? 'warning' : 'info'" effect="plain">
                {{ selectedUser.vip ? 'VIP' : '非 VIP' }}
              </el-tag>
              <div class="muted">{{ formatShanghaiDateTime(selectedUser.vipUntil) }}</div>
            </div>
            <div>
              <div class="muted">总积分</div>
              <strong>{{ selectedUser.pointBalance }}</strong>
            </div>
            <div>
              <div class="muted">冻结 / 可用</div>
              <strong>{{ selectedUser.frozenPoints }} / {{ selectedUser.availablePoints }}</strong>
            </div>
            <div>
              <div class="muted">冷钱包</div>
              <span v-if="selectedUser.walletAddress" class="mono">
                {{ selectedUser.walletNetwork }} · {{ selectedUser.walletAddress }}
              </span>
              <span v-else>未绑定</span>
            </div>
            <div>
              <div class="muted">创建时间</div>
              <span>{{ selectedUser.createdAt }}</span>
            </div>
          </div>
          <el-tabs>
            <el-tab-pane label="运营操作">
              <section class="operation-block">
                <div>
                  <h2>VIP 权益</h2>
                  <p>设置未来到期时间或取消当前权益，不会修改任何 VIP 订单。</p>
                </div>
                <el-form class="inline-form" @submit.prevent="submitVip">
                  <el-date-picker
                    v-model="vipForm.vipUntil"
                    type="datetime"
                    placeholder="选择 VIP 到期时间"
                  />
                  <el-button :loading="operationLoading" native-type="submit" type="primary">
                    设置 VIP
                  </el-button>
                  <el-button
                    :disabled="!selectedUser.vipUntil"
                    :loading="operationLoading"
                    type="danger"
                    plain
                    @click="cancelVip"
                  >
                    取消 VIP
                  </el-button>
                </el-form>
              </section>
              <section class="operation-block">
                <div>
                  <h2>用户状态</h2>
                  <p>DISABLED 和 BLACKLISTED 都会阻止登录及 App 写操作。</p>
                </div>
                <div class="status-actions">
                  <el-button
                    v-for="status in statusOptions"
                    :key="status"
                    :disabled="status === selectedUser.status"
                    :loading="operationLoading"
                    :type="status === 'ACTIVE' ? 'success' : status === 'BLACKLISTED' ? 'danger' : 'warning'"
                    plain
                    @click="changeSelectedUserStatus(status)"
                  >
                    {{ status }}
                  </el-button>
                </div>
              </section>
              <section class="operation-block">
                <div>
                  <h2>积分调整</h2>
                  <p>支持正负数调整，后端负责余额下限校验。</p>
                </div>
                <el-form class="inline-form" @submit.prevent="submitPointAdjustment">
                  <el-input-number v-model="pointsForm.amount" :step="1" />
                  <el-input
                    v-model="pointsForm.reason"
                    maxlength="255"
                    placeholder="调整原因"
                    show-word-limit
                  />
                  <el-button :loading="operationLoading" native-type="submit" type="primary">
                    提交
                  </el-button>
                </el-form>
              </section>
            </el-tab-pane>
            <el-tab-pane :label="`观看记录 ${selectedUser.watchRecordCount}`">
              <el-table :data="watchRecords" border>
                <el-table-column label="剧名" min-width="180" prop="bookTitle" />
                <el-table-column label="集数" prop="episodeNum" width="80" />
                <el-table-column label="进度" width="100">
                  <template #default="{ row }">{{ row.progressPercent }}%</template>
                </el-table-column>
                <el-table-column label="位置" width="140">
                  <template #default="{ row }">{{ row.positionSeconds }} / {{ row.durationSeconds }}s</template>
                </el-table-column>
                <el-table-column label="更新时间" min-width="220" prop="updatedAt" />
              </el-table>
            </el-tab-pane>
            <el-tab-pane :label="`积分流水 ${selectedUser.pointRecordCount}`">
              <el-table :data="pointRecords" border>
                <el-table-column align="right" label="变动" prop="amount" width="90" />
                <el-table-column align="right" label="余额" prop="balanceAfter" width="90" />
                <el-table-column label="来源" prop="source" width="170" />
                <el-table-column label="原因" min-width="180" prop="reason" />
                <el-table-column label="时间" min-width="220" prop="createdAt" />
              </el-table>
            </el-tab-pane>
            <el-tab-pane :label="`提现记录 ${selectedUser.withdrawalRecordCount}`">
              <el-table :data="withdrawals" border>
                <el-table-column align="right" label="积分" prop="pointAmount" width="90" />
                <el-table-column align="right" label="USDT" prop="usdtAmount" width="100" />
                <el-table-column label="钱包" min-width="240">
                  <template #default="{ row }">
                    <span class="mono">{{ row.network }} · {{ row.walletAddress }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="状态" width="120">
                  <template #default="{ row }">
                    <el-tag :type="withdrawalStatusType(row.status)" effect="plain">{{ row.status }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="tx hash" min-width="180">
                  <template #default="{ row }">{{ row.txHash || '-' }}</template>
                </el-table-column>
                <el-table-column label="时间" min-width="220" prop="createdAt" />
              </el-table>
            </el-tab-pane>
          </el-tabs>
        </template>
      </div>
    </el-drawer>
  </section>
</template>
