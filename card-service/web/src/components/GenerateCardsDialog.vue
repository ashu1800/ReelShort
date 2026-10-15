<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import GeneratedCodesPanel from './GeneratedCodesPanel.vue'
import { createCards, createCardsBatch } from '../api/admin'
import { errorMessage } from '../api/http'
import { MAX_BATCH_COUNT, MAX_CARD_LENGTH, MIN_CARD_LENGTH } from '../constants'
import type { CardType, GenerateCardsPayload, GenerateCardsResult } from '../types/api'
import type { GenerateCardsMode } from '../types/ui'

const open = defineModel<boolean>({ required: true })

const props = withDefaults(defineProps<{
  mode: GenerateCardsMode
  types: CardType[]
  defaultTypeId?: number | null
}>(), {
  defaultTypeId: null,
})

const emit = defineEmits<{
  generated: [result: GenerateCardsResult]
}>()

interface GenerateForm {
  type_id: number | null
  count: number
  length: number
  prefix: string
  max_rebinds: number | undefined
  batch_no: string
  remark: string
}

const formRef = ref<FormInstance>()
const form = reactive<GenerateForm>({
  type_id: null,
  count: 10,
  length: 16,
  prefix: '',
  max_rebinds: undefined,
  batch_no: '',
  remark: '',
})
const result = ref<GenerateCardsResult | null>(null)
const errorText = ref('')
const submitting = ref(false)

const isBatch = computed(() => props.mode === 'batch')
const title = computed(() => (isBatch.value ? '批量生成卡密' : '新增卡密'))
const countLimit = computed(() => (isBatch.value ? MAX_BATCH_COUNT : 1))

const rules: FormRules<GenerateForm> = {
  type_id: [{ required: true, message: '请选择卡密类型', trigger: 'change' }],
  count: [
    {
      validator: (_rule, value, callback) => {
        if (typeof value !== 'number' || !Number.isInteger(value) || value < 1 || value > countLimit.value) {
          callback(new Error(`数量需为 1-${countLimit.value} 的整数`))
          return
        }
        callback()
      },
      trigger: 'change',
    },
  ],
  prefix: [
    {
      validator: (_rule, value, callback) => {
        if (typeof value === 'string' && value !== '' && !/^[0-9A-Za-z]+$/.test(value)) {
          callback(new Error('前缀只能是字母或数字'))
          return
        }
        callback()
      },
      trigger: 'blur',
    },
  ],
}

watch(open, (isOpen) => {
  if (isOpen) {
    form.type_id = props.defaultTypeId ?? (props.types.length > 0 ? props.types[0].id : null)
    errorText.value = ''
    result.value = null
  }
})

async function handleSubmit(): Promise<void> {
  if (submitting.value) return
  errorText.value = ''
  const valid = await formRef.value?.validate().catch(() => false)
  if (valid !== true) return

  const payload: GenerateCardsPayload = {
    type_id: Number(form.type_id),
    count: isBatch.value ? form.count : 1,
    prefix: form.prefix.trim().toUpperCase(),
    length: form.length,
  }
  if (typeof form.max_rebinds === 'number') payload.max_rebinds = form.max_rebinds
  if (form.batch_no.trim() !== '') payload.batch_no = form.batch_no.trim()
  if (form.remark.trim() !== '') payload.remark = form.remark.trim()

  submitting.value = true
  try {
    const created = isBatch.value ? await createCardsBatch(payload) : await createCards(payload)
    result.value = created
    ElMessage.success(`已生成 ${created.count} 张卡密`)
    emit('generated', created)
  } catch (caught) {
    errorText.value = errorMessage(caught, '生成卡密失败，请稍后重试')
  } finally {
    submitting.value = false
  }
}

function handleContinue(): void {
  result.value = null
  errorText.value = ''
}
</script>

<template>
  <el-dialog v-model="open" :title="title" width="560px" destroy-on-close>
    <el-alert
      v-if="errorText"
      class="dialog-alert"
      type="error"
      :closable="false"
      show-icon
      :title="errorText"
    />

    <GeneratedCodesPanel
      v-if="result"
      :result="result"
      @continue="handleContinue"
    />

    <el-form
      v-else
      ref="formRef"
      :model="form"
      :rules="rules"
      label-width="96px"
      @submit.prevent="handleSubmit"
    >
      <el-form-item label="卡密类型" prop="type_id">
        <el-select v-model="form.type_id" placeholder="请选择卡密类型" style="width: 100%">
          <el-option
            v-for="type in props.types"
            :key="type.id"
            :label="type.enabled ? type.name : `${type.name}（已停用）`"
            :value="type.id"
          />
        </el-select>
      </el-form-item>

      <el-form-item v-if="isBatch" label="数量" prop="count">
        <el-input-number v-model="form.count" :min="1" :max="MAX_BATCH_COUNT" :step="10" />
        <span class="form-hint">单次最多 {{ MAX_BATCH_COUNT }} 张</span>
      </el-form-item>

      <el-form-item label="卡密长度">
        <el-input-number
          v-model="form.length"
          :min="MIN_CARD_LENGTH"
          :max="MAX_CARD_LENGTH"
          :step="4"
        />
        <span class="form-hint">{{ MIN_CARD_LENGTH }}–{{ MAX_CARD_LENGTH }} 位（不含分隔符）</span>
      </el-form-item>

      <el-form-item label="前缀" prop="prefix">
        <el-input v-model="form.prefix" placeholder="可空，仅字母数字，自动大写" maxlength="8" />
      </el-form-item>

      <el-form-item label="换绑上限">
        <el-input-number v-model="form.max_rebinds" :min="0" :max="100" placeholder="留空用默认值" />
        <span class="form-hint">留空使用系统默认换绑次数</span>
      </el-form-item>

      <el-form-item label="批次号">
        <el-input v-model="form.batch_no" placeholder="可空，留空由服务端生成" maxlength="64" />
      </el-form-item>

      <el-form-item label="备注">
        <el-input
          v-model="form.remark"
          type="textarea"
          :rows="2"
          maxlength="200"
          show-word-limit
          placeholder="可空"
        />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="open = false">关闭</el-button>
      <el-button
        v-if="!result"
        type="primary"
        :loading="submitting"
        :disabled="submitting"
        @click="handleSubmit"
      >
        {{ submitting ? '生成中…' : '生成' }}
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.dialog-alert {
  margin-bottom: var(--space-4);
}

.form-hint {
  margin-left: var(--space-2);
}
</style>
