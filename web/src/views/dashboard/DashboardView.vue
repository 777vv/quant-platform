<template>
  <div class="dashboard">
    <!-- 资产总览：主卡（品牌渐变，承载总资产）+ 6 张副卡（左侧语义色条 + 涨跌色块 + 迷你走势）
         副卡数量固定为 6，与主卡的 span=6 合计 24 栅格；间距/字号/阴影全部走 token -->
    <el-row :gutter="12" class="stat-row">
      <el-col :xs="24" :sm="12" :md="6">
        <el-card shadow="never" class="stat-card stat-card--hero">
          <div class="hero-top">
            <span class="hero-label">总资产（元）</span>
            <el-button v-if="userStore.can(PERM.ACTION_TRADE)" link class="q-on-hero" @click="openEntry(4)">转入/转出</el-button>
          </div>
          <div class="hero-value num">{{ formatAmount(assets?.totalAssets) }}</div>
          <div class="hero-sub">
            市值 {{ formatAmount(assets?.marketValue) }} ＋ 现金 {{ formatAmount(assets?.cashBalance) }}
          </div>
          <div class="hero-foot">
            <span>持仓 {{ assets?.holdingCount ?? 0 }} 只</span>
            <span class="hero-dot">·</span>
            <span>自选 {{ assets?.watchCount ?? 0 }} 只</span>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6" :md="3">
        <el-card shadow="never" class="stat-card" :class="`stat-card--${tone(assets?.dayPnl)}`">
          <div class="stat-label">当日盈亏</div>
          <div class="stat-value num" :class="[changeColorClass(assets?.dayPnl), chipClass(assets?.dayPnl)]">
            {{ signed(assets?.dayPnl) }}
          </div>
          <div class="stat-sub">按最近两日价差估算</div>
          <div v-if="showSparkRow" class="stat-spark" />
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6" :md="3">
        <el-card shadow="never" class="stat-card" :class="`stat-card--${tone(assets?.floatingPnl)}`">
          <div class="stat-label">浮动盈亏</div>
          <div class="stat-value num" :class="[changeColorClass(assets?.floatingPnl), chipClass(assets?.floatingPnl)]">
            {{ signed(assets?.floatingPnl) }}
          </div>
          <div class="stat-sub">收益率 {{ formatPercent(assets?.floatingPnlPct) }}</div>
          <div v-if="showSparkRow" class="stat-spark" />
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6" :md="3">
        <el-card shadow="never" class="stat-card" :class="`stat-card--${tone(assets?.totalPnl)}`">
          <div class="stat-label">累计收益</div>
          <div class="stat-value num" :class="[changeColorClass(assets?.totalPnl), chipClass(assets?.totalPnl)]">
            {{ signed(assets?.totalPnl) }}
          </div>
          <div class="stat-sub">含已实现 {{ formatAmount(assets?.realizedPnl) }} / 收益率
            {{ formatPercent(assets?.totalPnlPct) }}</div>
          <div v-if="showSparkRow" class="stat-spark">
            <SparkLine
              v-if="totalTrend.values.length >= 2"
              :points="totalTrend.values"
              :labels="totalTrend.labels"
              :color="changeColor(assets?.totalPnl)"
            />
          </div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6" :md="3">
        <el-card shadow="never" class="stat-card" :class="`stat-card--${tone(assets?.monthPnl)}`">
          <div class="stat-label">本月收益</div>
          <div class="stat-value num" :class="[changeColorClass(assets?.monthPnl), chipClass(assets?.monthPnl)]">
            {{ signed(assets?.monthPnl) }}
          </div>
          <div class="stat-sub">
            {{ assets?.monthPnlPct == null ? '本月起始持仓为空' : `收益率 ${formatPercent(assets?.monthPnlPct)}` }}
          </div>
          <div v-if="showSparkRow" class="stat-spark">
            <SparkLine
              v-if="monthTrend.values.length >= 2"
              :points="monthTrend.values"
              :labels="monthTrend.labels"
              :color="changeColor(assets?.monthPnl)"
            />
          </div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6" :md="3">
        <el-card shadow="never" class="stat-card" :class="`stat-card--${tone(assets?.yearPnl)}`">
          <div class="stat-label">本年收益</div>
          <div class="stat-value num" :class="[changeColorClass(assets?.yearPnl), chipClass(assets?.yearPnl)]">
            {{ signed(assets?.yearPnl) }}
          </div>
          <div class="stat-sub">
            {{ assets?.yearPnlPct == null ? '本年起持仓为空' : `收益率 ${formatPercent(assets?.yearPnlPct)}` }}
          </div>
          <div v-if="showSparkRow" class="stat-spark">
            <SparkLine
              v-if="yearTrend.values.length >= 2"
              :points="yearTrend.values"
              :labels="yearTrend.labels"
              :color="changeColor(assets?.yearPnl)"
            />
          </div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6" :md="3">
        <el-card shadow="never" class="stat-card" :class="`stat-card--${tone(assets?.weekPnl)}`">
          <div class="stat-label">近 7 日收益</div>
          <div class="stat-value num" :class="[changeColorClass(assets?.weekPnl), chipClass(assets?.weekPnl)]">
            {{ signed(assets?.weekPnl) }}
          </div>
          <div class="stat-sub">同步 {{ overview?.syncSummary ?? '--' }}</div>
          <div v-if="showSparkRow" class="stat-spark">
            <SparkLine
              v-if="weekTrend.values.length >= 2"
              :points="weekTrend.values"
              :labels="weekTrend.labels"
              :color="changeColor(assets?.weekPnl)"
            />
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 空账户引导（未开始使用时的三步上手卡）：替代下方成排的灰空态，明确"下一步做什么" -->
    <el-card v-if="needGuide" shadow="never" class="board-card guide-card">
      <template #header>
        <div class="card-header">
          <span>开始使用</span>
          <span class="muted">总资产 = 持仓市值 ＋ 现金；录入流水后自动生成收益曲线与各项收益</span>
        </div>
      </template>
      <div class="guide-steps">
        <div
          v-for="step in guideSteps"
          :key="step.no"
          class="guide-step"
          :class="{ 'guide-step--done': step.done }"
        >
          <span class="guide-no">{{ step.no }}</span>
          <div class="guide-text">
            <div class="guide-title">{{ step.title }}</div>
            <div class="guide-desc">{{ step.desc }}</div>
          </div>
          <el-button size="small" :type="step.done ? 'default' : 'primary'" @click="step.action()">
            {{ step.done ? '已完成' : step.button }}
          </el-button>
        </div>
      </div>
    </el-card>

    <!-- 全球指数看板（M4-06） -->
    <el-card shadow="never" class="board-card">
      <template #header>
        <div class="card-header">
          <span>全球指数看板</span>
          <div class="card-header-right">
            <el-tag v-if="indicesDegraded" size="small" type="warning" effect="plain">
              数据源暂不可用 · 显示最近快照
            </el-tag>
            <el-tag v-else-if="indicesTrendMissing" size="small" type="info" effect="plain">
              部分迷你线待补齐
            </el-tag>
            <span v-if="indices.length" class="muted">行情时间 {{ indexTime }}</span>
            <el-button size="small" :loading="indicesLoading" @click="forceLoadIndices">刷新</el-button>
          </div>
        </div>
      </template>
      <!-- 市场切换：一次只展示一个市场（不再平铺全部区域），label 直接切换 -->
      <div class="index-switch">
        <el-radio-group v-model="activeRegion" size="small">
          <el-radio-button v-for="group in indexGroups" :key="group.region" :value="group.region">
            {{ group.label }}
          </el-radio-button>
        </el-radio-group>
        <span v-if="activeGroupItems.length" class="muted">共 {{ activeGroupItems.length }} 个指数</span>
      </div>
      <el-row :gutter="10">
        <!-- 一行 6 个：EP 的栅格靠断点 CSS 生效，且 sm 的媒体查询在基础 span 之后，
             只写 :span="4" :sm="8" 时宽屏仍按 8（3 个一行）渲染 → 必须补 md 断点覆盖 -->
        <el-col
          v-for="item in activeGroupItems"
          :key="item.indexCode"
          :span="4"
          :xs="12"
          :sm="8"
          :md="4"
          class="index-col"
        >
          <div class="index-card">
            <div class="index-head">
              <span class="index-name">{{ item.indexName }}</span>
            </div>
            <div class="index-price">
              <span class="index-last">{{ item.lastPrice?.toLocaleString('zh-CN') }}</span>
              <span class="index-pct" :class="changeColorClass(item.changePct)">
                {{ item.changePct === null ? '--' : `${item.changePct > 0 ? '+' : ''}${item.changePct.toFixed(2)}%` }}
              </span>
            </div>
            <SparkLine
              :points="item.trend.map((point) => point.price)"
              :labels="item.trend.map((point) => point.time)"
              :color="sparkColor(item.changePct)"
            />
          </div>
        </el-col>
      </el-row>
      <el-empty
        v-if="activeGroupItems.length === 0"
        description="该市场暂无指数数据，可点右上角刷新重试"
        :image-size="60"
      />
    </el-card>

    <!-- 收益曲线（M4-07）：未开始使用时不展示（避免灰空态），由上方引导卡说明 -->
    <el-card v-if="!needGuide" shadow="never" class="board-card">
      <template #header>
        <div class="card-header">
          <span>
            收益曲线（对比沪深300）
            <!-- 基准缺失（数据源限流降级）时说明原因，避免"标题写着对比却看不到线"的困惑 -->
            <span v-if="curve && curve.hasData && !benchmarkAvailable" class="muted">· 基准暂不可用（数据源限流时会自动恢复）</span>
          </span>
          <el-radio-group v-model="range" size="small" @change="loadCurve">
            <el-radio-button v-for="item in RANGES" :key="item.value" :value="item.value">
              {{ item.label }}
            </el-radio-button>
          </el-radio-group>
        </div>
      </template>
      <el-empty v-if="curve && !curve.hasData" description="暂无持仓流水，录入交易后展示收益曲线" />
      <ChartPanel v-else :option="curveOption" height="340px" />
    </el-card>

    <!-- 速览区（M4-08）：未开始使用时不展示（信号/持仓/近 7 日收益此时必然为空） -->
    <el-row v-if="!needGuide" :gutter="12">
      <el-col :span="8" :xs="24">
        <el-card shadow="never" class="board-card">
          <template #header>
            <div class="card-header">
              <span>最新信号</span>
              <el-button size="small" text :loading="signalsLoading" @click="loadSignals">刷新</el-button>
            </div>
          </template>
          <el-empty v-if="latestSignals.length === 0" description="暂无信号（交易日 09:00 自动计算）" :image-size="60" />
          <ul v-else class="signal-list">
            <li
              v-for="signal in latestSignals"
              :key="signal.id"
              class="signal-item signal-item--clickable"
              :title="signal.readFlag === 0 ? '点击标记为已读' : '已读'"
              @click="openSignal(signal)"
            >
              <span v-if="signal.readFlag === 0" class="unread-dot" />
              <el-tag :type="signal.direction === 'BUY' ? 'danger' : signal.direction === 'SELL' ? 'success' : 'info'"
                      size="small" effect="dark">
                {{ directionName(signal.direction) }}
              </el-tag>
              <span class="signal-fund">{{ fundNameOf(signal.fundCode) || signal.fundCode }}</span>
              <span class="signal-date">{{ signal.signalDate }}</span>
            </li>
          </ul>
        </el-card>
      </el-col>
      <el-col :span="8" :xs="24">
        <el-card shadow="never" class="board-card">
          <template #header><span>近 7 日收益（元）</span></template>
          <el-empty v-if="!weekBars.length" description="暂无持仓流水" :image-size="60" />
          <ChartPanel v-else :option="weekBarOption" height="220px" />
        </el-card>
      </el-col>
      <el-col :span="8" :xs="24">
        <el-card shadow="never" class="board-card">
          <template #header><span>持仓概览（市值前 5）</span></template>
          <el-empty v-if="overview && overview.holdings.length === 0" description="暂无持仓" :image-size="60" />
          <el-table v-else :data="overview?.holdings ?? []" size="small">
            <el-table-column prop="fundName" label="基金" min-width="120" show-overflow-tooltip>
              <template #default="{ row }">
                <!-- 名称即详情入口（与基金池/持仓表同款 fund-link） -->
                <span
                  class="fund-link"
                  :title="`查看 ${row.fundName} 详情`"
                  @click="$router.push(`/funds/${row.fundCode}`)"
                >{{ row.fundName }}</span>
              </template>
            </el-table-column>
            <el-table-column label="市值" align="right" width="90">
              <template #default="{ row }">{{ formatAmount(row.marketValue) }}</template>
            </el-table-column>
            <el-table-column label="占比" align="right" width="70">
              <template #default="{ row }">{{ formatPercent(row.weightPct) }}</template>
            </el-table-column>
            <el-table-column label="当日盈亏" align="right" width="90">
              <template #default="{ row }">
                <span :class="changeColorClass(row.dayPnl)">{{ signed(row.dayPnl) }}</span>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <!-- 数据类速览：引导态（未开始使用）下只在确实有数据时渲染，避免成排灰空态 -->
    <el-row v-if="showMovers || showTradesCard" class="data-row" :gutter="12">
      <el-col v-if="showMovers" :span="8" :xs="24">
        <el-card shadow="never" class="board-card">
          <template #header><span>自选 7 日涨跌榜</span></template>
          <el-empty v-if="overview && overview.movers.length === 0" description="暂无数据" :image-size="60" />
          <template v-else>
            <!-- 领涨与领跌并排：两榜合起来最多 12 条，竖排会让本卡比同排卡片高出一倍 -->
            <div class="mover-columns">
              <div class="mover-column">
                <div class="mover-group-title">领涨</div>
                <ul class="signal-list">
                  <li v-for="item in topMovers" :key="item.fundCode" class="signal-item">
                    <span
                      class="signal-fund fund-link"
                      :title="`查看 ${item.fundName} 详情`"
                      @click="$router.push(`/funds/${item.fundCode}`)"
                    >{{ item.fundName }}</span>
                    <span class="index-pct" :class="changeColorClass(item.changePct7d)">
                      {{ formatPercent(item.changePct7d) }}
                    </span>
                  </li>
                </ul>
              </div>
              <div class="mover-column">
                <div class="mover-group-title">领跌</div>
                <ul class="signal-list">
                  <li v-for="item in bottomMovers" :key="item.fundCode" class="signal-item">
                    <span
                      class="signal-fund fund-link"
                      :title="`查看 ${item.fundName} 详情`"
                      @click="$router.push(`/funds/${item.fundCode}`)"
                    >{{ item.fundName }}</span>
                    <span class="index-pct" :class="changeColorClass(item.changePct7d)">
                      {{ formatPercent(item.changePct7d) }}
                    </span>
                  </li>
                </ul>
              </div>
            </div>
          </template>
        </el-card>
      </el-col>

      <el-col v-if="showAllocation" :span="8" :xs="24">
        <el-card shadow="never" class="board-card">
          <template #header><span>资产配置</span></template>
          <el-empty v-if="overview && overview.allocation.length === 0" description="暂无持仓" :image-size="60" />
          <ChartPanel v-else :option="pieOption" height="100%" />
        </el-card>
      </el-col>
      <el-col v-if="showTradesCard" :span="8" :xs="24">
        <el-card shadow="never" class="board-card">
          <template #header>
            <div class="card-header">
              <span>交易流水</span>
              <router-link class="trades-link" to="/trades">查看全部</router-link>
            </div>
          </template>
          <el-table v-loading="tradesLoading" :data="recentTrades" size="small">
            <el-table-column label="交易日期" min-width="96">
              <template #default="{ row }">{{ row.tradeDate }}</template>
            </el-table-column>
            <el-table-column label="类型" min-width="68">
              <template #default="{ row }">
                <el-tag :type="typeTagOf(row.tradeType)" size="small">{{ typeTextOf(row.tradeType) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="基金" min-width="120" show-overflow-tooltip>
              <template #default="{ row }">
                <template v-if="row.fundCode">
                  {{ row.fundCode }}
                  <span v-if="fundNameOf(row.fundCode)" class="muted">{{ fundNameOf(row.fundCode) }}</span>
                </template>
                <span v-else class="muted">账户资金</span>
              </template>
            </el-table-column>
            <el-table-column label="金额" min-width="100" align="right">
              <template #default="{ row }">{{ formatAmount(row.amount) }}</template>
            </el-table-column>
          </el-table>
          <el-empty
            v-if="!tradesLoading && recentTrades.length === 0"
            description="暂无交易流水"
            :image-size="60"
          />
        </el-card>
      </el-col>
    </el-row>
    <TradeEntryDialog v-model="entryVisible" :preset-type="entryType" @saved="onEntrySaved" />
  </div>
</template>

<script setup lang="ts">
import { useUserStore } from '@/stores/user'
import { PERM } from '@/utils/permissions'
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import type { EChartsOption } from 'echarts'
import ChartPanel from '@/components/charts/ChartPanel.vue'
import SparkLine from '@/components/dashboard/SparkLine.vue'
import {
  dashboardAssets,
  dashboardIndices,
  refreshDashboardIndices,
  dashboardOverview,
  profitCurve,
  type AssetSummaryVO,
  type DashboardOverviewVO,
  type IndexQuoteVO,
  type ProfitCurveVO
} from '@/api/dashboard'
import { recentSignals, markSignalsRead, type SignalRecord } from '@/api/strategy'
import TradeEntryDialog from '@/components/trade/TradeEntryDialog.vue'
import { pageTrades, watchlist, type TradeFlow } from '@/api/fund'
import { changeColorClass, formatAmount, formatPercent } from '@/utils/format'
import { changeColor, UP, DOWN } from '@/utils/palette'

const userStore = useUserStore()

/** 基准线是否可用：基准数据源被封堵时后端返回全 null，此处用于给出说明文案 */
const benchmarkAvailable = computed(() => (curve.value?.benchmarkPct ?? []).some((value) => value !== null))

/** 曲线区间选项（与后端 rangeStart 对应） */
const RANGES = [
  { value: '1M', label: '近1月' },
  { value: '3M', label: '近3月' },
  { value: '6M', label: '近6月' },
  { value: 'YTD', label: '今年' },
  { value: '1Y', label: '近1年' },
  { value: '3Y', label: '近3年' },
  { value: 'ALL', label: '全部' }
]

const router = useRouter()
const assets = ref<AssetSummaryVO | null>(null)

/**
 * 记账弹窗的预设交易类型：1=买入（引导卡「记一笔」），4=资金转入（总资产卡「转入/转出」）。
 * 两个入口共用同一个弹窗实例，避免挂载多份对话框。
 */
const entryVisible = ref(false)
const entryType = ref(4)

/** 打开记账弹窗并指定预设交易类型（4=转入/转出，1=买入） */
function openEntry(tradeType: number) {
  entryType.value = tradeType
  entryVisible.value = true
}

/** 流水保存后刷新资产/速览/曲线（划转与买卖都会影响总资产与收益口径） */
function onEntrySaved() {
  loadAssets()
  loadOverview()
  loadCurve()
  loadWeekCurve()
}
const overview = ref<DashboardOverviewVO | null>(null)
/** 指数看板（quotes 为行情列表，degraded/trendMissing 为数据源降级标记） */
const indices = ref<IndexQuoteVO[]>([])
const indicesDegraded = ref(false)
const indicesTrendMissing = ref(false)
const indicesLoading = ref(false)
const curve = ref<ProfitCurveVO | null>(null)
const range = ref('1Y')
const signals = ref<SignalRecord[]>([])
const signalsLoading = ref(false)
/** 近 7 日收益柱图数据（由 1M 曲线派生，避免额外口径分叉） */
const weekCurve = ref<ProfitCurveVO | null>(null)
/** 正在手动重试同步的基金代码（行内 loading） */
/** 最近交易流水（仪表盘卡片，最近 6 笔） */
const recentTrades = ref<TradeFlow[]>([])
const tradesLoading = ref(false)
/** 基金代码 → 名称（自选池，供流水卡片显示名称） */
const fundNameMap = ref<Record<string, string>>({})

async function loadRecentTrades() {
  tradesLoading.value = true
  try {
    recentTrades.value = (await pageTrades({ page: 1, size: 6 })).records
  } finally {
    tradesLoading.value = false
  }
}

async function loadFundNames() {
  const funds = await watchlist().catch(() => [])
  const map: Record<string, string> = {}
  funds.forEach((fund) => {
    map[fund.fundCode] = fund.fundName
  })
  fundNameMap.value = map
}


/** 指数/速览自动刷新间隔（毫秒）：与后端 5 分钟行情任务对齐 */
const AUTO_REFRESH_MS = 5 * 60 * 1000
let timer: number | undefined
/** 页面隐藏时暂停轮询（FR1：页面隐藏暂停刷新） */
let paused = false

/** 最新信号（V5.40 用户口径：最近 5 条记录，跨日期取最新；字段只展示 基金/日期/方向） */
const latestSignals = computed(() => signals.value.slice(0, 5))

/** 每榜最多展示条数（V5.31：5 → 7；V5.54 用户口径：7 → 6，两榜合计最多 12 条；自选不足 12 只时按实际数量取） */
const MOVER_SIZE = 6

const topMovers = computed(() => (overview.value?.movers ?? []).slice(0, MOVER_SIZE))

/**
 * 领跌榜：取排序末尾的若干条，并**剔除已出现在领涨榜的基金**。
 * 自选不足 10 只时两榜必然重叠（如 8 只时原来有 2 只会同时出现在领涨与领跌里），
 * 同一只基金在两榜各说一次属于误导。
 */
const bottomMovers = computed(() => {
  const all = overview.value?.movers ?? []
  const taken = new Set(topMovers.value.map((item) => item.fundCode))
  const rest = all.filter((item) => !taken.has(item.fundCode))
  return rest.slice(-MOVER_SIZE).reverse()
})

/** 近 7 日逐日收益（元）：由 1M 曲线的相邻两日累计收益差派生 */
const weekBars = computed(() => {
  const curveData = weekCurve.value
  if (!curveData || !curveData.hasData || curveData.pnl.length < 2) {
    return [] as { date: string; pnl: number }[]
  }
  const bars: { date: string; pnl: number }[] = []
  const start = Math.max(1, curveData.pnl.length - 7)
  for (let i = start; i < curveData.pnl.length; i++) {
    bars.push({ date: curveData.dates[i], pnl: Number((curveData.pnl[i] - curveData.pnl[i - 1]).toFixed(2)) })
  }
  return bars
})

/**
 * 引导态判定：**尚无任何持仓成交**（无持仓且无已实现收益）时展示三步引导卡。
 * 只取"无持仓 + 无已实现收益"两个条件：已转入现金但还没买入（常见起步状态）同样需要引导，
 * 且此时收益曲线/信号/持仓概览必然为空，靠引导卡替代成排灰空态；
 * 曾经持有过（已实现收益非零）的用户不再看到"开始使用"，避免打扰。
 */
const needGuide = computed(() => {
  const data = assets.value
  if (!data) {
    return false
  }
  return (data.holdingCount ?? 0) === 0 && (data.realizedPnl ?? 0) === 0
})

/** 引导卡三步：done 由当前账户状态推导（已导入基金 / 已有现金 / 已有持仓） */
const guideSteps = computed(() => [
  {
    no: 1,
    title: '导入指数基金',
    desc: '支持场内 ETF 与场外指数基金，自动拉取近 15 年（或自成立以来）历史数据',
    button: '去数据导入',
    done: (assets.value?.watchCount ?? 0) > 0,
    action: () => router.push('/import'),
    perm: PERM.ACTION_IMPORT_FUND
  },
  {
    no: 2,
    title: '转入资金到账户',
    desc: '从银行卡转入的资金计入现金余额，与持仓市值一起构成总资产',
    button: '转入资金',
    done: (assets.value?.cashBalance ?? 0) > 0,
    action: () => openEntry(4),
    perm: PERM.ACTION_TRADE
  },
  {
    no: 3,
    title: '记一笔买入或卖出',
    desc: '录入后自动重算份额与摊薄成本，并生成收益曲线和各项收益指标',
    button: '记一笔',
    done: (assets.value?.holdingCount ?? 0) > 0,
    action: () => openEntry(1),
    perm: PERM.ACTION_TRADE
  }
].filter((step: { perm?: string }) => !step.perm || userStore.can(step.perm)))

/** 涨跌榜是否有内容可展示（引导态下无数据则整卡不渲染） */
const showMovers = computed(() => !needGuide.value || (overview.value?.movers.length ?? 0) > 0)

/** 同步状态是否有内容可展示（引导态下无自选基金则整卡不渲染） */
/** 交易流水卡片是否展示（引导态下无流水则不渲染） */
const showTradesCard = computed(() => !needGuide.value || recentTrades.value.length > 0)

/** 资产配置是否有内容可展示（只有现金时也有"现金"份额，故按数据判断而非引导态） */
const showAllocation = computed(() => !needGuide.value || (overview.value?.allocation.length ?? 0) > 0)

/** 是否有任意一张副卡有迷你线可画：都没有时整行不渲染槽位，避免无意义的底部留白 */
const showSparkRow = computed(() =>
  [totalTrend.value, monthTrend.value, yearTrend.value, weekTrend.value].some((trend) => trend.values.length >= 2)
)

/** 涨跌色调键：红涨绿跌，零值与无值归中性（主卡左侧色条与副卡数值色块共用） */
function tone(value: number | null | undefined): 'up' | 'down' | 'flat' {
  if (value === null || value === undefined || Number.isNaN(value) || value === 0) {
    return 'flat'
  }
  return value > 0 ? 'up' : 'down'
}

/** 副卡数值的浅色底色块（红涨绿跌浅底）；中性值不加底，避免"零值也有颜色" */
function chipClass(value: number | null | undefined): string {
  const key = tone(value)
  return key === 'flat' ? '' : `stat-value--${key}`
}

/** 迷你线数据：values 数值序列，labels 与之一一对应的日期（悬停提示用） */
interface TrendSeries {
  /** 走势数值 */
  values: number[]
  /** 日期标签 */
  labels: string[]
}

/**
 * 从收益曲线截取迷你线序列。
 *
 * @param from 起始日期（yyyy-MM-dd）；传 null 表示用整段曲线（累计收益卡）
 * 区间切片按首日重基（首点归零），迷你线只表达区间内走势形状、不误导绝对水平；
 * 曲线未覆盖该区间或点数不足 2 时返回空序列，模板退化为留白而不是画一条假线
 */
function trendOfRange(from: string | null): TrendSeries {
  const data = curve.value
  if (!data || !data.hasData || data.pnl.length < 2) {
    return { values: [], labels: [] }
  }
  const start = from ? data.dates.findIndex((date) => date >= from) : 0
  if (start < 0) {
    return { values: [], labels: [] }
  }
  const values = data.pnl.slice(start)
  if (values.length < 2) {
    return { values: [], labels: [] }
  }
  const base = from ? values[0] : 0
  return {
    values: values.map((value) => Number((value - base).toFixed(2))),
    labels: data.dates.slice(start)
  }
}

/** 本自然月首日（本地时区；不能用 toISOString，东八区凌晨会错一天） */
function monthStart(): string {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-01`
}

/** 本自然年首日 */
function yearStart(): string {
  return `${new Date().getFullYear()}-01-01`
}

/** 累计收益迷你线（整段曲线，工具提示显示真实累计值） */
const totalTrend = computed<TrendSeries>(() => trendOfRange(null))

/** 本月收益迷你线（自本月首日起，首点归零） */
const monthTrend = computed<TrendSeries>(() => trendOfRange(monthStart()))

/** 本年收益迷你线（自本年首日起，首点归零） */
const yearTrend = computed<TrendSeries>(() => trendOfRange(yearStart()))

/** 近 7 日收益迷你线（逐日收益，正负交替） */
const weekTrend = computed<TrendSeries>(() => ({
  values: weekBars.value.map((bar) => bar.pnl),
  labels: weekBars.value.map((bar) => bar.date)
}))

/** 近 7 日收益柱状图（红涨绿跌） */
const weekBarOption = computed<EChartsOption>(() => ({
  tooltip: {
    trigger: 'axis',
    valueFormatter: (value) => `${value} 元`
  },
  grid: { left: 60, right: 20, top: 20, bottom: 30 },
  xAxis: {
    type: 'category',
    data: weekBars.value.map((bar) => bar.date.substring(5))
  },
  yAxis: { type: 'value', name: '元' },
  series: [
    {
      type: 'bar',
      barMaxWidth: 26,
      data: weekBars.value.map((bar) => ({
        value: bar.pnl,
        itemStyle: { color: bar.pnl >= 0 ? UP : DOWN }
      }))
    }
  ]
}))

const indexTime = computed(() => {
  const time = indices.value[0]?.quoteTime
  return time ? time.replace('T', ' ').substring(0, 16) : ''
})

/** 收益曲线：左轴累计收益（元），右轴沪深300（%） */
const curveOption = computed<EChartsOption>(() => ({
  tooltip: { trigger: 'axis' },
  legend: { data: ['累计收益(元)', '沪深300(%)'] },
  grid: { left: 70, right: 60, top: 40, bottom: 40 },
  xAxis: { type: 'category', data: curve.value?.dates ?? [] },
  yAxis: [
    { type: 'value', name: '元' },
    { type: 'value', name: '%', axisLabel: { formatter: '{value}%' } }
  ],
  dataZoom: [{ type: 'inside' }, { type: 'slider', height: 14, bottom: 8 }],
  series: [
    {
      name: '累计收益(元)',
      type: 'line',
      smooth: true,
      symbol: 'none',
      areaStyle: { opacity: 0.12 },
      data: curve.value?.pnl ?? []
    },
    {
      name: '沪深300(%)',
      type: 'line',
      smooth: true,
      symbol: 'none',
      yAxisIndex: 1,
      data: curve.value?.benchmarkPct ?? []
    }
  ]
}))

/**
 * 资产配置饼图。
 * 图例（V5.38 用户要求）：用默认的 plain 类型**换行铺开**，不再用 type:'scroll'——滚动分页一次只显示
 * 一行、还要点箭头翻页，看不出整体配置；换行后一眼看全。图例占的行数随基金数增加，
 * 画布高度由卡片等高布局决定（height="100%" 填充卡身剩余空间，见 .data-row 样式）。
 */
const pieOption = computed<EChartsOption>(() => ({
  tooltip: { trigger: 'item', formatter: '{b}<br/>市值 {c} 元（{d}%）' },
  legend: { bottom: 0, type: 'plain', left: 'center', itemGap: 8, itemWidth: 12, itemHeight: 8, textStyle: { fontSize: 12 } },
  series: [
    {
      type: 'pie',
      radius: ['32%', '52%'],
      center: ['50%', '40%'],
      label: { formatter: '{b} {d}%', fontSize: 12 },
      data: (overview.value?.allocation ?? []).map((item) => ({
        name: item.fundName || item.fundCode,
        value: item.marketValue
      }))
    }
  ]
}))

async function loadAssets() {
  assets.value = await dashboardAssets()
}

async function loadOverview() {
  overview.value = await dashboardOverview()
}

async function loadIndices() {
  indicesLoading.value = true
  try {
    const board = await dashboardIndices()
    indices.value = board.quotes
    indicesDegraded.value = board.degraded
    indicesTrendMissing.value = board.trendMissing
  } finally {
    indicesLoading.value = false
  }
}

/** 手动刷新：强制拉取东财最新数据（定时任务之外的即时更新入口） */
async function forceLoadIndices() {
  indicesLoading.value = true
  try {
    const board = await refreshDashboardIndices()
    indices.value = board.quotes
    indicesDegraded.value = board.degraded
    indicesTrendMissing.value = board.trendMissing
  } finally {
    indicesLoading.value = false
  }
}

/** 指数看板分组：固定区域顺序展示（A股/港股/美股/亚太/欧洲） */
const INDEX_REGION_ORDER: Array<{ region: string; label: string }> = [
  { region: 'CN', label: 'A 股' },
  { region: 'HK', label: '港 股' },
  { region: 'US', label: '美 股' },
  { region: 'ASIA', label: '亚 太' },
  { region: 'EU', label: '欧 洲' }
]

const indexGroups = computed(() =>
  INDEX_REGION_ORDER.map(({ region, label }) => ({
    region,
    label,
    items: indices.value.filter((item) => item.region === region)
  })).filter((group) => group.items.length > 0)
)

/** 当前查看的市场区域（CN/HK/US/ASIA/EU）；看板一次只渲染一个市场，避免 17 张卡平铺过长 */
const activeRegion = ref('CN')

/** 当前市场下的指数卡片 */
const activeGroupItems = computed(() => indices.value.filter((item) => item.region === activeRegion.value))

/** 数据到达后若当前区域没有指数（如首次加载或该区域拉取失败），自动切到第一个有数据的区域 */
watch(indexGroups, (groups) => {
  if (groups.length > 0 && !groups.some((group) => group.region === activeRegion.value)) {
    activeRegion.value = groups[0].region
  }
})

async function loadCurve() {
  curve.value = await profitCurve(range.value)
}

async function loadWeekCurve() {
  weekCurve.value = await profitCurve('1M')
}

async function loadSignals() {
  signalsLoading.value = true
  try {
    signals.value = await recentSignals(7)
  } finally {
    signalsLoading.value = false
  }
}

/** 点击信号：标记已读并跳转该基金详情（FR1：未读红点 + 点击跳转详情） */
async function openSignal(signal: SignalRecord) {
  if (signal.readFlag === 0) {
    await markSignalsRead([signal.id])
    signal.readFlag = 1
  }
  router.push(`/funds/${signal.fundCode}`)
}

/** 交易类型选项（与后端 TradeTypeEnum 一致） */
const TRADE_TYPES = [
  { value: 1, label: '买入' },
  { value: 2, label: '卖出' },
  { value: 3, label: '分红' },
  { value: 4, label: '转入' },
  { value: 5, label: '转出' }
]

function typeTextOf(tradeType: number): string {
  return TRADE_TYPES.find((item) => item.value === tradeType)?.label ?? '未知'
}

/** 类型标签色：买入红 / 卖出绿 / 分红琥珀 / 划转中性（与交易流水页同规则） */
function typeTagOf(tradeType: number): 'danger' | 'success' | 'warning' | 'info' {
  switch (tradeType) {
    case 1:
      return 'danger'
    case 2:
      return 'success'
    case 3:
      return 'warning'
    default:
      return 'info'
  }
}

/** 基金名称查询（取不到时返回空串，模板显示为只留代码） */
function fundNameOf(fundCode: string): string {
  return fundNameMap.value[fundCode] ?? ''
}

/** 带符号金额（正数补 +） */
function signed(value: number | null | undefined): string {
  if (value === null || value === undefined || Number.isNaN(value)) {
    return '--'
  }
  return `${value > 0 ? '+' : ''}${formatAmount(value)}`
}

function directionName(direction: string): string {
  return direction === 'BUY' ? '买入' : direction === 'SELL' ? '卖出' : '持有'
}

/** 走势线颜色随涨跌（红涨绿跌） */
/** 迷你线颜色：红涨绿跌（与全站涨跌语义色一致） */
function sparkColor(changePct: number | null): string {
  return changeColor(changePct)
}

/** 页面可见性变化：隐藏时暂停轮询，恢复时立即刷新一次 */
function onVisibilityChange() {
  paused = document.hidden
  if (!paused) {
    loadIndices()
    loadOverview()
    loadAssets()
  }
}

onMounted(() => {
  loadFundNames()
  loadRecentTrades()
  loadAssets()
  loadOverview()
  loadIndices()
  loadCurve()
  loadWeekCurve()
  loadSignals()
  document.addEventListener('visibilitychange', onVisibilityChange)
  timer = window.setInterval(() => {
    if (paused) {
      return
    }
    loadIndices()
    loadOverview()
    loadAssets()
  }, AUTO_REFRESH_MS)
})

onUnmounted(() => {
  document.removeEventListener('visibilitychange', onVisibilityChange)
  if (timer) {
    window.clearInterval(timer)
  }
})
</script>

<style scoped>
/* 区块间距 12 → 16：卡片体系更需要呼吸感，仍取 token 刻度 */
.dashboard {
  display: flex;
  flex-direction: column;
  gap: var(--q-space-4);
}

.stat-row {
  flex: none;
}

/* KPI 行等高：el-row 是 flex，让列拉伸、卡片撑满列高，迷你线用 margin-top:auto 贴底，
   这样带走势线与不带走势线的副卡高度一致（无需写死 min-height） */
.stat-row :deep(.el-col) {
  display: flex;
}

.stat-card {
  flex: 1;
  display: flex;
  flex-direction: column;
  /* 左侧 3px 语义色条：涨跌/中性一眼可辨（颜色在下方按 tone 覆盖） */
  border-left: 3px solid var(--q-border);
  transition: box-shadow 0.18s, transform 0.18s;
}

.stat-card:hover {
  box-shadow: var(--q-shadow-hover);
  transform: translateY(-1px);
}

.stat-card--up {
  border-left-color: var(--q-color-up);
}

.stat-card--down {
  border-left-color: var(--q-color-down);
}

.stat-card--flat {
  /* 中性值不参与涨跌编码：色条退回 1px，与其他边框等宽，避免整排"灰杠" */
  border-left-width: 1px;
  border-left-color: var(--q-border);
}

/* 主卡：浅红微底 + 深色文字 + 淡红光晕，承载"总资产"这一唯一主角
   （V2.3 按用户要求由品牌蓝改为微红；红色只作底纹，数字仍用中性深色，不与"涨"混淆） */
.stat-card--hero {
  border: none;
  border-left: none;
  background: var(--q-bg-hero-card);
  box-shadow: var(--q-shadow-hero);
  color: var(--q-text-on-hero);
}

.stat-card--hero:hover {
  box-shadow: var(--q-shadow-hero);
  transform: none;
}

.stat-card :deep(.el-card__body) {
  flex: 1;
  display: flex;
  flex-direction: column;
  /* EP 的卡片体是 overflow:auto，KPI 卡内容高度固定，改为裁切以免出现内部滚动条 */
  overflow: hidden;
  /* V5.63 用户反馈整排卡过高：上下内边距 12→8、左右 16→12 */
  padding: var(--q-space-2) var(--q-space-3);
}

.hero-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.hero-label {
  font-size: var(--q-font-sm);
  color: var(--q-text-on-hero-sub);
}

.hero-value {
  margin-top: var(--q-space-1);
  font-size: var(--q-font-2xl);
  font-weight: 600;
  line-height: 1.2;
  color: var(--q-text-on-hero);
  font-variant-numeric: tabular-nums;
  font-feature-settings: 'tnum';
}

.hero-sub {
  margin-top: var(--q-space-1);
  font-size: var(--q-font-xs);
  color: var(--q-text-on-hero-sub);
}

.hero-foot {
  display: flex;
  align-items: center;
  gap: var(--q-space-1);
  margin-top: auto;
  padding-top: var(--q-space-1);
  font-size: var(--q-font-xs);
  color: var(--q-text-on-hero-sub);
}

.hero-dot {
  opacity: 0.6;
}

.stat-label {
  font-size: var(--q-font-sm);
  color: var(--q-text-secondary);
}

.stat-value {
  margin-top: var(--q-space-1);
  /* 副卡宽度随栅格变化（窄屏 119px 时 24px 数字会挤出卡片），
     故用 clamp 在 --q-font-lg 与 --q-font-xl 之间按视口宽度自适应；V5.63 上限 24→20 压高度 */
  font-size: clamp(var(--q-font-lg), 1.5vw, var(--q-font-xl));
  font-weight: 600;
  line-height: 1.2;
  color: var(--q-text-primary);
  font-variant-numeric: tabular-nums;
  font-feature-settings: 'tnum';
}

/* 涨跌数值的浅色底色块：让红绿成块，而不是孤零零一行数字 */
.stat-value--up,
.stat-value--down {
  align-self: flex-start;
  padding: 0 var(--q-space-1);
  border-radius: var(--q-radius-sm);
}

.stat-value--up {
  background: var(--q-color-up-soft);
}

.stat-value--down {
  background: var(--q-color-down-soft);
}

.stat-sub {
  margin-top: var(--q-space-1);
  font-size: var(--q-font-xs);
  color: var(--q-text-muted);
  line-height: 1.5;
}

/* 迷你走势槽位：贴底对齐（高度由 30px 的走势线撑开，不再写死高度） */
.stat-spark {
  margin-top: auto;
  padding-top: var(--q-space-1);
}

/* ---------- 空账户引导卡 ---------- */
.guide-steps {
  display: flex;
  flex-direction: column;
  gap: var(--q-space-2);
}

.guide-step {
  display: flex;
  align-items: center;
  gap: var(--q-space-3);
  padding: var(--q-space-3);
  border: 1px solid var(--q-border-light);
  border-radius: var(--q-radius-sm);
  background: var(--q-bg-subtle);
}

.guide-step--done {
  opacity: 0.62;
}

.guide-no {
  flex: none;
  width: 24px;
  height: 24px;
  line-height: 24px;
  text-align: center;
  border-radius: 50%;
  background: var(--q-color-primary-soft);
  color: var(--q-color-primary);
  font-size: var(--q-font-sm);
  font-weight: 600;
}

.guide-step--done .guide-no {
  /* 完成态用中性灰：绿色（--q-color-down）在本站专表"下跌"，不借来表达"已完成" */
  background: var(--q-bg-hover);
  color: var(--q-text-muted);
}

.guide-text {
  flex: 1;
  min-width: 0;
}

.guide-title {
  font-size: var(--q-font-base);
  font-weight: 600;
  color: var(--q-text-primary);
}

.guide-desc {
  margin-top: 2px;
  font-size: var(--q-font-xs);
  color: var(--q-text-secondary);
  line-height: 1.5;
}

.board-card :deep(.el-card__body) {
  padding: var(--q-space-3) var(--q-space-4);
}

/* 涨跌榜/资产配置/交易流水同排卡片等高（V5.53 用户要求）：
   el-col 拉伸为 flex，卡片撑满列高；资产配置的饼图填充卡身剩余空间，
   高度随左右卡片走而不是自己撑开（与 .stat-row 的 KPI 行同一手法） */
.data-row :deep(.el-col) {
  display: flex;
}

.data-row :deep(.board-card) {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.data-row :deep(.el-card__body) {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.data-row :deep(.el-card__body > .chart-panel) {
  flex: 1;
  min-height: 0;
}

/* 涨跌榜：两列列表随卡片等高拉伸，每行均分高度填满卡身（消除底部空白，V5.55 用户要求）；
   与右侧交易流水表格的行距视觉对齐 */
.data-row :deep(.mover-columns) {
  flex: 1;
  min-height: 0;
}

/* 列本身纵向 flex：标题占自身高度，列表取剩余空间——不能用 height:100%（会把标题高度算漏，
   总高超出卡身而出滚动条，实测踩过） */
.data-row :deep(.mover-column) {
  display: flex;
  flex-direction: column;
}

.data-row :deep(.mover-column ul) {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.data-row :deep(.mover-column ul li) {
  flex: 1;
}

/* 卡片标题：品牌色短竖条，与指数看板分组标题同一视觉母题，形成页面节奏 */
.card-header {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-left: var(--q-space-3);
  font-size: var(--q-font-base);
  font-weight: 600;
  color: var(--q-text-primary);
}

.card-header::before {
  content: '';
  position: absolute;
  left: 0;
  top: 50%;
  width: 3px;
  height: 14px;
  border-radius: 1px;
  background: var(--q-color-primary);
  transform: translateY(-50%);
}

.card-header-right {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 400;
}

/* 市场切换条：与卡片头操作区同一行高度节奏 */
.index-switch {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--q-space-3);
  margin-bottom: var(--q-space-3);
}

.index-col {
  margin-bottom: 10px;
}

.index-card {
  border: 1px solid var(--q-border);
  border-radius: var(--q-radius-sm);
  padding: var(--q-space-3);
  background: var(--q-bg-card);
  transition: box-shadow 0.18s, border-color 0.18s;
}

.index-card:hover {
  border-color: var(--q-color-primary-border);
  box-shadow: var(--q-shadow-hover);
}

.index-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.index-name {
  font-size: var(--q-font-sm);
  font-weight: 600;
  color: var(--q-text-primary);
  /* 一行 6 个时卡片较窄，名称过长省略而不是撑破卡片 */
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.index-region {
  font-size: 12px;
  color: var(--q-text-muted);
  border: 1px solid var(--q-border-light);
  border-radius: var(--q-radius-sm);
  padding: 1px var(--q-space-1);
  background: var(--q-bg-subtle);
}

.index-price {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin: 6px 0;
}

.index-last {
  font-size: var(--q-font-lg);
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.index-pct {
  font-size: var(--q-font-sm);
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.signal-list {
  list-style: none;
  margin: 0;
  padding: 0;
}

.signal-item {
  display: flex;
  align-items: center;
  gap: var(--q-space-2);
  padding: var(--q-space-2) 0;
  border-bottom: 1px solid var(--q-border-light);
  font-size: var(--q-font-sm);
}

.signal-item:last-child {
  border-bottom: none;
}

.signal-item--clickable {
  cursor: pointer;
}

.unread-dot {
  flex: none;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--q-color-up);
}

:deep(.sync-lagging-row) {
  background: var(--q-color-up-soft);
}

.signal-fund {
  flex: none;
  font-weight: 600;
}

/* 可点击的基金名称：主色 + 悬停下划线，与基金池/持仓表的详情入口同款（V5.38） */
.fund-link {
  color: var(--q-color-primary);
  cursor: pointer;
}

.fund-link:hover {
  text-decoration: underline;
}

.signal-date {
  flex: none;
  margin-left: auto;
  font-size: var(--q-font-xs);
  color: var(--q-text-muted);
}

.mover-group-title {
  font-size: var(--q-font-xs);
  color: var(--q-text-muted);
  margin: var(--q-space-2) 0 var(--q-space-1);
  letter-spacing: 0.04em;
}

/* 领涨/领跌并排两列：卡片高度只按较长的一榜算，不再两榜相加 */
.mover-columns {
  display: flex;
  gap: var(--q-space-3);
}

.mover-column {
  flex: 1;
  min-width: 0;
}

/* 列宽只有半卡，长基金名必须可省略，否则会把涨跌幅顶出可视区（百分比永远可见） */
.mover-columns .signal-fund {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.mover-columns .index-pct {
  flex: none;
}

/* 窄屏（手机/平板）两列会挤到看不清基金名，退回上下排布 */
@media (max-width: 768px) {
  .mover-columns {
    display: block;
  }
}
</style>
