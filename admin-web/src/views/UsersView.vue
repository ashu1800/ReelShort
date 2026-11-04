<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  User,
  GoldMedal,
  Coin,
  Lock,
  Search,
  Refresh,
  Check,
  CopyDocument,
  View,
  Wallet,
} from '@element-plus/icons-vue'
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

// 搜索与多维过滤
const searchQuery = ref('')
const statusFilter = ref<string>('ALL')
const vipFilter = ref<string>('ALL')

// 分页控制
const currentPage = ref(1)
const pageSize = ref(15)

// 表单状态
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

// 指标卡计算
const totalUsersCount = computed(() => users.value.length)
const vipUsersCount = computed(() => users.value.filter((u) => u.vip).length)
const totalPointBalance = computed(() =>
  users.value.reduce((acc, cur) => acc + (cur.pointBalance || 0), 0),
)
const totalFrozenPoints = computed(() =>
  users.value.reduce((acc, cur) => acc + (cur.frozenPoints || 0), 0),
)

// 实时可用积分与变动预测
const projectedBalance = computed(() => {
  if (!selectedUser.value) return 0
  return (selectedUser.value.availablePoints || 0) + (pointsForm.amount || 0)
})

// 实时 VIP 剩余天数计算
const projectedVipDays = computed(() => {
  if (!vipForm.vipUntil) return 0
  const diff = vipForm.vipUntil.getTime() - Date.now()
  if (diff <= 0) return 0
  return Math.ceil(diff / (1000 * 60 * 60 * 24))
})

// 过滤后的用户列表
const filteredUsers = computed(() => {
  let list = users.value
  const query = searchQuery.value.trim().toLowerCase()

  if (query) {
    list = list.filter(
      (u) =>
        u.username.toLowerCase().includes(query) ||
        u.id.toLowerCase().includes(query),
    )
  }

  if (statusFilter.value !== 'ALL') {
    list = list.filter((u) => u.status === statusFilter.value)
  }

  if (vipFilter.value === 'VIP') {
    list = list.filter((u) => u.vip)
  } else if (vipFilter.value === 'NORMAL') {
    list = list.filter((u) => !u.vip)
  }

  return list
})

// 分页切片
const pagedUsers = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return filteredUsers.value.slice(start, start + pageSize.value)
})

function resetFilters() {
  searchQuery.value = ''
  statusFilter.value = 'ALL'
  vipFilter.value = 'ALL'
  currentPage.value = 1
}

function formatNumber(num?: number) {
  if (num === undefined || num === null) return '0'
  return num.toLocaleString()
}

