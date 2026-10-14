<template>
  <el-radio-group v-if="props.fundType === 2" v-model="navMode" size="small" class="block">
    <el-radio-button value="unitNav">单位净值</el-radio-button>
    <el-radio-button value="accNav">累计净值</el-radio-button>
    <el-radio-button value="adjNav">复权净值</el-radio-button>
  </el-radio-group>
  <div class="chart-toolbar">
    <el-select
      v-model="rangeDays"
      size="small"
      style="width: 110px"
      :disabled="!!chartDateRange"
      @change="onPresetRangeChange"
    >
      <el-option :value="90" label="近3个月" />
      <el-option :value="365" label="近1年" />
      <el-option :value="1095" label="近3年" />
      <el-option :value="1825" label="近5年" />
      <el-option :value="3650" label="近10年" />
    </el-select>
    <span class="chart-toolbar-label">或指定日期</span>
    <el-date-picker
      v-model="chartDateRange"
      type="daterange"
      size="small"
      value-format="YYYY-MM-DD"
      start-placeholder="开始日期"
      end-placeholder="结束日期"
      unlink-panels
      style="width: 250px"
      @change="loadChart"
    />
    <span class="chart-toolbar-label">主图指标</span>
    <el-radio-group v-model="mainIndicator" size="small" @change="loadChart">
      <el-radio-button value="MA">MA</el-radio-button>
      <el-radio-button value="BOLL">BOLL</el-radio-button>
      <el-radio-button value="NONE">无</el-radio-button>
    </el-radio-group>
    <!-- 均线勾选（V6.25）：可选 10~250 共 8 条，**默认只画 MA120**，其余按需勾选 -->
    <template v-if="mainIndicator === 'MA'">
      <span class="chart-toolbar-label">均线</span>
      <el-select
        v-model="maWindows"
        multiple
        collapse-tags
        collapse-tags-tooltip
        size="small"
        placeholder="选择均线"
        style="width: 180px"
        @change="loadChart"
      >
        <el-option v-for="window in MA_WINDOWS" :key="window" :value="window" :label="`MA${window}`" />
      </el-select>
    </template>
    <el-checkbox v-model="showMacd" size="small" @change="loadChart">MACD 副图</el-checkbox>
    <el-checkbox v-model="showYield" size="small" @change="loadChart">股息率副图</el-checkbox>
    <el-checkbox v-model="showScale" size="small" @change="loadChart">规模副图</el-checkbox>
    <el-checkbox v-model="showValuation" size="small" @change="onValuationToggle">估值副图</el-checkbox>
    <span v-if="showScale && scaleData && scaleData.length === 0" class="muted">
      规模历史自 V5.3 上线日起逐日积累，无法回补；V6.15 起场内 ETF 每个交易日写一行“每日估算”（份额×当日净值），场外基金仍是季报披露值
    </span>
    <span v-else-if="showScale && scaleData && scaleData.length > 0 && scaleData.length < 10" class="muted">
      规模历史已积累 {{ scaleData.length }} 天（自 {{ scaleData[0].date }} 起，每个交易日 +1），数据较少时副图仅右端可见
    </span>
    <span v-if="showYield && yieldData && !yieldData.priceAvailable" class="muted">
      历史股息率待补齐：需要未复权价（已排入下次同步）
    </span>
    <!-- 估值副图提示：有数据给「指数 + 当前 PE + 分位」摘要（原「指数估值」页签的告警条内容），无数据说明原因 -->
    <span v-if="showValuation && valuationSummary" class="muted">
      {{ valuationSummary }}
      <el-tag :type="valuationTagType" size="small" class="name-tag">{{ valuationTagText }}</el-tag>
    </span>
    <span v-else-if="showValuation && valuationData !== null && !valuationSummary" class="muted">
      {{ valuationEmptyText }}
    </span>
    <span class="muted">
      双击全屏 · 图上拖动可框选区间 · 标记：<b class="mark-b">b</b> 买入
      <b class="mark-s">s</b> 卖出 <b class="mark-q">q</b> 分红除息
    </span>
  </div>
  <div v-if="chartOption" @dblclick="openFullscreen">
    <ChartPanel
      :option="chartOption as EChartsOption"
      :height="chartHeight"
      :brush="true"
      @brush-end="onBrushRange"
      @zoom-change="onZoomChange"
    />
  </div>

  <!-- 区间表现：统计口径 = 图上**当前可见**的那一段，缩放/框选/切区间都会自动重算 -->
  <div class="range-stats">
    <div class="range-stats-head">
      <span class="section-title">区间表现</span>
      <span class="muted">按图上当前可见区间统计 · 缩放或框选会自动更新</span>
    </div>
    <el-table v-if="rangeStats" :data="[rangeStats]" size="small">
      <el-table-column prop="from" label="起始日" min-width="120" />
      <el-table-column prop="to" label="截止日" min-width="120" />
      <el-table-column prop="days" label="交易日" min-width="100" align="right">
        <template #default="{ row }"><span class="num">{{ row.days }}</span></template>
      </el-table-column>
      <el-table-column label="区间涨跌幅" min-width="130" align="right">
        <template #default="{ row }">
          <span class="num" :class="changeColorClass(row.returnPct)">
            {{ row.returnPct === null ? '--' : (row.returnPct > 0 ? '+' : '') + row.returnPct.toFixed(2) + '%' }}
          </span>
        </template>
      </el-table-column>
      <el-table-column label="年化收益率" min-width="130" align="right">
        <template #default="{ row }">
          <span class="num" :class="changeColorClass(row.annualizedReturnPct)">
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
    <el-empty v-else description="当前区间数据不足，无法统计" :image-size="60" />
  </div>

  <!-- 全屏查看层（Esc 或右上角关闭退出） -->
  <Teleport to="body">
    <div v-if="fullscreen" class="chart-fullscreen">
      <div class="chart-fullscreen-head">
        <span class="section-title">{{ props.fundName }} · 行情走势</span>
        <el-button link @click="fullscreen = false">关闭（Esc）</el-button>
      </div>
      <ChartPanel
        v-if="chartOption"
        :option="chartOption as EChartsOption"
        height="calc(100vh - 110px)"
        :brush="true"
        @brush-end="onBrushRange"
      />
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import type { EChartsOption } from 'echarts'
import ChartPanel from '@/components/charts/ChartPanel.vue'
import {
  CANDLE_UP,
  CANDLE_DOWN,
  VOLUME,
  PE_LINE,
  PRIMARY,
  BENCHMARK,
  MA_COLORS,
  BOLL_MID,
  BOLL_BAND,
  MACD_DIF,
  MACD_DEA
} from '@/utils/palette'
import { ma, boll, macd } from '@/utils/indicators'
import { changeColorClass } from '@/utils/format'
import { annualizedReturnPct, annualizedVolatilityPct, maxDrawdownPct, rangeReturnPct } from '@/utils/metrics'
import { TEXT_INVERSE, TRADE_BUY, TRADE_DIVIDEND, TRADE_SELL } from '@/utils/palette'
import { useEscToClose } from '@/utils/escClose'
import {
  fundDividendYield,
  fundKline,
  fundMarks,
  fundNav,
  fundScaleHistory,
  fundValuation
} from '@/api/fund'
import type { DividendYieldVO, FundMarkVO, FundScalePoint, SeriesPoint, ValuationSeriesVO } from '@/api/fund'

