<script setup lang="ts">
/**
 * 在线会话（契约 3.5）：默认只看在线会话，支持按卡密筛选与强制下线。
 */
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import PageHeader from '../components/PageHeader.vue'
import AsyncSection from '../components/AsyncSection.vue'
import AppPagination from '../components/AppPagination.vue'
import SessionTable from '../components/SessionTable.vue'
import { fetchSessions, kickSession } from '../api/admin'
import { errorMessage } from '../api/http'
import { usePagedList } from '../composables/usePagedList'
import { normalizeCardCode } from '../utils/format.mjs'
import type { Session, SessionListQuery } from '../types/api'

const onlyActive = ref(true)
const cardCode = ref('')
const kicking = ref(false)

const { rows, loading, error, page, pageSize, total, load, search, retry, changePage, changePageSize } =
  usePagedList<Session>((targetPage, targetSize) => fetchSessions(buildQuery(targetPage, targetSize)))

function buildQuery(targetPage: number, targetSize: number): SessionListQuery {
  const query: SessionListQuery = { active: onlyActive.value, page: targetPage, page_size: targetSize }
  const code = normalizeCardCode(cardCode.value)
  if (code !== '') query.card_code = code
  return query
}

onMounted(() => {
  void load()
})

const isEmpty = computed(() => rows.value.length === 0)

async function kick(session: Session): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `确定把卡密 ${session.card_code} 的会话强制下线吗？该客户端下一次心跳会失败并需要重新上线。`,
      '强制下线',
      { type: 'warning', confirmButtonText: '强制下线', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  kicking.value = true
  try {
    await kickSession(session.id)
    ElMessage.success('已强制下线')
    await load()
  } catch (caught) {
    ElMessage.error(errorMessage(caught, '操作失败'))
  } finally {
    kicking.value = false
  }
}

function reset(): void {
  onlyActive.value = true
  cardCode.value = ''
  void search()
}
</script>

<template>
  <div class="page">
    <PageHeader title="在线会话" subtitle="心跳有效的会话；超时会话会由服务端自动关闭">
      <template #actions>
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
      </template>
    </PageHeader>

    <section class="panel">
      <div class="filter-bar">
        <div class="filter-item">
          <span class="filter-label">只看在线</span>
          <el-switch v-model="onlyActive" @change="search" />
        </div>
        <div class="filter-item filter-item-grow">
          <span class="filter-label">卡密</span>
          <el-input
            v-model="cardCode"
            placeholder="卡密（可带分隔符）"
            clearable
            @keyup.enter="search"
            @clear="search"
          />
        </div>
        <div class="filter-item">
          <el-button type="primary" @click="search">查询</el-button>
          <el-button @click="reset">重置</el-button>
        </div>
      </div>

      <AsyncSection
        :loading="loading"
        :error="error"
        :empty="!loading && error === null && isEmpty"
        empty-text="当前没有符合条件的会话"
        :skeleton-rows="6"
        @retry="retry"
      >
        <SessionTable
          :sessions="rows"
          :loading="loading"
          mode="page"
          kickable
          @kick="kick"
        />
        <AppPagination
          :page="page"
          :page-size="pageSize"
          :total="total"
          :disabled="kicking"
          @update:page="changePage"
          @update:page-size="changePageSize"
        />
      </AsyncSection>
    </section>
  </div>
</template>

<style scoped>
.filter-item-grow {
  flex: 1 1 240px;
  min-width: 200px;
}
</style>
