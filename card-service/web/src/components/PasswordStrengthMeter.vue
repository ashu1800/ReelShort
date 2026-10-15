<script setup lang="ts">
import { computed } from 'vue'
import { passwordStrength, MIN_PASSWORD_LENGTH } from '../utils/password.mjs'

const props = defineProps<{
  password: string
}>()

const strength = computed(() => passwordStrength(props.password))
const hintText = computed(() => {
  if (props.password === '') return `至少 ${MIN_PASSWORD_LENGTH} 位，建议混合大小写字母、数字与符号`
  const hints = strength.value.hints
  return hints.length > 0 ? hints.join('；') : '强度良好'
})
</script>

<template>
  <div class="strength">
    <div class="strength-head">
      <span class="form-hint">密码强度</span>
      <span class="strength-label" :class="`level-${strength.level}`" aria-live="polite">
        {{ strength.label }}
      </span>
    </div>
    <div
      class="strength-track"
      role="progressbar"
      :aria-valuenow="strength.score"
      aria-valuemin="0"
      aria-valuemax="4"
      :aria-label="`密码强度：${strength.label}`"
    >
      <span
        v-for="index in 4"
        :key="index"
        class="strength-cell"
        :class="index <= strength.score ? `level-${strength.level}` : ''"
      />
    </div>
    <p class="form-hint">{{ hintText }}</p>
  </div>
</template>

<style scoped>
.strength {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
  max-width: 100%;
}

.strength-head {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.strength-label {
  font-size: var(--fs-meta);
  font-weight: 600;
}

.strength-track {
  display: flex;
  gap: var(--space-1);
}

.strength-cell {
  height: 4px;
  flex: 1 1 0;
  border-radius: 2px;
  background: var(--c-border-strong);
}

.level-weak {
  color: var(--c-danger);
}

.strength-cell.level-weak {
  background: var(--c-danger);
}

.level-medium {
  color: var(--c-warning);
}

.strength-cell.level-medium {
  background: var(--c-warning);
}

.level-strong {
  color: var(--c-accent);
}

.strength-cell.level-strong {
  background: var(--c-accent);
}
</style>
