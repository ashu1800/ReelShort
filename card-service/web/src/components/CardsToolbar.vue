<script setup lang="ts">
import { ArrowDown, Download, Files, Plus } from '@element-plus/icons-vue'
import type { BatchAction } from '../types/api'
import type { ExportTarget } from '../types/ui'

const props = withDefaults(defineProps<{
  selectedCount: number
  exporting?: boolean
  batchBusy?: boolean
}>(), {
  exporting: false,
  batchBusy: false,
})

const emit = defineEmits<{
  create: []
  generate: []
  batch: [action: BatchAction]
  export: [target: ExportTarget]
}>()

const reveal = defineModel<boolean>('reveal', { required: true })

function handleCommand(command: ExportTarget): void {
  emit('export', command)
}
</script>

<template>
  <section class="panel table-toolbar" aria-label="卡密操作">
    <el-button type="primary" :icon="Plus" @click="emit('create')">新增卡密</el-button>
    <el-button :icon="Files" @click="emit('generate')">批量生成</el-button>

    <template v-if="props.selectedCount > 0">
      <el-divider direction="vertical" />
      <span class="text-muted">已选 {{ props.selectedCount }} 张</span>
      <el-button size="small" :disabled="props.batchBusy" @click="emit('batch', 'disable')">
        批量禁用
      </el-button>
      <el-button size="small" :disabled="props.batchBusy" @click="emit('batch', 'enable')">
        批量启用
      </el-button>
      <el-button size="small" :disabled="props.batchBusy" @click="emit('batch', 'extend')">
        批量延期
      </el-button>
      <el-button
        size="small"
        type="danger"
        plain
        :disabled="props.batchBusy"
        @click="emit('batch', 'delete')"
      >
        批量删除
      </el-button>
    </template>

    <div class="table-toolbar-spacer" />

    <el-switch v-model="reveal" active-text="显示明文" aria-label="显示卡密明文" />
    <el-dropdown trigger="click" @command="handleCommand">
      <el-button :icon="Download" :loading="props.exporting">
        导出
        <el-icon class="el-icon--right"><ArrowDown /></el-icon>
      </el-button>
      <template #dropdown>
        <el-dropdown-menu>
          <el-dropdown-item command="csv">导出筛选结果 CSV</el-dropdown-item>
          <el-dropdown-item command="txt">导出筛选结果 TXT</el-dropdown-item>
          <el-dropdown-item command="page-csv" divided>导出当前页 CSV</el-dropdown-item>
        </el-dropdown-menu>
      </template>
    </el-dropdown>
  </section>
</template>
