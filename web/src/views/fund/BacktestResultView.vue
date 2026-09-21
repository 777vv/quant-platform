<template>
  <div v-if="record">
    <el-page-header content="返回" class="page-header" @back="$router.back()" />
    <el-row :gutter="12" class="block">
      <el-col :span="6"><el-card shadow="never"><el-statistic title="总收益率" :value="record.totalReturnPct ?? 0" :precision="2" suffix="%" /></el-card></el-col>
      <el-col :span="6"><el-card shadow="never"><el-statistic title="年化收益率" :value="record.annualizedPct ?? 0" :precision="2" suffix="%" /></el-card></el-col>
      <el-col :span="6"><el-card shadow="never"><el-statistic title="最大回撤" :value="record.maxDrawdownPct ?? 0" :precision="2" suffix="%" /></el-card></el-col>
      <el-col :span="6"><el-card shadow="never"><el-statistic title="夏普比率" :value="record.sharpe ?? 0" :precision="2" /></el-card></el-col>
    </el-row>
    <el-card shadow="never" class="block">
      <el-descriptions :column="5" border size="small">
        <el-descriptions-item label="基金">{{ record.fundCode }}</el-descriptions-item>
        <el-descriptions-item label="策略">{{ record.strategyType }}</el-descriptions-item>
        <el-descriptions-item label="区间">{{ record.startDate }} ~ {{ record.endDate }}</el-descriptions-item>
        <el-descriptions-item label="初始资金">{{ record.initialCapital }}</el-descriptions-item>
        <el-descriptions-item label="期末资产">{{ record.finalAssets }}</el-descriptions-item>
        <el-descriptions-item label="回撤峰谷">{{ record.ddPeakDate || '--' }} → {{ record.ddTroughDate || '--' }}</el-descriptions-item>
        <el-descriptions-item label="回撤修复">{{ record.ddRecoverDate || '未修复' }}</el-descriptions-item>
        <el-descriptions-item label="交易笔数">{{ record.tradeCount }}</el-descriptions-item>
        <el-descriptions-item label="胜率">{{ record.winRate == null ? '--' : record.winRate + '%' }}</el-descriptions-item>
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
      <el-table :data="trades" border size="small" max-height="420">
        <el-table-column prop="tradeDate" label="日期" width="110" />
        <el-table-column label="方向" width="70">
          <template #default="{ row }">
            <el-tag :type="row.direction === 'BUY' ? 'danger' : 'success'" size="small">
              {{ row.direction === 'BUY' ? '买入' : '卖出' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="price" label="价格" width="100" align="right" />
        <el-table-column prop="share" label="份额" width="110" align="right" />
        <el-table-column prop="amount" label="金额" width="120" align="right" />
        <el-table-column prop="fee" label="手续费" width="90" align="right" />
        <el-table-column prop="cashAfter" label="成交后现金" width="130" align="right" />
        <el-table-column prop="positionAfter" label="成交后份额" width="120" align="right" />
        <el-table-column prop="reason" label="信号理由" min-width="200" show-overflow-tooltip />
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import type { EChartsOption } from 'echarts'
import { UP } from '@/utils/palette'
import ChartPanel from '@/components/charts/ChartPanel.vue'
import { backtestDetail, backtestTrades } from '@/api/strategy'
import type { BacktestRecord, BacktestTrade } from '@/api/strategy'

const route = useRoute()
const id = Number(route.params.id)
const record = ref<BacktestRecord | null>(null)
const trades = ref<BacktestTrade[]>([])
const equityOption = ref<EChartsOption | null>(null)
const drawdownOption = ref<EChartsOption | null>(null)

function lineOf(curveJson: string | null): { dates: string[]; values: number[] } | null {
  if (!curveJson) return null
  const arr = JSON.parse(curveJson) as [string, number][]
  return { dates: arr.map((p) => p[0]), values: arr.map((p) => Number(p[1])) }
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
    equityOption.value = {
      tooltip: { trigger: 'axis' },
      legend: { data: ['策略', '买入持有'] },
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
.page-header {
  margin-bottom: 8px;
}

.block {
  margin-bottom: 14px;
}
</style>
