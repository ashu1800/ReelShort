<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { onMounted, reactive, ref } from 'vue'
import {
  adjustUserPoints,
  fetchUserDetail,
  fetchUserPointRecords,
  fetchUserPointTransfers,
  fetchUserWithdrawals,
  fetchUsers,
  fetchUserWatchRecords,
  updateUserStatus,
} from '../services/adminApi'
import type {
  AdminUserDetail,
  AdminUserSummary,
  PointRecord,
  PointTransfer,
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
const pointTransfers = ref<PointTransfer[]>([])
const withdrawals = ref<WithdrawalRequest[]>([])
const pointsForm = reactive({
  amount: 0,
  reason: '',
})

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
  pointTransfers.value = []
  withdrawals.value = []
  try {
    const [detail, watches, points, transfers, userWithdrawals] = await Promise.all([
      fetchUserDetail(userId),
      fetchUserWatchRecords(userId),
      fetchUserPointRecords(userId),
      fetchUserPointTransfers(userId),
      fetchUserWithdrawals(userId),
    ])
    selectedUser.value = detail
    watchRecords.value = watches
    pointRecords.value = points
    pointTransfers.value = transfers
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

async function submitPointAdjustment() {
  if (!selectedUser.value) return
  const userId = selectedUser.value.id
  const reason = pointsForm.reason.trim()
  if (!pointsForm.amount || !reason) {
    ElMessage.warning('请输入非 0 积分金额和调整原因')
    return
  }
  let adjusted = false
  operationLoading.value = true
  try {
    selectedUser.value = await adjustUserPoints(userId, pointsForm.amount, reason)
    pointsForm.amount = 0
    pointsForm.reason = ''
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
            <el-tab-pane :label="`积分交易 ${selectedUser.pointTransferRecordCount}`">
              <el-table :data="pointTransfers" border>
                <el-table-column label="方向" width="90">
                  <template #default="{ row }">
                    <el-tag :type="row.direction === 'IN' ? 'success' : 'warning'" effect="plain">
                      {{ row.direction === 'IN' ? '转入' : '转出' }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column align="right" label="积分" prop="pointAmount" width="90" />
                <el-table-column label="发送方" min-width="160">
                  <template #default="{ row }"><span class="mono">{{ row.senderAccount }}</span></template>
                </el-table-column>
                <el-table-column label="接收方" min-width="160">
                  <template #default="{ row }"><span class="mono">{{ row.recipientAccount }}</span></template>
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
