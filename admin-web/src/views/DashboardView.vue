<script setup lang="ts">
const metrics = [
  { label: '用户管理', value: '待接入' },
  { label: '观看记录', value: '待接入' },
  { label: '积分流水', value: '待接入' },
  { label: '内容缓存', value: '待接入' },
]
</script>

<template>
  <section>
    <h1>控制台</h1>
    <div class="metric-grid">
      <div v-for="metric in metrics" :key="metric.label" class="metric">
        <div class="metric-label">{{ metric.label }}</div>
        <div class="metric-value">{{ metric.value }}</div>
      </div>
    </div>
  </section>
</template>