function formatShanghaiDateTime(value: string | null) {
  if (!value) return '未开通'
  try {
    return new Intl.DateTimeFormat('zh-CN', {
      timeZone: 'Asia/Shanghai',
      dateStyle: 'medium',
      timeStyle: 'short',
      hour12: false,
    }).format(new Date(value))
  } catch {
    return value
  }
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

function getVipRemainingDays(vipUntil: string | null) {
  if (!vipUntil) return 0
  const diff = new Date(vipUntil).getTime() - Date.now()
  if (diff <= 0) return 0
  return Math.ceil(diff / (1000 * 60 * 60 * 24))
}

function getAvatarBg(username: string) {
  const colors = ['#2563eb', '#7c3aed', '#db2777', '#d97706', '#059669', '#0891b2']
  let hash = 0
  for (let i = 0; i < username.length; i++) {
    hash = username.charCodeAt(i) + ((hash << 5) - hash)
  }
  return colors[Math.abs(hash) % colors.length]
}

function setQuickVipDays(days: number) {
  const d = new Date()
  d.setDate(d.getDate() + days)
  vipForm.vipUntil = d
}

function setQuickPoints(amount: number) {
  pointsForm.amount = amount
}

async function copyText(text: string, label = '内容') {
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success(`${label}已复制到剪贴板`)
  } catch {
    ElMessage.warning('复制失败，请手动选中文本复制')
  }
}

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
    <!-- 顶部标题栏 -->
    <div class="page-header">
      <div>
        <div class="title-row">
          <h1>用户管理</h1>
          <el-tag effect="plain" type="primary" size="small" class="user-count-pill">
            共 {{ totalUsersCount }} 位注册用户
          </el-tag>
        </div>
        <p class="subtitle">查看用户状态、资产积分明细、VIP 会员期限与安全风控</p>
      </div>
      <el-button type="primary" :icon="Refresh" @click="loadUsers">刷新列表</el-button>
    </div>

    <el-alert v-if="error" :title="error" show-icon type="error" />

    <!-- 顶部业务 KPI 统计卡片 -->
    <div class="user-kpi-grid">
      <div class="user-kpi-card kpi-total">
        <div class="kpi-icon-box"><el-icon><User /></el-icon></div>
        <div class="kpi-body">
          <div class="kpi-label">平台注册用户</div>
          <div class="kpi-num mono">{{ totalUsersCount }}</div>
        </div>
      </div>
      <div class="user-kpi-card kpi-vip">
        <div class="kpi-icon-box"><el-icon><GoldMedal /></el-icon></div>
        <div class="kpi-body">
          <div class="kpi-label">VIP 尊享会员</div>
          <div class="kpi-num mono">{{ vipUsersCount }}</div>
        </div>
      </div>
      <div class="user-kpi-card kpi-points">
        <div class="kpi-icon-box"><el-icon><Coin /></el-icon></div>
        <div class="kpi-body">
          <div class="kpi-label">全站流通积分总量</div>
          <div class="kpi-num mono">{{ formatNumber(totalPointBalance) }}</div>
        </div>
      </div>
      <div class="user-kpi-card kpi-frozen">
        <div class="kpi-icon-box"><el-icon><Lock /></el-icon></div>
        <div class="kpi-body">
          <div class="kpi-label">提现冻结中积分</div>
          <div class="kpi-num mono">{{ formatNumber(totalFrozenPoints) }}</div>
        </div>
      </div>
    </div>

    <!-- 用户检索与过滤工具栏 -->
    <el-card shadow="never" class="table-card">
      <div class="filter-toolbar">
        <div class="search-box">
          <el-input
            v-model="searchQuery"
            placeholder="搜索用户名 / 用户 ID..."
            :prefix-icon="Search"
            clearable
            class="user-search-input"
          />
        </div>
        <div class="filter-actions">
          <el-radio-group v-model="statusFilter" size="small">
            <el-radio-button value="ALL">全部状态</el-radio-button>
            <el-radio-button value="ACTIVE">正常活跃</el-radio-button>
            <el-radio-button value="DISABLED">已禁用</el-radio-button>
            <el-radio-button value="BLACKLISTED">黑名单</el-radio-button>
          </el-radio-group>

          <el-radio-group v-model="vipFilter" size="small">
            <el-radio-button value="ALL">全部身份</el-radio-button>
            <el-radio-button value="VIP">仅看 VIP</el-radio-button>
            <el-radio-button value="NORMAL">普通用户</el-radio-button>
          </el-radio-group>

          <el-button link type="info" size="small" @click="resetFilters">重置筛选</el-button>
        </div>
      </div>

      <!-- 用户列表表格 -->
      <el-table
        v-loading="loading"
        :data="pagedUsers"
        border
        stripe
        style="width: 100%"
        class="custom-user-table"
      >
        <!-- 账号列 (带首字母头像与一键复制) -->
        <el-table-column label="账号与标识" min-width="190">
          <template #default="{ row }">
            <div class="user-cell" @click="openDetail(row)">
              <div class="user-avatar" :style="{ backgroundColor: getAvatarBg(row.username) }">
                {{ row.username.charAt(0).toUpperCase() }}
              </div>
              <div class="user-info">
                <span class="user-name mono">{{ row.username }}</span>
                <span class="user-id mono" :title="row.id">ID: {{ row.id.substring(0, 8) }}...</span>
              </div>
            </div>
          </template>
        </el-table-column>

        <!-- 状态列 -->
        <el-table-column label="账号状态" width="130" align="center">
          <template #default="{ row }">
            <div class="status-pill-wrap">
              <span
                class="status-pill"
                :class="{
                  'pill-active': row.status === 'ACTIVE',
                  'pill-disabled': row.status === 'DISABLED',
                  'pill-blacklisted': row.status === 'BLACKLISTED',
                }"
              >
                <span class="status-dot"></span>
                {{ row.status }}
              </span>
            </div>
          </template>
        </el-table-column>

        <!-- VIP 列 -->
        <el-table-column label="VIP 权益" min-width="190">
          <template #default="{ row }">
            <div v-if="row.vip" class="vip-active-cell">
              <div class="vip-badge-row">
                <span class="vip-gold-pill"><el-icon><GoldMedal /></el-icon> VIP 会员</span>
                <span class="vip-remaining-tag mono">剩 {{ getVipRemainingDays(row.vipUntil) }} 天</span>
              </div>
              <div class="vip-expire-date mono">{{ formatShanghaiDateTime(row.vipUntil) }}</div>
            </div>
            <div v-else class="vip-inactive-cell">
              <span class="vip-normal-pill">普通用户</span>
            </div>
          </template>
        </el-table-column>

        <!-- 积分列 -->
        <el-table-column align="right" label="总积分" prop="pointBalance" width="130">
          <template #default="{ row }">
            <span class="points-val total-pts mono">{{ formatNumber(row.pointBalance) }}</span>
          </template>
        </el-table-column>

        <el-table-column align="right" label="冻结保障" prop="frozenPoints" width="110">
          <template #default="{ row }">
            <span
              class="points-val mono"
              :class="{ 'frozen-highlight': row.frozenPoints > 0, 'text-dim': !row.frozenPoints }"
            >
              {{ formatNumber(row.frozenPoints) }}
            </span>
          </template>
        </el-table-column>

        <el-table-column align="right" label="可用积分" prop="availablePoints" width="120">
          <template #default="{ row }">
            <span class="points-val available-pts mono">{{ formatNumber(row.availablePoints) }}</span>
          </template>
        </el-table-column>

        <!-- 规范化创建时间 -->
        <el-table-column label="注册时间" min-width="180">
          <template #default="{ row }">
            <span class="mono time-text">{{ formatDateTime(row.createdAt) }}</span>
          </template>
        </el-table-column>

        <!-- 操作列 -->
        <el-table-column align="center" label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" size="small" :icon="View" @click="openDetail(row)">
              详情
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 底部分页栏 -->
      <div class="pagination-footer">
        <span class="page-count-text">
          当前展示 <b>{{ pagedUsers.length }}</b> / 共 <b>{{ filteredUsers.length }}</b> 位用户
        </span>
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[10, 15, 30, 50]"
          layout="sizes, prev, pager, next"
          :total="filteredUsers.length"
          size="small"
        />
      </div>
    </el-card>

    <!-- 用户 360 运营抽屉 -->
    <el-drawer
      v-model="detailOpen"
      size="760px"
      title="用户 360 全景详情与风控操作"
      :destroy-on-close="true"
    >
      <div v-loading="detailLoading" class="drawer-body">
        <el-empty v-if="!selectedUser && !detailLoading" description="未选择用户" />
        <template v-if="selectedUser">
          <!-- 用户履历卡片 (User Persona Header) -->
          <div class="user-hero-card">
            <div
              class="hero-avatar"
              :style="{ backgroundColor: getAvatarBg(selectedUser.username) }"
            >
              {{ selectedUser.username.charAt(0).toUpperCase() }}
            </div>
            <div class="hero-main-info">
              <div class="hero-name-row">
                <h2 class="hero-username mono">{{ selectedUser.username }}</h2>
                <span
                  class="status-pill"
                  :class="{
                    'pill-active': selectedUser.status === 'ACTIVE',
                    'pill-disabled': selectedUser.status === 'DISABLED',
                    'pill-blacklisted': selectedUser.status === 'BLACKLISTED',
                  }"
                >
                  <span class="status-dot"></span>
                  {{ selectedUser.status }}
                </span>
                <span v-if="selectedUser.vip" class="vip-gold-pill">
                  <el-icon><GoldMedal /></el-icon> VIP 活跃会员
                </span>
              </div>
              <div class="hero-sub-row">
                <span class="hero-meta mono">用户ID: {{ selectedUser.id }}</span>
                <el-button
                  link
                  :icon="CopyDocument"
                  size="small"
                  @click="copyText(selectedUser.id, '用户ID')"
                />
                <span class="meta-sep">·</span>
                <span class="hero-meta">注册于 {{ formatDateTime(selectedUser.createdAt) }}</span>
              </div>
            </div>
          </div>

          <!-- 资产与钱包指标网格 -->
          <div class="detail-summary">
            <div>
              <div class="muted">总积分余额</div>
              <strong class="mono pts-huge">{{ formatNumber(selectedUser.pointBalance) }}</strong>
            </div>
            <div>
              <div class="muted">可用积分</div>
              <strong class="mono pts-green">{{ formatNumber(selectedUser.availablePoints) }}</strong>
            </div>
            <div>
              <div class="muted">冻结保障积分</div>
              <strong class="mono" :class="selectedUser.frozenPoints > 0 ? 'pts-amber' : ''">
                {{ formatNumber(selectedUser.frozenPoints) }}
              </strong>
            </div>
            <div>
              <div class="muted">VIP 权益截止</div>
              <span class="mono">{{ formatShanghaiDateTime(selectedUser.vipUntil) }}</span>
            </div>
            <div class="wallet-cell">
              <div class="muted">绑定的 ERC-20 冷钱包地址</div>
              <div v-if="selectedUser.walletAddress" class="wallet-val">
                <el-icon><Wallet /></el-icon>
                <span class="mono wallet-text">{{ selectedUser.walletNetwork }} · {{ selectedUser.walletAddress }}</span>
                <el-button
                  link
                  :icon="CopyDocument"
                  size="small"
                  @click="copyText(selectedUser.walletAddress, '钱包地址')"
                />
              </div>
              <span v-else class="text-dim">尚未绑定冷钱包</span>
            </div>
          </div>

          <!-- 运营业务 Tabs -->
          <el-tabs class="custom-drawer-tabs">
            <el-tab-pane label="运营与风控操作">
              <!-- 1. VIP 权益管理 -->
              <section class="operation-block">
                <div class="block-header">
                  <div class="block-title-row">
                    <div class="block-title">
                      <el-icon><GoldMedal /></el-icon>
                      <span>VIP 会员特权管理</span>
                    </div>
                    <div class="vip-status-indicator">
                      <span v-if="selectedUser.vip" class="pill-active-vip">
                        <el-icon><GoldMedal /></el-icon>
                        已激活 · 剩 {{ getVipRemainingDays(selectedUser.vipUntil) }} 天
                      </span>
                      <span v-else class="pill-inactive-vip">普通用户 (未开通)</span>
                    </div>
                  </div>
                  <div class="block-desc">设定未来到期时间或立即终止特权，到期后将自动收回 VIP 权益。</div>
                </div>

                <div class="interactive-form-body">
                  <!-- 快捷期限选择 -->
                  <div class="preset-row">
                    <span class="preset-label">快捷期限:</span>
                    <div class="preset-chips">
                      <button
                        type="button"
                        class="preset-chip"
                        :class="{ 'is-selected': projectedVipDays >= 6 && projectedVipDays <= 8 }"
                        @click="setQuickVipDays(7)"
                      >
                        +7 天 (体验卡)
                      </button>
                      <button
                        type="button"
                        class="preset-chip"
                        :class="{ 'is-selected': projectedVipDays >= 28 && projectedVipDays <= 32 }"
                        @click="setQuickVipDays(30)"
                      >
                        +30 天 (月度会员)
                      </button>
                      <button
                        type="button"
                        class="preset-chip"
                        :class="{ 'is-selected': projectedVipDays >= 88 && projectedVipDays <= 92 }"
                        @click="setQuickVipDays(90)"
                      >
                        +90 天 (季度会员)
                      </button>
                      <button
                        type="button"
                        class="preset-chip"
                        :class="{ 'is-selected': projectedVipDays >= 360 }"
                        @click="setQuickVipDays(365)"
                      >
                        +365 天 (年度尊享)
                      </button>
                    </div>
                  </div>

                  <!-- 行内日期选择与操作按钮 -->
                  <el-form class="compact-action-row" @submit.prevent="submitVip">
                    <el-date-picker
                      v-model="vipForm.vipUntil"
                      type="datetime"
                      placeholder="选择自定义到期时间"
                      class="flex-input"
                    />
                    <el-button
                      :loading="operationLoading"
                      native-type="submit"
                      type="primary"
                      :disabled="!vipForm.vipUntil"
                    >
                      确认设置 VIP
                    </el-button>
                    <el-button
                      v-if="selectedUser.vipUntil"
                      :loading="operationLoading"
                      type="danger"
                      plain
                      @click="cancelVip"
                    >
                      取消当前 VIP
                    </el-button>
                  </el-form>

                  <!-- 设定预览反馈 -->
                  <div v-if="vipForm.vipUntil" class="live-projection-pill">
                    <el-icon color="#67c23a"><Check /></el-icon>
                    <span>
                      设置后将在 <b>{{ formatDateTime(vipForm.vipUntil.toISOString()) }}</b> 到期（共 <b>{{ projectedVipDays }}</b> 天）
                    </span>
                  </div>
                </div>
              </section>

              <!-- 2. 用户状态与安全风控 -->
              <section class="operation-block">
                <div class="block-header">
                  <div class="block-title">
                    <el-icon><Lock /></el-icon>
                    <span>账号状态与安全风控</span>
                  </div>
                  <div class="block-desc">切换用户登录与客户端操作权限，变更立即生效并在全平台生效。</div>
                </div>

                <!-- 3 列平铺的状态看板 -->
                <div class="status-tiers-grid">
                  <!-- ACTIVE -->
                  <div
                    class="status-tier-card"
                    :class="{ 'tier-active': selectedUser.status === 'ACTIVE' }"
                  >
                    <div class="tier-top">
                      <div class="tier-dot green"></div>
                      <span class="tier-name">正常 (ACTIVE)</span>
                      <span v-if="selectedUser.status === 'ACTIVE'" class="active-badge">当前生效</span>
                    </div>
                    <div class="tier-info">允许正常登录，可浏览内容、试看、充值与提现</div>
                    <div class="tier-btn-wrap">
                      <el-button
                        v-if="selectedUser.status !== 'ACTIVE'"
                        size="small"
                        type="success"
                        plain
                        :loading="operationLoading"
                        @click="changeSelectedUserStatus('ACTIVE')"
                      >
                        恢复正常
                      </el-button>
                      <span v-else class="tier-status-text green">✓ 状态生效中</span>
                    </div>
                  </div>

                  <!-- DISABLED -->
                  <div
                    class="status-tier-card"
                    :class="{ 'tier-active': selectedUser.status === 'DISABLED' }"
                  >
                    <div class="tier-top">
                      <div class="tier-dot amber"></div>
                      <span class="tier-name">禁用 (DISABLED)</span>
                      <span v-if="selectedUser.status === 'DISABLED'" class="active-badge amber">当前生效</span>
                    </div>
                    <div class="tier-info">禁止用户登录与调用所有写接口，保留已有资产</div>
                    <div class="tier-btn-wrap">
                      <el-button
                        v-if="selectedUser.status !== 'DISABLED'"
                        size="small"
                        type="warning"
                        plain
                        :loading="operationLoading"
                        @click="changeSelectedUserStatus('DISABLED')"
                      >
                        禁用账号
                      </el-button>
                      <span v-else class="tier-status-text amber">⚠ 已被禁用</span>
                    </div>
                  </div>

                  <!-- BLACKLISTED -->
                  <div
                    class="status-tier-card"
                    :class="{ 'tier-active': selectedUser.status === 'BLACKLISTED' }"
                  >
                    <div class="tier-top">
                      <div class="tier-dot red"></div>
                      <span class="tier-name">黑名单 (BLACKLISTED)</span>
                      <span v-if="selectedUser.status === 'BLACKLISTED'" class="active-badge red">当前生效</span>
                    </div>
                    <div class="tier-info">严重作弊或风控拉黑，阻断登录并拦截充提请求</div>
                    <div class="tier-btn-wrap">
                      <el-button
                        v-if="selectedUser.status !== 'BLACKLISTED'"
                        size="small"
                        type="danger"
                        plain
                        :loading="operationLoading"
                        @click="changeSelectedUserStatus('BLACKLISTED')"
                      >
                        移入黑名单
                      </el-button>
                      <span v-else class="tier-status-text red">⛔ 黑名单中</span>
                    </div>
                  </div>
                </div>
              </section>

              <!-- 3. 人工积分调整 -->
              <section class="operation-block">
                <div class="block-header">
                  <div class="block-title">
                    <el-icon><Coin /></el-icon>
                    <span>人工积分变动调节</span>
                  </div>
                  <div class="block-desc">运营人工入账奖励或违规扣减调账，变动结果即刻反映在用户账户与审计流中。</div>
                </div>

                <div class="interactive-form-body">
                  <!-- 实时余额联动预测条 -->
                  <div class="balance-projection-bar">
                    <div class="proj-segment">
                      <span class="proj-label">当前可用</span>
                      <span class="proj-val mono">{{ formatNumber(selectedUser.availablePoints) }}</span>
                    </div>
                    <span class="proj-op">+</span>
                    <div class="proj-segment">
                      <span class="proj-label">调整数额</span>
                      <span
                        class="proj-val mono"
                        :class="{
                          'text-green': pointsForm.amount > 0,
                          'text-red': pointsForm.amount < 0,
                          'text-muted': pointsForm.amount === 0,
                        }"
                      >
                        {{ pointsForm.amount > 0 ? `+${formatNumber(pointsForm.amount)}` : formatNumber(pointsForm.amount) }}
                      </span>
                    </div>
                    <span class="proj-op">=</span>
                    <div class="proj-segment">
                      <span class="proj-label">调整后预计</span>
                      <span
                        class="proj-val mono font-bold"
                        :class="projectedBalance < 0 ? 'text-red' : 'text-primary'"
                      >
                        {{ formatNumber(projectedBalance) }}
                      </span>
                    </div>
                    <div v-if="projectedBalance < 0" class="proj-warn-tip">
                      ⚠️ 扣减超额（低于 0），后端将拒绝
                    </div>
                  </div>

                  <!-- 快捷数值选择 -->
                  <div class="preset-row">
                    <span class="preset-label">快捷数值:</span>
                    <div class="preset-chips">
                      <button type="button" class="preset-chip plus" @click="setQuickPoints(100)">+100</button>
                      <button type="button" class="preset-chip plus" @click="setQuickPoints(500)">+500</button>
                      <button type="button" class="preset-chip plus" @click="setQuickPoints(1000)">+1,000</button>
                      <button type="button" class="preset-chip plus" @click="setQuickPoints(10000)">+10,000</button>
                      <button type="button" class="preset-chip minus" @click="setQuickPoints(-100)">-100</button>
                      <button type="button" class="preset-chip minus" @click="setQuickPoints(-500)">-500</button>
                      <button type="button" class="preset-chip minus" @click="setQuickPoints(-1000)">-1,000</button>
                      <button type="button" class="preset-chip" @click="setQuickPoints(0)">清零</button>
                    </div>
                  </div>

                  <!-- 紧凑行内表单 -->
                  <el-form class="compact-action-row" @submit.prevent="submitPointAdjustment">
                    <el-input-number
                      v-model="pointsForm.amount"
                      :step="10"
                      class="points-input-num"
                      controls-position="right"
                    />
                    <el-input
                      v-model="pointsForm.reason"
                      maxlength="255"
                      placeholder="请输入调整原因（将记入审计日志，如：活动补偿、误充调账）"
                      show-word-limit
                      clearable
                      class="flex-input"
                    />
                    <el-button
                      :loading="operationLoading"
                      native-type="submit"
                      type="primary"
                      :disabled="pointsForm.amount === 0 || !pointsForm.reason.trim() || projectedBalance < 0"
                    >
                      确认提交调整
                    </el-button>
                  </el-form>
                </div>
              </section>
            </el-tab-pane>

            <!-- 观看记录 Tab -->
            <el-tab-pane :label="`观看记录 (${selectedUser.watchRecordCount})`">
              <el-empty v-if="!watchRecords.length" description="暂无短剧观看记录" />
              <el-table v-else :data="watchRecords" border size="small">
                <el-table-column label="剧名" min-width="180" prop="bookTitle" />
                <el-table-column label="集数" prop="episodeNum" width="90" align="center">
                  <template #default="{ row }">
                    <el-tag size="small" effect="plain">EP {{ row.episodeNum }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="观看进度" width="130">
                  <template #default="{ row }">
                    <el-progress
                      :percentage="Math.min(100, row.progressPercent)"
                      :stroke-width="6"
                      :status="row.progressPercent >= 100 ? 'success' : undefined"
                    />
                  </template>
                </el-table-column>
                <el-table-column label="位置 / 时长" width="130">
                  <template #default="{ row }">
                    <span class="mono">{{ row.positionSeconds }}s / {{ row.durationSeconds }}s</span>
                  </template>
                </el-table-column>
                <el-table-column label="更新时间" min-width="180">
                  <template #default="{ row }">
                    <span class="mono">{{ formatDateTime(row.updatedAt) }}</span>
                  </template>
                </el-table-column>
              </el-table>
            </el-tab-pane>

            <!-- 积分流水 Tab -->
            <el-tab-pane :label="`积分流水 (${selectedUser.pointRecordCount})`">
              <el-empty v-if="!pointRecords.length" description="暂无积分流水明细" />
              <el-table v-else :data="pointRecords" border size="small">
                <el-table-column align="right" label="变动金额" prop="amount" width="110">
                  <template #default="{ row }">
                    <span
                      class="mono"
                      :class="row.amount >= 0 ? 'pts-delta-plus' : 'pts-delta-minus'"
                    >
                      {{ row.amount > 0 ? `+${row.amount}` : row.amount }}
                    </span>
                  </template>
                </el-table-column>
                <el-table-column align="right" label="变动后余额" prop="balanceAfter" width="110">
                  <template #default="{ row }">
                    <span class="mono font-bold">{{ row.balanceAfter }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="变动来源" prop="source" width="140">
                  <template #default="{ row }">
                    <el-tag size="small" type="info" effect="plain">{{ row.source }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="原因 / 描述" min-width="180" prop="reason" />
                <el-table-column label="时间" min-width="180">
                  <template #default="{ row }">
                    <span class="mono">{{ formatDateTime(row.createdAt) }}</span>
                  </template>
                </el-table-column>
              </el-table>
            </el-tab-pane>

            <!-- 提现记录 Tab -->
            <el-tab-pane :label="`提现申请 (${selectedUser.withdrawalRecordCount})`">
              <el-empty v-if="!withdrawals.length" description="暂无积分提现记录" />
              <el-table v-else :data="withdrawals" border size="small">
                <el-table-column align="right" label="兑换积分" prop="pointAmount" width="100">
                  <template #default="{ row }">
                    <span class="mono font-bold">{{ row.pointAmount }}</span>
                  </template>
                </el-table-column>
                <el-table-column align="right" label="应付 USDT" prop="usdtAmount" width="110">
                  <template #default="{ row }">
                    <span class="mono pts-green font-bold">{{ row.usdtAmount }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="收款钱包" min-width="220">
                  <template #default="{ row }">
                    <div class="wallet-copy-cell">
                      <span class="mono text-truncate">{{ row.network }} · {{ row.walletAddress }}</span>
                      <el-button
                        link
                        :icon="CopyDocument"
                        size="small"
                        @click="copyText(row.walletAddress, '钱包地址')"
                      />
                    </div>
                  </template>
                </el-table-column>
                <el-table-column label="审核状态" width="110" align="center">
                  <template #default="{ row }">
                    <el-tag :type="withdrawalStatusType(row.status)" effect="plain">
                      {{ row.status }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="tx hash" min-width="160">
                  <template #default="{ row }">
                    <span class="mono text-truncate">{{ row.txHash || '-' }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="申请时间" min-width="180">
                  <template #default="{ row }">
                    <span class="mono">{{ formatDateTime(row.createdAt) }}</span>
                  </template>
                </el-table-column>
              </el-table>
            </el-tab-pane>
          </el-tabs>
        </template>
      </div>
    </el-drawer>
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

.user-count-pill {
  font-weight: 600;
  border-radius: 9999px;
}

.subtitle {
  margin: 6px 0 0;
  color: #64748b;
  font-size: 14px;
}

/* 顶部 KPI 卡片网格 (4列) */
.user-kpi-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 20px;
}

@media (max-width: 1024px) {
  .user-kpi-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 640px) {
  .user-kpi-grid {
    grid-template-columns: 1fr;
  }
}

.user-kpi-card {
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  padding: 16px;
  display: flex;
  align-items: center;
  gap: 14px;
  transition: all 0.2s ease-in-out;
}

.user-kpi-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 14px -2px rgba(0, 0, 0, 0.06);
}

.kpi-icon-box {
  width: 44px;
  height: 44px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  flex-shrink: 0;
}

.kpi-total .kpi-icon-box {
  background: #eff6ff;
  color: #2563eb;
}
.kpi-vip .kpi-icon-box {
  background: #fef3c7;
  color: #d97706;
}
.kpi-points .kpi-icon-box {
  background: #f3e8ff;
  color: #7c3aed;
}
.kpi-frozen .kpi-icon-box {
  background: #fee2e2;
  color: #dc2626;
}

.kpi-body {
  display: flex;
  flex-direction: column;
}

.kpi-label {
  font-size: 12px;
  color: #64748b;
  font-weight: 500;
}

.kpi-num {
  font-size: 22px;
  font-weight: 700;
  color: #0f172a;
  margin-top: 2px;
}

/* 过滤工具栏 */
.table-card {
  border-radius: 10px;
  border: 1px solid #e2e8f0;
}

.filter-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
  flex-wrap: wrap;
  gap: 12px;
}

.search-box {
  flex: 1;
  min-width: 260px;
  max-width: 360px;
}

.filter-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

/* 用户表格项 */
.user-cell {
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
}

.user-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  color: #ffffff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 14px;
  flex-shrink: 0;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

.user-info {
  display: flex;
  flex-direction: column;
}

.user-name {
  font-size: 14px;
  font-weight: 600;
  color: #1e293b;
  transition: color 0.15s ease;
}

.user-cell:hover .user-name {
  color: #2563eb;
}

.user-id {
  font-size: 11px;
  color: #94a3b8;
}

/* 状态药丸胶囊 */
.status-pill-wrap {
  display: flex;
  justify-content: center;
}

.status-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 3px 10px;
  border-radius: 9999px;
  font-size: 12px;
  font-weight: 600;
  font-family: 'SFMono-Regular', Consolas, monospace;
}

.status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
}

.pill-active {
  background: #f0fdf4;
  color: #15803d;
  border: 1px solid #bbf7d0;
}
.pill-active .status-dot {
  background: #22c55e;
}

.pill-disabled {
  background: #fffbeb;
  color: #b45309;
  border: 1px solid #fde68a;
}
.pill-disabled .status-dot {
  background: #f59e0b;
}

.pill-blacklisted {
  background: #fef2f2;
  color: #b91c1c;
  border: 1px solid #fecaca;
}
.pill-blacklisted .status-dot {
  background: #ef4444;
}

/* VIP 单元格 */
.vip-active-cell {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.vip-badge-row {
  display: flex;
  align-items: center;
  gap: 6px;
}

.vip-gold-pill {
  background: linear-gradient(135deg, #fef3c7 0%, #fde68a 100%);
  color: #b45309;
  font-size: 11px;
  font-weight: 700;
  padding: 2px 8px;
  border-radius: 9999px;
  border: 1px solid #fcd34d;
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.vip-remaining-tag {
  font-size: 11px;
  color: #d97706;
  font-weight: 600;
}

.vip-expire-date {
  font-size: 11px;
  color: #94a3b8;
}

.vip-normal-pill {
  background: #f1f5f9;
  color: #64748b;
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 9999px;
}

.points-val {
  font-size: 13px;
  font-weight: 500;
}

.total-pts {
  color: #0f172a;
  font-weight: 600;
}

.available-pts {
  color: #16a34a;
  font-weight: 600;
}

.frozen-highlight {
  color: #d97706;
  font-weight: 600;
}

.time-text {
  font-size: 12px;
  color: #64748b;
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

/* 抽屉 360 视图 */
.drawer-body {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.user-hero-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 18px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
}

.hero-avatar {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  color: #ffffff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 24px;
  flex-shrink: 0;
  box-shadow: 0 4px 10px rgba(0, 0, 0, 0.15);
}

.hero-main-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.hero-name-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.hero-username {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
  color: #0f172a;
}

.hero-sub-row {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #64748b;
  flex-wrap: wrap;
}

.wallet-cell {
  grid-column: 1 / -1;
}

.wallet-val {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 4px;
}

.wallet-text {
  font-size: 13px;
  color: #2563eb;
  word-break: break-all;
}

.pts-huge {
  font-size: 20px;
  color: #0f172a;
}

.pts-green {
  color: #16a34a;
}

.pts-amber {
  color: #d97706;
}

.operation-block {
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  padding: 16px 18px;
  margin-bottom: 16px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.02);
}

.block-header {
  margin-bottom: 12px;
}

.block-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.block-title {
  font-size: 15px;
  font-weight: 700;
  color: #0f172a;
  display: flex;
  align-items: center;
  gap: 8px;
}

.block-desc {
  font-size: 12px;
  color: #64748b;
  margin-top: 3px;
}

.vip-status-indicator {
  display: flex;
  align-items: center;
}

.pill-active-vip {
  background: linear-gradient(135deg, #fef3c7 0%, #fde68a 100%);
  color: #b45309;
  padding: 3px 10px;
  border-radius: 9999px;
  font-size: 12px;
  font-weight: 700;
  border: 1px solid #fcd34d;
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.pill-inactive-vip {
  background: #f1f5f9;
  color: #64748b;
  padding: 3px 10px;
  border-radius: 9999px;
  font-size: 12px;
}

.interactive-form-body {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-top: 10px;
}

.preset-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.preset-label {
  font-size: 12px;
  color: #64748b;
  font-weight: 500;
  white-space: nowrap;
}

.preset-chips {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.preset-chip {
  border: 1px solid #e2e8f0;
  background: #f8fafc;
  color: #334155;
  font-size: 12px;
  padding: 4px 10px;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.15s ease;
  user-select: none;
}

.preset-chip:hover {
  border-color: #cbd5e1;
  background: #ffffff;
}

.preset-chip.is-selected {
  border-color: #3b82f6;
  background: #eff6ff;
  color: #1d4ed8;
  font-weight: 600;
}

.preset-chip.plus {
  color: #15803d;
}
.preset-chip.plus:hover {
  background: #f0fdf4;
  border-color: #86efac;
}

.preset-chip.minus {
  color: #b91c1c;
}
.preset-chip.minus:hover {
  background: #fef2f2;
  border-color: #fca5a5;
}

.compact-action-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.flex-input {
  flex: 1;
  min-width: 240px;
}

.points-input-num {
  width: 140px;
  flex-shrink: 0;
}

.live-projection-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #15803d;
  background: #f0fdf4;
  padding: 6px 12px;
  border-radius: 6px;
  border: 1px solid #bbf7d0;
}

/* 3 列状态平铺看板 */
.status-tiers-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  margin-top: 8px;
}

@media (max-width: 768px) {
  .status-tiers-grid {
    grid-template-columns: 1fr;
  }
}

.status-tier-card {
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 12px 14px;
  background: #ffffff;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  gap: 10px;
  transition: all 0.2s ease;
}

.status-tier-card:hover {
  border-color: #cbd5e1;
}

.status-tier-card.tier-active {
  border-width: 2px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
}

.tier-top {
  display: flex;
  align-items: center;
  gap: 8px;
}

.tier-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}
.tier-dot.green { background: #22c55e; }
.tier-dot.amber { background: #f59e0b; }
.tier-dot.red { background: #ef4444; }

.tier-name {
  font-size: 13px;
  font-weight: 700;
  color: #0f172a;
}

.active-badge {
  font-size: 11px;
  padding: 1px 7px;
  border-radius: 9999px;
  background: #f0fdf4;
  color: #15803d;
  border: 1px solid #bbf7d0;
  font-weight: 600;
  margin-left: auto;
}

.active-badge.amber {
  background: #fffbeb;
  color: #b45309;
  border-color: #fde68a;
}

.active-badge.red {
  background: #fef2f2;
  color: #b91c1c;
  border-color: #fecaca;
}

.tier-info {
  font-size: 11px;
  color: #64748b;
  line-height: 1.4;
}

.tier-btn-wrap {
  margin-top: 2px;
}

.tier-status-text {
  font-size: 12px;
  font-weight: 600;
}
.tier-status-text.green { color: #16a34a; }
.tier-status-text.amber { color: #d97706; }
.tier-status-text.red { color: #dc2626; }

/* 积分预测条 */
.balance-projection-bar {
  display: flex;
  align-items: center;
  gap: 14px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 10px 14px;
  flex-wrap: wrap;
}

.proj-segment {
  display: flex;
  flex-direction: column;
}

.proj-label {
  font-size: 11px;
  color: #64748b;
}

.proj-val {
  font-size: 15px;
  font-weight: 600;
  color: #0f172a;
}

.proj-op {
  font-size: 16px;
  color: #94a3b8;
  font-weight: 700;
}

.proj-warn-tip {
  margin-left: auto;
  font-size: 12px;
  color: #ef4444;
  font-weight: 600;
}

.text-green { color: #16a34a; }
.text-red { color: #ef4444; }
.text-primary { color: #2563eb; }
.text-muted { color: #94a3b8; }

.wallet-copy-cell {
  display: flex;
  align-items: center;
  gap: 6px;
}

.text-truncate {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.text-dim {
  color: #94a3b8;
}
</style>