/**
 * 行情走势面板（从 FundDetailView 原样抽取，行为/口径不变）：
 * 净值口径切换、区间选择、主图指标（MA/BOLL）、各类副图（MACD/股息率/规模/估值）、
 * 区间表现统计与双击全屏查看。基金代码/类型/名称等由父页面透传；
 * 父页面切回本页签时调用 reload() 重新取数。
 */
const props = defineProps<{
  /** 基金代码 */
  fundCode: string
  /** 基金类型：1=场内 ETF（K 线口径）2=场外指数基金（净值口径） */
  fundType: number
  /** 基金名称（全屏层标题展示用） */
  fundName: string
  /** 跟踪指数代码（估值副图空态文案用，未识别为 null） */
  indexCode: string | null
  /** 跟踪指数名称（估值副图空态文案用，未识别为 null） */
  indexName: string | null
}>()

/**
 * 均线组（V5.88 用户口径：只保留 30/60/90/120/180/250 这组长周期均线，原 5/10/20/60 移除）。
 * 注意配套：最长 250 日均线需要 250 个交易日（约 365 自然日）的前置数据才画得出来，
 * 故 INDICATOR_WARMUP_DAYS 同步提高，且预设区间也带预热段（见 loadChart）。
 */
/** 均线窗口（V6.25：加入 MA10/MA20，共 8 条） */
const MA_WINDOWS = [10, 20, 30, 60, 90, 120, 180, 250]

/** 默认显示的均线（V6.25 用户要求：进页面只画 MA120，其余按需在工具栏勾选） */
const DEFAULT_MA_WINDOWS = [120]

/** 当前显示的均线（工具栏「均线」多选的绑定值；默认只画 MA120） */
const maWindows = ref<number[]>([...DEFAULT_MA_WINDOWS])

/** 布林带参数（20 日 ± 2 倍标准差，行业默认） */
const BOLL_PERIOD = 20
const BOLL_K = 2

/** 行情区间天数（近3月/1年/3年/5年/10年） */
const rangeDays = ref(365)
/** 场外基金的净值口径切换 */
const navMode = ref<'unitNav' | 'accNav' | 'adjNav'>('unitNav')
const chartOption = ref<EChartsOption | null>(null)
/** 自定义日期区间（yyyy-MM-dd）；设置后忽略区间下拉，支持图上框选回填 */
const chartDateRange = ref<[string, string] | null>(null)
/** 当前图表序列的日期轴（框选时把索引换算成日期用） */
const chartDates = ref<string[]>([])
/** 当前图表序列的数值（与图形口径一致：ETF 用收盘价、场外用当前所选净值口径） */
const chartValues = ref<number[]>([])
/** 图上可见窗口（百分比，0=序列首 100=序列尾）：随预设区间/框选/手动缩放变化 */
const visibleWindow = ref({ startPercent: 0, endPercent: 100 })

/**
 * 区间表现：按图上**当前可见**的窗口统计（所见即所测）。
 * 直接依赖日期/数值序列与可见窗口，缩放或框选后自动重算，无需再请求接口。
 */
const rangeStats = computed(() => {
  const dates = chartDates.value
  const values = chartValues.value
  if (dates.length < 2 || values.length !== dates.length) {
    return null
  }
  const startIndex = Math.max(0, Math.round((visibleWindow.value.startPercent / 100) * (dates.length - 1)))
  const endIndex = Math.min(dates.length - 1, Math.round((visibleWindow.value.endPercent / 100) * (dates.length - 1)))
  if (endIndex - startIndex < 1) {
    return null
  }
  const slice = values.slice(startIndex, endIndex + 1)
  return {
    from: dates[startIndex],
    to: dates[endIndex],
    days: slice.length,
    returnPct: rangeReturnPct(slice),
    annualizedReturnPct: annualizedReturnPct(slice, dates[startIndex], dates[endIndex]),
    maxDrawdownPct: maxDrawdownPct(slice),
    volatilityPct: annualizedVolatilityPct(slice)
  }
})

/** 用户缩放/拖动 dataZoom：更新可见窗口，区间表现随之刷新 */
function onZoomChange(range: { startPercent: number; endPercent: number }) {
  visibleWindow.value = range
}
/**
 * 指标预热天数（自然日）：给均线与 BOLL/MACD 留足前置数据。
 * V5.88 由 150 提高到 400（约 275 个交易日）——均线组改为 30/60/90/120/180/250 后，
 * 最长 250 日线需要 250 个交易日前置，150 天（约 100 交易日）会让 MA180/250 整段为空。
 */
