<script setup lang="ts">
import { computed, ref } from 'vue'
import type { PointRecord } from '../services/adminApi'

const props = defineProps<{ records: PointRecord[] }>()

type DayRange = 7 | 30 | 90
const dayRange = ref<DayRange>(30)

const DAY_OPTIONS: { value: DayRange; label: string }[] = [
  { value: 7, label: '近 7 天' },
  { value: 30, label: '近 30 天' },
  { value: 90, label: '近 90 天' },
]

const SHANGHAI_DATE = new Intl.DateTimeFormat('en-US', {
  timeZone: 'Asia/Shanghai',
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
})

const CHART_WIDTH = 760
const CHART_HEIGHT = 220
const CHART_LEFT = 42
const CHART_RIGHT = 12
const CHART_TOP = 16
const CHART_BOTTOM = 30
const PLOT_WIDTH = CHART_WIDTH - CHART_LEFT - CHART_RIGHT
const PLOT_HEIGHT = CHART_HEIGHT - CHART_TOP - CHART_BOTTOM

function shanghaiDateKey(value: string | Date): string {
  const date = value instanceof Date ? value : new Date(value)
  if (Number.isNaN(date.getTime())) {
    return ''
  }
  const parts = SHANGHAI_DATE.formatToParts(date)
  const part = (type: string) => parts.find((item) => item.type === type)?.value ?? ''
  return `${part('year')}-${part('month')}-${part('day')}`
}

function buildDays(count: DayRange) {
  const [year, month, day] = shanghaiDateKey(new Date()).split('-').map(Number)
  return Array.from({ length: count }, (_, index) => {
    const date = new Date(Date.UTC(year, month - 1, day - (count - 1 - index)))
    return {
      key: date.toISOString().slice(0, 10),
      label: `${date.getUTCMonth() + 1}/${date.getUTCDate()}`,
    }
  })
}

const chart = computed(() => {
  const days = buildDays(dayRange.value)
  const pointsByDate = new Map<string, number>()
  for (const record of props.records) {
    if (record.source !== 'WATCH_REWARD' || record.amount <= 0) {
      continue
    }
    const key = shanghaiDateKey(record.createdAt)
    if (!key) {
      continue
    }
    pointsByDate.set(key, (pointsByDate.get(key) ?? 0) + record.amount)
  }

  const values = days.map((day) => ({ ...day, points: pointsByDate.get(day.key) ?? 0 }))
  const total = values.reduce((sum, day) => sum + day.points, 0)
  const activeDays = values.filter((day) => day.points > 0).length
  const peakPoints = values.length > 0 ? Math.max(...values.map((day) => day.points)) : 0
  const maxPoints = Math.max(peakPoints, 1)
  const slotWidth = PLOT_WIDTH / values.length
  const barWidth = Math.max(1, Math.min(18, slotWidth * 0.62))

  const bars = values.map((day, index) => {
    const height = day.points > 0 ? Math.max(2, Math.round((day.points / maxPoints) * PLOT_HEIGHT)) : 0
    return {
      ...day,
      x: CHART_LEFT + index * slotWidth + (slotWidth - barWidth) / 2,
      y: CHART_TOP + PLOT_HEIGHT - height,
      width: barWidth,
      height,
    }
  })

  const gridLines = [0, 0.5, 1].map((ratio) => ({
    y: CHART_TOP + PLOT_HEIGHT * (1 - ratio),
    label: Math.round(maxPoints * ratio).toLocaleString(),
  }))

  const labelIndexes = [...new Set([0, Math.floor((values.length - 1) / 2), values.length - 1])]
  const xLabels = labelIndexes.map((index) => ({
    x: CHART_LEFT + index * slotWidth + slotWidth / 2,
    label: values[index].label,
  }))

  return {
    days: bars,
    gridLines,
    xLabels,
    total,
    activeDays,
    average: values.length > 0 ? total / values.length : 0,
    peakPoints,
    maxPoints,
  }
})

function formatRate(value: number): string {
  return value.toFixed(2)
}
</script>

