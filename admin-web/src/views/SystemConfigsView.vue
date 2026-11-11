<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, onMounted, reactive, ref } from 'vue'
import {
  Setting,
  Lock,
  Coin,
  Film,
  Search,
  Refresh,
} from '@element-plus/icons-vue'
import { fetchSystemConfigs, updateSystemConfig } from '../services/adminApi'
import type { SystemConfig } from '../services/adminApi'
import {
  fetchReelshortAccount,
  saveReelshortAccount,
  testReelshortAccount,
} from '../services/adminApi'
import type { ReelshortAccountView } from '../services/adminApi'
import { configDisplayName, configDisplayDescription } from './systemConfigLabels'

const loading = ref(false)
const savingKey = ref('')
const error = ref('')
const configs = ref<SystemConfig[]>([])
const draftValues = reactive<Record<string, string>>({})

// 搜索与分类筛选
const searchQuery = ref('')
const activeCategory = ref<'ALL' | 'FUND' | 'POINTS' | 'CONTENT'>('ALL')

// W6a: 资金敏感配置键。改动会改变收款去向或用户实际到账金额，保存前必须二次确认。
// 仅做展示与确认，不在前端做任何金额换算。
const FUND_SENSITIVE_PREFIXES = ['withdraw.']
const FUND_SENSITIVE_KEYS = ['vip.collection-address', 'vip.price-usdt']

function isFundSensitiveConfig(configKey: string): boolean {
  return (
    FUND_SENSITIVE_KEYS.includes(configKey) ||
    FUND_SENSITIVE_PREFIXES.some((prefix) => configKey.startsWith(prefix))
  )
}

function isPointsConfig(configKey: string): boolean {
  return configKey.startsWith('points.')
}

// 内容上游切换：值只能是后端 SystemConfigRegistry 里的枚举，不能让管理员手输。
const UPSTREAM_CONFIG_KEY = 'content.upstream'
const UPSTREAM_OPTIONS = [
  { value: 'TWISTYTV', label: 'TwistyTV（默认）' },
  { value: 'REELSHORT', label: 'ReelShort（备用）' },
]

function isContentConfig(configKey: string): boolean {
  if (configKey === UPSTREAM_CONFIG_KEY) return true
  return configKey.startsWith('vip.') && !FUND_SENSITIVE_KEYS.includes(configKey)
}

function isUpstreamConfig(configKey: string): boolean {
  return configKey === UPSTREAM_CONFIG_KEY
}

const fundSensitiveCount = computed(
  () => configs.value.filter((c) => isFundSensitiveConfig(c.key)).length,
)
const pointsCount = computed(() => configs.value.filter((c) => isPointsConfig(c.key)).length)
const contentCount = computed(() => configs.value.filter((c) => isContentConfig(c.key)).length)

// 变更项数量
const dirtyCount = computed(() => {
  return configs.value.filter((c) => draftValues[c.key] !== c.value).length
})

// 过滤后的配置列表
const filteredConfigs = computed(() => {
  let list = configs.value
  const q = searchQuery.value.trim().toLowerCase()
  if (q) {
    list = list.filter(
      (c) =>
        c.key.toLowerCase().includes(q) ||
        configDisplayName(c.key).toLowerCase().includes(q) ||
        configDisplayDescription(c.key, c.description).toLowerCase().includes(q),
    )
  }
  if (activeCategory.value === 'FUND') {
    list = list.filter((c) => isFundSensitiveConfig(c.key))
  } else if (activeCategory.value === 'POINTS') {
    list = list.filter((c) => isPointsConfig(c.key))
  } else if (activeCategory.value === 'CONTENT') {
    list = list.filter((c) => isContentConfig(c.key))
  }
  return list
})

async function loadConfigs() {
  loading.value = true
  error.value = ''
  try {
    configs.value = await fetchSystemConfigs()
    configs.value.forEach((config) => {
      draftValues[config.key] = config.value
    })
  } catch {
    error.value = '系统配置加载失败'
  } finally {
    loading.value = false
  }
}