const INDICATOR_WARMUP_DAYS = 400

/**
 * 行情图交易标记（买入 b / 卖出 s / 分红 q）。
 * 每只基金只取一次并缓存：缩放、框选、换指标、切区间都复用这份数据，不重复请求；
 * 接口失败静默降级为"无标记"，不影响看 K 线。
 */
const tradeMarks = ref<FundMarkVO[]>([])

async function loadMarks() {
  tradeMarks.value = await fundMarks(props.fundCode).catch(() => [])
}

/** 主图指标：MA（均线组 30/60/90/120/180/250）/ BOLL（20 日 ±2σ）/ NONE */
const mainIndicator = ref<'MA' | 'BOLL' | 'NONE'>('MA')

/** 是否显示 MACD 副图（DIF/DEA + 柱，柱按红涨绿跌着色） */
const showMacd = ref(false)

/** 股息率副图开关与数据（单次分红股息率 + TTM 滚动 12 个月） */
const showYield = ref(false)
const yieldData = ref<DividendYieldVO | null>(null)

/** 规模副图开关与数据（fund_scale_history 每日快照，自上线日起积累） */
const showScale = ref(false)
const scaleData = ref<FundScalePoint[] | null>(null)

/** 全屏查看状态（双击图表进入，Esc 或关闭按钮退出） */
const fullscreen = ref(false)

/** 图表高度随副图数量自适应 */
const chartHeight = computed(() => `${420 + subChartCount.value * 100}px`)

/** 除成交量外的副图数量（MACD / 股息率 / 规模 / 估值），用于图表高度与网格划分 */
const subChartCount = computed(
  () => (showMacd.value ? 1 : 0) + (showYield.value ? 1 : 0) + (showScale.value ? 1 : 0) + (valuationOn.value ? 1 : 0)
)

/** 估值副图是否实际渲染：开关打开且该指数确有 PE 数据（无数据时不占网格，只在工具栏出提示文案） */
const valuationOn = computed(() => showValuation.value && !!valuationData.value?.hasData)

/**
 * 副图纵向布局：主图 +（成交量）+（MACD）+（股息率）+（规模）+（估值）。
 * 图表数量随开关变化，写死百分比会在开关组合下互相重叠，故按数量算：
 * 每张副图占固定高度，主图吃掉剩余空间，副图之间留 gap，底部预留 dataZoom 滑块的位置。
 *
 * @param includeVolume 是否包含成交量网格（净值图没有成交量，传 false 时其余网格从主图下方直接排起）
 * @returns grids 每张图的 [top%, height%]（顺序：主图, 成交量?, MACD?, 股息率?, 规模?, 估值?）
 */
function subChartGrids(includeVolume = true): { top: number; height: number }[] {
  const extra = subChartCount.value
  const volHeight = includeVolume ? (extra === 0 ? 15 : 12) : 0
  const extraHeight = extra <= 1 ? 14 : 11
  const gap = 3
  const topStart = 9
  const bottomReserve = 12
  const total = topStart + bottomReserve + volHeight + extra * extraHeight + (1 + extra) * gap
  const mainHeight = Math.max(24, 100 - total)
  const grids = [{ top: topStart, height: mainHeight }]
  let cursor = topStart + mainHeight + gap
  if (includeVolume) {
    grids.push({ top: cursor, height: volHeight })
    cursor += volHeight + gap
  }
  for (let i = 0; i < extra; i++) {
    grids.push({ top: cursor, height: extraHeight })
    cursor += extraHeight + gap
  }
  return grids
}

/**
 * 追加「股息率副图」（独立百分比轴）：TTM 滚动 12 个月阶梯线 + 每次分红的散点。
 * 场内 K 线与场外净值图共用（网格下标由调用方按当前副图数量推出）。
 *
 * @param series    图表 series 数组（就地追加）
 * @param xAxes     x 轴数组（就地追加）
 * @param yAxes     y 轴数组（就地追加）
 * @param dates     主图日期轴（用于把稀疏的阶跃点铺满）
 * @param gridIndex 该副图所在的网格下标
 */
function appendYieldSubChart(
  series: Record<string, unknown>[],
  xAxes: Record<string, unknown>[],
  yAxes: Record<string, unknown>[],
  dates: string[],
  gridIndex: number
) {
  xAxes.push({ type: 'category', gridIndex, data: dates, axisLabel: { show: false } })
  yAxes.push({
    gridIndex,
    scale: true,
    axisLabel: { formatter: '{value}%', show: true },
    splitLine: { show: false }
  })
  const ttm = fillLatestByDate(
    dates,
    (yieldData.value?.ttm ?? []).map((p) => ({ date: p.date, value: p.yieldPct }))
  )
  const indexByDate = new Map(dates.map((date, i) => [date, i]))
  const events = (yieldData.value?.events ?? [])
    .filter((event) => event.yieldPct != null && indexByDate.has(event.date))
    .map((event) => ({ value: [indexByDate.get(event.date), event.yieldPct] }))
  series.push({
    type: 'line',
    name: '股息率TTM',
    xAxisIndex: gridIndex,
    yAxisIndex: gridIndex,
    data: ttm,
    step: 'end',
    connectNulls: false,
    showSymbol: false,
    // 灰色基准线：紫色 PE_LINE 留给「估值副图」的 PE 线，避免同屏两个紫色序列在图例里混淆（V5.57）
    lineStyle: { width: 1.6, color: BENCHMARK },
    itemStyle: { color: BENCHMARK }
  })
  if (events.length > 0) {
    series.push({
      type: 'scatter',
      name: '单次分红股息率',
      xAxisIndex: gridIndex,
      yAxisIndex: gridIndex,
      symbolSize: 7,
      data: events,
      itemStyle: { color: PRIMARY }
    })
  }
}

