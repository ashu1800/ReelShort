<script setup lang="ts">
import { PAGE_SIZE_OPTIONS } from '../constants'

const props = withDefaults(defineProps<{
  page: number
  pageSize: number
  total: number
  pageSizes?: readonly number[]
  disabled?: boolean
}>(), {
  pageSizes: () => PAGE_SIZE_OPTIONS,
  disabled: false,
})

const emit = defineEmits<{
  'update:page': [page: number]
  'update:pageSize': [pageSize: number]
}>()

function handlePageChange(nextPage: number): void {
  emit('update:page', nextPage)
}

function handleSizeChange(nextSize: number): void {
  emit('update:pageSize', nextSize)
}
</script>

<template>
  <div v-if="props.total > 0" class="pagination-bar">
    <span class="text-muted">共 {{ props.total }} 条</span>
    <el-pagination
      background
      layout="sizes, prev, pager, next, jumper"
      :current-page="props.page"
      :page-size="props.pageSize"
      :page-sizes="[...props.pageSizes]"
      :total="props.total"
      :disabled="props.disabled"
      @current-change="handlePageChange"
      @size-change="handleSizeChange"
    />
  </div>
</template>

<style scoped>
.pagination-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  margin-top: var(--space-3);
}
</style>
