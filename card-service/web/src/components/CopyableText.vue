<script setup lang="ts">
import { computed, ref } from 'vue'
import { CopyDocument, Hide, View } from '@element-plus/icons-vue'
import { useClipboard } from '../composables/useClipboard'

const props = withDefaults(defineProps<{
  /** 明文值（复制的内容） */
  value: string
  /** 打码值；为空或与明文相同则不显示切换按钮 */
  maskedValue?: string
  /** 由外部强制显示明文（如列表页的「显示明文」总开关） */
  reveal?: boolean
  copyable?: boolean
  copyLabel?: string
  copySuccessMessage?: string
  emptyText?: string
  mono?: boolean
  /** 展示节点名称，用于无障碍文案，如「卡密」「机器码」 */
  label?: string
}>(), {
  maskedValue: '',
  reveal: false,
  copyable: true,
  copyLabel: '复制',
  copySuccessMessage: '已复制到剪贴板',
  emptyText: '—',
  mono: true,
  label: '内容',
})

const revealed = ref(false)
const { copying, copy } = useClipboard()

const hasValue = computed(() => typeof props.value === 'string' && props.value !== '')
const canToggle = computed(
  () => hasValue.value && props.maskedValue !== '' && props.maskedValue !== props.value,
)
const isPlain = computed(() => props.reveal || revealed.value || !canToggle.value)
const text = computed(() => (isPlain.value ? props.value : props.maskedValue))

function toggleReveal(): void {
  revealed.value = !revealed.value
}

function handleCopy(): void {
  void copy(props.value, props.copySuccessMessage)
}
</script>

<template>
  <span v-if="!hasValue" class="text-muted">{{ props.emptyText }}</span>
  <span v-else class="copyable-text" :class="{ mono: props.mono }">
    <span class="value" :title="text">{{ text }}</span>
    <el-button
      v-if="canToggle"
      text
      size="small"
      class="icon-action"
      :icon="isPlain ? Hide : View"
      :aria-label="isPlain ? `隐藏${props.label}` : `显示${props.label}明文`"
      :aria-pressed="isPlain"
      :title="isPlain ? '隐藏' : '显示明文'"
      @click="toggleReveal"
    />
    <el-button
      v-if="props.copyable"
      text
      size="small"
      class="icon-action"
      :icon="CopyDocument"
      :loading="copying"
      :aria-label="`${props.copyLabel}${props.label}`"
      :title="props.copyLabel"
      @click="handleCopy"
    />
  </span>
</template>

<style scoped>
.copyable-text {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  min-width: 0;
}

.value {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.icon-action {
  padding: 0 2px;
  height: 20px;
  min-height: 20px;
}
</style>