/**
 * 追加「基金规模副图」（独立单位：亿元）：规模快照的阶梯线。
 * 快照来自 fund_scale_history（每日档案刷新成功后落一行），
 * 每个交易日取"该日之前最近一次快照"的值，阶梯线直观呈现规模变化。
 *
 * @param series    图表 series 数组（就地追加）
 * @param xAxes     x 轴数组（就地追加）
 * @param yAxes     y 轴数组（就地追加）
 * @param dates     主图日期轴
 * @param gridIndex 该副图所在的网格下标
 */
function appendScaleSubChart(
  series: Record<string, unknown>[],
  xAxes: Record<string, unknown>[],
  yAxes: Record<string, unknown>[],
  dates: string[],
  gridIndex: number
) {
  xAxes.push({ type: 'category', gridIndex, data: dates, axisLabel: { show: false } })
  yAxes.push({
    gridIndex,
    scale: true,
    axisLabel: { formatter: '{value}亿', show: true },
    splitLine: { show: false }
  })
  series.push({
    type: 'line',
    name: '基金规模',
    xAxisIndex: gridIndex,
    yAxisIndex: gridIndex,
    data: fillLatestByDate(dates, scalePointsForChart()),
    step: 'end',
    connectNulls: false,
    // 数据点本身显示圆标：积累初期只有少数几天，仅画细线几乎看不见（V5.18 用户反馈"副图没加载出来"实为此因）
    showSymbol: true,
    symbol: 'circle',
    symbolSize: 5,
    lineStyle: { width: 1.6, color: MACD_DEA },
    itemStyle: { color: MACD_DEA }
  })
}

/**
 * 把稀疏的 {date, value} 点铺到主图日期轴：每个交易日取"该日之前最近一点"的值（首点之前为空）。
 * 规模快照 / 股息率 TTM / 指数 PE 都是各自独立的日期序列，与行情图日期轴对齐都用这一把刷子
 * （三处逻辑原本各写一份，V5.57 收敛为一个函数，避免走散）。
 */
function fillLatestByDate(dates: string[], points: { date: string; value: number | null }[]): (number | null)[] {
  const sorted = [...points].sort((a, b) => a.date.localeCompare(b.date))
  let cursor = 0
  let current: number | null = null
  return dates.map((date) => {
    while (cursor < sorted.length && sorted[cursor].date <= date) {
      current = sorted[cursor].value
      cursor += 1
    }
    return current
  })
}

/**
 * 追加「估值副图」（独立坐标轴）：跟踪指数 PE 走势。
 * 指数 PE 与基金收盘价/净值是两套序列，按"该交易日之前最近的 PE"对齐到行情图日期轴，
 * 与主图共用同一横轴，估值与行情的相对高低、拐点先后一目了然（V5.57，原「指数估值」页签并入）。
 *
 * @param series    图表 series 数组（就地追加）
 * @param xAxes     x 轴数组（就地追加）
 * @param yAxes     y 轴数组（就地追加）
 * @param dates     主图日期轴
 * @param gridIndex 该副图所在的网格下标
 */
function appendValuationSubChart(
  series: Record<string, unknown>[],
  xAxes: Record<string, unknown>[],
  yAxes: Record<string, unknown>[],
  dates: string[],
  gridIndex: number
) {
  xAxes.push({ type: 'category', gridIndex, data: dates, axisLabel: { show: false } })
  yAxes.push({ gridIndex, scale: true, splitLine: { show: false } })
  series.push({
    type: 'line',
    name: 'PE',
    xAxisIndex: gridIndex,
    yAxisIndex: gridIndex,
    data: fillLatestByDate(
      dates,
      (valuationData.value?.series ?? []).map((p) => ({ date: p.date, value: p.pe ?? null }))
    ),
    connectNulls: false,
    showSymbol: false,
    lineStyle: { width: 1.6, color: PE_LINE },
    itemStyle: { color: PE_LINE },
    areaStyle: { opacity: 0.08, color: PE_LINE }
  })
}

/** 把 {top,height} 数字转成 ECharts 需要的百分号字符串 */
function toGridOption(bands: { top: number; height: number }[]): Record<string, unknown>[] {
  return bands.map((band) => ({
    left: '10%',
    right: '3%',
    top: `${band.top.toFixed(1)}%`,
    height: `${band.height.toFixed(1)}%`
  }))
}

/** 双击图表进入全屏查看 */
function openFullscreen() {
  fullscreen.value = true
}

// 全屏层按 Esc 退出（普通 div 无法接收 keydown，需全局监听）
useEscToClose(fullscreen)

/** 估值副图开关与数据：跟踪指数 PE 全历史一次拉取后缓存，区间切换/缩放只做本地按日期对齐，不再重复请求 */
const showValuation = ref(false)
const valuationData = ref<ValuationSeriesVO | null>(null)

/** 开/关估值副图：首次打开时拉一次指数 PE 全历史，再按当前区间重建图表 */
async function onValuationToggle() {
  if (showValuation.value && valuationData.value === null) {
    valuationData.value = await fundValuation(props.fundCode).catch(() => null)
  }
  loadChart()
}

/**
 * 估值空态文案：区分三种情况，避免"跟踪指数已识别但该指数没有 PE"被误报成"未识别跟踪指数"。
 * 例如国债 ETF 跟踪的上证 5 年期国债指数属于债券指数，本就不适用 PE 估值。
 */
const valuationEmptyText = computed(() => {
  if (props.indexCode) {
    return '该指数暂无估值数据（数据源仅覆盖中证/上证系列指数）'
  }
  if (props.indexName) {
    return `跟踪指数「${props.indexName}」不适用 PE 估值（债券/货币类指数无市盈率）`
  }
  return '未识别跟踪指数，估值不可用'
})

const valuationTagType = computed(() => {
  const p = valuationData.value?.currentPercentile
  if (p === null || p === undefined) return 'info'
  if (p <= 20) return 'success'
  if (p >= 80) return 'danger'
  return 'warning'
})

