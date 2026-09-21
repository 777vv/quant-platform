<template>
  <div v-if="detail">
    <el-page-header content="返回基金池" class="page-header" @back="$router.push('/funds')" />
    <el-card class="block">
      <template #header>
        <div class="detail-header">
          <span>
            {{ detail.fundName }}（{{ detail.fundCode }}）
            <el-tag :type="detail.fundType === 1 ? 'primary' : 'success'" size="small" class="name-tag">
              {{ detail.fundTypeDesc }}
            </el-tag>
            <!-- 标签：预定义库中已贴到本基金的标签 -->
            <el-tag v-for="tag in tags" :key="tag.id" size="small" type="info" class="name-tag">
              {{ tag.name }}
            </el-tag>
            <el-button link type="primary" size="small" @click="tagEditVisible = true">
              {{ tags.length ? '编辑标签' : '添加标签' }}
            </el-button>
            <span v-if="detail.lastPrice != null" class="price">
              {{ detail.fundType === 1 ? detail.lastPrice.toFixed(4) : detail.lastPrice.toFixed(4) }}
              <span :class="detail.changePct && detail.changePct > 0 ? 'text-up' : 'text-down'">
                {{ detail.changePct == null ? '' : `${detail.changePct > 0 ? '+' : ''}${detail.changePct}%` }}
              </span>
            </span>
          </span>
        </div>
      </template>
      <FundTagEditDialog v-model="tagEditVisible" :fund-code="code" @saved="loadTags" />
      <el-descriptions :column="4" border size="small">
        <el-descriptions-item label="跟踪指数">
          {{ detail.indexName || '未识别' }}
          <span v-if="detail.indexCode" class="muted">{{ detail.indexCode }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="成立日期">{{ detail.inceptionDate || '--' }}</el-descriptions-item>
        <el-descriptions-item label="基金公司">{{ detail.fundCompany || '--' }}</el-descriptions-item>
        <el-descriptions-item label="规模(亿)">
          <el-tooltip v-if="detail.fundScale != null" :content="`净资产规模，截止 ${detail.fundScaleDate ?? '未知'}`" placement="top">
            <span class="num">{{ detail.fundScale.toFixed(2) }}</span>
          </el-tooltip>
          <span v-else class="num">--</span>
        </el-descriptions-item>
        <el-descriptions-item label="运作费率">
          <el-tooltip v-if="detail.opFeeRate != null" :content="detailFeeBreakdown" placement="top">
            <span class="num">{{ detail.opFeeRate.toFixed(2) }}%</span>
          </el-tooltip>
          <span v-else class="num">--</span>
        </el-descriptions-item>
        <el-descriptions-item label="溢价率">
          <el-tooltip
            v-if="detail.premiumRate != null"
            :content="`按净值日 ${detail.premiumDate} 的收盘价与单位净值计算`"
            placement="top"
          >
            <span class="num">{{ detail.premiumRate > 0 ? '+' : '' }}{{ detail.premiumRate.toFixed(2) }}%</span>
          </el-tooltip>
          <span v-else class="num">--</span>
        </el-descriptions-item>
        <el-descriptions-item label="最后同步">{{ detail.lastSyncDate || '--' }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-tabs v-model="activeTab" class="block">
      <el-tab-pane label="行情走势" name="chart">
        <el-radio-group v-if="detail.fundType === 2" v-model="navMode" size="small" class="block">
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
          <el-checkbox v-model="showMacd" size="small" @change="loadChart">MACD 副图</el-checkbox>
          <el-checkbox v-model="showYield" size="small" @change="loadChart">股息率副图</el-checkbox>
          <span v-if="showYield && yieldData && !yieldData.priceAvailable" class="muted">
            历史股息率待补齐：需要未复权价（已排入下次同步）
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
              <span class="section-title">{{ detail.fundName }} · 行情走势</span>
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
      </el-tab-pane>

      <el-tab-pane label="指数估值" name="valuation">
        <template v-if="valuation && valuation.hasData">
          <el-alert type="info" :closable="false" class="block">
            跟踪指数 {{ valuation.indexName }}，当前 PE {{ valuation.latestPe?.toFixed(2) }}，
            处于近10年 <b>{{ valuation.currentPercentile }}%</b> 分位
            <el-tag :type="valuationTagType" size="small" class="name-tag">{{ valuationTagText }}</el-tag>
          </el-alert>
          <ChartPanel v-if="valuationOption" :option="valuationOption as EChartsOption" height="360px" />
        </template>
        <el-empty v-else :description="valuationEmptyText" />
      </el-tab-pane>

      <el-tab-pane label="交易流水" name="trades">
        <div class="toolbar">
          <!-- 新增走共享弹窗（与基金池「记一笔」同一套录入规则）；行内「编辑」仍用本页弹窗做更正 -->
          <el-button type="primary" size="small" @click="addVisible = true">新增流水</el-button>
        </div>
        <el-table v-loading="tradesLoading" :data="tradeRecords" border size="small">
          <el-table-column prop="tradeDate" label="日期" min-width="120" />
          <el-table-column label="类型" min-width="100">
            <template #default="{ row }">
              <el-tag :type="row.tradeType === 1 ? 'danger' : row.tradeType === 2 ? 'success' : 'info'" size="small">
                {{ tradeTypeText(row.tradeType) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="价格" min-width="120" align="right">
            <template #default="{ row }">{{ row.price.toFixed(4) }}</template>
          </el-table-column>
          <el-table-column label="份额" min-width="140" align="right">
            <template #default="{ row }">{{ row.share.toFixed(2) }}</template>
          </el-table-column>
          <el-table-column label="金额" min-width="140" align="right">
            <template #default="{ row }">{{ row.amount.toFixed(2) }}</template>
          </el-table-column>
          <el-table-column label="手续费" min-width="110" align="right">
            <template #default="{ row }">{{ row.fee.toFixed(2) }}</template>
          </el-table-column>
          <!-- 备注给出固定宽度：它是本表唯一的弹性列时会吃掉全部剩余宽度（实测 298px），
               固定后剩余宽度由各列按比例分摊，表格依然铺满容器 -->
          <el-table-column prop="note" label="备注" min-width="160" show-overflow-tooltip />
          <!-- 操作列作为唯一的弹性列吸收剩余宽度、按钮靠右结尾：
               若让"备注"当弹性列，它会吃掉全部剩余宽度（实测 298px，用户反馈占太宽） -->
          <el-table-column label="操作" min-width="150" align="right">
            <template #default="{ row }">
              <el-button size="small" @click="openDialog(row)">编辑</el-button>
              <el-button size="small" type="danger" plain @click="handleDeleteTrade(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="策略配置" name="strategies">
        <div class="toolbar">
          <el-button type="primary" size="small" @click="strategyDialogVisible = true">新增策略</el-button>
        </div>
        <el-table :data="strategies" border size="small">
          <el-table-column prop="strategyType" label="类型" width="140" />
          <el-table-column prop="strategyName" label="名称" width="110" />
          <el-table-column label="参数" min-width="220" show-overflow-tooltip>
            <template #default="{ row }">{{ row.params }}</template>
          </el-table-column>
          <el-table-column label="启用" width="90">
            <template #default="{ row }">
              <el-switch :model-value="row.enabled === 1" @change="toggleStrategy(row)" />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="90">
            <template #default="{ row }">
              <el-button size="small" type="danger" plain @click="handleDeleteStrategy(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="回测" name="backtest">
        <el-card shadow="never" class="block">
          <template #header>发起回测</template>
          <el-form inline>
            <el-form-item label="策略">
              <el-select v-model="backtestForm.strategyType" style="width: 150px">
                <el-option v-for="t in strategyTypeList" :key="t.type" :value="t.type" :label="t.name" />
              </el-select>
            </el-form-item>
            <el-form-item label="开始日期">
              <el-date-picker v-model="backtestForm.startDate" type="date" value-format="YYYY-MM-DD" />
            </el-form-item>
            <el-form-item label="结束日期">
              <el-date-picker v-model="backtestForm.endDate" type="date" value-format="YYYY-MM-DD" />
            </el-form-item>
            <el-form-item label="初始资金">
              <el-input-number v-model="backtestForm.initialCapital" :min="1000" :controls="false" style="width: 140px" />
            </el-form-item>
          </el-form>
          <StrategyParamForm v-model="backtestForm.params" :type="backtestForm.strategyType" />
          <el-button type="primary" :loading="backtestRunning" @click="handleBacktest">开始回测</el-button>
        </el-card>
        <el-table :data="backtestRecords" border size="small">
          <el-table-column prop="id" label="#" width="60" />
          <el-table-column prop="strategyType" label="策略" width="130" />
          <el-table-column label="区间" width="200">
            <template #default="{ row }">{{ row.startDate }} ~ {{ row.endDate }}</template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag :type="row.status === 1 ? 'success' : row.status === 2 ? 'danger' : 'info'" size="small">
                {{ row.status === 1 ? '成功' : row.status === 2 ? '失败' : '运行中' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="总收益%" width="100" align="right">
            <template #default="{ row }">{{ row.totalReturnPct ?? '--' }}</template>
          </el-table-column>
          <el-table-column label="最大回撤%" width="110" align="right">
            <template #default="{ row }">{{ row.maxDrawdownPct ?? '--' }}</template>
          </el-table-column>
          <el-table-column prop="tradeCount" label="交易数" width="80" align="right" />
          <el-table-column prop="errorMsg" label="失败原因" min-width="140" show-overflow-tooltip />
          <el-table-column label="操作" width="90" fixed="right">
            <template #default="{ row }">
              <el-button size="small" @click="$router.push(`/backtest/${row.id}`)">结果</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="strategyDialogVisible" title="新增策略配置" width="460px">
      <el-form label-width="100px">
        <el-form-item label="策略类型">
          <el-select v-model="newStrategyType" style="width: 100%">
            <el-option v-for="t in strategyTypeList" :key="t.type" :value="t.type" :label="t.name" />
          </el-select>
        </el-form-item>
        <StrategyParamForm v-model="newStrategyParams" :type="newStrategyType" />
      </el-form>
      <template #footer>
        <el-button @click="strategyDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSaveStrategy">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="dialogVisible" title="编辑流水" width="480px">
      <el-form :model="tradeForm" label-width="90px">
        <el-form-item label="交易日期">
          <el-date-picker v-model="tradeForm.tradeDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="类型">
          <el-radio-group v-model="tradeForm.tradeType">
            <el-radio-button :value="1">买入</el-radio-button>
            <el-radio-button :value="2">卖出</el-radio-button>
            <el-radio-button :value="3">分红</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="价格/净值">
          <el-input-number v-model="tradeForm.price" :precision="4" :min="0" :controls="false" style="width: 100%" />
        </el-form-item>
        <el-form-item label="份额">
          <el-input-number v-model="tradeForm.share" :precision="2" :min="0" :controls="false" style="width: 100%" />
        </el-form-item>
        <el-form-item label="金额">
          <el-input-number v-model="tradeForm.amount" :precision="2" :min="0" :controls="false" style="width: 100%" />
        </el-form-item>
        <el-form-item label="手续费">
          <el-input-number v-model="tradeForm.fee" :precision="2" :min="0" :controls="false" style="width: 100%" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="tradeForm.note" maxlength="100" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSaveTrade">保存</el-button>
      </template>
    </el-dialog>
    <TradeEntryDialog v-model="addVisible" :preset-fund="code" :preset-type="1" @saved="onTradeAdded" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { EChartsOption } from 'echarts'
import {
  CANDLE_UP,
  CANDLE_DOWN,
  VOLUME,
  PE_LINE,
  PRIMARY,
  MA_COLORS,
  BOLL_MID,
  BOLL_BAND,
  MACD_DIF,
  MACD_DEA
} from '@/utils/palette'
import { ma, boll, macd } from '@/utils/indicators'
import { changeColorClass } from '@/utils/format'
import { annualizedVolatilityPct, maxDrawdownPct, rangeReturnPct } from '@/utils/metrics'
import { TEXT_INVERSE, TRADE_BUY, TRADE_DIVIDEND, TRADE_SELL } from '@/utils/palette'
import { useEscToClose } from '@/utils/escClose'

/** 均线组（行业默认参数） */
const MA_WINDOWS = [5, 10, 20, 60]

/** 布林带参数（20 日 ± 2 倍标准差，行业默认） */
const BOLL_PERIOD = 20
const BOLL_K = 2
import ChartPanel from '@/components/charts/ChartPanel.vue'
import FundTagEditDialog from '@/components/fund/FundTagEditDialog.vue'
import StrategyParamForm from '@/components/strategy/StrategyParamForm.vue'
import TradeEntryDialog from '@/components/trade/TradeEntryDialog.vue'
import {
  addStrategy,
  backtestDetail,
  createBacktest,
  deleteStrategy,
  fundStrategies,
  pageBacktest,
  strategyTypes,
  updateStrategy
} from '@/api/strategy'
import type { BacktestRecord, StrategyConfig, StrategyTypeVO } from '@/api/strategy'
import {
  deleteTrade,
  fundDetail,
  fundDividendYield,
  fundKline,
  fundMarks,
  fundNav,
  fundTags,
  fundValuation,
  pageTrades,
  updateTrade
} from '@/api/fund'
import type {
  DividendYieldVO,
  FundDetailVO,
  FundMarkVO,
  FundTagVO,
  SeriesPoint,
  TradeFlow,
  TradeFlowRequest,
  ValuationSeriesVO
} from '@/api/fund'

const route = useRoute()
const code = route.params.code as string

const detail = ref<FundDetailVO | null>(null)
const activeTab = ref('chart')
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
    maxDrawdownPct: maxDrawdownPct(slice),
    volatilityPct: annualizedVolatilityPct(slice)
  }
})

/** 用户缩放/拖动 dataZoom：更新可见窗口，区间表现随之刷新 */
function onZoomChange(range: { startPercent: number; endPercent: number }) {
  visibleWindow.value = range
}
/** 指标预热天数（自然日）：给 MA60/BOLL/MACD 留足前置数据 */
const INDICATOR_WARMUP_DAYS = 150

/**
 * 行情图交易标记（买入 b / 卖出 s / 分红 q）。
 * 每只基金只取一次并缓存：缩放、框选、换指标、切区间都复用这份数据，不重复请求；
 * 接口失败静默降级为"无标记"，不影响看 K 线。
 */
const tradeMarks = ref<FundMarkVO[]>([])

async function loadMarks() {
  tradeMarks.value = await fundMarks(code).catch(() => [])
}

/** 主图指标：MA（均线组 5/10/20/60）/ BOLL（20 日 ±2σ）/ NONE */
const mainIndicator = ref<'MA' | 'BOLL' | 'NONE'>('MA')

/** 是否显示 MACD 副图（DIF/DEA + 柱，柱按红涨绿跌着色） */
const showMacd = ref(false)

/** 股息率副图开关与数据（单次分红股息率 + TTM 滚动 12 个月） */
const showYield = ref(false)
const yieldData = ref<DividendYieldVO | null>(null)

/** 全屏查看状态（双击图表进入，Esc 或关闭按钮退出） */
const fullscreen = ref(false)

/** 图表高度随副图数量自适应 */
const chartHeight = computed(() => `${420 + subChartCount.value * 100}px`)

/** 除成交量外的副图数量（MACD / 股息率），用于图表高度与网格划分 */
const subChartCount = computed(() => (showMacd.value ? 1 : 0) + (showYield.value ? 1 : 0))

/**
 * 副图纵向布局：主图 + 成交量 +（MACD）+（股息率）。
 * 图表数量随开关变化，写死百分比会在开关组合下互相重叠，故按数量算：
 * 每张副图占固定高度，主图吃掉剩余空间，副图之间留 gap，底部预留 dataZoom 滑块的位置。
 *
 * @returns grids 每张图的 [top%, height%]（顺序：主图, 成交量, MACD?, 股息率?）
 */
function subChartGrids(): { top: number; height: number }[] {
  const extra = subChartCount.value
  const volHeight = extra === 0 ? 15 : 12
  const extraHeight = extra <= 1 ? 14 : 11
  const gap = 3
  const topStart = 9
  const bottomReserve = 12
  const total = topStart + bottomReserve + volHeight + extra * extraHeight + (1 + extra) * gap
  const mainHeight = Math.max(24, 100 - total)
  const grids = [{ top: topStart, height: mainHeight }, { top: 0, height: volHeight }]
  grids[1].top = topStart + mainHeight + gap
  let cursor = grids[1].top + volHeight + gap
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
  const ttm = expandTtm(dates, yieldData.value?.ttm ?? [])
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
    lineStyle: { width: 1.6, color: PE_LINE },
    itemStyle: { color: PE_LINE }
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

/** 把 {top,height} 数字转成 ECharts 需要的百分号字符串 */
function toGridOption(bands: { top: number; height: number }[]): Record<string, unknown>[] {
  return bands.map((band) => ({
    left: '10%',
    right: '3%',
    top: `${band.top.toFixed(1)}%`,
    height: `${band.height.toFixed(1)}%`
  }))
}

/**
 * 把稀疏的 TTM 阶跃点铺到图表的日期轴上（每根 K 线一个值，取值 = 之前最近一个阶跃点）。
 * 稀疏点直接画线只会连出几段折线、两头还断空，铺开后才是一条完整的阶梯。
 */
function expandTtm(dates: string[], ttm: { date: string; yieldPct: number | null }[]): (number | null)[] {
  const sorted = [...ttm].sort((a, b) => (a.date < b.date ? -1 : 1))
  let cursor = 0
  let current: number | null = null
  return dates.map((date) => {
    while (cursor < sorted.length && sorted[cursor].date <= date) {
      current = sorted[cursor].yieldPct
      cursor++
    }
    return current
  })
}

/** 双击图表进入全屏查看 */
function openFullscreen() {
  fullscreen.value = true
}

// 全屏层按 Esc 退出（普通 div 无法接收 keydown，需全局监听）
useEscToClose(fullscreen)

/** 本基金已贴标签（来自预定义标签库） */
const tags = ref<FundTagVO[]>([])
const tagEditVisible = ref(false)

/** 拉取本基金标签 */
async function loadTags() {
  const result = await fundTags(code)
  tags.value = result.tags
}
const valuation = ref<ValuationSeriesVO | null>(null)
const valuationOption = ref<EChartsOption | null>(null)
const tradeRecords = ref<TradeFlow[]>([])
const tradesLoading = ref(false)
const dialogVisible = ref(false)
/** 共享录入弹窗（新增流水）可见性 */
const addVisible = ref(false)
const editingId = ref<number | null>(null)

const defaultForm = (): TradeFlowRequest => ({
  fundCode: code,
  tradeType: 1,
  tradeDate: new Date().toISOString().slice(0, 10),
  price: 0,
  share: 0,
  amount: 0,
  fee: 0,
  note: ''
})
const tradeForm = ref<TradeFlowRequest>(defaultForm())

/**
 * 估值空态文案：区分三种情况，避免"跟踪指数已识别但该指数没有 PE"被误报成"未识别跟踪指数"。
 * 例如国债 ETF 跟踪的上证 5 年期国债指数属于债券指数，本就不适用 PE 估值。
 */
/** 运作费率明细（悬浮提示）：管理费 + 托管费 + 销售服务费 */
const detailFeeBreakdown = computed(() => {
  const part = (label: string, value: number | null | undefined) => `${label} ${(value ?? 0).toFixed(2)}%`
  return `${part('管理费', detail.value?.mgmtFeeRate)} + ${part('托管费', detail.value?.custFeeRate)} + `
    + `${part('销售服务费', detail.value?.salesFeeRate)}（年化）`
})

const valuationEmptyText = computed(() => {
  if (detail.value?.indexCode) {
    return '该指数暂无估值数据（数据源仅覆盖中证/上证系列指数）'
  }
  if (detail.value?.indexName) {
    return `跟踪指数「${detail.value.indexName}」不适用 PE 估值（债券/货币类指数无市盈率）`
  }
  return '未识别跟踪指数，估值不可用'
})

const valuationTagType = computed(() => {
  const p = valuation.value?.currentPercentile
  if (p === null || p === undefined) return 'info'
  if (p <= 20) return 'success'
  if (p >= 80) return 'danger'
  return 'warning'
})

const valuationTagText = computed(() => {
  const p = valuation.value?.currentPercentile
  if (p === null || p === undefined) return ''
  if (p <= 20) return '低估'
  if (p >= 80) return '高估'
  return '合理'
})

function tradeTypeText(type: number): string {
  return type === 1 ? '买入' : type === 2 ? '卖出' : '分红'
}

async function loadDetail() {
  detail.value = await fundDetail(code)
  await loadTags()
}

/**
 * 加载行情图。
 * 未指定日期时按区间下拉取数；指定了自定义日期区间（含框选）时：
 * ① 往前多取 INDICATOR_WARMUP_DAYS 天作为**指标预热段**——MA60/BOLL/MACD 需要足量前置数据，
 *    只取选区会让窗口开头的指标失真甚至为空；
 * ② 指标在完整序列上计算，再用 dataZoom 把显示窗口收敛到选区，做到"看某一段"又不丢指标精度。
 */
async function loadChart() {
  if (!detail.value) return
  const range = chartDateRange.value
  const start = range ? shiftDays(range[0], -INDICATOR_WARMUP_DAYS) : undefined
  const end = range ? range[1] : undefined
  // 股息率副图打开时才拉（并按区间缓存）：关掉副图就不产生任何额外请求
  if (showYield.value) {
    const span = range ? Math.max(1, daysBetween(shiftDays(range[0], -1), range[1])) : rangeDays.value
    yieldData.value = await fundDividendYield(code, span).catch(() => null)
  }
  const points: SeriesPoint[] =
    detail.value.fundType === 1
      ? await fundKline(code, rangeDays.value, start, end)
      : await fundNav(code, rangeDays.value, start, end)
  const base = detail.value.fundType === 1 ? klineOption(points) : navOption(points, navMode.value)
  chartDates.value = points.map((point) => point.date)
  chartValues.value = points.map((point) =>
    detail.value?.fundType === 1 ? Number(point.close) : Number(point[navMode.value] ?? point.unitNav)
  )
  const withMarks = appendTradeMarks(range ? withWindow(base, chartDates.value, range) : base, points)
  chartOption.value = withMarks
  // 初始可见窗口取 option 上的 dataZoom（预设区间默认显示最近 40%、自定义区间为选区）
  const zooms = (withMarks.dataZoom as Array<{ start?: number; end?: number }> | undefined) ?? []
  visibleWindow.value = { startPercent: zooms[0]?.start ?? 0, endPercent: zooms[0]?.end ?? 100 }
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
    detail.value?.fundType === 1 ? Number(point.low ?? point.close) : Number(point[navMode.value] ?? point.unitNav)
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
  const grids: Record<string, unknown>[] = toGridOption(subChartGrids())
  const xAxes: Record<string, unknown>[] = [
    { type: 'category', data: dates },
    { type: 'category', gridIndex: 1, data: dates, axisLabel: { show: false } }
  ]
  const yAxes: Record<string, unknown>[] = [
    { type: 'value', scale: true },
    { gridIndex: 1, axisLabel: { show: false }, splitLine: { show: false } }
  ]
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

  // MACD 副图
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
  } else {
    // 未开 MACD 时副图仅作为留白占位，避免主图被拉满导致刻度拥挤
    grids.pop()
    xAxes.pop()
    yAxes.pop()
  }

  if (yieldOn) {
    appendYieldSubChart(series, xAxes, yAxes, dates, macdOn ? 3 : 2)
  }

  // 原来没有 MACD 时只驱动轴 0，成交量副图不会跟着缩放；这里按实际网格数量生成
  const axisIndexes = Array.from({ length: 2 + subChartCount.value }, (_, i) => i)
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

async function loadValuation() {
  valuation.value = await fundValuation(code)
  if (valuation.value.hasData) {
    valuationOption.value = {
      tooltip: { trigger: 'axis' },
      grid: { left: '8%', right: '3%', top: '8%', bottom: '12%' },
      xAxis: { type: 'category', data: valuation.value.series.map((p) => p.date) },
      yAxis: { type: 'value', scale: true },
      dataZoom: [{ type: 'inside', start: 60, end: 100 }],
      series: [
        {
          type: 'line',
          name: 'PE',
          data: valuation.value.series.map((p) => p.pe),
          showSymbol: false,
          itemStyle: { color: PE_LINE },
          lineStyle: { color: PE_LINE, width: 1.6 },
          areaStyle: { opacity: 0.08, color: PE_LINE }
        }
      ]
    }
  }
}

async function loadTrades() {
  tradesLoading.value = true
  try {
    const page = await pageTrades({ fundCode: code, page: 1, size: 100 })
    tradeRecords.value = page.records
  } finally {
    tradesLoading.value = false
  }
}

/**
 * 打开"编辑流水"弹窗（本页专用，用于更正历史流水：价/量/金额都可手改）。
 * 新增流水请走共享的 {@link TradeEntryDialog}（金额+数量 → 价格派生）。
 *
 * @param row 待编辑的流水行
 */
function openDialog(row: TradeFlow) {
  editingId.value = row.id
  tradeForm.value = {
    fundCode: row.fundCode,
    tradeType: row.tradeType,
    tradeDate: row.tradeDate,
    price: row.price,
    share: row.share,
    amount: row.amount,
    fee: row.fee,
    note: row.note
  }
  dialogVisible.value = true
}

/** 留空：编辑分支只做更新（新增已改用共享弹窗） */
async function handleSaveTrade() {
  if (!editingId.value) {
    ElMessage.warning('未指定要更正的流水')
    return
  }
  if (!tradeForm.value.tradeDate || tradeForm.value.amount <= 0) {
    ElMessage.warning('请填写完整的日期、成交价、份额与金额')
    return
  }
  await updateTrade(editingId.value, tradeForm.value)
  ElMessage.success('已保存，持仓已重算')
  dialogVisible.value = false
  loadTrades()
}

/** 共享弹窗新增成功后：刷新流水与本页持仓相关展示 */
function onTradeAdded() {
  loadTrades()
  loadDetail()
}

async function handleDeleteTrade(row: TradeFlow) {
  await ElMessageBox.confirm(`确认删除 ${row.tradeDate} 的${tradeTypeText(row.tradeType)}流水？`, '删除确认', {
    type: 'warning'
  })
  await deleteTrade(row.id)
  ElMessage.success('已删除，持仓已重算')
  loadTrades()
}

watch(navMode, () => loadChart())

// ===== 策略配置与回测 =====
const strategies = ref<StrategyConfig[]>([])
const strategyTypeList = ref<StrategyTypeVO[]>([])
const strategyDialogVisible = ref(false)
const newStrategyType = ref('GRID')
const newStrategyParams = ref<Record<string, unknown>>({})
const backtestForm = reactive({
  strategyType: 'GRID',
  params: {} as Record<string, unknown>,
  startDate: '',
  endDate: '',
  initialCapital: 100000
})
const backtestRecords = ref<BacktestRecord[]>([])
const backtestRunning = ref(false)

async function loadStrategies() {
  strategies.value = await fundStrategies(code)
  // 已配置的策略参数预填回测表单
  if (strategies.value.length > 0 && !backtestForm.startDate) {
    const first = strategies.value[0]
    backtestForm.strategyType = first.strategyType
    backtestForm.params = JSON.parse(first.params)
  }
}

async function loadStrategyTypes() {
  strategyTypeList.value = await strategyTypes()
}

async function handleSaveStrategy() {
  await addStrategy(code, { strategyType: newStrategyType.value, params: newStrategyParams.value })
  ElMessage.success('策略已保存')
  strategyDialogVisible.value = false
  loadStrategies()
}

async function toggleStrategy(row: StrategyConfig) {
  await updateStrategy(row.id, { enabled: row.enabled === 1 ? 0 : 1 })
  loadStrategies()
}

async function handleDeleteStrategy(row: StrategyConfig) {
  await ElMessageBox.confirm(`确认删除 ${row.strategyName} 配置？`, '删除确认', { type: 'warning' })
  await deleteStrategy(row.id)
  loadStrategies()
}

async function loadBacktests() {
  const page = await pageBacktest(code, 1, 20)
  backtestRecords.value = page.records
}

async function handleBacktest() {
  if (!backtestForm.startDate || !backtestForm.endDate) {
    ElMessage.warning('请选择回测区间')
    return
  }
  backtestRunning.value = true
  try {
    const res = await createBacktest({ fundCode: code, ...backtestForm })
    ElMessage.success('回测任务已提交，稍候刷新查看结果')
    const timer = window.setInterval(async () => {
      const record = await backtestDetail(res.id)
      if (record.status !== 0) {
        window.clearInterval(timer)
        loadBacktests()
        if (record.status === 1) {
          ElMessage.success('回测完成，点击"结果"查看')
        } else {
          ElMessage.error(`回测失败：${record.errorMsg}`)
        }
      }
    }, 1500)
  } finally {
    backtestRunning.value = false
  }
}

onMounted(() => {
  loadMarks()
  loadDetail().then(() => loadChart())
  loadValuation()
  loadTrades()
  loadStrategyTypes()
  loadStrategies()
  loadBacktests()
})
</script>

<style scoped>
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

.page-header {
  margin-bottom: 8px;
}

.block {
  margin-bottom: 16px;
}

.name-tag {
  margin-left: 8px;
}

.price {
  margin-left: 16px;
  font-size: var(--q-font-lg);
  font-weight: 600;
}

.detail-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
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

.range-select {
  margin-left: 12px;
}

.toolbar {
  margin-bottom: 10px;
}
</style>
