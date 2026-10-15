<script setup lang="ts">
/**
 * 系统设置（契约 3.5）：10 个运行参数，保存前展示「旧值 → 新值」二次确认。
 * 字段元数据来自 `utils/settings-fields.mjs`，与服务端校验规则保持一致（后端仍是最终权威）。
 */
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import PageHeader from '../components/PageHeader.vue'
import AsyncSection from '../components/AsyncSection.vue'
import { useSettingsStore } from '../stores/settings'
import { errorMessage } from '../api/http'
import { settingsToBag, buildSettingsPatch } from '../utils/settings-patch'
import { SETTING_FIELDS, diffSettings, validateSettingsDraft } from '../utils/settings-fields.mjs'

interface NumberField {
  type: 'number'
  key: string
  label: string
  hint: string
  min: number
  max: number
}

interface BooleanField {
  type: 'boolean'
  key: string
  label: string
  hint: string
}

interface SelectField {
  type: 'select'
  key: string
  label: string
  hint: string
  options: ReadonlyArray<{ value: string, label: string }>
}

type SettingField = NumberField | BooleanField | SelectField

type SettingValue = number | boolean | string
type SettingBag = Record<string, SettingValue>

const fields = SETTING_FIELDS as unknown as SettingField[]
const numberFields = fields.filter((field): field is NumberField => field.type === 'number')
const booleanFields = fields.filter((field): field is BooleanField => field.type === 'boolean')
const selectFields = fields.filter((field): field is SelectField => field.type === 'select')

const store = useSettingsStore()
const draft = ref<SettingBag>({})
const message = ref<string | null>(null)
const confirmOpen = ref(false)
const pendingChanges = ref<ReadonlyArray<{ key: string, label: string, oldText: string, newText: string }>>([])

const loading = computed(() => store.loading)
const error = computed(() => store.error)
const saving = computed(() => store.saving)
const hasSettings = computed(() => store.settings !== null)

async function load(force = false): Promise<void> {
  message.value = null
  const loaded = await store.load(force)
  draft.value = loaded === null ? {} : settingsToBag(loaded)
}

onMounted(() => {
  void load()
})

function numberValue(key: string): number {
  const value = draft.value[key]
  return typeof value === 'number' ? value : 0
}

function setNumber(key: string, value: number | undefined): void {
  draft.value = { ...draft.value, [key]: typeof value === 'number' && Number.isFinite(value) ? value : 0 }
}

function booleanValue(key: string): boolean {
  return draft.value[key] === true
}

function setBoolean(key: string, value: unknown): void {
  draft.value = { ...draft.value, [key]: value === true }
}

function stringValue(key: string): string {
  const value = draft.value[key]
  return typeof value === 'string' ? value : ''
}

function setString(key: string, value: unknown): void {
  draft.value = { ...draft.value, [key]: typeof value === 'string' ? value : String(value ?? '') }
}

function requestSave(): void {
  message.value = null
  const current = store.settings
  if (current === null) {
    message.value = '设置尚未加载完成，请刷新后重试'
    return
  }
  const errors = validateSettingsDraft(draft.value)
  if (errors.length > 0) {
    message.value = errors.map((item) => item.message).join('；')
    return
  }
  const changes = diffSettings(settingsToBag(current), draft.value)
  if (changes.length === 0) {
    ElMessage.info('没有需要保存的修改')
    return
  }
  pendingChanges.value = changes.map((change) => ({
    key: change.key,
    label: change.label,
    oldText: change.oldText,
    newText: change.newText,
  }))
  confirmOpen.value = true
}

async function confirmSave(): Promise<void> {
  const changedKeys = pendingChanges.value.map((change) => change.key)
  try {
    const saved = await store.save(buildSettingsPatch(draft.value, changedKeys))
    draft.value = settingsToBag(saved)
    confirmOpen.value = false
    ElMessage.success('设置已保存')
  } catch (caught) {
    confirmOpen.value = false
    message.value = errorMessage(caught, '保存失败')
  }
}

function revert(): void {
  message.value = null
  const current = store.settings
  draft.value = current === null ? {} : settingsToBag(current)
}
</script>

