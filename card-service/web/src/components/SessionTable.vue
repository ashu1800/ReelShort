<script setup lang="ts">
import { closeReasonLabel, sessionStateLabel } from '../utils/labels.mjs'
import { formatDateTime } from '../utils/format.mjs'
import type { Session } from '../types/api'

const props = withDefaults(defineProps<{
  sessions: Session[]
  loading?: boolean
  /** page：在线会话页；history：卡密详情抽屉里的会话历史 */
  mode?: 'page' | 'history'
  kickable?: boolean
}>(), {
  loading: false,
  mode: 'page',
  kickable: false,
})

const emit = defineEmits<{
  kick: [session: Session]
}>()

function userAgentText(value: string): string {
  return value === '' ? '—' : value
}
</script>

<template>
  <el-table :data="props.sessions" row-key="id">
    <el-table-column v-if="props.mode === 'page'" label="卡密" min-width="200">
      <template #default="scope">
        <span class="mono truncate" :title="scope.row.card_code">{{ scope.row.card_code }}</span>
      </template>
    </el-table-column>

    <el-table-column label="机器码" min-width="150">
      <template #default="scope">
        <span class="mono truncate" :title="scope.row.machine_code">
          {{ scope.row.machine_code || '—' }}
        </span>
      </template>
    </el-table-column>

    <el-table-column label="IP" width="130">
      <template #default="scope">{{ scope.row.ip || '—' }}</template>
    </el-table-column>

    <el-table-column v-if="props.mode === 'page'" label="User-Agent" min-width="200">
      <template #default="scope">
        <el-tooltip :content="userAgentText(scope.row.user_agent)" placement="top" :show-after="200">
          <span class="truncate ua-cell">{{ userAgentText(scope.row.user_agent) }}</span>
        </el-tooltip>
      </template>
    </el-table-column>

    <el-table-column v-if="props.mode === 'page'" label="创建时间" width="170">
      <template #default="scope">{{ formatDateTime(scope.row.created_at) }}</template>
    </el-table-column>

    <el-table-column label="最后心跳" width="170">
      <template #default="scope">{{ formatDateTime(scope.row.last_heartbeat_at) }}</template>
    </el-table-column>

    <el-table-column v-if="props.mode === 'page'" label="到期时间" width="170">
      <template #default="scope">{{ formatDateTime(scope.row.expires_at) }}</template>
    </el-table-column>

    <el-table-column v-else label="关闭原因" width="150">
      <template #default="scope">{{ closeReasonLabel(scope.row.close_reason) }}</template>
    </el-table-column>

    <el-table-column label="状态" width="100">
      <template #default="scope">
        <span class="session-state" :class="scope.row.active ? 'is-online' : 'is-closed'">
          <span class="dot" aria-hidden="true" />
          {{ sessionStateLabel(scope.row.active) }}
        </span>
      </template>
    </el-table-column>

    <el-table-column v-if="props.kickable" label="操作" width="110" fixed="right">
      <template #default="scope">
        <el-button
          v-if="scope.row.active"
          link
          type="danger"
          @click="emit('kick', scope.row)"
        >
          强制下线
        </el-button>
        <span v-else class="text-muted">—</span>
      </template>
    </el-table-column>

    <template #empty>
      <el-empty description="暂无会话记录" />
    </template>
  </el-table>
</template>

<style scoped>
.ua-cell {
  max-width: 100%;
}

.session-state {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  font-size: var(--fs-body);
}

.dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--c-text-muted);
  flex: none;
}

.session-state.is-online {
  color: var(--c-accent);
}

.session-state.is-online .dot {
  background: var(--c-accent);
}

.session-state.is-closed {
  color: var(--c-text-secondary);
}
</style>
