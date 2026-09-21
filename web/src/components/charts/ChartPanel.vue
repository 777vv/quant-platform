<template>
  <div ref="chartEl" class="chart-panel" :style="{ height }"></div>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, ref, watch } from 'vue'
import * as echarts from 'echarts'

const props = withDefaults(defineProps<{ option: echarts.EChartsOption; height?: string; brush?: boolean }>(), {
  height: '360px',
  brush: false
})

const emit = defineEmits<{
  /**
   * 框选结束（仅 brush=true 时触发）。
   * 索引为类目轴上的**数据索引**（可能带小数），调用方据此换算成业务日期。
   */
  (e: 'brushEnd', range: { startIndex: number; endIndex: number }): void
  /**
   * 可见窗口变化（用户缩放/拖动 dataZoom 时触发）。
   * 百分比口径与 ECharts 一致：0=序列首、100=序列尾；调用方据此换算成日期区间。
   */
  (e: 'zoomChange', range: { startPercent: number; endPercent: number }): void
}>()

const chartEl = ref<HTMLElement>()
let chart: echarts.ECharts | null = null
let observer: ResizeObserver | null = null

/**
 * 注入框选配置（页面无需重复配置）：
 * 只在主图 x 轴上拉框（lineX），brushLink 关闭避免把副图一起刷上；
 * throttle 用 debounce，避免拖拽过程中反复触发。
 */
function withBrush(base: echarts.EChartsOption): echarts.EChartsOption {
  if (!props.brush) {
    return base
  }
  const toolbox = (base.toolbox ?? {}) as Record<string, unknown>
  const feature = (toolbox.feature ?? {}) as Record<string, unknown>
  return {
    ...base,
    toolbox: {
      ...toolbox,
      right: 12,
      feature: {
        ...feature,
        brush: { type: ['lineX', 'clear'] }
      }
    },
    brush: {
      xAxisIndex: 0,
      brushMode: 'single',
      brushLink: 'none',
      throttleType: 'debounce',
      throttleDelay: 300
    }
  } as echarts.EChartsOption
}

function render() {
  if (!chartEl.value) return
  if (!chart) {
    chart = echarts.init(chartEl.value)
    chart.on('dataZoom', () => {
      const zooms = chart?.getOption()?.dataZoom as Array<{ start?: number; end?: number }> | undefined
      const first = zooms?.[0]
      if (first && typeof first.start === 'number' && typeof first.end === 'number') {
        emit('zoomChange', { startPercent: first.start, endPercent: first.end })
      }
    })
    if (props.brush) {
      chart.on('brushEnd', (params: unknown) => {
        const areas = (params as { areas?: Array<{ coordRange?: number[] }> })?.areas
        const coordRange = areas?.[0]?.coordRange
        if (!coordRange || coordRange.length < 2) {
          return
        }
        emit('brushEnd', { startIndex: coordRange[0], endIndex: coordRange[1] })
      })
    }
  }
  chart.setOption(withBrush(props.option), true)
  if (props.brush) {
    // 让"按住拖动=框选"直接可用：默认需要先点工具箱里的框选图标，
    // 对"拖一段看这段"的用法太绕；滚轮缩放与滑块不受影响
    chart.dispatchAction({
      type: 'takeGlobalCursor',
      key: 'brush',
      brushOption: { brushType: 'lineX', brushMode: 'single' }
    })
  }
}

function resize() {
  chart?.resize()
}

watch(() => props.option, render, { deep: true })

/**
 * 高度 prop 变化时重算：容器高度由本组件的 `height` 决定（如打开「MACD/股息率副图」会变高），
 * 实测这种情况 ResizeObserver 不一定触发（宽度变化会），故这里显式补一条确定性路径——
 * 否则画布会停在旧高度、副图被压扁甚至重叠。
 */
watch(() => props.height, () => {
  // 用定时器而不是 requestAnimationFrame：后台标签页里 rAF 与 ResizeObserver 都会被浏览器冻结，
  // 只有普通事件与定时器照常执行（实测后台标签下高度变了但画布不跟）。
  window.setTimeout(() => resize(), 0)
  window.setTimeout(() => resize(), 200)
})

onMounted(() => {
  render()
  // 容器尺寸变化即重算：图表若在**隐藏的 Tab 分支**里创建（如基金详情的"指数估值"），
  // 创建时容器宽度为 0，ECharts 会退化成默认 100px 宽且不会自愈——切到该 Tab 后由这里纠正。
  if (chartEl.value && typeof ResizeObserver !== 'undefined') {
    observer = new ResizeObserver(() => resize())
    observer.observe(chartEl.value)
  }
  window.addEventListener('resize', resize)
})

onUnmounted(() => {
  observer?.disconnect()
  observer = null
  window.removeEventListener('resize', resize)
  chart?.dispose()
  chart = null
})
</script>

<style scoped>
.chart-panel {
  width: 100%;
}
</style>
