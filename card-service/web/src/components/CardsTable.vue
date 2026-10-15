<script setup lang="ts">
import { ArrowDown, MoreFilled } from '@element-plus/icons-vue'
import CopyableText from './CopyableText.vue'
import StateTag from './StateTag.vue'
import { formatDateTime, formatExpiry, formatRemaining, maskCardCode, maskMachineCode } from '../utils/format.mjs'
import type { Card, CardSort } from '../types/api'
import type { CardRowAction } from '../types/ui'

const props = withDefaults(defineProps<{
  rows: Card[]
  loading?: boolean
  /** 页面级「显示明文」开关 */
  revealCodes?: boolean
}>(), {
  loading: false,
  revealCodes: false,
})

const emit = defineEmits<{
  action: [action: CardRowAction, card: Card]
  'selection-change': [cards: Card[]]
  sort: [sort: CardSort]
}>()

function handleSelectionChange(selection: unknown[]): void {
  emit('selection-change', selection as Card[])
}

function handleSortChange(payload: { prop?: string | null; order?: string | null }): void {
  if (payload.prop === 'expires_at' && payload.order === 'ascending') {
    emit('sort', 'expires_at_asc')
    return
  }
  if (payload.prop === 'created_at' && payload.order === 'ascending') {
    emit('sort', 'created_at_asc')
    return
  }
  emit('sort', 'created_at_desc')
}

function handleCommand(command: CardRowAction, card: Card): void {
  emit('action', command, card)
}
</script>

<template>
  <el-table
    v-loading="props.loading"
    :data="props.rows"
    row-key="id"
    :border="false"
    :show-overflow-tooltip="false"
    @selection-change="handleSelectionChange"
    @sort-change="handleSortChange"
  >
    <el-table-column type="selection" width="40" :selectable="() => true" />
    <el-table-column label="卡密" min-width="210">
      <template #default="scope">
        <CopyableText
          :value="String(scope.row.code)"
          :masked-value="maskCardCode(scope.row.code)"
          :reveal="props.revealCodes"
          label="卡密"
          copy-label="复制"
        />
      </template>
    </el-table-column>
    <el-table-column label="类型" width="100">
      <template #default="scope">{{ scope.row.type_name || '—' }}</template>
    </el-table-column>
    <el-table-column label="状态" width="120">
      <template #default="scope">
        <div class="state-cell">
          <StateTag :state="String(scope.row.display_state)" />
          <el-tooltip
            v-if="scope.row.online"
            :content="`在线机器码：${scope.row.online_machine || '—'}`"
            placement="top"
          >
            <span class="online-badge">在线</span>
          </el-tooltip>
        </div>
      </template>
    </el-table-column>
    <el-table-column label="绑定机器码" min-width="150">
      <template #default="scope">
        <CopyableText
          :value="String(scope.row.bound_machine || '')"
          :masked-value="maskMachineCode(scope.row.bound_machine)"
          label="机器码"
        />
      </template>
    </el-table-column>
    <el-table-column label="到期时间" width="170" prop="expires_at" sortable="custom">
      <template #default="scope">
        {{ formatExpiry(scope.row.expires_at, scope.row.permanent) }}
      </template>
    </el-table-column>
    <el-table-column label="剩余时长" width="150">
      <template #default="scope">
        {{ formatRemaining(scope.row.remaining_seconds, scope.row.permanent) }}
      </template>
    </el-table-column>
    <el-table-column label="换绑" width="90">
      <template #default="scope">
        <span class="nowrap">{{ scope.row.rebind_count }}/{{ scope.row.max_rebinds }}</span>
      </template>
    </el-table-column>
    <el-table-column label="批次" width="150" prop="batch_no">
      <template #default="scope">
        <span class="truncate" :title="scope.row.batch_no">{{ scope.row.batch_no || '—' }}</span>
      </template>
    </el-table-column>
    <el-table-column label="备注" min-width="140">
      <template #default="scope">
        <span v-if="scope.row.remark" class="truncate" :title="scope.row.remark">{{ scope.row.remark }}</span>
        <span v-else class="text-muted">—</span>
      </template>
    </el-table-column>
    <el-table-column label="创建时间" width="170" prop="created_at" sortable="custom">
      <template #default="scope">{{ formatDateTime(scope.row.created_at) }}</template>
    </el-table-column>
    <el-table-column label="操作" width="180" fixed="right">
      <template #default="scope">
        <div class="table-actions">
          <el-button link type="primary" @click="emit('action', 'detail', scope.row)">
            详情
          </el-button>
          <el-button
            v-if="scope.row.display_state === 'disabled'"
            link
            type="primary"
            @click="emit('action', 'enable', scope.row)"
          >
            启用
          </el-button>
          <el-button v-else link type="danger" @click="emit('action', 'disable', scope.row)">
            禁用
          </el-button>
          <el-dropdown trigger="click" @command="(command: CardRowAction) => handleCommand(command, scope.row)">
            <el-button link type="primary" :icon="ArrowDown" aria-label="更多操作" title="更多操作" />
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="unbind" :icon="MoreFilled">解绑机器码</el-dropdown-item>
                <el-dropdown-item command="reset-rebinds" :icon="MoreFilled">重置换绑次数</el-dropdown-item>
                <el-dropdown-item command="extend" :icon="MoreFilled">延期</el-dropdown-item>
                <el-dropdown-item command="delete" divided :icon="MoreFilled">删除</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </template>
    </el-table-column>

    <template #empty>
      <el-empty description="没有符合条件的卡密" />
    </template>
  </el-table>
</template>

<style scoped>
.state-cell {
  display: flex;
  align-items: center;
  gap: var(--space-1);
}

.online-badge {
  display: inline-flex;
  align-items: center;
  padding: 0 4px;
  border: 1px solid var(--c-accent);
  border-radius: var(--radius);
  color: var(--c-accent);
  font-size: var(--fs-meta);
  line-height: 16px;
  cursor: default;
}
</style>