<template>
  <section class="points-chart-panel">
    <div class="chart-toolbar">
      <div>
        <div class="chart-title">积分获取趋势</div>
        <div class="chart-subtitle">仅统计观看奖励（WATCH_REWARD），按上海时区自然日汇总</div>
      </div>
      <el-radio-group v-model="dayRange" size="small">
        <el-radio-button v-for="option in DAY_OPTIONS" :key="option.value" :value="option.value">
          {{ option.label }}
        </el-radio-button>
      </el-radio-group>
    </div>

    <div class="chart-summary">
      <div class="summary-item">
        <span class="summary-label">区间获取</span>
        <strong class="summary-value mono">{{ chart.total.toLocaleString() }}</strong>
      </div>
      <div class="summary-item">
        <span class="summary-label">日均获取</span>
        <strong class="summary-value mono">{{ formatRate(chart.average) }}</strong>
      </div>
      <div class="summary-item">
        <span class="summary-label">获取天数</span>
        <strong class="summary-value mono">{{ chart.activeDays }} 天</strong>
      </div>
      <div class="summary-item">
        <span class="summary-label">单日峰值</span>
        <strong class="summary-value mono">{{ chart.peakPoints.toLocaleString() }}</strong>
      </div>
    </div>

    <el-empty v-if="chart.total === 0" description="所选区间暂无观看奖励记录" :image-size="72" />
    <svg
      v-else
      class="chart-svg"
      :viewBox="`0 0 ${CHART_WIDTH} ${CHART_HEIGHT}`"
      role="img"
      aria-label="用户积分获取趋势图"
    >
      <line
        v-for="grid in chart.gridLines"
        :key="`grid-${grid.label}`"
        :x1="CHART_LEFT"
        :x2="CHART_WIDTH - CHART_RIGHT"
        :y1="grid.y"
        :y2="grid.y"
        class="chart-grid-line"
      />
      <text
        v-for="grid in chart.gridLines"
        :key="`grid-label-${grid.label}`"
        :x="CHART_LEFT - 8"
        :y="grid.y + 3"
        text-anchor="end"
        class="chart-axis-label"
      >
        {{ grid.label }}
      </text>
      <rect
        v-for="day in chart.days"
        :key="day.key"
        :x="day.x"
        :y="day.y"
        :width="day.width"
        :height="day.height"
        rx="2"
        class="chart-bar"
      >
        <title>{{ day.key }}：{{ day.points.toLocaleString() }} 积分</title>
      </rect>
      <text
        v-for="label in chart.xLabels"
        :key="`x-${label.label}`"
        :x="label.x"
        :y="CHART_HEIGHT - 8"
        text-anchor="middle"
        class="chart-axis-label"
      >
        {{ label.label }}
      </text>
    </svg>
  </section>
</template>

<style scoped>
.points-chart-panel {
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  background: #ffffff;
  padding: 16px;
}

.chart-toolbar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}

.chart-title {
  color: #0f172a;
  font-size: 15px;
  font-weight: 600;
}

.chart-subtitle {
  margin-top: 4px;
  color: #64748b;
  font-size: 12px;
}

.chart-summary {
  display: flex;
  gap: 32px;
  flex-wrap: wrap;
  margin: 16px 0 10px;
  padding-bottom: 12px;
  border-bottom: 1px dashed #e2e8f0;
}

.summary-item {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.summary-label {
  color: #64748b;
  font-size: 12px;
}

.summary-value {
  color: #0f172a;
  font-size: 18px;
  font-weight: 700;
}

.chart-svg {
  display: block;
  width: 100%;
  height: 220px;
  overflow: visible;
}

.chart-grid-line {
  stroke: #e2e8f0;
  stroke-width: 1;
}

.chart-axis-label {
  fill: #94a3b8;
  font-size: 11px;
}

.chart-bar {
  fill: #10b981;
  transition: fill 0.15s ease;
}

.chart-bar:hover {
  fill: #059669;
}
</style>
