<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { maskCardCode } from '../utils/format.mjs'
import type { Card } from '../types/api'

const open = defineModel<boolean>({ required: true })

const props = withDefaults(defineProps<{
  /** 待延期的卡密（单个来自行操作，多个来自批量操作） */
  cards: Card[]
  submitting?: boolean
}>(), {
  submitting: false,
})

const emit = defineEmits<{
  submit: [days: number]
}>()

const days = ref(30)

const isSingle = computed(() => props.cards.length === 1)
const targetText = computed(() => {
  if (props.cards.length === 0) return '未选择卡密'
  if (isSingle.value) return maskCardCode(props.cards[0].code)
  return `已选择 ${props.cards.length} 张卡密`
})

const permanentCount = computed(() => props.cards.filter((card) => card.permanent).length)

watch(open, (isOpen) => {
  if (isOpen) days.value = 30
})

function handleSubmit(): void {
  const value = Number(days.value)
  if (!Number.isInteger(value) || value < 1 || value > 3650) {
    return
  }
  emit('submit', value)
}
</script>

<template>
  <el-dialog v-model="open" title="卡密延期" width="420px" destroy-on-close>
    <div class="stack-3">
      <p class="target-line">
        延期对象：<span class="mono">{{ targetText }}</span>
      </p>
      <p class="form-hint">
        从「当前时间」与「现有到期时间」中较晚者开始顺延；永久卡不受影响。
      </p>
      <el-alert
        v-if="permanentCount > 0"
        type="info"
        :closable="false"
        :title="`其中 ${permanentCount} 张为永久卡，延期不会改变其有效期`"
      />
      <el-form label-width="72px" @submit.prevent="handleSubmit">
        <el-form-item label="延期天数">
          <el-input-number v-model="days" :min="1" :max="3650" />
        </el-form-item>
      </el-form>
      <div class="quick-days">
        <el-button v-for="preset in [1, 7, 30, 90, 365]" :key="preset" size="small" @click="days = preset">
          {{ preset }} 天
        </el-button>
      </div>
    </div>

    <template #footer>
      <el-button @click="open = false">取消</el-button>
      <el-button type="primary" :loading="props.submitting" :disabled="props.submitting" @click="handleSubmit">
        确认延期
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.target-line {
  font-size: var(--fs-body);
}

.quick-days {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}
</style>
