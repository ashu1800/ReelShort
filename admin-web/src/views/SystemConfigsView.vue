<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { onMounted, reactive, ref } from 'vue'
import { fetchSystemConfigs, updateSystemConfig } from '../services/adminApi'
import type { SystemConfig } from '../services/adminApi'

const loading = ref(false)
const savingKey = ref('')
const error = ref('')
const configs = ref<SystemConfig[]>([])
const draftValues = reactive<Record<string, string>>({})

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

async function saveConfig(config: SystemConfig) {
  const value = draftValues[config.key]?.trim()
  if (!value) {
    ElMessage.warning('配置值不能为空')
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
      <el-table-column label="配置键" min-width="240" prop="key" />
      <el-table-column label="配置值" min-width="220">
        <template #default="{ row }">
          <el-input v-model="draftValues[row.key]" />
        </template>
      </el-table-column>
      <el-table-column label="说明" min-width="260" prop="description" />
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