const valuationTagText = computed(() => {
  const p = valuationData.value?.currentPercentile
  if (p === null || p === undefined) return ''
  if (p <= 20) return '低估'
  if (p >= 80) return '高估'
  return '合理'
})

/** 估值副图工具栏摘要（原「指数估值」页签告警条的内容）：跟踪指数 + 当前 PE + 近10年分位 */
const valuationSummary = computed(() => {
  const v = valuationData.value
  if (!v?.hasData) return ''
  const pct = v.currentPercentile === null || v.currentPercentile === undefined ? '--' : `${v.currentPercentile}%`
  return `跟踪指数 ${v.indexName} · 当前 PE ${v.latestPe?.toFixed(2) ?? '--'} · 近10年 ${pct} 分位`
})

/**
 * 加载行情图。**取数区间与显示区间分离**（V5.88）：
 * ① 取数：目标区间（预设区间或自定义/框选区间）**再往前多取 INDICATOR_WARMUP_DAYS 天**作为指标预热段
 *    ——均线改为 30/60/90/120/180/250 后，长周期均线需要大量前置数据，只取目标区间会让窗口开头的
 *    MA120/180/250 为空甚至整段缺失；
 * ② 显示：指标在完整序列上计算，再用 dataZoom（{@link withWindow}）把可见窗口**精确收敛回目标区间**，
 *    做到"看某一段"既不丢指标精度、也不会把预热段混进画面。
 */
async function loadChart() {
  const range = chartDateRange.value
  const today = todayStr()
  // 目标区间：自定义区间优先，否则按区间下拉（近3个月/1年/3年/5年）
  const targetStart = range ? range[0] : shiftDays(today, -rangeDays.value)
  const targetEnd = range ? range[1] : today
  const start = shiftDays(targetStart, -INDICATOR_WARMUP_DAYS)
  // end 预设时也要传（= 今天）：后端以「start 与 end 都非空」判定自定义区间，只传 start 会被忽略、
  // 退化成按 rangeDays 回看——预热段随之失效，MA90/120/180/250 在窗口开头整段为空（V5.88 实测踩过）
  const end = targetEnd
  // 股息率副图打开时才拉（并按区间缓存）：关掉副图就不产生任何额外请求
  if (showYield.value) {
    const span = Math.max(1, daysBetween(shiftDays(targetStart, -1), targetEnd))
    yieldData.value = await fundDividendYield(props.fundCode, span).catch(() => null)
  }
  // 规模副图打开时才拉：全量返回（自上线日起，量小）
  if (showScale.value) {
    scaleData.value = await fundScaleHistory(props.fundCode).catch(() => [])
  }
  const points: SeriesPoint[] =
    props.fundType === 1
      ? await fundKline(props.fundCode, rangeDays.value, start, end)
      : await fundNav(props.fundCode, rangeDays.value, start, end)
  const base = props.fundType === 1 ? klineOption(points) : navOption(points, navMode.value)
  chartDates.value = points.map((point) => point.date)
  chartValues.value = points.map((point) =>
    props.fundType === 1 ? Number(point.close) : Number(point[navMode.value] ?? point.unitNav)
  )
  // 显示窗口统一收敛到目标区间（预设与自定义同一套逻辑）
  const withMarks = appendTradeMarks(withWindow(base, chartDates.value, [targetStart, targetEnd]), points)
  chartOption.value = withMarks
  const zooms = (withMarks.dataZoom as Array<{ start?: number; end?: number }> | undefined) ?? []
  visibleWindow.value = { startPercent: zooms[0]?.start ?? 0, endPercent: zooms[0]?.end ?? 100 }
}

/** 今天（本地时区）yyyy-MM-dd */
function todayStr(): string {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
}

/** 切换区间下拉时清掉自定义日期（两者互斥） */
function onPresetRangeChange() {
  chartDateRange.value = null
  loadChart()
}

/** 在图上框选一段：把选中的起止日期回填到日期选择器并重新加载该区间 */
function onBrushRange(selected: { startIndex: number; endIndex: number }) {
  const dates = chartDates.value
  if (dates.length === 0) return
  const from = dates[Math.round(selected.startIndex)]
  const to = dates[Math.round(selected.endIndex)]
  if (!from || !to || from >= to) return
  chartDateRange.value = [from, to]
  rangeDays.value = rangeDays.value
  loadChart()
}

/** 两个 yyyy-MM-dd 之间相差的天数 */
function daysBetween(from: string, to: string): number {
  const [y1, m1, d1] = from.split('-').map(Number)
  const [y2, m2, d2] = to.split('-').map(Number)
  return Math.round((new Date(y2, m2 - 1, d2).getTime() - new Date(y1, m1 - 1, d1).getTime()) / 86400000)
}

/** 日期字符串加减天数（按本地时区构造，避免 UTC 偏移导致差一天） */
function shiftDays(date: string, days: number): string {
  const [y, m, d] = date.split('-').map(Number)
  const base = new Date(y, m - 1, d + days)
  return `${base.getFullYear()}-${String(base.getMonth() + 1).padStart(2, '0')}-${String(base.getDate()).padStart(2, '0')}`
}

/**
 * 在行情图上叠加交易标记串（b 买入 / s 卖出 / q 分红除息）。
 *
 * <p>实现要点（性能相关）：
 * <ul>
 *   <li>标记全部一次性放进 scatter 系列，**由 ECharts 按可见窗口自动裁剪**——
 *       缩放/框选只重绘、不重新请求，也不需要前端再算一次可见集合；</li>
 *   <li>后端已按"同一天同类型"合并，十年长周期也只有几十个点，渲染开销可忽略；</li>
 *   <li>标记不参与 tooltip（避免污染原有的 K 线悬停信息），字符本身即标识，
 *       具体明细看下方"交易流水"表。</li>
 * </ul>
 *
 * @param option 已经构建好的图表配置
 * @param points 当前序列（K 线用 low 定位、净值用当日值定位）
 */
