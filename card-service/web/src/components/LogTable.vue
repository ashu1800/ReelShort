<script setup lang="ts">
import { actionLabel, resultLabel, resultTagType } from '../utils/labels.mjs'
import { formatDateTime } from '../utils/format.mjs'
import type { Log } from '../types/api'

const props = withDefaults(defineProps<{
  logs: Log[]
  loading?: boolean
}>(), {
  loading: false,
})
</script>

<template>
  <el-table :data="props.logs" row-key="id">
    <el-table-column label="时间" width="170">
      <template #default="scope">{{ formatDateTime(scope.row.created_at) }}</template>
    </el-table-column>

    <el-table-column label="卡密" min-width="190">
      <template #default="scope">
        <span class="mono truncate" :title="scope.row.code_masked">{{ scope.row.code_masked || '—' }}</span>
      </template>
    </el-table-column>

    <el-table-column label="动作" width="140">
      <template #default="scope">
        <el-tooltip :content="`原始动作：${scope.row.action}`" placement="top" :show-after="200">
          <span>{{ actionLabel(scope.row.action) }}</span>
        </el-tooltip>
      </template>
    </el-table-column>

    <el-table-column label="结果" width="90">
      <template #default="scope">
        <el-tag :type="resultTagType(scope.row.result)" size="small" effect="light" disable-transitions>
          {{ resultLabel(scope.row.result) }}
        </el-tag>
      </template>
    </el-table-column>

    <el-table-column label="消息" min-width="180">
      <template #default="scope">
        <span v-if="scope.row.message" class="truncate" :title="scope.row.message">{{ scope.row.message }}</span>
        <span v-else class="text-muted">—</span>
      </template>
    </el-table-column>

    <el-table-column label="机器码" width="150">
      <template #default="scope">
        <span class="mono truncate" :title="scope.row.machine_code">{{ scope.row.machine_code || '—' }}</span>
      </template>
    </el-table-column>

    <el-table-column label="IP" width="130">
      <template #default="scope">{{ scope.row.ip || '—' }}</template>
    </el-table-column>

    <template #empty>
      <el-empty description="暂无操作日志" />
    </template>
  </el-table>
</template>
