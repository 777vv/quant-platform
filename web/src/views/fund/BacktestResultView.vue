<template>
  <div v-if="record">
    <el-page-header content="返回" class="page-header" @back="$router.back()" />
    <el-row :gutter="12" class="block">
      <el-col :span="6"><el-card shadow="never"><el-statistic title="总收益率" :value="record.totalReturnPct ?? 0" :precision="2" suffix="%" /></el-card></el-col>
      <el-col :span="6"><el-card shadow="never"><el-statistic title="年化收益率" :value="record.annualizedPct ?? 0" :precision="2" suffix="%" /></el-card></el-col>
      <el-col :span="6"><el-card shadow="never"><el-statistic title="最大回撤" :value="record.maxDrawdownPct ?? 0" :precision="2" suffix="%" /></el-card></el-col>
      <el-col :span="6"><el-card shadow="never"><el-statistic title="夏普比率" :value="record.sharpe ?? 0" :precision="2" /></el-card></el-col>
    </el-row>
    <!-- 结论条：一眼看出策略相对"买入持有"是胜是负（分别按全额与同仓位两个口径） -->
    <el-card v-if="verdicts.length" shadow="never" class="block verdict-card">
      <div class="verdict-list">
        <div v-for="item in verdicts" :key="item.label" class="verdict-item">
          <span class="verdict-label">{{ item.label }}</span>
          <span class="verdict-value">{{ item.leftText }}</span>
          <span class="verdict-vs">vs</span>
          <span class="verdict-value">{{ item.rightText }}</span>
          <el-tag :type="item.win ? 'success' : 'danger'" size="small" effect="dark">{{ item.win ? '跑赢' : '落后' }}</el-tag>
        </div>
      </div>
    </el-card>

    <el-card shadow="never" class="block">
      <el-descriptions :column="5" border size="small" title="回测概况">
        <el-descriptions-item label="基金">{{ record.fundCode }}</el-descriptions-item>
        <el-descriptions-item label="策略">{{ strategyName }}</el-descriptions-item>
        <el-descriptions-item label="区间">{{ record.startDate }} ~ {{ record.endDate }}</el-descriptions-item>
        <el-descriptions-item label="初始资金">{{ record.initialCapital }}</el-descriptions-item>
        <el-descriptions-item label="期末资产">{{ record.finalAssets }}</el-descriptions-item>
      </el-descriptions>
      <el-descriptions :column="5" border size="small" title="策略表现" class="desc-block">
        <el-descriptions-item label="总收益%">{{ record.totalReturnPct ?? '--' }}</el-descriptions-item>
        <el-descriptions-item label="年化%">{{ record.annualizedPct ?? '--' }}</el-descriptions-item>
        <el-descriptions-item label="最大回撤%">{{ record.maxDrawdownPct ?? '--' }}</el-descriptions-item>
        <el-descriptions-item label="夏普">{{ record.sharpe ?? '--' }}</el-descriptions-item>
        <el-descriptions-item label="交易笔数">{{ record.tradeCount }}</el-descriptions-item>
        <el-descriptions-item label="胜率">{{ record.winRate == null ? '--' : record.winRate + '%' }}</el-descriptions-item>
        <el-descriptions-item label="回撤峰谷">{{ record.ddPeakDate || '--' }} → {{ record.ddTroughDate || '--' }}</el-descriptions-item>
        <el-descriptions-item label="回撤修复">{{ record.ddRecoverDate || '未修复' }}</el-descriptions-item>
        <el-descriptions-item label="平均仓位份额">{{ record.avgPositionShare ?? '--' }}</el-descriptions-item>
        <el-descriptions-item label="平均持仓市值">{{ record.avgPositionValue ?? '--' }}</el-descriptions-item>
        <el-descriptions-item label="平均持仓成本">{{ record.avgPositionCost ?? '--' }}</el-descriptions-item>
        <el-descriptions-item label="平均成本/本金">
          {{ avgCostRatioPct }}
          <el-tooltip content="平均持仓成本 ÷ 初始资金。自检恒等式：总收益率 = 持仓资产收益率 × 本比例。若比例超过 100%，说明策略把赚到的钱又投了进去（复利再投入），此时持仓资产收益率仍可能低于总收益率，属正常现象">
            <el-icon class="desc-help"><QuestionFilled /></el-icon>
          </el-tooltip>
        </el-descriptions-item>
      </el-descriptions>
      <el-descriptions :column="5" border size="small" title="与「买入持有」对照" class="desc-block">
        <el-descriptions-item label="持仓资产收益率%">
          <span :class="record.positionReturnPct == null ? '' : record.positionReturnPct >= 0 ? 'text-up' : 'text-down'">
            {{ record.positionReturnPct ?? '--' }}
          </span>
          <el-tooltip content="（期末资产 − 初始资金）÷ 平均持仓成本。分母是「实际投进去的钱」（不随行情虚增），所以平均仓位没打满时会高于总收益率；自检：总收益率 = 本指标 × (平均持仓成本 ÷ 初始资金)">
            <el-icon class="desc-help"><QuestionFilled /></el-icon>
          </el-tooltip>
        </el-descriptions-item>
        <el-descriptions-item label="持有总收益%">{{ record.benchTotalReturnPct ?? '--' }}</el-descriptions-item>
        <el-descriptions-item label="持有最大回撤%">{{ record.benchMaxDrawdownPct ?? '--' }}</el-descriptions-item>
        <el-descriptions-item label="同仓位持有收益%">
          {{ sameExposureBenchPct }}
          <el-tooltip content="把买入持有基准按策略的平均仓位占比折算，回答「同样的仓位暴露下策略是否跑赢」；按平均仓位等比折算，属近似对照">
            <el-icon class="desc-help"><QuestionFilled /></el-icon>
          </el-tooltip>
        </el-descriptions-item>
        <el-descriptions-item label="参数">{{ record.params }}</el-descriptions-item>
      </el-descriptions>
    </el-card>
    <el-card shadow="never" class="block" header="资金曲线（策略 vs 买入持有基准）">
      <ChartPanel v-if="equityOption" :option="equityOption as EChartsOption" height="340px" />
    </el-card>
    <el-card shadow="never" class="block" header="回撤曲线（%）">
      <ChartPanel v-if="drawdownOption" :option="drawdownOption as EChartsOption" height="240px" />
    </el-card>
    <el-card shadow="never" class="block" header="交易明细">
      <!-- 列宽全部用 min-width 参与均分（合计约 900px）：表格恒 100% 铺满、窄容器也不出横向滚动条，富余宽度由信号理由吸收 -->
      <el-table :data="tradeRows" border size="small" max-height="420">
        <el-table-column prop="tradeDate" label="日期" min-width="100" />
        <el-table-column label="方向" min-width="55">
          <template #default="{ row }">
            <el-tag :type="row.direction === 'BUY' ? 'danger' : 'success'" size="small">
              {{ row.direction === 'BUY' ? '买入' : '卖出' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="price" label="价格" min-width="70" align="right" />
        <el-table-column prop="share" label="份额" min-width="80" align="right" />
        <el-table-column prop="amount" label="金额" min-width="82" align="right" />
        <el-table-column prop="fee" label="手续费" min-width="60" align="right" />
        <el-table-column prop="cashAfter" label="成交后现金" min-width="90" align="right" />
        <el-table-column label="成交后资产" min-width="90" align="right">
          <template #default="{ row }">{{ row.assetsAfter }}</template>
        </el-table-column>
        <!-- 交易收益 = 本笔成交后资产 − 上一笔成交后资产；只有卖出产生这层含义，买入显示横杠 -->
        <el-table-column label="交易收益" min-width="82" align="right">
          <template #default="{ row }">
            <span v-if="row.tradePnl != null" :class="row.pnlClass">{{ row.tradePnl }}</span>
            <span v-else class="pnl-dash">—</span>
          </template>
        </el-table-column>
        <el-table-column prop="positionAfter" label="成交后份额" min-width="82" align="right" />
        <el-table-column prop="reason" label="信号理由" min-width="110" show-overflow-tooltip />
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { QuestionFilled } from '@element-plus/icons-vue'
import { useRoute } from 'vue-router'
import type { EChartsOption, ScatterSeriesOption } from 'echarts'
import { DOWN, UP } from '@/utils/palette'
import { changeColorClass } from '@/utils/format'
import ChartPanel from '@/components/charts/ChartPanel.vue'
import { backtestDetail, backtestTrades } from '@/api/strategy'
import type { BacktestRecord, BacktestTrade } from '@/api/strategy'

const route = useRoute()
const id = Number(route.params.id)
const record = ref<BacktestRecord | null>(null)
const trades = ref<BacktestTrade[]>([])
const equityOption = ref<EChartsOption | null>(null)
const drawdownOption = ref<EChartsOption | null>(null)

/** 交易明细展示行：在接口行上附加「成交后资产」与「交易收益」（仅卖出行有值）两列 */
type TradeDisplayRow = BacktestTrade & { assetsAfter: string; tradePnl: string | null; pnlClass: string }

/**
 * 展示行装配（按成交时间顺序逐行推进）：
 * 成交后资产 = 成交后现金 + 成交后份额 × 成交价（该笔成交时点上的总资产快照）；
 * 交易收益 = 本笔成交后资产 − 上一笔成交后资产，只对卖出展示（买入不产生已实现收益，显示横杠）。
 */
const tradeRows = computed<TradeDisplayRow[]>(() => {
  let prevAssets: number | null = null
  return trades.value.map((t) => {
    const assets = t.cashAfter + t.positionAfter * t.price
    const pnl = t.direction === 'SELL' && prevAssets != null ? assets - prevAssets : null
    const row: TradeDisplayRow = {
      ...t,
      assetsAfter: assets.toFixed(2),
      tradePnl: pnl == null ? null : (pnl > 0 ? '+' : '') + pnl.toFixed(2),
      pnlClass: changeColorClass(pnl)
    }
    prevAssets = assets
    return row
  })
})

/**
 * 同仓位持有收益%：把「买入持有」基准按策略的平均仓位占比折算。
 * 口径说明：策略平均只用了 X% 的资金，直接拿全额基准比不公平；
 * 折算后回答"同样的仓位暴露下，策略比一直持有好还是差"。
 * 近似之处：按**平均**仓位等比折算，未还原逐日仓位变化（波动的仓位会带来看不见的差异）。
 */
/**
 * 平均持仓成本占初始资金的比例（自检用）：总收益率 = 持仓资产收益率 × 本比例。
 * 超过 100% 表示策略把盈利再投入（成本基础超过本金），此时持仓资产收益率可能仍低于总收益率。
 */
const avgCostRatioPct = computed(() => {
  const item = record.value
  if (!item || item.avgPositionCost == null || !item.initialCapital) {
    return '--'
  }
  return `${(item.avgPositionCost / item.initialCapital * 100).toFixed(1)}%`
})

/** 策略展示名（类型码 → 中文名，未知回退类型码） */
const strategyName = computed(() => {
  const map: Record<string, string> = { OSC_UP: '震荡向上', DIV_GRID: '红利网格', NDX_GRID: '纳指网格', PYRAMID_GRID: '金字塔网格', INV_PYRAMID_GRID: '倒金字塔网格', MA_TP_GRID: '均线止盈/加仓', GRID: '网格交易（已下线）', VAL_PERCENTILE: '估值百分位（已下线）' }
  return record.value ? map[record.value.strategyType] ?? record.value.strategyType : '--'
})

/**
 * 结论条（两组口径）：
 * ① 持仓资产收益率 vs 持有总收益——策略"投出去的钱"的收益率是否高于一直持有；
 * ② 总收益 vs 同仓位持有收益——同仓位暴露下的胜负。
 * 数据缺失（老回测）时不显示，避免给出误导结论。
 */
const verdicts = computed(() => {
  const item = record.value
  if (!item) {
    return []
  }
  const list: { label: string; leftText: string; rightText: string; win: boolean }[] = []
  const pos = item.positionReturnPct
  const bench = item.benchTotalReturnPct
  if (pos != null && bench != null) {
    list.push({
      label: '持仓资产收益率 vs 持有总收益',
      leftText: `${pos}%`,
      rightText: `${bench}%`,
      win: Number(pos) > Number(bench)
    })
  }
  const same = sameExposureBenchPct.value
  if (item.totalReturnPct != null && same !== '--') {
    list.push({
      label: '总收益 vs 同仓位持有',
      leftText: `${item.totalReturnPct}%`,
      rightText: `${same}%`,
      win: Number(item.totalReturnPct) > Number(same)
    })
  }
  return list
})

const sameExposureBenchPct = computed(() => {
  const item = record.value
  if (!item || item.benchTotalReturnPct == null || item.avgPositionValue == null
      || !item.initialCapital) {
    return '--'
  }
  const exposure = item.avgPositionValue / item.initialCapital
  return (item.benchTotalReturnPct * exposure).toFixed(4)
})

function lineOf(curveJson: string | null): { dates: string[]; values: number[] } | null {
  if (!curveJson) return null
  const arr = JSON.parse(curveJson) as [string, number][]
  return { dates: arr.map((p) => p[0]), values: arr.map((p) => Number(p[1])) }
}

/**
 * 资金曲线上的买卖点散点（数据直接来自页面已取回的交易明细，不重复请求）。
 * 纵坐标取该成交日收盘后的资金曲线值；成交日不在曲线区间内的记录跳过。
 * 悬浮提示不展示买卖点本身（skill 3.2.1：标记不进 tooltip，避免用成交价污染原有悬浮信息）。
 */
function markerSeries(
  direction: 'BUY' | 'SELL',
  dates: string[],
  values: number[]
): ScatterSeriesOption[] {
  const valueByDate = new Map(dates.map((d, i) => [d, values[i]]))
  const data = trades.value
    .filter((trade) => trade.direction === direction && valueByDate.has(trade.tradeDate))
    .map((trade) => ({ value: [trade.tradeDate, valueByDate.get(trade.tradeDate)] }))
  if (data.length === 0) {
    return []
  }
  const isBuy = direction === 'BUY'
  return [
    {
      type: 'scatter',
      name: isBuy ? '买入点' : '卖出点',
      data,
      // 买入三角朝上、卖出三角朝下，方向一眼可辨
      symbol: 'triangle',
      symbolRotate: isBuy ? 0 : 180,
      symbolSize: 9,
      itemStyle: { color: isBuy ? UP : DOWN },
      tooltip: { show: false }
    }
  ]
}

onMounted(async () => {
  record.value = await backtestDetail(id)
  const tradePage = await backtestTrades(id, 1, 500)
  trades.value = tradePage.records
  const equity = lineOf(record.value.equityCurve)
  const benchmark = lineOf(record.value.benchmarkCurve)
  const drawdown = lineOf(record.value.drawdownCurve)
  if (equity) {
    // 先构建类型化 series：条件展开会破坏字面量推断，导致 lineStyle.type 被放宽为 string 而报类型错
    const equitySeries: NonNullable<EChartsOption['series']> = [
      { type: 'line', name: '策略', data: equity.values, showSymbol: false, lineStyle: { width: 1.6 } }
    ]
    if (benchmark) {
      equitySeries.push({
        type: 'line',
        name: '买入持有',
        data: benchmark.values,
        showSymbol: false,
        lineStyle: { width: 1.2, type: 'dashed' }
      })
    }
    // 买卖点标注（红▲买入 / 绿▼卖出）
    const markers = [...markerSeries('BUY', equity.dates, equity.values), ...markerSeries('SELL', equity.dates, equity.values)]
    equitySeries.push(...markers)
    const legendData = ['策略', '买入持有']
    if (markers.some((m) => m.name === '买入点')) {
      legendData.push('买入点')
    }
    if (markers.some((m) => m.name === '卖出点')) {
      legendData.push('卖出点')
    }
    equityOption.value = {
      tooltip: { trigger: 'axis' },
      legend: { data: legendData },
      grid: { left: '8%', right: '3%', top: '12%', bottom: '10%' },
      xAxis: { type: 'category', data: equity.dates },
      yAxis: { type: 'value', scale: true },
      dataZoom: [{ type: 'inside', start: 60, end: 100 }],
      series: equitySeries
    }
  }
  if (drawdown) {
    drawdownOption.value = {
      tooltip: { trigger: 'axis' },
      grid: { left: '8%', right: '3%', top: '8%', bottom: '12%' },
      xAxis: { type: 'category', data: drawdown.dates },
      yAxis: { type: 'value' },
      dataZoom: [{ type: 'inside', start: 60, end: 100 }],
      series: [
        {
          type: 'line',
          name: '回撤%',
          data: drawdown.values,
          showSymbol: false,
          areaStyle: { opacity: 0.15, color: UP },
          lineStyle: { color: UP, width: 1.2 }
        }
      ]
    }
  }
})
</script>

<style scoped>
/* 买入行的交易收益横杠：弱化展示（没有可表达的收益） */
.pnl-dash {
  color: var(--q-text-muted);
}

/* 结论条：一排"左值 vs 右值 + 胜负标签"，比在描述列表里找数字直观 */
.verdict-card :deep(.el-card__body) {
  padding: var(--q-space-3) var(--q-space-4);
}

.verdict-list {
  display: flex;
  flex-wrap: wrap;
  gap: var(--q-space-2) var(--q-space-4);
}

.verdict-item {
  display: flex;
  align-items: center;
  gap: var(--q-space-2);
  font-size: var(--q-font-xs);
}

.verdict-label {
  color: var(--q-text-muted);
}

.verdict-value {
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  color: var(--q-text-primary);
}

.verdict-vs {
  color: var(--q-text-muted);
}

/* 三组描述之间的间距 */
.desc-block {
  margin-top: var(--q-space-3);
}

.desc-help {
  margin-left: 4px;
  color: var(--q-text-muted);
  cursor: help;
}

.page-header {
  margin-bottom: 8px;
}

.block {
  margin-bottom: 14px;
}
</style>
