<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(defineProps<{
  loading: boolean
  error: string | null
  empty?: boolean
  emptyText?: string
  skeletonRows?: number
  retryText?: string
}>(), {
  empty: false,
  emptyText: '暂无数据',
  skeletonRows: 5,
  retryText: '重试',
})

const emit = defineEmits<{
  retry: []
}>()

const skeletonCount = computed(() => Math.max(1, props.skeletonRows))
</script>

<template>
  <div class="async-section">
    <div v-if="props.loading" class="async-skeleton" aria-busy="true" aria-live="polite">
      <span class="sr-only">正在加载数据</span>
      <el-skeleton :rows="skeletonCount" animated />
    </div>

    <div v-else-if="props.error" class="async-error" role="alert">
      <el-result icon="warning" title="数据加载失败" :sub-title="props.error">
        <template #extra>
          <el-button type="primary" @click="emit('retry')">{{ props.retryText }}</el-button>
        </template>
      </el-result>
    </div>

    <el-empty v-else-if="props.empty" :description="props.emptyText" />

    <slot v-else />
  </div>
</template>

<style scoped>
.async-section {
  display: block;
}

.async-skeleton {
  padding: var(--space-2) 0;
}

.async-error {
  padding: var(--space-2) 0;
}
</style>
