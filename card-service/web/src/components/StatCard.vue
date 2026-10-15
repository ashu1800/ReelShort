<script setup lang="ts">
import { computed } from 'vue'
import { formatInteger } from '../utils/format.mjs'

const props = withDefaults(defineProps<{
  label: string
  value: number
  hint?: string
  /** 强调色（第二行小字或数值着色） */
  tone?: 'default' | 'accent' | 'warning' | 'danger'
  loading?: boolean
}>(), {
  hint: '',
  tone: 'default',
  loading: false,
})

const displayValue = computed(() => formatInteger(props.value))
</script>

<template>
  <div class="stat-card">
    <p class="stat-label">{{ props.label }}</p>
    <el-skeleton v-if="props.loading" :rows="1" animated class="stat-skeleton" />
    <p v-else class="stat-value" :class="`tone-${props.tone}`">{{ displayValue }}</p>
    <p v-if="props.hint" class="stat-hint">{{ props.hint }}</p>
  </div>
</template>

<style scoped>
.stat-card {
  background: var(--c-surface);
  border: 1px solid var(--c-border);
  border-radius: var(--radius);
  padding: var(--space-4);
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
  min-width: 0;
}

.stat-label {
  color: var(--c-text-secondary);
  font-size: var(--fs-meta);
}

.stat-value {
  font-size: 24px;
  font-weight: 600;
  line-height: 1.25;
  font-variant-numeric: tabular-nums;
}

.stat-hint {
  color: var(--c-text-muted);
  font-size: var(--fs-meta);
}

.tone-accent {
  color: var(--c-accent);
}

.tone-warning {
  color: var(--c-warning);
}

.tone-danger {
  color: var(--c-danger);
}

.stat-skeleton {
  padding: var(--space-1) 0;
}
</style>
