<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import PageHeader from '../components/PageHeader.vue'
import StatCard from '../components/StatCard.vue'
import TrendChart from '../components/TrendChart.vue'
import AsyncSection from '../components/AsyncSection.vue'
import { fetchStats } from '../api/admin'
import { errorMessage } from '../api/http'
import { formatDateTime, formatInteger } from '../utils/format.mjs'
import type { Stats } from '../types/api'

const stats = ref<Stats | null>(null)
const loading = ref(true)
const error = ref<string | null>(null)

async function load(): Promise<void> {
  loading.value = true
  error.value = null
  try {
    stats.value = await fetchStats()
  } catch (caught) {
    error.value = errorMessage(caught, '统计数据加载失败')
    stats.value = null
  } finally {
    loading.value = false
  }
}

onMounted(load)

const cards = computed(() => stats.value?.cards ?? null)
const today = computed(() => stats.value?.today ?? null)
const trend = computed(() => stats.value?.trend ?? [])
const serverTime = computed(() => stats.value?.server_time ?? '')
const isEmpty = computed(() => stats.value === null)
</script>

<template>
  <div class="page">
    <PageHeader title="概览" :subtitle="`服务器时间：${formatDateTime(serverTime)}`">
      <template #actions>
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
      </template>
    </PageHeader>

    <AsyncSection
      :loading="loading"
      :error="error"
      :empty="isEmpty"
      empty-text="暂无统计数据"
      :skeleton-rows="6"
      @retry="load"
    >
      <div class="stack-3">
        <section class="stat-grid" aria-label="卡密统计">
          <StatCard label="总卡密" :value="cards?.total ?? 0" tone="accent" />
          <StatCard label="未激活" :value="cards?.unused ?? 0" />
          <StatCard label="激活中" :value="cards?.active ?? 0" />
          <StatCard label="已过期" :value="cards?.expired ?? 0" tone="warning" />
          <StatCard label="已禁用" :value="cards?.disabled ?? 0" tone="danger" />
          <StatCard label="当前在线" :value="cards?.online ?? 0" tone="accent" hint="含心跳有效的会话" />
        </section>

        <section class="panel">
          <div class="panel-header">
            <h2 class="panel-title">今日数据</h2>
            <span class="panel-hint">按服务器自然日（Asia/Shanghai）统计</span>
          </div>
          <dl class="today-grid">
            <div class="today-item">
              <dt>激活</dt>
              <dd>{{ formatInteger(today?.activated ?? 0) }}</dd>
            </div>
            <div class="today-item">
              <dt>校验</dt>
              <dd>{{ formatInteger(today?.verified ?? 0) }}</dd>
            </div>
            <div class="today-item">
              <dt>上线</dt>
              <dd>{{ formatInteger(today?.logged_in ?? 0) }}</dd>
            </div>
            <div class="today-item">
              <dt>换绑</dt>
              <dd>{{ formatInteger(today?.rebinds ?? 0) }}</dd>
            </div>
          </dl>
        </section>

        <section class="panel">
          <div class="panel-header">
            <h2 class="panel-title">近 7 天激活趋势</h2>
            <span class="panel-hint">单位：张</span>
          </div>
          <TrendChart :points="trend" />
        </section>
      </div>
    </AsyncSection>
  </div>
</template>

<style scoped>
.stat-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: var(--space-3);
}

.today-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(120px, 1fr));
  gap: var(--space-3);
  margin: 0;
}

.today-item {
  border: 1px solid var(--c-border);
  border-radius: var(--radius);
  padding: var(--space-3);
}

.today-item dt {
  color: var(--c-text-secondary);
  font-size: var(--fs-meta);
}

.today-item dd {
  margin: var(--space-1) 0 0;
  font-size: 20px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}
</style>
