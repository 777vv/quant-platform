<template>
  <div class="compare-page">
    <!-- 对比配置：最多 3 只基金 + 区间 -->
    <el-card shadow="never">
      <div class="compare-toolbar">
        <div class="compare-picker" v-for="index in MAX_FUNDS" :key="index">
          <span class="compare-slot-label" :style="{ color: slotColor(index - 1) }">基金{{ index }}</span>
          <el-select
            v-model="slots[index - 1]"
            clearable
            filterable
            placeholder="选择基金"
            style="width: 220px"
            @change="onSlotChange"
          >
            <el-option
              v-for="item in funds"
              :key="item.fundCode"
              :value="item.fundCode"
              :label="`${item.fundCode} ${item.fundName}`"
              :disabled="isSlotDisabled(item.fundCode)"
            />
          </el-select>
        </div>
        <el-select v-model="rangeDays" style="width: 120px" :disabled="!!dateRange" @change="onPresetChange">
          <el-option :value="90" label="近3个月" />
          <el-option :value="365" label="近1年" />
          <el-option :value="1095" label="近3年" />
          <el-option :value="1825" label="近5年" />
          <el-option :value="3650" label="近10年" />
        </el-select>
        <span class="compare-or">或</span>
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          value-format="YYYY-MM-DD"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          unlink-panels
          style="width: 250px"
          @change="onDateRangeChange"
        />
        <span class="muted">双击图表全屏查看</span>
      </div>
      <el-alert type="info" :closable="false" class="compare-tip">
        以区间首日归一为 100 对比涨跌幅；ETF 用前复权收盘价、场外用复权净值（均含分红再投资，口径可比）。
        可直接选预设区间、用日期选择器指定任意起止日期，或在图上按住拖动框选一段（自动回填日期并重载）。
      </el-alert>
    </el-card>

    <!-- 对比图 -->
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>走势对比（起点归一为 100）</span>
          <span class="muted">{{ loadedCount }}/3 只已选</span>
        </div>
      </template>
      <el-empty v-if="loadedCount === 0" description="请选择 2-3 只基金进行对比" :image-size="60" />
      <div v-else @dblclick="fullscreen = true">
        <ChartPanel :option="compareOption" height="480px" :brush="true" @brush-end="onBrushRange" />
      </div>
    </el-card>

    <!-- 区间涨跌幅与相关性小结：归一化之外的量化补充 -->
    <el-card v-if="summaries.length > 0" shadow="never">
      <template #header><span>区间表现</span></template>
      <el-table :data="summaries" size="small">
        <!-- 各列都用 min-width：剩余宽度由所有列均匀分摊，避免某一列（原来只有"基金"是弹性列）
             被撑到内容的数倍宽（实测基金列占满余量） -->
        <el-table-column prop="fundName" label="基金" min-width="200" show-overflow-tooltip />
        <el-table-column prop="startDate" label="起始日" min-width="120" />
        <el-table-column prop="endDate" label="截止日" min-width="120" />
        <el-table-column prop="dayCount" label="交易日" min-width="100" align="right">
          <template #default="{ row }"><span class="num">{{ row.dayCount }}</span></template>
        </el-table-column>
        <el-table-column label="区间涨跌幅" min-width="130" align="right">
          <template #default="{ row }">
            <span :class="changeColorClass(row.returnPct)" class="num">
              {{ row.returnPct === null ? '--' : (row.returnPct > 0 ? '+' : '') + row.returnPct.toFixed(2) + '%' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="年化收益率" min-width="130" align="right">
          <template #default="{ row }">
            <span :class="changeColorClass(row.annualizedReturnPct)" class="num">
              {{ row.annualizedReturnPct === null ? '--' : (row.annualizedReturnPct > 0 ? '+' : '') + row.annualizedReturnPct.toFixed(2) + '%' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="最大回撤" min-width="130" align="right">
          <template #default="{ row }">
            <span class="num text-down">{{ row.maxDrawdownPct === null ? '--' : row.maxDrawdownPct.toFixed(2) + '%' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="年化波动率" min-width="130" align="right">
          <template #default="{ row }">
            <span class="num">{{ row.volatilityPct === null ? '--' : row.volatilityPct.toFixed(2) + '%' }}</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 全屏对比 -->
    <Teleport to="body">
      <div v-if="fullscreen" class="chart-fullscreen">
        <div class="chart-fullscreen-head">
          <span class="section-title">基金走势对比（起点归一为 100）</span>
          <el-button link @click="fullscreen = false">关闭（Esc）</el-button>
        </div>
        <ChartPanel :option="compareOption" height="calc(100vh - 110px)" />
      </div>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { EChartsOption } from 'echarts'
import ChartPanel from '@/components/charts/ChartPanel.vue'
import { annualizedReturnPct, annualizedVolatilityPct, maxDrawdownPct, rangeReturnPct } from '@/utils/metrics'
import { fundKline, fundNav, fundOptions, type SeriesPoint, type FundOptionVO } from '@/api/fund'
import { changeColorClass } from '@/utils/format'
import { useEscToClose } from '@/utils/escClose'
import { PIE_PALETTE, AXIS_LABEL, AXIS_LINE, SPLIT_LINE } from '@/utils/palette'

/** 最多对比的基金数 */
const MAX_FUNDS = 3

const route = useRoute()
const router = useRouter()

const funds = ref<FundOptionVO[]>([])
/** 三个选择槽（空字符串表示未选） */
const slots = ref<string[]>(['', '', ''])
const rangeDays = ref(365)

/** 自定义日期区间（[start, end]；非空时优先于预设区间，二者互斥） */
const dateRange = ref<[string, string] | null>(null)

/** 当前图表 x 轴的日期序列（框选时把索引换算成日期用） */
const compareDates = ref<string[]>([])

/** 选预设区间时清掉自定义区间 */
function onPresetChange() {
  dateRange.value = null
  load()
}

/** 选自定义区间时清掉预设选中态（预设保持原值但不生效），随后按日期加载 */
function onDateRangeChange() {
  load()
}

/**
 * 图上框选一段：把选中的起止日期回填到日期选择器并重新加载该区间。
 * 对比图是归一化序列、无指标预热问题，直接按选区取数即可（y 轴会自动贴合这一段）。
 */
function onBrushRange(selected: { startIndex: number; endIndex: number }) {
  const dates = compareDates.value
  if (dates.length === 0) return
  const from = dates[Math.round(selected.startIndex)]
  const to = dates[Math.round(selected.endIndex)]
  if (!from || !to || from >= to) return
  dateRange.value = [from, to]
  load()
}
const fullscreen = ref(false)

// 全屏层按 Esc 退出（普通 div 无法接收 keydown，需全局监听）
useEscToClose(fullscreen)

/** 每只基金归一化后的序列数据 */
interface CompareSeries {
  fundCode: string
  fundName: string
  dates: string[]
  /** 归一化值（首日=100） */
  normalized: number[]
  returnPct: number | null
  annualizedReturnPct: number | null
  maxDrawdownPct: number | null
  volatilityPct: number | null
}

const seriesList = ref<CompareSeries[]>([])

const loadedCount = computed(() => seriesList.value.length)

/** 槽位配色（与图表序列顺序一致） */
function slotColor(index: number): string {
  return PIE_PALETTE[index % PIE_PALETTE.length]
}

/** 已被其它槽选中的基金不可重复选择 */
function isSlotDisabled(fundCode: string): boolean {
  return slots.value.includes(fundCode)
}

function onSlotChange() {
  load()
}

/** 加载所选基金的序列并归一化 */
async function load() {
  // 过滤必须用 falsy 判断：EP 可清空下拉点 × 后 v-model 是 undefined 而非 ''，
  // 只排除 '' 会让 undefined 混进取数列表，弹「undefined 不在自选池中」（V5.62 用户反馈）
  const codes = slots.value.filter((c) => c)
  if (codes.length === 0) {
    seriesList.value = []
    return
  }
  const results: CompareSeries[] = []
  compareDates.value = []
  for (const fundCode of codes) {
    const fund = funds.value.find((f) => f.fundCode === fundCode)
    if (!fund) {
      // 可能已移出自选池：明确提示而不是静默跳过，避免"选了却没画出来"的困惑
      ElMessage.warning(`${fundCode} 不在自选池中（可能已删除），已跳过`)
      continue
    }
    try {
      // 口径：ETF 前复权收盘价；场外复权净值（均含分红再投资，跨类型可比）
      const start = dateRange.value?.[0]
      const end = dateRange.value?.[1]
      const points: SeriesPoint[] =
        fund.fundType === 1
          ? await fundKline(fundCode, rangeDays.value, start, end)
          : await fundNav(fundCode, rangeDays.value, start, end)
      const values = points
        .map((p) => (fund.fundType === 1 ? Number(p.close) : Number(p.adjNav ?? p.unitNav)))
        .filter((v) => Number.isFinite(v) && v > 0)
      if (values.length < 2) {
        ElMessage.warning(`${fund.fundName} 在所选区间内没有足够数据`)
        continue
      }
      const base = values[0]
      if (compareDates.value.length === 0) {
        compareDates.value = points.map((p) => p.date)
      }
      results.push({
        fundCode,
        fundName: fund.fundName,
        dates: points.map((p) => p.date),
        normalized: values.map((v) => Number(((v / base) * 100).toFixed(3))),
        returnPct: rangeReturnPct(values),
        annualizedReturnPct: annualizedReturnPct(
          values,
          points[0]?.date ?? '',
          points[points.length - 1]?.date ?? ''
        ),
        maxDrawdownPct: maxDrawdownPct(values),
        volatilityPct: annualizedVolatilityPct(values)
      })
    } catch (e) {
      ElMessage.error(`${fund.fundName} 数据加载失败`)
    }
  }
  seriesList.value = results
}

/** 对比图：三条归一化曲线（共同日期轴，取并集以保证不同交易日历的基金也能同图） */
const compareOption = computed<EChartsOption>(() => {
  const allDates = Array.from(new Set(seriesList.value.flatMap((s) => s.dates))).sort()
  return {
    animation: false,
    tooltip: { trigger: 'axis', valueFormatter: (value) => (value === null ? '--' : Number(value).toFixed(2)) },
    legend: { top: 0, left: 'center', itemWidth: 14, itemHeight: 8 },
    grid: { left: '8%', right: '4%', top: '12%', bottom: '14%' },
    xAxis: {
      type: 'category',
      data: allDates,
      axisLine: { lineStyle: { color: AXIS_LINE } },
      axisLabel: { color: AXIS_LABEL }
    },
    yAxis: {
      type: 'value',
      name: '归一化（首日=100）',
      scale: true,
      axisLabel: { color: AXIS_LABEL },
      splitLine: { lineStyle: { color: SPLIT_LINE } }
    },
    dataZoom: [
      { type: 'inside', start: 0, end: 100 },
      { type: 'slider', height: 16, bottom: 4 }
    ],
    series: seriesList.value.map((item, index) => ({
      type: 'line',
      name: item.fundName,
      smooth: true,
      showSymbol: false,
      connectNulls: true,
      itemStyle: { color: PIE_PALETTE[index % PIE_PALETTE.length] },
      lineStyle: { width: 1.6, color: PIE_PALETTE[index % PIE_PALETTE.length] },
      // 按并集日期轴对齐：该基金当日无数据则留空（connectNulls 处理停牌/非交易日）
      data: allDates.map((date) => {
        const i = item.dates.indexOf(date)
        return i >= 0 ? item.normalized[i] : null
      })
    }))
  } as EChartsOption
})

/** 区间表现表数据 */
const summaries = computed(() =>
  seriesList.value.map((item) => ({
    fundName: `${item.fundCode} ${item.fundName}`,
    startDate: item.dates[0] ?? '--',
    endDate: item.dates[item.dates.length - 1] ?? '--',
    dayCount: item.dates.length,
    returnPct: item.returnPct,
    annualizedReturnPct: item.annualizedReturnPct,
    maxDrawdownPct: item.maxDrawdownPct,
    volatilityPct: item.volatilityPct
  }))
)

onMounted(async () => {
  funds.value = await fundOptions()
  // 从基金池列表"对比"按钮跳转时带 ?codes=510300,515080
  const codesParam = (route.query.codes as string | undefined) ?? ''
  const codes = codesParam.split(',').filter((c) => c !== '').slice(0, MAX_FUNDS)
  codes.forEach((code, index) => {
    slots.value[index] = code
  })
  if (codes.length > 0) {
    // 用 query 带参进入后清掉参数，避免刷新重复带入
    router.replace({ path: '/compare' })
    await load()
  }
})
</script>

<style scoped>
.compare-page {
  display: flex;
  flex-direction: column;
  gap: var(--q-space-3);
}

.compare-toolbar {
  display: flex;
  align-items: center;
  gap: var(--q-space-3);
  flex-wrap: wrap;
}

.compare-picker {
  display: flex;
  align-items: center;
  gap: var(--q-space-2);
}

.compare-slot-label {
  font-size: var(--q-font-xs);
  font-weight: 600;
}

.compare-or {
  font-size: var(--q-font-xs);
  color: var(--q-text-muted);
}

.compare-tip {
  margin-top: var(--q-space-3);
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: var(--q-font-base);
  font-weight: 600;
}

/* 全屏对比层（与详情页图表全屏一致的交互） */
.chart-fullscreen {
  position: fixed;
  inset: 0;
  z-index: 3000;
  display: flex;
  flex-direction: column;
  gap: var(--q-space-3);
  padding: var(--q-space-4);
  background: var(--q-bg-card);
}

.chart-fullscreen-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
</style>
