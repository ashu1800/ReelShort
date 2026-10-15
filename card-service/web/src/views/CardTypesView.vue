<script setup lang="ts">
/**
 * 卡密类型管理（契约 3.4）：列表 / 新增 / 编辑 / 启停 / 删除。
 * 时长以「天」录入，`0` 表示永久；提交时换算为契约的 `duration_minutes`。
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import PageHeader from '../components/PageHeader.vue'
import AsyncSection from '../components/AsyncSection.vue'
import { createCardType, deleteCardType, fetchCardTypes, updateCardType } from '../api/admin'
import { errorMessage } from '../api/http'
import { formatDateTime } from '../utils/format.mjs'
import type { CardType, CardTypePayload } from '../types/api'

const types = ref<CardType[]>([])
const loading = ref(true)
const error = ref<string | null>(null)
const saving = ref(false)

const dialogOpen = ref(false)
const editingId = ref<number | null>(null)
const formError = ref<string | null>(null)

const form = reactive({
  name: '',
  permanent: false,
  durationDays: 30,
  maxRebinds: 3,
  enabled: true,
  sortOrder: 30,
})

const dialogTitle = computed(() => (editingId.value === null ? '新增卡密类型' : '编辑卡密类型'))
const durationMinutes = computed(() => (form.permanent ? 0 : Math.round(form.durationDays * 1440)))

async function load(): Promise<void> {
  loading.value = true
  error.value = null
  try {
    types.value = await fetchCardTypes()
  } catch (caught) {
    error.value = errorMessage(caught, '卡密类型加载失败')
    types.value = []
  } finally {
    loading.value = false
  }
}

onMounted(load)

const isEmpty = computed(() => types.value.length === 0)

function resetForm(): void {
  form.name = ''
  form.permanent = false
  form.durationDays = 30
  form.maxRebinds = 3
  form.enabled = true
  form.sortOrder = 30
  formError.value = null
}

function openCreate(): void {
  editingId.value = null
  resetForm()
  dialogOpen.value = true
}

function openEdit(row: CardType): void {
  editingId.value = row.id
  form.name = row.name
  form.permanent = row.duration_minutes <= 0
  form.durationDays = row.duration_minutes > 0 ? row.duration_minutes / 1440 : 30
  form.maxRebinds = row.max_rebinds
  form.enabled = row.enabled
  form.sortOrder = row.sort_order
  formError.value = null
  dialogOpen.value = true
}

function validate(): string | null {
  const name = form.name.trim()
  if (name === '') return '请填写类型名称'
  if (name.length > 32) return '类型名称不能超过 32 个字'
  if (!form.permanent) {
    if (!Number.isFinite(form.durationDays) || form.durationDays <= 0) return '时长必须大于 0 天；不限期请勾选「永久」'
    if (form.durationDays * 1440 > 5256000) return '时长不能超过 10 年'
  }
  if (!Number.isInteger(form.maxRebinds) || form.maxRebinds < 0 || form.maxRebinds > 100) {
    return '默认换绑上限需为 0–100 的整数'
  }
  if (!Number.isInteger(form.sortOrder) || form.sortOrder < 0 || form.sortOrder > 100000) {
    return '排序值需为 0–100000 的整数'
  }
  return null
}

async function submit(): Promise<void> {
  const invalid = validate()
  if (invalid !== null) {
    formError.value = invalid
    return
  }
  const payload: CardTypePayload = {
    name: form.name.trim(),
    duration_minutes: durationMinutes.value,
    max_rebinds: form.maxRebinds,
    enabled: form.enabled,
    sort_order: form.sortOrder,
  }
  saving.value = true
  try {
    if (editingId.value === null) {
      await createCardType(payload)
      ElMessage.success('卡密类型已创建')
    } else {
      await updateCardType(editingId.value, payload)
      ElMessage.success('卡密类型已更新')
    }
    dialogOpen.value = false
    await load()
  } catch (caught) {
    formError.value = errorMessage(caught, '保存失败')
  } finally {
    saving.value = false
  }
}

async function toggleEnabled(row: CardType): Promise<void> {
  try {
    await updateCardType(row.id, { enabled: !row.enabled })
    ElMessage.success(row.enabled ? '已停用' : '已启用')
    await load()
  } catch (caught) {
    ElMessage.error(errorMessage(caught, '操作失败'))
  }
}

async function remove(row: CardType): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `确定删除卡密类型「${row.name}」吗？该类型下仍有卡密时无法删除。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  try {
    await deleteCardType(row.id)
    ElMessage.success('已删除')
    await load()
  } catch (caught) {
    ElMessage.error(errorMessage(caught, '删除失败'))
  }
}
</script>

<template>
  <div class="page">
    <PageHeader title="卡密类型" subtitle="卡密时长模板：新建卡密时选择类型，时长与默认换绑上限自动带入">
      <template #actions>
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
        <el-button type="primary" :icon="Plus" @click="openCreate">新增类型</el-button>
      </template>
    </PageHeader>

    <section class="panel">
      <AsyncSection
        :loading="loading"
        :error="error"
        :empty="isEmpty"
        empty-text="还没有卡密类型"
        :skeleton-rows="5"
        @retry="load"
      >
        <el-table :data="types" row-key="id">
          <el-table-column label="名称" min-width="140">
            <template #default="scope">
              <span class="text-strong">{{ scope.row.name }}</span>
            </template>
          </el-table-column>

          <el-table-column label="时长" min-width="140">
            <template #default="scope">
              <span>{{ scope.row.duration_text }}</span>
              <span v-if="scope.row.duration_minutes > 0" class="text-muted">
                （{{ scope.row.duration_minutes }} 分钟）
              </span>
            </template>
          </el-table-column>

          <el-table-column label="默认换绑上限" width="150">
            <template #default="scope">{{ scope.row.max_rebinds }} 次</template>
          </el-table-column>

          <el-table-column label="排序" width="90">
            <template #default="scope">{{ scope.row.sort_order }}</template>
          </el-table-column>

          <el-table-column label="关联卡密" width="110">
            <template #default="scope">{{ scope.row.card_count }}</template>
          </el-table-column>

          <el-table-column label="状态" width="100">
            <template #default="scope">
              <el-tag :type="scope.row.enabled ? 'success' : 'info'" size="small" effect="light" disable-transitions>
                {{ scope.row.enabled ? '启用' : '停用' }}
              </el-tag>
            </template>
          </el-table-column>

          <el-table-column label="创建时间" width="170">
            <template #default="scope">{{ formatDateTime(scope.row.created_at) }}</template>
          </el-table-column>

          <el-table-column label="操作" width="200" fixed="right">
            <template #default="scope">
              <div class="table-actions">
                <el-button link type="primary" @click="openEdit(scope.row)">编辑</el-button>
                <el-button link @click="toggleEnabled(scope.row)">
                  {{ scope.row.enabled ? '停用' : '启用' }}
                </el-button>
                <el-button link type="danger" @click="remove(scope.row)">删除</el-button>
              </div>
            </template>
          </el-table-column>

          <template #empty>
            <el-empty description="还没有卡密类型" />
          </template>
        </el-table>
      </AsyncSection>
    </section>

    <el-dialog v-model="dialogOpen" :title="dialogTitle" width="520px" destroy-on-close>
      <el-form label-width="120px" label-position="right">
        <el-form-item label="类型名称" required>
          <el-input v-model="form.name" maxlength="32" show-word-limit placeholder="如：月卡" />
        </el-form-item>

        <el-form-item label="是否永久">
          <el-switch v-model="form.permanent" active-text="永久" inactive-text="按时长" />
        </el-form-item>

        <el-form-item v-if="!form.permanent" label="时长（天）" required>
          <el-input-number v-model="form.durationDays" :min="1" :max="3650" :step="1" />
          <span class="form-hint">折算 {{ durationMinutes }} 分钟</span>
        </el-form-item>

        <el-form-item label="默认换绑上限">
          <el-input-number v-model="form.maxRebinds" :min="0" :max="100" :step="1" />
          <span class="form-hint">新建卡密时的默认可换绑次数</span>
        </el-form-item>

        <el-form-item label="排序值">
          <el-input-number v-model="form.sortOrder" :min="0" :max="100000" :step="10" />
        </el-form-item>

        <el-form-item label="启用">
          <el-switch v-model="form.enabled" />
        </el-form-item>

        <p v-if="formError" class="form-error" role="alert">{{ formError }}</p>
      </el-form>

      <template #footer>
        <el-button @click="dialogOpen = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>