function appendTradeMarks(option: EChartsOption, points: SeriesPoint[]): EChartsOption {
  if (tradeMarks.value.length === 0 || points.length === 0) {
    return option
  }
  const dates = points.map((point) => point.date)
  const indexByDate = new Map(dates.map((date, index) => [date, index]))
  const anchors = points.map((point) =>
    props.fundType === 1 ? Number(point.low ?? point.close) : Number(point[navMode.value] ?? point.unitNav)
  )
  const data: Record<string, unknown>[] = []
  for (const mark of tradeMarks.value) {
    // 记账日期可能不在该序列（非交易日、或早于取数区间）：顺延到最近的交易日，避免整条标记丢失
    let index = indexByDate.get(mark.date)
    if (index === undefined) {
      const next = dates.findIndex((date) => date >= mark.date)
      index = next < 0 ? undefined : next
    }
    if (index === undefined || !Number.isFinite(anchors[index])) {
      continue
    }
    const letter = mark.kind === 'BUY' ? 'b' : mark.kind === 'SELL' ? 's' : 'q'
    const color = mark.kind === 'BUY' ? TRADE_BUY : mark.kind === 'SELL' ? TRADE_SELL : TRADE_DIVIDEND
    data.push({
      value: [index, anchors[index]],
      name: letter,
      label: { formatter: letter },
      itemStyle: { color },
      markText: mark.text
    })
  }
  if (data.length === 0) {
    return option
  }
  const series = ((option.series as Record<string, unknown>[]) ?? []).slice()
  series.push({
    name: '交易标记',
    type: 'scatter',
    xAxisIndex: 0,
    yAxisIndex: 0,
    symbol: 'circle',
    symbolSize: 16,
    // 往下错开一点，避免与当日 K 线实体重叠
    symbolOffset: [0, 12],
    label: { show: true, position: 'inside', color: TEXT_INVERSE, fontSize: 11, fontWeight: 600 },
    // 标记不进 tooltip：原有 K 线/净值悬停信息保持干净
    tooltip: { show: false },
    z: 12,
    data
  })
  return { ...option, series } as EChartsOption
}

/** 把 dataZoom 的显示窗口收敛到 [from, to]（序列里可能没有正好等于端点的交易日，按区间取最近边界） */
function withWindow(option: EChartsOption, dates: string[], range: [string, string]): EChartsOption {
  const startIndex = dates.findIndex((date) => date >= range[0])
  let endIndex = -1
  for (let i = dates.length - 1; i >= 0; i--) {
    if (dates[i] <= range[1]) {
      endIndex = i
      break
    }
  }
  if (startIndex < 0 || endIndex < 0 || dates.length < 2) {
    return option
  }
  const percent = (index: number) => Number(((index / (dates.length - 1)) * 100).toFixed(2))
  const zooms = (option.dataZoom as Record<string, unknown>[] | undefined) ?? []
  return {
    ...option,
    dataZoom: zooms.map((zoom) => ({ ...zoom, start: percent(startIndex), end: percent(Math.max(endIndex, startIndex + 1)) }))
  }
}

/**
 * ETF 前复权 K 线图：主图（蜡烛 + MA/BOLL 二选一）+ 成交量 + 可选 MACD 副图。
 * 网格按副图数量动态划分：带 MACD 时主图收窄、成交量上移、MACD 占底部。
 */