// M12: 配置值类型/范围校验，防止无效输入导致后端 500
function validateConfigValue(config: SystemConfig, value: string): string | null {
  const INTEGER_CONFIGS: Record<string, { min: number; max: number; label: string }> = {
    'points.watch.seconds-per-point': { min: 1, max: 3600, label: '每积分秒数' },
    'points.daily-earned.maximum': { min: 0, max: 100000, label: '每日积分上限' },
    'points.daily-earned.fluctuation-percent': { min: 0, max: 100, label: '每日浮动百分比' },
    'points.fair-mode.enabled': { min: 0, max: 1, label: '公平模式' },
    'vip.free-episodes': { min: 0, max: 100, label: '免费集数' },
    'vip.order-timeout-minutes': { min: 1, max: 1440, label: '订单超时分钟' },
    'withdraw.fee-percent': { min: 0, max: 99, label: '提现手续费百分比' },
  }
  const DECIMAL_CONFIGS: Record<string, { min: number; label: string }> = {
    'vip.price-usdt': { min: 0.01, label: 'VIP 价格' },
    'withdraw.usdt-per-50-points': { min: 0.00000001, label: '每50积分USDT' },
  }
  if (config.key in INTEGER_CONFIGS) {
    const rule = INTEGER_CONFIGS[config.key]
    const num = parseInt(value, 10)
    if (isNaN(num) || !/^\d+$/.test(value)) {
      return `${rule.label}必须是整数`
    }
    if (num < rule.min || num > rule.max) {
      return `${rule.label}必须在 ${rule.min}-${rule.max} 之间`
    }
  }
  if (config.key in DECIMAL_CONFIGS) {
    const rule = DECIMAL_CONFIGS[config.key]
    const num = parseFloat(value)
    if (isNaN(num) || num < rule.min) {
      return `${rule.label}必须是不小于 ${rule.min} 的数字`
    }
  }
  return null
}