<template>
  <div class="page">
    <PageHeader title="系统设置" subtitle="修改会立即影响线上客户端行为，保存前会展示变更对比">
      <template #actions>
        <el-button :icon="Refresh" :loading="loading" @click="load(true)">重新加载</el-button>
      </template>
    </PageHeader>

    <AsyncSection
      :loading="loading"
      :error="error"
      :empty="!loading && error === null && !hasSettings"
      empty-text="暂无设置数据"
      :skeleton-rows="8"
      @retry="load(true)"
    >
      <section class="panel">
        <div class="panel-header">
          <h2 class="panel-title">会话与风控</h2>
          <span class="panel-hint">心跳、超时与顶号策略</span>
        </div>

        <el-form label-width="200px" label-position="right" class="settings-form">
          <el-form-item v-for="field in numberFields" :key="field.key" :label="field.label">
            <el-input-number
              :model-value="numberValue(field.key)"
              :min="field.min"
              :max="field.max"
              :step="1"
              @update:model-value="(value: number | undefined) => setNumber(field.key, value)"
            />
            <span class="form-hint">{{ field.hint }}</span>
          </el-form-item>

          <el-form-item v-for="field in booleanFields" :key="field.key" :label="field.label">
            <el-switch
              :model-value="booleanValue(field.key)"
              @update:model-value="(value: unknown) => setBoolean(field.key, value)"
            />
            <span class="form-hint">{{ field.hint }}</span>
          </el-form-item>

          <el-form-item v-for="field in selectFields" :key="field.key" :label="field.label">
            <el-select
              :model-value="stringValue(field.key)"
              style="width: 220px"
              @update:model-value="(value: unknown) => setString(field.key, value)"
            >
              <el-option
                v-for="option in field.options"
                :key="option.value"
                :label="option.label"
                :value="option.value"
              />
            </el-select>
            <span class="form-hint">{{ field.hint }}</span>
          </el-form-item>

          <p v-if="message" class="form-error" role="alert">{{ message }}</p>

          <el-form-item label=" ">
            <el-button type="primary" :loading="saving" @click="requestSave">保存设置</el-button>
            <el-button :disabled="saving" @click="revert">还原</el-button>
          </el-form-item>
        </el-form>
      </section>

      <section class="panel">
        <div class="panel-header">
          <h2 class="panel-title">参数说明</h2>
        </div>
        <ul class="notes">
          <li>心跳间隔只是给客户端的参考值；真正决定掉线的是「会话超时」——连续超过该时长没有心跳，会话即被关闭。</li>
          <li>「允许顶号」关闭后，同一张卡密已有在线会话时再次上线会被拒绝（<code>409 online_other</code>）；开启则新会话顶掉旧会话。</li>
          <li>换机不在顶号范围内：卡密绑定机器码，换机器必须走「换绑」（受每卡换绑次数限制）。</li>
          <li>「激活时机」决定有效期从哪一步开始计算：默认在校验卡密时激活；改为上线时激活则只校验不计时。</li>
          <li>限流按 IP / 卡密 / 会话三个维度独立计数，超限客户端会收到 <code>429</code> 提示。</li>
        </ul>
      </section>
    </AsyncSection>

    <el-dialog v-model="confirmOpen" title="确认保存设置" width="560px">
      <p class="confirm-intro">以下参数将立即生效：</p>
      <el-table :data="pendingChanges" size="small">
        <el-table-column prop="label" label="参数" min-width="180" />
        <el-table-column prop="oldText" label="原值" width="140" />
        <el-table-column label="新值" width="160">
          <template #default="scope">
            <span class="text-strong">{{ scope.row.newText }}</span>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="confirmOpen = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="confirmSave">确认保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.settings-form :deep(.el-form-item) {
  margin-bottom: var(--space-4);
}

.settings-form .form-hint {
  margin-left: var(--space-2);
}

.notes {
  margin: 0;
  padding-left: 1.25rem;
  color: var(--c-text-secondary);
  font-size: var(--fs-body);
  line-height: 1.9;
}

.confirm-intro {
  margin: 0 0 var(--space-2);
  color: var(--c-text-secondary);
  font-size: var(--fs-body);
}
</style>
