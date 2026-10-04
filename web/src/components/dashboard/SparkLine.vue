<template>
  <!-- 迷你走势线：SVG 归一化绘制（无图表库开销），悬停按采样点显示日期与点位 -->
  <svg
    v-if="polyline"
    class="spark-line"
    viewBox="0 0 100 30"
    preserveAspectRatio="none"
    :aria-label="`走势图（${samples.length} 个采样点）`"
  >
    <polyline :points="polyline" fill="none" :stroke="color" stroke-width="1.6" stroke-linejoin="round" />
    <!-- 透明命中区：每个采样点一段，悬停由浏览器原生 tooltip 显示"日期 点位" -->
    <g v-if="hoverEnabled">
      <rect
        v-for="item in hoverBands"
        :key="item.label"
        :x="item.x"
        y="0"
        :width="item.width"
        height="30"
        fill="transparent"
      >
        <title>{{ item.label }} {{ formatPrice(item.value) }}</title>
      </rect>
    </g>
  </svg>
  <div v-else class="spark-line spark-line--empty">--</div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { FLAT } from '@/utils/palette'

/** 迷你走势线：传入价格采样点与可选日期标签，SVG 归一化绘制 */
const props = withDefaults(
  defineProps<{
    /** 价格采样点（按时间升序） */
    points: number[]
    /** 与 points 一一对应的日期标签（用于悬停提示，缺省则只显示点位） */
    labels?: string[]
    /** 折线颜色（红涨绿跌由调用方决定） */
    color?: string
  }>(),
  { color: FLAT, labels: () => [] }
)

/** 有效采样点（值 + 标签成对保留，剔除非法值以免索引错位） */
const samples = computed(() =>
  props.points
    .map((value, index) => ({ value, label: props.labels[index] ?? '' }))
    .filter((item) => Number.isFinite(item.value))
)

/** 归一化为 100x30 视窗内的折线坐标；点数不足时返回空 */
const polyline = computed<string | null>(() => {
  const values = samples.value.map((item) => item.value)
  if (values.length < 2) {
    return null
  }
  const min = Math.min(...values)
  const max = Math.max(...values)
  const span = max - min
  return values
    .map((value, index) => {
      const x = (index / (values.length - 1)) * 100
      const y = span === 0 ? 15 : 27 - ((value - min) / span) * 24
      return `${x.toFixed(1)},${y.toFixed(1)}`
    })
    .join(' ')
})

/** 是否有日期标签（无标签时不渲染命中区，避免无意义的悬停提示） */
const hoverEnabled = computed(() => props.labels.length === props.points.length && samples.value.length > 1)

/** 每个采样点的悬停命中带（宽度均分视窗） */
const hoverBands = computed(() => {
  const size = samples.value.length
  if (size < 2) {
    return []
  }
  const width = 100 / size
  return samples.value.map((item, index) => ({ x: index * width, width, label: item.label, value: item.value }))
})

/** 点位展示：去掉多余小数位 */
function formatPrice(value: number): string {
  return Number(value.toFixed(4)).toLocaleString('zh-CN')
}
</script>

<style scoped>
.spark-line {
  display: block;
  width: 100%;
  height: 22px;
}

.spark-line--empty {
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--q-text-muted);
  font-size: 13px;
}
</style>
