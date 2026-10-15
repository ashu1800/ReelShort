<script setup lang="ts">
import { RefreshLeft, Search } from '@element-plus/icons-vue'
import { CARD_STATE_FILTERS, cardStateFilterLabel } from '../utils/labels.mjs'
import type { CardType } from '../types/api'
import type { CardFilters } from '../types/filters'

const filters = defineModel<CardFilters>({ required: true })

const props = withDefaults(defineProps<{
  types: CardType[]
  loading?: boolean
}>(), {
  loading: false,
})

const emit = defineEmits<{
  search: []
  reset: []
}>()

const stateOptions = CARD_STATE_FILTERS.map((value) => ({
  value,
  label: cardStateFilterLabel(value),
}))

/** el-date-picker 的模型值形态不稳定，这里显式收窄为 `YYYY-MM-DD` 元组。 */
function handleRangeChange(value: unknown): void {
  if (
    Array.isArray(value)
    && value.length === 2
    && typeof value[0] === 'string'
    && typeof value[1] === 'string'
    && value[0] !== ''
    && value[1] !== ''
  ) {
    filters.value.createdRange = [value[0], value[1]]
    return
  }
  filters.value.createdRange = null
}
</script>

<template>
  <section class="panel filter-bar" aria-label="卡密筛选条件">
    <div class="filter-item">
      <label class="filter-label" for="card-filter-keyword">关键词</label>
      <el-input
        id="card-filter-keyword"
        v-model="filters.keyword"
        placeholder="卡密 / 机器码 / 批次 / 备注"
        clearable
        style="width: 220px"
        @keyup.enter="emit('search')"
      />
    </div>

    <div class="filter-item">
      <label class="filter-label" for="card-filter-state">状态</label>
      <el-select
        id="card-filter-state"
        v-model="filters.state"
        placeholder="全部状态"
        clearable
        style="width: 140px"
      >
        <el-option
          v-for="option in stateOptions"
          :key="option.value"
          :label="option.label"
          :value="option.value"
        />
      </el-select>
    </div>

    <div class="filter-item">
      <label class="filter-label" for="card-filter-type">类型</label>
      <el-select
        id="card-filter-type"
        v-model="filters.type_id"
        placeholder="全部类型"
        clearable
        style="width: 160px"
      >
        <el-option
          v-for="type in props.types"
          :key="type.id"
          :label="type.name"
          :value="type.id"
        />
      </el-select>
    </div>

    <div class="filter-item">
      <label class="filter-label" for="card-filter-batch">批次</label>
      <el-input
        id="card-filter-batch"
        v-model="filters.batch_no"
        placeholder="批次号"
        clearable
        style="width: 160px"
        @keyup.enter="emit('search')"
      />
    </div>

    <div class="filter-item">
      <label class="filter-label" for="card-filter-created">创建日期</label>
      <el-date-picker
        id="card-filter-created"
        :model-value="filters.createdRange"
        type="daterange"
        value-format="YYYY-MM-DD"
        unlink-panels
        range-separator="至"
        start-placeholder="开始日期"
        end-placeholder="结束日期"
        style="width: 260px"
        @update:model-value="handleRangeChange"
      />
    </div>

    <div class="filter-actions">
      <el-button type="primary" :icon="Search" :loading="props.loading" @click="emit('search')">
        查询
      </el-button>
      <el-button :icon="RefreshLeft" @click="emit('reset')">重置</el-button>
    </div>
  </section>
</template>

<style scoped>
.filter-actions {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  margin-left: auto;
}
</style>
