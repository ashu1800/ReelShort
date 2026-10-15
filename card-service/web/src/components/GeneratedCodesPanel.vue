<script setup lang="ts">
import { CopyDocument, Download } from '@element-plus/icons-vue'
import { useClipboard } from '../composables/useClipboard'
import { downloadText } from '../utils/download'
import { buildCodesCsv, buildCodesTxt, exportFileName } from '../utils/export.mjs'
import { formatInteger } from '../utils/format.mjs'
import type { GenerateCardsResult } from '../types/api'

const props = defineProps<{
  result: GenerateCardsResult
}>()

const emit = defineEmits<{
  continue: []
}>()

const { copying, copy } = useClipboard()

function handleCopyAll(): void {
  void copy(props.result.codes.join('\n'), `已复制 ${props.result.codes.length} 张卡密`)
}

function handleDownloadTxt(): void {
  downloadText(exportFileName(new Date(), 'txt'), buildCodesTxt(props.result.codes))
}

function handleDownloadCsv(): void {
  downloadText(exportFileName(new Date(), 'csv'), buildCodesCsv(props.result.codes), {
    bom: true,
    mime: 'text/csv;charset=utf-8',
  })
}
</script>

<template>
  <div class="stack-3">
    <el-alert
      type="success"
      :closable="false"
      show-icon
      :title="`已生成 ${formatInteger(props.result.count)} 张卡密`"
      :description="props.result.batch_no !== '' ? `批次号：${props.result.batch_no}` : '未指定批次号'"
    />

    <div class="table-toolbar">
      <el-button :icon="CopyDocument" :loading="copying" @click="handleCopyAll">复制全部</el-button>
      <el-button :icon="Download" @click="handleDownloadTxt">下载 TXT</el-button>
      <el-button :icon="Download" @click="handleDownloadCsv">下载 CSV</el-button>
      <div class="table-toolbar-spacer" />
      <el-button link type="primary" @click="emit('continue')">继续生成</el-button>
    </div>

    <ol class="code-list" aria-label="生成的卡密列表">
      <li v-for="code in props.result.codes" :key="code" class="code-item mono">{{ code }}</li>
    </ol>
  </div>
</template>

<style scoped>
.code-list {
  margin: 0;
  padding: var(--space-2) var(--space-3) var(--space-2) var(--space-6);
  max-height: 280px;
  overflow: auto;
  background: var(--c-surface-muted);
  border: 1px solid var(--c-border);
  border-radius: var(--radius);
  font-size: 13px;
  line-height: 1.8;
}

.code-item {
  word-break: break-all;
}
</style>