function klineOption(points: SeriesPoint[]): EChartsOption {
  const k = points.map((p) => [p.open, p.close, p.low, p.high])
  const dates = points.map((p) => p.date)
  const closes = points.map((p) => Number(p.close))
  const volumes = points.map((p) => p.volume)
  const macdOn = showMacd.value
  const yieldOn = showYield.value
  const scaleOn = showScale.value
  const grids: Record<string, unknown>[] = toGridOption(subChartGrids())
  const xAxes: Record<string, unknown>[] = [
    { type: 'category', data: dates, boundaryGap: true },
    { type: 'category', gridIndex: 1, data: dates, axisLabel: { show: false } }
  ]
  const yAxes: Record<string, unknown>[] = [
    { scale: true },
    { gridIndex: 1, axisLabel: { show: false }, splitLine: { show: false } }
  ]
  const series: Record<string, unknown>[] = [
    {
      type: 'candlestick',
      name: 'K线',
      data: k,
      itemStyle: { color: CANDLE_UP, color0: CANDLE_DOWN, borderColor: CANDLE_UP, borderColor0: CANDLE_DOWN }
    },
    {
      type: 'bar',
      name: '成交量',
      xAxisIndex: 1,
      yAxisIndex: 1,
      data: volumes,
      itemStyle: { color: VOLUME }
    }
  ]

  // 主图指标（互斥切换，与雪球式主图指标一致）
  if (mainIndicator.value === 'MA') {
    MA_WINDOWS.forEach((window, index) => {
      // 只画工具栏里勾选的均线（默认 MA120；V6.25 起共 8 条可选）
      if (!maWindows.value.includes(window)) {
        return
      }
      series.push({
        type: 'line',
        name: `MA${window}`,
        data: ma(closes, window),
        smooth: true,
        showSymbol: false,
        lineStyle: { width: 1.2, color: MA_COLORS[index % MA_COLORS.length] },
        itemStyle: { color: MA_COLORS[index % MA_COLORS.length] }
      })
    })
  } else if (mainIndicator.value === 'BOLL') {
    const bands = boll(closes, BOLL_PERIOD, BOLL_K)
    series.push(
      {
        type: 'line',
        name: `BOLL(${BOLL_PERIOD})`,
        data: bands.mid,
        smooth: true,
        showSymbol: false,
        lineStyle: { width: 1.3, color: BOLL_MID },
        itemStyle: { color: BOLL_MID }
      },
      {
        type: 'line',
        name: '上轨',
        data: bands.upper,
        smooth: true,
        showSymbol: false,
        lineStyle: { width: 1, type: 'dashed', color: BOLL_BAND },
        itemStyle: { color: BOLL_BAND }
      },
      {
        type: 'line',
        name: '下轨',
        data: bands.lower,
        smooth: true,
        showSymbol: false,
        lineStyle: { width: 1, type: 'dashed', color: BOLL_BAND },
        itemStyle: { color: BOLL_BAND }
      }
    )
  }

  // MACD 副图（柱按红涨绿跌）
  if (macdOn) {
    xAxes.push({ type: 'category', gridIndex: 2, data: dates, axisLabel: { show: false } })
    yAxes.push({ gridIndex: 2, axisLabel: { show: false }, splitLine: { show: false } })
    const result = macd(closes)
    series.push(
      {
        type: 'bar',
        name: 'MACD',
        xAxisIndex: 2,
        yAxisIndex: 2,
        data: result.bar.map((value) => ({
          value,
          itemStyle: { color: value !== null && value >= 0 ? CANDLE_UP : CANDLE_DOWN }
        }))
      },
      {
        type: 'line',
        name: 'DIF',
        xAxisIndex: 2,
        yAxisIndex: 2,
        data: result.dif,
        showSymbol: false,
        lineStyle: { width: 1.1, color: MACD_DIF },
        itemStyle: { color: MACD_DIF }
      },
      {
        type: 'line',
        name: 'DEA',
        xAxisIndex: 2,
        yAxisIndex: 2,
        data: result.dea,
        showSymbol: false,
        lineStyle: { width: 1.1, color: MACD_DEA },
        itemStyle: { color: MACD_DEA }
      }
    )
  }

  if (yieldOn) {
    appendYieldSubChart(series, xAxes, yAxes, dates, macdOn ? 3 : 2)
  }
  if (scaleOn) {
    appendScaleSubChart(series, xAxes, yAxes, dates, (macdOn ? 1 : 0) + (yieldOn ? 1 : 0) + 2)
  }
  if (valuationOn.value) {
    appendValuationSubChart(series, xAxes, yAxes, dates, (macdOn ? 1 : 0) + (yieldOn ? 1 : 0) + (scaleOn ? 1 : 0) + 2)
  }

  const axisIndexes = Array.from({ length: 2 + subChartCount.value }, (_, i) => i)
  return {
    animation: false,
    tooltip: { trigger: 'axis', axisPointer: { type: 'cross' } },
    legend: { top: 0, left: 'center', itemWidth: 12, itemHeight: 8, textStyle: { fontSize: 11 } },
    grid: grids,
    xAxis: xAxes,
    yAxis: yAxes,
    dataZoom: [
      { type: 'inside', xAxisIndex: axisIndexes, start: 60, end: 100 },
      { type: 'slider', xAxisIndex: axisIndexes, height: 16, bottom: 4 }
    ],
    series
  } as EChartsOption
}

/**
 * 场外净值图：与 ETF 口径一致地支持主图指标与 MACD 副图——
 * 指标基于**当前所选净值序列**计算（单位/累计/复权三选一），切换口径即重算。
 */