// W6a: 资金敏感配置用 HTML 表格展示"旧值 → 新值"，值来自接口与管理员输入，统一转义避免注入
function escapeHtml(value: string): string {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

async function confirmFundSensitiveChange(config: SystemConfig, newValue: string): Promise<boolean> {
  const rows = [
    ['配置项', configDisplayName(config.key)],
    ['旧值', config.value],
    ['新值', newValue],
  ]
    .map(
      ([label, value]) =>
        `<tr><td style="padding: 4px 12px 4px 0; color: #6b7280; white-space: nowrap;">${escapeHtml(label)}</td>` +
        `<td style="padding: 4px 0; word-break: break-all;"><code>${escapeHtml(value)}</code></td></tr>`,
    )
    .join('')
  try {
    await ElMessageBox.confirm(
      `<p style="margin: 0 0 12px;">该配置影响资金收款去向或用户实际到账金额，保存后立即对后续订单和提现生效。</p>` +
        `<table style="border-collapse: collapse;">${rows}</table>`,
      '确认修改资金类配置',
      {
        dangerouslyUseHTMLString: true,
        confirmButtonText: '确认保存',
        cancelButtonText: '取消',
        type: 'warning',
      },
    )
    return true
  } catch {
    return false
  }
}

async function confirmUpstreamSwitch(config: SystemConfig, newValue: string): Promise<boolean> {
  const label = (candidate: string) =>
    UPSTREAM_OPTIONS.find((option) => option.value === candidate)?.label ?? candidate
  try {
    await ElMessageBox.confirm(
      `切换后立即清空视频库并在后台重新抓取（旧上游书目在新上游下无法播放），App 首页与搜索会换成新上游的内容。\n当前：${label(config.value)} → 切换为：${label(newValue)}`,
      '确认切换内容上游',
      { confirmButtonText: '确认切换', cancelButtonText: '取消', type: 'warning' },
    )
    return true
  } catch {
    return false
  }
}

async function saveConfig(config: SystemConfig) {
  const value = draftValues[config.key]?.trim()
  if (!value) {
    ElMessage.warning('配置值不能为空')
    return
  }
  // M12: 前端类型/范围校验
  const validationError = validateConfigValue(config, value)
  if (validationError) {
    ElMessage.warning(validationError)
    return
  }
  // W6a: 资金敏感配置保存前二次确认（普通配置保持原交互）
  if (isFundSensitiveConfig(config.key) && !(await confirmFundSensitiveChange(config, value))) {
    return
  }
  // 切上游会立刻清空视频库并重抓，App 首页/搜索整批换内容——必须让管理员确认一次。
  if (isUpstreamConfig(config.key) && !(await confirmUpstreamSwitch(config, value))) {
    return
  }
  savingKey.value = config.key
  try {
    const updated = await updateSystemConfig(config.key, value)
    const index = configs.value.findIndex((item) => item.key === updated.key)
    if (index >= 0) {
      configs.value[index] = updated
    }
    draftValues[updated.key] = updated.value
    ElMessage.success('系统配置已保存')
  } catch {
    ElMessage.error('系统配置保存失败')
  } finally {
    savingKey.value = ''
  }
}

function resetDraft(config: SystemConfig) {
  draftValues[config.key] = config.value
}

// ---- ReelShort 上游账号 ----
// 账号存后端数据库，保存即推给内容源；密码只进不出，表单留空表示不修改。
const account = ref<ReelshortAccountView>({
  emailMasked: '',
  configured: false,
  passwordSet: false,
  updatedAt: null,
  live: false,
  authenticated: false,
  vipKnown: false,
  vipActive: null,
  vipExpireAt: null,
  vipRenew: null,
  coins: null,
})
const accountForm = reactive({ email: '', password: '' })
const accountSaving = ref(false)
const accountTesting = ref(false)
const accountRefreshing = ref(false)
const accountStatus = ref<{ authenticated: boolean; message: string } | null>(null)

async function loadAccount() {
  try {
    account.value = await fetchReelshortAccount()
  } catch {
    // 账号卡片失败不影响整页配置表
  }
}

async function refreshAccount() {
  accountRefreshing.value = true
  try {
    await loadAccount()
  } finally {
    accountRefreshing.value = false
  }
}

// 会员状态三态：内容源没回话 / 已登录但查不到 / 明确的会员或非会员。
const vipLabel = computed(() => {
  if (!account.value.live || !account.value.vipKnown) return '会员未知'
  return account.value.vipActive ? 'VIP 会员' : '非会员'
})

const vipTagType = computed(() => {
  if (!account.value.live || !account.value.vipKnown) return 'warning'
  return account.value.vipActive ? 'success' : 'info'
})

const vipExpiryLabel = computed(() => {
  const expiry = account.value.vipExpireAt
  if (!account.value.vipActive || !expiry) return '-'
  const moment = new Date(expiry)
  if (isNaN(moment.getTime())) return expiry
  const days = Math.ceil((moment.getTime() - Date.now()) / 86_400_000)
  return `${formatDateTime(expiry).slice(0, 10)}（${days > 0 ? `剩 ${days} 天` : '已过期'}）`
})

const renewLabel = computed(() => {
  if (account.value.vipRenew === null) return '-'
  return account.value.vipRenew ? '自动续费' : '不自动续费'
})

const coinsLabel = computed(() =>
  account.value.coins === null ? '-' : String(account.value.coins),
)

function applyAccountResult(result: { authenticated: boolean; message: string }) {
  accountStatus.value = { authenticated: result.authenticated, message: result.message }
  if (result.authenticated) {
    ElMessage.success(result.message)
  } else {
    ElMessage.warning(result.message)
  }
}

async function submitAccount() {
  if (!accountForm.email.trim()) {
    ElMessage.warning('请填写 ReelShort 账号邮箱')
    return
  }
  accountSaving.value = true
  try {
    const result = await saveReelshortAccount(accountForm.email.trim(), accountForm.password)
    accountForm.password = ''
    await loadAccount()
    applyAccountResult(result)
  } catch {
    ElMessage.error('保存失败：请检查邮箱格式与密码长度（6–64 位）')
  } finally {
    accountSaving.value = false
  }
}

async function retryAccountLogin() {
  accountTesting.value = true
  try {
    applyAccountResult(await testReelshortAccount())
    // 重新登录后会员状态可能变了（比如刚买了会员），顺手刷新一次
    await loadAccount()
  } catch {
    ElMessage.error('测试失败：内容源暂不可达')
  } finally {
    accountTesting.value = false
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

onMounted(() => {
  void loadConfigs()
  void loadAccount()
})
</script>

<template>
  <section class="page-section">
    <!-- 顶部标题栏 -->
    <div class="page-header">
      <div>
        <div class="title-row">
          <h1>系统配置</h1>
          <el-tag effect="plain" type="primary" size="small" class="count-pill">
            共 {{ configs.length }} 项运行时参数
          </el-tag>
          <el-tag v-if="dirtyCount > 0" effect="dark" type="warning" size="small" class="dirty-pill">
            {{ dirtyCount }} 项已修改待保存
          </el-tag>
        </div>
        <p class="subtitle">维护观看奖励秒数、提现换算比例、VIP 收款地址与内容门禁等运营参数</p>
      </div>
      <el-button type="primary" :icon="Refresh" @click="loadConfigs">刷新配置</el-button>
    </div>

    <el-alert v-if="error" :title="error" show-icon type="error" />

    <!-- 顶部业务 KPI 统计卡片 (4 列) -->
    <div class="config-kpi-grid">
      <div class="kpi-card kpi-total">
        <div class="kpi-icon-box"><el-icon><Setting /></el-icon></div>
        <div class="kpi-content">
          <div class="kpi-label">已装载配置总数</div>
          <div class="kpi-num mono">{{ configs.length }}</div>
        </div>
      </div>

      <div class="kpi-card kpi-fund">
        <div class="kpi-icon-box"><el-icon><Lock /></el-icon></div>
        <div class="kpi-content">
          <div class="kpi-label">资金与收款核心项</div>
          <div class="kpi-num mono text-amber">{{ fundSensitiveCount }}</div>
        </div>
      </div>

      <div class="kpi-card kpi-points">
        <div class="kpi-icon-box"><el-icon><Coin /></el-icon></div>
        <div class="kpi-content">
          <div class="kpi-label">积分与观看奖励规则</div>
          <div class="kpi-num mono text-green">{{ pointsCount }}</div>
        </div>
      </div>

      <div class="kpi-card kpi-content">
        <div class="kpi-icon-box"><el-icon><Film /></el-icon></div>
        <div class="kpi-content">
          <div class="kpi-label">VIP 订阅与门禁参数</div>
          <div class="kpi-num mono text-purple">{{ contentCount }}</div>
        </div>
      </div>
    </div>

    <!-- ReelShort 上游账号：存后端库，保存即同步内容源，密码只进不出 -->
    <el-card shadow="never" class="table-card">
      <template #header>
        <div class="account-header">
          <span class="account-title">ReelShort 上游账号</span>
          <el-tag v-if="account.configured" size="small" type="success" effect="light">
            已配置 · {{ account.emailMasked }}
          </el-tag>
          <el-tag v-else size="small" type="info" effect="light">未配置</el-tag>
          <el-tag
            v-if="account.configured"
            size="small"
            effect="plain"
            :type="account.authenticated ? 'success' : 'info'"
          >
            {{ account.live ? (account.authenticated ? '已登录' : '未登录') : '登录态未知' }}
          </el-tag>
          <el-tag v-if="account.configured" size="small" effect="plain" :type="vipTagType">
            {{ vipLabel }}
          </el-tag>
          <el-tag
            v-if="accountStatus"
            size="small"
            effect="plain"
            :type="accountStatus.authenticated ? 'success' : 'warning'"
          >
            {{ accountStatus.authenticated ? '同步成功' : '同步失败' }}
          </el-tag>
          <el-button
            v-if="account.configured"
            text
            size="small"
            :loading="accountRefreshing"
            @click="refreshAccount"
          >
            刷新状态
          </el-button>
        </div>
      </template>

      <div v-if="account.configured" class="account-stats">
        <div class="account-stat">
          <span class="account-stat-label">会员到期</span>
          <span class="account-stat-value">{{ vipExpiryLabel }}</span>
        </div>
        <div class="account-stat">
          <span class="account-stat-label">续费</span>
          <span class="account-stat-value">{{ renewLabel }}</span>
        </div>
        <div class="account-stat">
          <span class="account-stat-label">金币余额</span>
          <span class="account-stat-value">{{ coinsLabel }}</span>
        </div>
        <div class="account-stat">
          <span class="account-stat-label">最近更新</span>
          <span class="account-stat-value">{{ formatDateTime(account.updatedAt) }}</span>
        </div>
      </div>

      <el-form label-width="88px" class="account-form" @submit.prevent>
        <el-form-item label="账号邮箱">
          <el-input v-model="accountForm.email" placeholder="登录 reelshort.com 用的邮箱" clearable />
        </el-form-item>
        <el-form-item label="密码">
          <el-input
            v-model="accountForm.password"
            type="password"
            show-password
            :placeholder="account.passwordSet ? '已保存；留空表示不修改' : '请输入 ReelShort 登录密码'"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="accountSaving" @click="submitAccount">
            保存并同步
          </el-button>
          <el-button
            :disabled="!account.configured"
            :loading="accountTesting"
            @click="retryAccountLogin"
          >
            测试登录
          </el-button>
          <span class="account-hint">
            密码只保存在服务端且不会回显；保存后立即推给内容源并尝试登录，不需要改配置文件或重启服务。
            <template v-if="accountStatus">最近一次：{{ accountStatus.message }}</template>
          </span>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 表格与分类过滤器卡片 -->
    <el-card shadow="never" class="table-card">
      <div class="table-filter-bar">
        <el-input
          v-model="searchQuery"
          placeholder="搜索配置项名称 / 说明..."
          :prefix-icon="Search"
          clearable
          class="search-input"
        />

        <div class="filter-group">
          <el-radio-group v-model="activeCategory" size="small">
            <el-radio-button value="ALL">全部 ({{ configs.length }})</el-radio-button>
            <el-radio-button value="FUND">资金敏感 ({{ fundSensitiveCount }})</el-radio-button>
            <el-radio-button value="POINTS">积分规则 ({{ pointsCount }})</el-radio-button>
            <el-radio-button value="CONTENT">内容门禁 ({{ contentCount }})</el-radio-button>
          </el-radio-group>
        </div>
      </div>

      <!-- 配置表格 -->
      <el-table v-loading="loading" :data="filteredConfigs" border stripe class="custom-table">
        <el-table-column label="配置项名称" min-width="240">
          <template #default="{ row }">
            <div class="config-name-cell">
              <div class="name-row">
                <span class="display-name">{{ configDisplayName(row.key) }}</span>
                <el-tag
                  v-if="isFundSensitiveConfig(row.key)"
                  class="fund-tag"
                  size="small"
                  type="danger"
                  effect="light"
                >
                  <el-icon><Lock /></el-icon> 资金敏感
                </el-tag>
              </div>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="配置值" min-width="280">
          <template #default="{ row }">
            <div class="config-input-wrap">
              <el-select
                v-if="isUpstreamConfig(row.key)"
                v-model="draftValues[row.key]"
                class="upstream-select"
                :class="{ 'is-dirty-input': draftValues[row.key] !== row.value }"
              >
                <el-option
                  v-for="option in UPSTREAM_OPTIONS"
                  :key="option.value"
                  :label="option.label"
                  :value="option.value"
                />
              </el-select>
              <el-input
                v-else
                v-model="draftValues[row.key]"
                :class="{ 'is-dirty-input': draftValues[row.key] !== row.value }"
                clearable
              >
                <template #suffix>
                  <span v-if="draftValues[row.key] !== row.value" class="dirty-dot" title="已修改未保存"></span>
                </template>
              </el-input>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="业务说明" min-width="260">
          <template #default="{ row }">
            <span class="desc-text">{{ configDisplayDescription(row.key, row.description) }}</span>
          </template>
        </el-table-column>

        <el-table-column label="更新时间" min-width="180">
          <template #default="{ row }">
            <span class="mono time-text">{{ formatDateTime(row.updatedAt) }}</span>
          </template>
        </el-table-column>

        <el-table-column align="center" fixed="right" label="操作" width="160">
          <template #default="{ row }">
            <div class="operation-actions">
              <el-button
                :loading="savingKey === row.key"
                type="primary"
                size="small"
                :disabled="draftValues[row.key] === row.value"
                @click="saveConfig(row)"
              >
                保存
              </el-button>
              <el-button
                v-if="draftValues[row.key] !== row.value"
                size="small"
                link
                @click="resetDraft(row)"
              >
                撤销
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
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

.count-pill {
  font-weight: 600;
  border-radius: 9999px;
}

.dirty-pill {
  font-weight: 600;
  border-radius: 9999px;
}

/* 顶部 KPI 卡片网格 (4列) */
.config-kpi-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 18px;
}

@media (max-width: 1024px) {
  .config-kpi-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 640px) {
  .config-kpi-grid {
    grid-template-columns: 1fr;
  }
}

.kpi-card {
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  padding: 16px;
  display: flex;
  align-items: center;
  gap: 14px;
  transition: all 0.2s ease-in-out;
}

.kpi-card:hover {
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

.kpi-total .kpi-icon-box { background: #eff6ff; color: #2563eb; }
.kpi-fund .kpi-icon-box { background: #fee2e2; color: #dc2626; }
.kpi-points .kpi-icon-box { background: #f0fdf4; color: #16a34a; }
.kpi-content .kpi-icon-box { background: #f5f3ff; color: #7c3aed; }

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
  font-size: 22px;
  font-weight: 700;
  color: #0f172a;
  margin-top: 2px;
}

.text-amber { color: #d97706; }
.text-green { color: #16a34a; }
.text-purple { color: #7c3aed; }

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
  max-width: 340px;
}

.config-name-cell {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.name-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.display-name {
  font-weight: 600;
  color: #0f172a;
  font-size: 13px;
}

.fund-tag {
  font-size: 10px;
  padding: 1px 6px;
  border-radius: 4px;
}

.is-dirty-input :deep(.el-input__wrapper) {
  border-color: #f59e0b;
  box-shadow: 0 0 0 1px #f59e0b inset;
}

.dirty-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background-color: #f59e0b;
}

.desc-text {
  font-size: 12px;
  color: #475569;
  line-height: 1.4;
}

.time-text {
  font-size: 12px;
  color: #64748b;
}

.operation-actions {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
}

.account-header {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.account-stats {
  display: flex;
  flex-wrap: wrap;
  gap: 28px;
  margin: 0 0 18px;
  padding: 12px 16px;
  border-radius: 8px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
}

.account-stat {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 120px;
}

.account-stat-label {
  font-size: 12px;
  color: #64748b;
}

.account-stat-value {
  font-size: 14px;
  font-weight: 600;
  color: #0f172a;
}

.account-title {
  font-weight: 600;
  color: #0f172a;
}

.account-form {
  max-width: 720px;
}

.account-hint {
  margin-left: 12px;
  font-size: 12px;
  color: #64748b;
  line-height: 1.5;
}
</style>
