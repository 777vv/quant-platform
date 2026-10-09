<template>
  <div class="market-signal">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>市场信号</span>
          <el-select
            v-model="selectedTags"
            multiple
            collapse-tags
            collapse-tags-tooltip
            clearable
            placeholder="全部标签"
            style="width: 200px"
          >
            <el-option v-for="t in tagOptions" :key="t" :value="t" :label="t" />
          </el-select>
          <el-input
            v-model="fundKeywordInput"
            placeholder="基金名称或代码，回车查询"
            clearable
            style="width: 240px"
            @clear="applyFundKeyword"
            @keyup.enter="applyFundKeyword"
          />
          <span class="muted">全部指标由库内数据计算，只作参考不构成投资建议；各列数据截至日不同，见对应列</span>
          <el-button size="small" text :loading="loading" @click="load(true)">刷新</el-button>
          <span v-if="loadedAt" class="muted">数据时间 {{ loadedAt }}</span>
        </div>
      </template>

      <el-tabs v-model="activeTab">
        <!-- 页签一：多窗口涨跌榜（每个窗口的"正确读法"写进表头提示） -->
        <el-tab-pane label="涨跌榜" name="chg">
          <el-table v-loading="loading" :data="chgRows" size="small" :default-sort="{ prop: 'chg5d', order: 'descending' }" @sort-change="onChgSort">
            <el-table-column label="基金" min-width="150" show-overflow-tooltip>
              <template #default="{ row }">
                <span class="fund-link" :title="`查看 ${row.fundName} 详情`" @click="$router.push(`/funds/${row.fundCode}`)">
                  {{ row.fundName }}
                </span>
                <span class="fund-code">{{ row.fundCode }}</span>
              </template>
            </el-table-column>
            <el-table-column label="现价" align="right" min-width="80">
              <template #default="{ row }">
                <span class="num">{{ formatAmount(row.lastClose) }}</span>
              </template>
            </el-table-column>
            <el-table-column align="right" min-width="88" prop="chg5d" sortable="custom">
              <template #header>
                <span title="近5个交易日涨跌。A股短期反转效应最强窗口：领涨≠能追，领跌=超跌关注">5日</span>
              </template>
              <template #default="{ row }">
                <span class="num" :class="changeColorClass(row.chg5d)">{{ formatPercent(row.chg5d) }}</span>
              </template>
            </el-table-column>
            <el-table-column align="right" min-width="88" prop="chg10d" sortable="custom">
              <template #header>
                <span title="近10个交易日（两周）涨跌。同 5 日：短期看反转，领涨要当心回吐">10日</span>
              </template>
              <template #default="{ row }">
                <span class="num" :class="changeColorClass(row.chg10d)">{{ formatPercent(row.chg10d) }}</span>
              </template>
            </el-table-column>
            <el-table-column align="right" min-width="88" prop="chg20d" sortable="custom">
              <template #header>
                <span title="近20个交易日（约一个月）涨跌。短中期过渡">20日</span>
              </template>
              <template #default="{ row }">
                <span class="num" :class="changeColorClass(row.chg20d)">{{ formatPercent(row.chg20d) }}</span>
              </template>
            </el-table-column>
            <el-table-column align="right" min-width="88" prop="chg30d" sortable="custom">
              <template #header>
                <span title="近30个交易日涨跌。中期动量参考（3~12个月窗口的动量效应相对更稳）">30日</span>
              </template>
              <template #default="{ row }">
                <span class="num" :class="changeColorClass(row.chg30d)">{{ formatPercent(row.chg30d) }}</span>
              </template>
            </el-table-column>
            <el-table-column align="right" min-width="88" prop="chg60d" sortable="custom">
              <template #header>
                <span title="近60个交易日（约一个季度）涨跌。中期动量参考">60日</span>
              </template>
              <template #default="{ row }">
                <span class="num" :class="changeColorClass(row.chg60d)">{{ formatPercent(row.chg60d) }}</span>
              </template>
            </el-table-column>
            <el-table-column align="right" min-width="88" prop="chg90d" sortable="custom">
              <template #header>
                <span title="近90个交易日涨跌。中期动量参考">90日</span>
              </template>
              <template #default="{ row }">
                <span class="num" :class="changeColorClass(row.chg90d)">{{ formatPercent(row.chg90d) }}</span>
              </template>
            </el-table-column>
            <el-table-column align="right" min-width="88" prop="chg120d" sortable="custom">
              <template #header>
                <span title="近120个交易日（约半年）涨跌。中期动量参考">120日</span>
              </template>
              <template #default="{ row }">
                <span class="num" :class="changeColorClass(row.chg120d)">{{ formatPercent(row.chg120d) }}</span>
              </template>
            </el-table-column>
            <el-table-column align="right" min-width="92" prop="chg250d" sortable="custom">
              <template #header>
                <span title="近250个交易日（约一年）涨跌，对应「52周位置」：接近新高=趋势强；但纯新高策略回测会跑输，仅作状态参考">250日</span>
              </template>
              <template #default="{ row }">
                <span class="num" :class="changeColorClass(row.chg250d)">{{ formatPercent(row.chg250d) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="价格数据截至" align="center" min-width="100">
              <template #default="{ row }">{{ row.lastDate ?? '—' }}</template>
            </el-table-column>
          </el-table>
          <div class="pager-row">
            <el-pagination v-model:current-page="chgPage" v-model:page-size="chgSize" :page-sizes="PAGE_SIZES" :total="filteredRows.length" layout="total, sizes, prev, pager, next" background />
          </div>
        </el-tab-pane>

        <!-- 页签二：估值红绿灯（分位窗口可切换，选择记在浏览器本地） -->
        <el-tab-pane label="估值红绿灯" name="valuation">
          <div class="window-row">
            <span class="window-label">分位窗口</span>
            <el-radio-group v-model="peWindow">
              <el-radio-button value="3y">近3年</el-radio-button>
              <el-radio-button value="5y">近5年</el-radio-button>
              <el-radio-button value="10y">近10年</el-radio-button>
              <el-radio-button value="all">全历史</el-radio-button>
            </el-radio-group>
            <span class="muted">灯色：低于30% 低估（绿）/ 30~70% 正常（黄）/ 超过70% 高估（红）；与基金池、估值页签同口径</span>
          </div>
          <el-table v-loading="loading" :data="valRows" size="small">
            <el-table-column label="基金" min-width="150" show-overflow-tooltip>
              <template #default="{ row }">
                <span class="fund-link" :title="`查看 ${row.fundName} 详情`" @click="$router.push(`/funds/${row.fundCode}`)">
                  {{ row.fundName }}
                </span>
                <span class="fund-code">{{ row.fundCode }}</span>
              </template>
            </el-table-column>
            <el-table-column label="跟踪指数" min-width="110" show-overflow-tooltip>
              <template #default="{ row }">{{ row.indexName ?? '—' }}</template>
            </el-table-column>
            <el-table-column label="最新PE" align="right" min-width="80">
              <template #default="{ row }">
                <span class="num">{{ row.pe ?? '—' }}</span>
              </template>
            </el-table-column>
            <el-table-column align="right" min-width="140" :label="`PE百分位（${peWindowLabel}）`">
              <template #default="{ row }">
                <span class="num">{{ row.pePctile ?? '—' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="灯" align="center" min-width="72">
              <template #default="{ row }">
                <el-tag :type="lightType(row.pePctile)" size="small">{{ lightText(row.pePctile) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column align="right" min-width="88">
              <template #header>
                <span title="窗口内的日频 PE 样本天数，样本太少时分位不可信（少于 30 天直接不展示分位）">样本天数</span>
              </template>
              <template #default="{ row }">
                <span class="num">{{ row.peSamples ?? '—' }}</span>
              </template>
            </el-table-column>
            <el-table-column align="right" min-width="92">
              <template #header>
                <span title="股息率 TTM（滚动12个月口径），仅展示、不参与灯色判断">股息率TTM</span>
              </template>
              <template #default="{ row }">
                <span class="num">{{ formatPercent(row.dyTtm) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="PE数据截至" align="center" min-width="100">
              <template #default="{ row }">{{ row.peDate ?? '—' }}</template>
            </el-table-column>
          </el-table>
          <div class="pager-row">
            <el-pagination v-model:current-page="valPage" v-model:page-size="valSize" :page-sizes="PAGE_SIZES" :total="filteredRows.length" layout="total, sizes, prev, pager, next" background />
          </div>
        </el-tab-pane>

        <!-- 页签三：技术面与溢价（阈值本地生效，默认 3%，记在浏览器本地） -->
        <el-tab-pane label="技术面与溢价" name="tech">
          <div class="window-row">
            <span class="window-label">溢价告警阈值</span>
            <el-input-number v-model="premiumThreshold" :min="0.5" :max="50" :step="0.5" :precision="1" :controls="false" style="width: 90px" />
            <span class="muted">% —— 超过阈值的行标红（如纳指 513300 这类 QDII 高溢价买入，指数不跌也可能亏溢价收敛的钱）；修改即时生效</span>
          </div>
          <el-table
            v-loading="loading"
            :data="premiumRows"
            size="small"
            :default-sort="{ prop: 'premiumPct', order: 'descending' }"
            @sort-change="onPremiumSort"
          >
            <el-table-column label="基金" min-width="150" show-overflow-tooltip>
              <template #default="{ row }">
                <span class="fund-link" :class="{ 'text-up': isPremiumWarn(row) }" :title="`查看 ${row.fundName} 详情`" @click="$router.push(`/funds/${row.fundCode}`)">
                  {{ row.fundName }}
                </span>
                <span class="fund-code">{{ row.fundCode }}</span>
              </template>
            </el-table-column>
            <el-table-column label="现价" align="right" min-width="80">
              <template #default="{ row }">
                <span class="num">{{ formatAmount(row.lastClose) }}</span>
              </template>
            </el-table-column>
            <el-table-column align="center" min-width="66">
              <template #header><span title="现价相对20日均线的位置">MA20</span></template>
              <template #default="{ row }">
                <span :class="maClass(row.aboveMa20)">{{ maText(row.aboveMa20) }}</span>
              </template>
            </el-table-column>
            <el-table-column align="center" min-width="66">
              <template #header><span title="现价相对60日均线的位置（网格策略的趋势闸门也用 60 日线）">MA60</span></template>
              <template #default="{ row }">
                <span :class="maClass(row.aboveMa60)">{{ maText(row.aboveMa60) }}</span>
              </template>
            </el-table-column>
            <el-table-column align="center" min-width="70">
              <template #header><span title="现价相对200日均线的位置，长线牛熊分界的常用参考">MA200</span></template>
              <template #default="{ row }">
                <span :class="maClass(row.aboveMa200)">{{ maText(row.aboveMa200) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="趋势" align="center" min-width="80">
              <template #default="{ row }">{{ row.maSummary ?? '—' }}</template>
            </el-table-column>
            <el-table-column align="right" min-width="88">
              <template #header><span title="现价相对历史最高收盘的跌幅%（0=处于高点）。跌得深是左侧参考，但「便宜」要看估值页签">回撤深度</span></template>
              <template #default="{ row }">
                <span class="num">{{ row.drawdownPct == null ? '—' : `${row.drawdownPct.toFixed(2)}%` }}</span>
              </template>
            </el-table-column>
            <el-table-column align="right" min-width="88">
              <template #header><span title="历史全部交易日回撤中，小于当前回撤的占比。90=比历史上90%的时间跌得深（极端）；0=处于高点附近">回撤分位</span></template>
              <template #default="{ row }">
                <span class="num">{{ row.drawdownPctile == null ? '—' : `${row.drawdownPctile.toFixed(1)}%` }}</span>
              </template>
            </el-table-column>
            <el-table-column align="right" min-width="130" prop="premiumPct" sortable="custom">
              <template #header><span title="溢价率=(收盘价−净值)/净值，仅场内ETF。高溢价买入，指数不跌也可能亏溢价收敛的钱">溢价率(净值日)</span></template>
              <template #default="{ row }">
                <span v-if="row.premiumPct != null" class="num">
                  {{ row.premiumPct.toFixed(2) }}%<span class="premium-date">@{{ row.premiumDate ?? '—' }}</span>
                </span>
                <span v-else>—</span>
              </template>
            </el-table-column>
          </el-table>
          <div class="pager-row">
            <el-pagination v-model:current-page="premiumPage" v-model:page-size="premiumSize" :page-sizes="PAGE_SIZES" :total="filteredRows.length" layout="total, sizes, prev, pager, next" background />
          </div>
        </el-tab-pane>
        <!-- 页签四：均价（V5.68）。数据来自每日 23:00 定时任务落表的 fund_ma_daily；
             比值 = 现价 ÷ 对应周期均价（3 位小数，>1 红色=均线上方，<1 绿色=下方）；
             刷新=手动触发计算落表（仅交易日）；回跑=补算过去 N 个交易日 -->
        <el-tab-pane label="均价" name="ma">
          <div class="ma-toolbar">
            <span class="muted">
              数据日期：{{ maDataDate ?? '—' }} · 现价÷均价保留 3 位小数（&gt;1 红色=价格在均线上方，&lt;1 绿色=下方）· 每交易日 23:00 自动计算
            </span>
            <div v-if="userStore.can(PERM.ACTION_SYNC)" class="ma-actions">
              <el-button size="small" :loading="maRefreshing" @click="onMaRefresh">刷新</el-button>
              <el-button size="small" @click="maBackfillVisible = true">回跑数据</el-button>
            </div>
          </div>
          <el-alert
            v-if="!userStore.can(PERM.ACTION_SYNC)"
            type="info"
            :closable="false"
            class="block"
            title="均价快照由系统每个交易日 23:00 自动计算落表；如需立即更新请联系管理员点「刷新」。"
          />
          <el-table v-loading="maLoading" :data="maPaged" size="small" border @sort-change="onMaSort">
            <el-table-column label="基金" min-width="150" show-overflow-tooltip>
              <template #default="{ row }">
                <span class="fund-link" :title="`查看 ${row.fundName} 详情`" @click="$router.push(`/funds/${row.fundCode}`)">
                  {{ row.fundName }}
                </span>
                <span class="fund-code">{{ row.fundCode }}</span>
              </template>
            </el-table-column>
            <el-table-column label="现价" align="right" min-width="84">
              <template #default="{ row }">
                <span class="num">{{ formatAmount(row.closePrice) }}</span>
              </template>
            </el-table-column>
            <el-table-column
              v-for="window in MA_WINDOWS"
              :key="`ratio${window}`"
              align="right"
              min-width="86"
              :prop="`ratio${window}`"
              sortable="custom"
            >
              <template #header>
                <span :title="`现价 ÷ ${window}个交易日均价（简单平均），>1 价格在均线上方`">{{ window }}日</span>
              </template>
              <template #default="{ row }: { row: FundMaRow }">
                <span class="num" :class="ratioClass(ratioOf(row, window))">
                  {{ ratioOf(row, window) == null ? '—' : ratioOf(row, window)!.toFixed(3) }}
                </span>
              </template>
            </el-table-column>
            <!-- 日期：该基金行情数据截至日（各基金可能略有差异，如场外净值滞后一天） -->
            <el-table-column label="日期" align="center" min-width="104">
              <template #default="{ row }: { row: FundMaRow }">
                <span class="num">{{ row.dataDate ?? '—' }}</span>
              </template>
            </el-table-column>
          </el-table>
          <div class="pager-row">
            <el-pagination v-model:current-page="maPage" v-model:page-size="maSize" :page-sizes="PAGE_SIZES" :total="maFilteredRows.length" layout="total, sizes, prev, pager, next" background />
          </div>
        </el-tab-pane>
      </el-tabs>
      <!-- 回跑均线弹窗 -->
      <el-dialog v-model="maBackfillVisible" title="回跑均线数据" width="480px">
        <el-form label-width="110px">
          <el-form-item label="回跑交易日数">
            <el-input-number v-model="maBackfillDays" :min="5" :max="500" :controls="false" style="width: 140px" />
            <span class="field-hint">5~500；取自选池价格日期并集的最近 N 个交易日，已存在的日期会被覆盖更新</span>
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="maBackfillVisible = false">取消</el-button>
          <el-button type="primary" :loading="maBackfilling" @click="onMaBackfill">开始回跑</el-button>
        </template>
      </el-dialog>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { changeColorClass, formatAmount, formatPercent } from '@/utils/format'
import { marketSignalOverviewCached, type MarketSignalRow, type PeWindow } from '@/api/marketSignals'
import { backfillMa, fundMaRows, refreshMa, type FundMaRow } from '@/api/marketSignals'
import { useUserStore } from '@/stores/user'
import { PERM } from '@/utils/permissions'

const loading = ref(false)
const activeTab = ref('chg')
const userStore = useUserStore()
const rows = ref<MarketSignalRow[]>([])
const selectedTags = ref<string[]>([])

// PE 分位窗口与溢价阈值都记在浏览器本地（单用户自用；换浏览器/清缓存回默认值）
const PE_WINDOW_KEY = 'ms-pe-window'
const THRESHOLD_KEY = 'ms-premium-threshold'
const PE_WINDOW_LABELS: Record<PeWindow, string> = { '3y': '近3年', '5y': '近5年', '10y': '近10年', all: '全历史' }

const storedWindow = localStorage.getItem(PE_WINDOW_KEY)
const peWindow = ref<PeWindow>(
  storedWindow && storedWindow in PE_WINDOW_LABELS ? (storedWindow as PeWindow) : '10y'
)
const peWindowLabel = computed(() => PE_WINDOW_LABELS[peWindow.value])

const storedThreshold = Number(localStorage.getItem(THRESHOLD_KEY))
const premiumThreshold = ref(Number.isFinite(storedThreshold) && storedThreshold > 0 ? storedThreshold : 3)

watch(peWindow, (value) => {
  localStorage.setItem(PE_WINDOW_KEY, value)
  load()
})
watch(premiumThreshold, (value) => {
  localStorage.setItem(THRESHOLD_KEY, String(value))
})

const tagOptions = computed(() => {
  const set = new Set<string>()
  rows.value.forEach((row) => row.tags.forEach((t) => set.add(t)))
  return [...set].sort()
})

/** 全局筛选：基金关键词（模糊匹配代码或名称，回车生效）与标签筛选同时生效，均为空=不过滤 */
const filteredRows = computed(() => {
  const kw = appliedFundKeyword.value.trim().toLowerCase()
  return rows.value.filter((row) => {
    if (kw && !row.fundCode.toLowerCase().includes(kw) && !row.fundName.toLowerCase().includes(kw)) {
      return false
    }
    if (selectedTags.value.length > 0 && !row.tags.some((t) => selectedTags.value.includes(t))) {
      return false
    }
    return true
  })
})

/** 基金模糊查询：输入框值与已生效值分离——回车（或清空）才把输入值应用到筛选（V6.02 用户口径） */
const fundKeywordInput = ref('')
const appliedFundKeyword = ref('')

function applyFundKeyword() {
  appliedFundKeyword.value = fundKeywordInput.value
}

// 筛选/换基金/换数据后所有页签页码回位，防越界空页（V5.36 持仓列表同款处理）
watch(appliedFundKeyword, resetPages)
watch(selectedTags, resetPages)

// ===== 分页（V5.51 用户口径：每页条数可自选；三个页签的页码与条数各自独立，互不影响）=====
const PAGE_SIZES = [10, 20, 50, 100]
const chgPage = ref(1)
const chgSize = ref(10)
const valPage = ref(1)
const valSize = ref(10)
const premiumPage = ref(1)
const premiumSize = ref(10)

// ===== 【均价】页签（V5.68）：数据读 fund_ma_daily 表；刷新/回跑写表需 ACTION_SYNC =====
const MA_WINDOWS = [5, 10, 20, 30, 60, 90, 120, 250]
const maRows = ref<FundMaRow[]>([])
const maLoading = ref(false)
const maLoaded = ref(false)
const maRefreshing = ref(false)
const maBackfillVisible = ref(false)
const maBackfillDays = ref(250)
const maBackfilling = ref(false)
const maPage = ref(1)
const maSize = ref(10)
const maSort = ref<{ prop: string; order: string | null }>({ prop: 'ratio20', order: 'descending' })

const maDataDate = computed(() => {
  const dates = maRows.value.map((row) => row.dataDate).sort()
  return dates.length > 0 ? dates[dates.length - 1] : null
})

/** 比值着色：>1 红色（均线上方/强势）、<1 绿色（下方），=1 无色（用户拍板的显示规则） */
function ratioClass(value: number | null): string {
  if (value == null) {
    return ''
  }
  return value > 1 ? 'text-up' : value < 1 ? 'text-down' : ''
}

function onMaSort({ prop, order }: { prop: string; order: string | null }) {
  maSort.value = { prop, order }
}

/** 取行内指定周期的比值（动态键的类型安全封装） */
function ratioOf(row: FundMaRow, window: number): number | null {
  return (row as unknown as Record<string, number | null>)[`ratio${window}`]
}

/**
 * 均价页签也吃顶部的全局筛选（标签 / 基金，V5.94 用户反馈修复）。
 *
 * 均价接口（fund_ma_daily 快照）**不带标签**，标签只能取自概览那份数据（同一自选池，overview 才带 tags）；
 * 因此概览里没有的基金视为"无标签"——不筛选时照常显示，只在按标签筛选时被排除（口径与其它页签一致）。
 */
/** 概览里的 基金代码 → 名称（均价页签的数据来自另一接口、不带名称，模糊查询用它补名称） */
const nameByCode = computed(() => {
  const map = new Map<string, string>()
  rows.value.forEach((row) => map.set(row.fundCode, row.fundName))
  return map
})

const tagsByCode = computed(() => {
  const map = new Map<string, string[]>()
  rows.value.forEach((row) => map.set(row.fundCode, row.tags))
  return map
})

const maFilteredRows = computed(() => {
  return maRows.value.filter((row) => {
    const kw = appliedFundKeyword.value.trim().toLowerCase()
    if (kw && !row.fundCode.toLowerCase().includes(kw)
        && !(nameByCode.value.get(row.fundCode) ?? '').toLowerCase().includes(kw)) {
      return false
    }
    if (selectedTags.value.length > 0 && !(tagsByCode.value.get(row.fundCode) ?? []).some((t) => selectedTags.value.includes(t))) {
      return false
    }
    return true
  })
})

const maSorted = computed(() => {
  const sorted = [...maFilteredRows.value]
  if (!maSort.value.prop || !maSort.value.order) {
    return sorted
  }
  const dir = maSort.value.order === 'descending' ? -1 : 1
  const key = maSort.value.prop
  sorted.sort((a, b) => {
    const av = (a as unknown as Record<string, number | null>)[key]
    const bv = (b as unknown as Record<string, number | null>)[key]
    if (av == null && bv == null) return 0
    if (av == null) return 1
    if (bv == null) return -1
    return (av - bv) * dir
  })
  return sorted
})

const maPaged = computed(() => {
  const start = (maPage.value - 1) * maSize.value
  return maSorted.value.slice(start, start + maSize.value)
})

async function loadMa() {
  maLoading.value = true
  try {
    maRows.value = await fundMaRows()
    maLoaded.value = true
  } finally {
    maLoading.value = false
  }
}

/** 手动刷新：后端校验交易日并按最新数据日重算落表（幂等），完成后重新拉列表 */
async function onMaRefresh() {
  maRefreshing.value = true
  try {
    const result = await refreshMa()
    ElMessage.success(`已刷新 ${result.fundCount} 只基金（数据日 ${result.dateFrom ?? '—'} ~ ${result.dateTo ?? '—'}）`)
    await loadMa()
  } finally {
    maRefreshing.value = false
  }
}

/** 回跑：确认后调后端（可能数秒~数十秒），完成后重新拉列表 */
async function onMaBackfill() {
  maBackfilling.value = true
  try {
    const result = await backfillMa(maBackfillDays.value)
    ElMessage.success(`回跑完成：${result.rowCount} 行（${result.dateFrom ?? '—'} ~ ${result.dateTo ?? '—'}，${result.fundCount} 只基金）`)
    maBackfillVisible.value = false
    await loadMa()
  } finally {
    maBackfilling.value = false
  }
}

// 切到均价页签时懒加载一次
watch(activeTab, (tab) => {
  if (tab === 'ma' && !maLoaded.value && !maLoading.value) {
    loadMa()
  }
})

// 各页签的排序状态：必须对**全量**排序后再切页，否则点列头只排当前页、跨页排序会错
const chgSort = ref<{ prop: string; order: string | null }>({ prop: 'chg5d', order: 'descending' })
const premiumSortState = ref<{ prop: string; order: string | null }>({ prop: 'premiumPct', order: 'descending' })

function onChgSort({ prop, order }: { prop: string; order: string | null }) {
  chgSort.value = { prop, order }
}

function onPremiumSort({ prop, order }: { prop: string; order: string | null }) {
  premiumSortState.value = { prop, order }
}

/** 数值列排序：空值恒排最后；无排序列时保持后端原序（基金代码升序） */
function sortByState(list: MarketSignalRow[], state: { prop: string; order: string | null }): MarketSignalRow[] {
  const sorted = [...list]
  if (!state.prop || !state.order) {
    return sorted
  }
  const dir = state.order === 'descending' ? -1 : 1
  sorted.sort((a, b) => {
    const av = a[state.prop as keyof MarketSignalRow] as number | null
    const bv = b[state.prop as keyof MarketSignalRow] as number | null
    if (av == null && bv == null) return 0
    if (av == null) return 1
    if (bv == null) return -1
    return (av - bv) * dir
  })
  return sorted
}

function paged(list: MarketSignalRow[], pageNumber: number, sizeNumber: number): MarketSignalRow[] {
  return list.slice((pageNumber - 1) * sizeNumber, pageNumber * sizeNumber)
}

/** 涨跌榜：全量排序 → 切当前页 */
const chgRows = computed(() => paged(sortByState(filteredRows.value, chgSort.value), chgPage.value, chgSize.value))
/** 估值红绿灯：无排序列，直接切当前页 */
const valRows = computed(() => paged(filteredRows.value, valPage.value, valSize.value))
/** 技术面与溢价：全量排序 → 切当前页 */
const premiumRows = computed(() => paged(sortByState(filteredRows.value, premiumSortState.value), premiumPage.value, premiumSize.value))

// 筛选/换基金/换数据后所有页签页码回位，防越界空页（V5.36 持仓列表同款处理）
watch(appliedFundKeyword, resetPages)
watch(selectedTags, resetPages)

/** 本次展示数据的取数时间（缓存命中时保持首次取数时间，便于判断新鲜度） */
const loadedAt = ref('')

/** 筛选/换数据后所有页签页码回位，防越界空页 */
function resetPages() {
  chgPage.value = 1
  valPage.value = 1
  premiumPage.value = 1
  maPage.value = 1
}

/**
 * 取数：默认走 5 分钟缓存（缓存实体在 api 模块里，见 marketSignalOverviewCached 注释），
 * force=true（「刷新」按钮）强制拉取。
 */
async function load(force = false) {
  loading.value = true
  try {
    const { data, at } = await marketSignalOverviewCached(peWindow.value, force)
    rows.value = data.rows
    loadedAt.value = formatTime(at)
    resetPages()
  } finally {
    loading.value = false
  }
}

/** 取数时间显示为 HH:mm:ss */
function formatTime(timestamp: number): string {
  const d = new Date(timestamp)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

/** 估值灯色：<30 低估 / 30~70 正常 / >70 高估 / 无数据 */
function lightType(pct: number | null): 'success' | 'warning' | 'danger' | 'info' {
  if (pct == null) return 'info'
  if (pct < 30) return 'success'
  if (pct <= 70) return 'warning'
  return 'danger'
}

function lightText(pct: number | null): string {
  if (pct == null) return '无数据'
  if (pct < 30) return '低估'
  if (pct <= 70) return '正常'
  return '高估'
}

function maText(state: number | null): string {
  if (state == null) return '—'
  return state === 1 ? '上' : '下'
}

/** 均线上=红（多头）、下=绿（空头），与红涨绿跌的语义一致 */
function maClass(state: number | null): string {
  if (state == null) return ''
  return state === 1 ? 'text-up' : 'text-down'
}

/** 溢价超阈值：只在基金名上标红提示风险（不做整行底色） */
function isPremiumWarn(row: MarketSignalRow): boolean {
  return row.premiumPct != null && row.premiumPct > premiumThreshold.value
}

// 显式箭头调用：避免 Hook 回调参数被当成 force 传进 load（那会让缓存永远失效）
onMounted(() => load())
</script>

<style scoped>
.card-header {
  display: flex;
  align-items: center;
  gap: var(--q-space-3);
  padding-left: var(--q-space-3);
  font-size: var(--q-font-base);
  font-weight: 600;
  color: var(--q-text-primary);
}

/* 标题与两个下拉都不许被挤压折行：挤不下时由右侧说明文字自己收缩换行 */
.card-header > span:first-of-type {
  flex: none;
  white-space: nowrap;
}

.card-header .el-select {
  flex: none;
}

.card-header::before {
  content: '';
  flex: none;
  width: 3px;
  height: 14px;
  border-radius: 1px;
  background: var(--q-color-primary);
}

.card-header .muted {
  font-weight: 400;
}

.muted {
  font-size: var(--q-font-xs);
  color: var(--q-text-muted);
}

.window-row {
  display: flex;
  align-items: center;
  gap: var(--q-space-2);
  margin-bottom: var(--q-space-2);
}

.window-label {
  font-size: var(--q-font-sm);
  color: var(--q-text-regular);
}

.fund-link {
  color: var(--q-color-primary);
  cursor: pointer;
}

.fund-link:hover {
  text-decoration: underline;
}

/* 溢价超阈值：基金名标红（scoped 的 .fund-link[data-v] 权重高于全局 .text-up，须在组件内同权重覆盖） */
.fund-link.text-up {
  color: var(--q-color-up);
}

.fund-code {
  margin-left: var(--q-space-2);
  font-size: var(--q-font-xs);
  color: var(--q-text-muted);
}

.premium-date {
  margin-left: var(--q-space-1);
  font-size: var(--q-font-xs);
  color: var(--q-text-muted);
}

/* 分页器：右对齐贴表格尾部 */
.pager-row {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--q-space-2);
}

</style>

<style scoped>
/* 均价页签工具行：左侧说明文字、右侧操作按钮 */
.ma-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--q-space-3);
  margin-bottom: var(--q-space-3);
}

.ma-actions {
  display: flex;
  gap: var(--q-space-2);
}

.field-hint {
  margin-left: var(--q-space-3);
  font-size: var(--q-font-xs);
  color: var(--q-text-muted);
}
</style>
