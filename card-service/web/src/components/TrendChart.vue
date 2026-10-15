<script setup lang="ts">
import { computed } from 'vue'
import { formatInteger } from '../utils/format.mjs'
import type { TrendPoint } from '../types/api'

const props = withDefaults(defineProps<{
  points: TrendPoint[]
  /** 图表标题（用于无障碍描述） */
  title?: string
  emptyText?: string
}>(), {
  title: '近 7 天激活趋势',
  emptyText: '近 7 天暂无激活数据',
})

const WIDTH = 760
const HEIGHT = 260
const PADDING = { top: 16, right: 16, bottom: 32, left: 48 }
const PLOT_WIDTH = WIDTH - PADDING.left - PADDING.right
const PLOT_HEIGHT = HEIGHT - PADDING.top - PADDING.bottom

/** 坐标轴上限取「好看的整数」，避免出现 3.5 这类刻度。 */
function niceMax(value: number): number {
  if (!Number.isFinite(value) || value <= 0) return 1
  const magnitude = 10 ** Math.floor(Math.log10(value))
  for (const factor of [1, 2, 2.5, 5, 10]) {
    const candidate = magnitude * factor
    if (candidate >= value) return candidate
  }
  return magnitude * 10
}

const maxValue = computed(() => niceMax(Math.max(0, ...props.points.map((point) => point.activated))))
const isEmpty = computed(() => props.points.length === 0)

const gridLines = computed(() =>
  [0, 0.25, 0.5, 0.75, 1].map((ratio) => ({
    ratio,
    y: PADDING.top + PLOT_HEIGHT * (1 - ratio),
    value: Math.round(maxValue.value * ratio),
  })),
)

const bars = computed(() => {
  const count = props.points.length
  if (count === 0) return []
  const slot = PLOT_WIDTH / count
  const barWidth = Math.min(36, Math.max(10, slot * 0.5))
  return props.points.map((point, index) => {
    const value = Math.max(0, point.activated)
    const height = maxValue.value === 0 ? 0 : (value / maxValue.value) * PLOT_HEIGHT
    const x = PADDING.left + slot * index + (slot - barWidth) / 2
    return {
      key: `${point.date}-${index}`,
      date: point.date,
      shortDate: point.date.length >= 10 ? point.date.slice(5) : point.date,
      value,
      x,
      width: barWidth,
      y: PADDING.top + PLOT_HEIGHT - height,
      height,
      labelX: PADDING.left + slot * index + slot / 2,
    }
  })
})

const summary = computed(() =>
  props.points.map((point) => `${point.date} ${formatInteger(point.activated)}`).join('；'),
)
</script>

<template>
  <div class="trend-chart">
    <el-empty v-if="isEmpty" :description="props.emptyText" />
    <template v-else>
      <svg
        :viewBox="`0 0 ${WIDTH} ${HEIGHT}`"
        class="chart"
        role="img"
        :aria-label="`${props.title}，共 ${props.points.length} 天：${summary}`"
        preserveAspectRatio="xMidYMid meet"
      >
        <g aria-hidden="true">
          <line
            v-for="line in gridLines"
            :key="`grid-${line.ratio}`"
            :x1="PADDING.left"
            :x2="WIDTH - PADDING.right"
            :y1="line.y"
            :y2="line.y"
            class="grid-line"
          />
          <text
            v-for="line in gridLines"
            :key="`label-${line.ratio}`"
            :x="PADDING.left - 8"
            :y="line.y + 4"
            class="axis-label"
            text-anchor="end"
          >
            {{ formatInteger(line.value) }}
          </text>

          <g v-for="bar in bars" :key="bar.key">
            <rect
              :x="bar.x"
              :y="bar.y"
              :width="bar.width"
              :height="Math.max(bar.height, bar.value > 0 ? 2 : 0)"
              rx="2"
              class="bar"
            />
            <text :x="bar.labelX" :y="bar.y - 6" class="value-label" text-anchor="middle">
              {{ bar.value > 0 ? formatInteger(bar.value) : '' }}
            </text>
            <text :x="bar.labelX" :y="HEIGHT - PADDING.bottom + 18" class="axis-label" text-anchor="middle">
              {{ bar.shortDate }}
            </text>
          </g>
        </g>
      </svg>

      <ul class="sr-only">
        <li v-for="point in props.points" :key="point.date">
          {{ point.date }} 激活 {{ point.activated }} 张
        </li>
      </ul>
    </template>
  </div>
</template>

<style scoped>
.trend-chart {
  width: 100%;
  overflow-x: auto;
}

.chart {
  width: 100%;
  min-width: 480px;
  height: 260px;
  display: block;
}

.grid-line {
  stroke: var(--c-border);
  stroke-width: 1;
  stroke-dasharray: 3 3;
}

.axis-label {
  fill: var(--c-text-muted);
  font-size: 12px;
}

.value-label {
  fill: var(--c-text-secondary);
  font-size: 12px;
}

.bar {
  fill: var(--c-accent);
}

.bar:hover {
  fill: var(--c-accent-hover);
}
</style>
