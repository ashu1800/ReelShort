<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import AsyncSection from './AsyncSection.vue'
import CopyableText from './CopyableText.vue'
import LogTable from './LogTable.vue'
import SessionTable from './SessionTable.vue'
import StateTag from './StateTag.vue'
import { fetchCardDetail } from '../api/admin'
import { errorMessage } from '../api/http'
import { useIsMobile } from '../composables/useMediaQuery'
import {
  formatDateTime,
  formatDurationText,
  formatExpiry,
  formatRemaining,
  maskCardCode,
  maskMachineCode,
} from '../utils/format.mjs'
import { storageStateLabel } from '../utils/labels.mjs'
import type { CardDetail } from '../types/api'

const open = defineModel<boolean>({ required: true })

const props = defineProps<{
  cardId: number | null
}>()

const isMobile = useIsMobile()
const drawerSize = computed(() => (isMobile.value ? '100%' : '720px'))

const detail = ref<CardDetail | null>(null)
const loading = ref(false)
const error = ref<string | null>(null)

async function load(): Promise<void> {
  if (props.cardId === null) return
  loading.value = true
  error.value = null
  try {
    detail.value = await fetchCardDetail(props.cardId)
  } catch (caught) {
    detail.value = null
    error.value = errorMessage(caught, '卡密详情加载失败')
  } finally {
    loading.value = false
  }
}

watch([open, () => props.cardId], ([isOpen]) => {
  if (isOpen) void load()
})
</script>

<template>
  <el-drawer v-model="open" title="卡密详情" :size="drawerSize" destroy-on-close>
    <AsyncSection
      :loading="loading"
      :error="error"
      :empty="detail === null"
      empty-text="未获取到卡密详情"
      :skeleton-rows="6"
      @retry="load"
    >
      <div v-if="detail" class="stack-3">
        <section aria-label="基本信息">
          <h3 class="panel-title">基本信息</h3>
          <el-descriptions class="detail-summary" :column="isMobile ? 1 : 2" size="small">
            <el-descriptions-item label="卡密">
              <CopyableText
                :value="detail.card.code"
                :masked-value="maskCardCode(detail.card.code)"
                label="卡密"
              />
            </el-descriptions-item>
            <el-descriptions-item label="状态">
              <span class="row-2">
                <StateTag :state="detail.card.display_state" />
                <span v-if="detail.card.online" class="text-muted">在线</span>
              </span>
            </el-descriptions-item>
            <el-descriptions-item label="类型">
              {{ detail.card.type_name || '—' }}（{{ formatDurationText(detail.card.duration_minutes) }}）
            </el-descriptions-item>
            <el-descriptions-item label="存储态">
              {{ storageStateLabel(detail.card.state) }}
            </el-descriptions-item>
            <el-descriptions-item label="绑定机器码">
              <CopyableText
                :value="detail.card.bound_machine || ''"
                :masked-value="maskMachineCode(detail.card.bound_machine)"
                label="机器码"
              />
            </el-descriptions-item>
            <el-descriptions-item label="绑定时间">
              {{ formatDateTime(detail.card.bound_at) }}
            </el-descriptions-item>
            <el-descriptions-item label="激活时间">
              {{ formatDateTime(detail.card.activated_at) }}
            </el-descriptions-item>
            <el-descriptions-item label="到期时间">
              {{ formatExpiry(detail.card.expires_at, detail.card.permanent) }}
            </el-descriptions-item>
            <el-descriptions-item label="剩余时长">
              {{ formatRemaining(detail.card.remaining_seconds, detail.card.permanent) }}
            </el-descriptions-item>
            <el-descriptions-item label="换绑次数">
              {{ detail.card.rebind_count }}/{{ detail.card.max_rebinds }}
              （剩余 {{ detail.card.rebind_remaining }} 次）
            </el-descriptions-item>
            <el-descriptions-item label="批次">
              {{ detail.card.batch_no || '—' }}
            </el-descriptions-item>
            <el-descriptions-item label="备注">
              {{ detail.card.remark || '—' }}
            </el-descriptions-item>
            <el-descriptions-item v-if="detail.card.online" label="在线机器码">
              <span class="mono">{{ detail.card.online_machine || '—' }}</span>
            </el-descriptions-item>
            <el-descriptions-item v-if="detail.card.online" label="上线时间">
              {{ formatDateTime(detail.card.online_at) }}
            </el-descriptions-item>
            <el-descriptions-item label="创建人">
              {{ detail.card.created_by || '—' }}
            </el-descriptions-item>
            <el-descriptions-item label="创建时间">
              {{ formatDateTime(detail.card.created_at) }}
            </el-descriptions-item>
            <el-descriptions-item label="更新时间">
              {{ formatDateTime(detail.card.updated_at) }}
            </el-descriptions-item>
          </el-descriptions>
        </section>

        <section aria-label="会话历史">
          <h3 class="panel-title sessions-title">会话历史（{{ detail.sessions.length }}）</h3>
          <SessionTable :sessions="detail.sessions" mode="history" />
        </section>

        <section aria-label="操作日志">
          <h3 class="panel-title sessions-title">操作日志（{{ detail.logs.length }}）</h3>
          <LogTable :logs="detail.logs" />
        </section>
      </div>
    </AsyncSection>
  </el-drawer>
</template>

<style scoped>
.sessions-title {
  margin-bottom: var(--space-2);
}

.detail-summary :deep(.el-descriptions__label) {
  width: 96px;
}
</style>