function navOption(points: SeriesPoint[], mode: 'unitNav' | 'accNav' | 'adjNav'): EChartsOption {
  const dates = points.map((p) => p.date)
  const values = points.map((p) => Number(p[mode]))
  const macdOn = showMacd.value
  const yieldOn = showYield.value
  const scaleOn = showScale.value
  // 净值图没有成交量副图：网格直接从主图下方排起（MACD 打开时网格 1 归 MACD）
  // V6.45 修复：第二根 x/y 轴此前**无条件**添加（gridIndex=1）——MACD 关闭且无其他副图时网格只有 1 个，
  // 孤儿轴引用不存在的网格，ECharts setOption 抛 "__ec_inner_47" 内部错误、图表整块空白（场外基金默认状态必现；
  // 场内 K 线因恒有成交量网格未受影响）。改为仅 MACD 开启时添加占位轴——股息率/规模/估值副图自带各自的轴。
  const grids: Record<string, unknown>[] = toGridOption(subChartGrids(false))
  const xAxes: Record<string, unknown>[] = [{ type: 'category', data: dates }]
  const yAxes: Record<string, unknown>[] = [{ type: 'value', scale: true }]
  if (macdOn) {
    xAxes.push({ type: 'category', gridIndex: 1, data: dates, axisLabel: { show: false } })
    yAxes.push({ gridIndex: 1, axisLabel: { show: false }, splitLine: { show: false } })
  }
  const series: Record<string, unknown>[] = [
    {
      type: 'line',
      name: navModeLabel(mode),
      data: values,
      showSymbol: false,
      itemStyle: { color: PRIMARY },
      lineStyle: { color: PRIMARY, width: 1.6 }
    }
  ]

  // 主图指标：MA 组 / BOLL（与 ETF 一致）
  if (mainIndicator.value === 'MA') {
    MA_WINDOWS.forEach((window, index) => {
      // 只画工具栏里勾选的均线（默认 MA120；V6.25 起共 8 条可选）
      if (!maWindows.value.includes(window)) {
        return
      }
      series.push({
        type: 'line',
        name: `MA${window}`,
        data: ma(values, window),
        smooth: true,
        showSymbol: false,
        lineStyle: { width: 1.2, color: MA_COLORS[index % MA_COLORS.length] },
        itemStyle: { color: MA_COLORS[index % MA_COLORS.length] }
      })
    })
  } else if (mainIndicator.value === 'BOLL') {
    const bands = boll(values, BOLL_PERIOD, BOLL_K)
    series.push(
      {
        type: 'line',
        name: `BOLL(${BOLL_PERIOD})`,
        data: bands.mid,
        smooth: true,
        showSymbol: false,
        lineStyle: { width: 1.3, color: BOLL_MID },
        itemStyle: { color: BOLL_MID }
      },
      {
        type: 'line',
        name: '上轨',
        data: bands.upper,
        smooth: true,
        showSymbol: false,
        lineStyle: { width: 1, type: 'dashed', color: BOLL_BAND },
        itemStyle: { color: BOLL_BAND }
      },
      {
        type: 'line',
        name: '下轨',
        data: bands.lower,
        smooth: true,
        showSymbol: false,
        lineStyle: { width: 1, type: 'dashed', color: BOLL_BAND },
        itemStyle: { color: BOLL_BAND }
      }
    )
  }

  // MACD 副图（与 K 线图不同：净值图没有成交量，网格 1 直接给 MACD 用；关闭时移除该占位网格）
  let nextGrid = 1
  if (macdOn) {
    const result = macd(values)
    series.push(
      {
        type: 'bar',
        name: 'MACD',
        xAxisIndex: 1,
        yAxisIndex: 1,
        data: result.bar.map((value) => ({
          value,
          itemStyle: { color: value !== null && value >= 0 ? CANDLE_UP : CANDLE_DOWN }
        }))
      },
      {
        type: 'line',
        name: 'DIF',
        xAxisIndex: 1,
        yAxisIndex: 1,
        data: result.dif,
        showSymbol: false,
        lineStyle: { width: 1.1, color: MACD_DIF },
        itemStyle: { color: MACD_DIF }
      },
      {
        type: 'line',
        name: 'DEA',
        xAxisIndex: 1,
        yAxisIndex: 1,
        data: result.dea,
        showSymbol: false,
        lineStyle: { width: 1.1, color: MACD_DEA },
        itemStyle: { color: MACD_DEA }
      }
    )
    nextGrid = 2
  }

  // 其余副图用游标依次分配网格：写死下标在开关组合下会空格或越界（V5.57 随估值副图一并修正）
  if (yieldOn) {
    appendYieldSubChart(series, xAxes, yAxes, dates, nextGrid)
    nextGrid += 1
  }
  if (scaleOn) {
    appendScaleSubChart(series, xAxes, yAxes, dates, nextGrid)
    nextGrid += 1
  }
  if (valuationOn.value) {
    appendValuationSubChart(series, xAxes, yAxes, dates, nextGrid)
    nextGrid += 1
  }

  // 轴数随开关组合变化：MACD 关闭时网格 1 不存在，轴从 1 根起算（K 线图恒为 2 + 副图数）
  const axisIndexes = Array.from({ length: (macdOn ? 2 : 1) + subChartCount.value }, (_, i) => i)
  return {
    animation: false,
    tooltip: { trigger: 'axis', axisPointer: { type: 'cross' } },
    legend: { top: 0, left: 'center', itemWidth: 12, itemHeight: 8, textStyle: { fontSize: 11 } },
    grid: grids,
    xAxis: xAxes,
    yAxis: yAxes,
    dataZoom: [
      { type: 'inside', xAxisIndex: axisIndexes, start: 70, end: 100 },
      { type: 'slider', xAxisIndex: axisIndexes, height: 16, bottom: 4 }
    ],
    series
  } as EChartsOption
}

/** 净值口径的中文名（图例展示） */
function navModeLabel(mode: 'unitNav' | 'accNav' | 'adjNav'): string {
  return mode === 'unitNav' ? '单位净值' : mode === 'accNav' ? '累计净值' : '复权净值'
}

// 切换净值口径（单位/累计/复权）即重新取图
watch(navMode, () => loadChart())

/**
 * 规模副图的数据点（V6.15）：有"每日估算"行时**只画估算行**，
 * 并把第一个估算行之前的最近一条披露行接上（口径切换点），避免两种口径混画出现假跳变
 * ——例如估算日 10-09 记 1056 亿、次日（估算失败只落了季报平值）又回到 948 亿的那种"假跌"。
 * 没有任何估算行时（场外基金 / 功能上线前）保持原样：只画披露快照的台阶线。
 */
function scalePointsForChart(): { date: string; value: number }[] {
  const rows = scaleData.value ?? []
  const firstEstimated = rows.findIndex((r) => r.source === 'ESTIMATED')
  const kept = firstEstimated < 0
    ? rows
    : [
        // 切换点：第一个估算行之前最后一条披露行（台阶线的终点）
        ...rows.slice(0, firstEstimated).filter((r) => r.source === 'DISCLOSED').slice(-1),
        // 其后全部按估算行画（每日连续）
        ...rows.slice(firstEstimated)
      ]
  return kept.map((p) => ({ date: p.date, value: Number(p.scale) }))
}

onMounted(() => {
  loadMarks()
  loadChart()
})

/** 父页面切回本页签时重新取数（保持"回到页签重新取数"的既有体验） */
defineExpose({ reload: loadChart })
</script>

<style scoped>
/* 以下样式从 FundDetailView 原样搬来（行情走势页签专用） */
/* 页签内下拉框与下方内容的间距（父页面 .block 同款式，本组件内独立保留一份以保证视觉一致） */
.block {
  margin-bottom: 16px;
}

/* 估值提示里的小标签间距（父页面 .name-tag 同款式，本组件内独立保留一份） */
.name-tag {
  margin-left: 8px;
}

/* 图例里的标记字：与图上气泡同色，说明 b/s/q 的含义 */
.mark-b {
  color: var(--q-color-up);
}

.mark-s {
  color: var(--q-color-down);
}

.mark-q {
  color: var(--q-color-primary);
}

.range-stats {
  margin-top: var(--q-space-4);
}

.range-stats-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: var(--q-space-2);
}

.chart-toolbar {
  display: flex;
  align-items: center;
  gap: var(--q-space-3);
  flex-wrap: wrap;
  margin-bottom: var(--q-space-3);
}

.chart-toolbar-label {
  font-size: var(--q-font-xs);
  color: var(--q-text-muted);
}

/* 全屏查看层：覆盖视口，保留标题与关闭入口（Esc 可退出） */
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
