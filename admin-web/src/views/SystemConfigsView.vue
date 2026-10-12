<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { onMounted, reactive, ref } from 'vue'
import { fetchSystemConfigs, updateSystemConfig } from '../services/adminApi'
import type { SystemConfig } from '../services/adminApi'
import { configDisplayName, configDisplayDescription } from './systemConfigLabels'

const loading = ref(false)
const savingKey = ref('')
const error = ref('')
const configs = ref<SystemConfig[]>([])
const draftValues = reactive<Record<string, string>>({})

// W6a: 资金敏感配置键。改动会改变收款去向或用户实际到账金额，保存前必须二次确认。
// 仅做展示与确认，不在前端做任何金额换算。
const FUND_SENSITIVE_PREFIXES = ['withdraw.']
const FUND_SENSITIVE_KEYS = ['vip.collection-address', 'vip.price-usdt']

function isFundSensitiveConfig(configKey: string): boolean {
  return FUND_SENSITIVE_KEYS.includes(configKey)
    || FUND_SENSITIVE_PREFIXES.some((prefix) => configKey.startsWith(prefix))
}

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
  ].map(([label, value]) =>
    `<tr><td style="padding: 4px 12px 4px 0; color: #6b7280; white-space: nowrap;">${escapeHtml(label)}</td>`
    + `<td style="padding: 4px 0; word-break: break-all;"><code>${escapeHtml(value)}</code></td></tr>`,
  ).join('')
  try {
    await ElMessageBox.confirm(
      `<p style="margin: 0 0 12px;">该配置影响资金收款去向或用户实际到账金额，保存后立即对后续订单和提现生效。</p>`
      + `<table style="border-collapse: collapse;">${rows}</table>`,
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

onMounted(loadConfigs)
</script>

<template>
  <section class="page-section">
    <div class="page-header">
      <div>
        <h1>系统配置</h1>
        <p>维护观看奖励、推荐策略等运营参数</p>
      </div>
      <el-button @click="loadConfigs">刷新</el-button>
    </div>
    <el-alert v-if="error" :title="error" show-icon type="error" />
    <el-table v-loading="loading" :data="configs" border>
      <el-table-column label="配置项" min-width="200">
        <template #default="{ row }">
          <span>{{ configDisplayName(row.key) }}</span>
          <el-tag v-if="isFundSensitiveConfig(row.key)" class="fund-tag" size="small" type="warning" effect="plain">
            资金类
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="配置值" min-width="220">
        <template #default="{ row }">
          <el-input v-model="draftValues[row.key]" />
        </template>
      </el-table-column>
      <el-table-column label="说明" min-width="260">
        <template #default="{ row }">
          <span>{{ configDisplayDescription(row.key, row.description) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="更新时间" min-width="220" prop="updatedAt" />
      <el-table-column align="right" label="操作" width="120">
        <template #default="{ row }">
          <el-button
            :loading="savingKey === row.key"
            type="primary"
            text
            @click="saveConfig(row)"
          >
            保存
          </el-button>
        </template>
      </el-table-column>
    </el-table>
  </section>
</template>

<style scoped>
.fund-tag {
  margin-left: 8px;
}
</style>
