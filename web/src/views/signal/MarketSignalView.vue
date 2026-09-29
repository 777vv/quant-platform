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
          <el-select
            v-model="selectedFund"
            filterable
            clearable
            placeholder="全部基金"
            style="width: 220px"
          >
            <el-option v-for="f in fundOptions" :key="f.fundCode" :value="f.fundCode" :label="`${f.fundName} ${f.fundCode}`" />
          </el-select>
          <span class="muted">全部指标由库内数据计算，只作参考不构成投资建议；各列数据截至日不同，见对应列</span>
        </div>
      </template>

      <el-tabs v-model="activeTab">
        <!-- 页签一：多窗口涨跌榜（每个窗口的"正确读法"写进表头提示） -->
        <el-tab-pane label="涨跌榜" name="chg">
          <el-table v-loading="loading" :data="chgRows" size="small" :default-sort="{ prop: 'chg7d', order: 'descending' }" @sort-change="onChgSort">
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
            <el-table-column align="right" min-width="88" prop="chg7d" sortable="custom">
              <template #header>
                <span title="近7个交易日涨跌。A股短期（1个月内）反转效应强：领涨≠能追，领跌=超跌关注">7日</span>
              </template>
              <template #default="{ row }">
                <span class="num" :class="changeColorClass(row.chg7d)">{{ formatPercent(row.chg7d) }}</span>
              </template>
            </el-table-column>
            <el-table-column align="right" min-width="88" prop="chg15d" sortable="custom">
              <template #header>
                <span title="近15个交易日涨跌。同 7 日：短期看反转，领涨要当心回吐">15日</span>
              </template>
              <template #default="{ row }">
                <span class="num" :class="changeColorClass(row.chg15d)">{{ formatPercent(row.chg15d) }}</span>
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
                <span title="近60个交易日涨跌。中期动量参考">60日</span>
              </template>
              <template #default="{ row }">
                <span class="num" :class="changeColorClass(row.chg60d)">{{ formatPercent(row.chg60d) }}</span>
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
            <el-pagination v-model:current-page="page" :page-size="PAGE_SIZE" :total="filteredRows.length" layout="total, prev, pager, next" background />
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
            <el-pagination v-model:current-page="page" :page-size="PAGE_SIZE" :total="filteredRows.length" layout="total, prev, pager, next" background />
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
            <el-pagination v-model:current-page="page" :page-size="PAGE_SIZE" :total="filteredRows.length" layout="total, prev, pager, next" background />
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { changeColorClass, formatAmount, formatPercent } from '@/utils/format'
import { marketSignalOverview, type MarketSignalRow, type PeWindow } from '@/api/marketSignals'

const loading = ref(false)
const activeTab = ref('chg')
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

/** 全局筛选：基金下拉（选了就只看这一只）与标签筛选同时生效，均为空=不过滤 */
const filteredRows = computed(() => {
  return rows.value.filter((row) => {
    if (selectedFund.value && row.fundCode !== selectedFund.value) {
      return false
    }
    if (selectedTags.value.length > 0 && !row.tags.some((t) => selectedTags.value.includes(t))) {
      return false
    }
    return true
  })
})

/** 基金下拉选项（按代码升序，与列表同序） */
const fundOptions = computed(() =>
  rows.value.map((row) => ({ fundCode: row.fundCode, fundName: row.fundName }))
)

const selectedFund = ref<string | null>(null)

// 换基金/清空后页码回位，防越界空页
watch(selectedFund, () => {
  page.value = 1
})

// ===== 分页（每页 10 条，与持仓列表同模式）：数据量小、三个页签共用一份计算结果，故前端分页 =====
const PAGE_SIZE = 10
const page = ref(1)

// 各页签的排序状态：必须对**全量**排序后再切页，否则点列头只排当前页、跨页排序会错
const chgSort = ref<{ prop: string; order: string | null }>({ prop: 'chg7d', order: 'descending' })
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

function paged(list: MarketSignalRow[]): MarketSignalRow[] {
  return list.slice((page.value - 1) * PAGE_SIZE, page.value * PAGE_SIZE)
}

/** 涨跌榜：全量排序 → 切当前页 */
const chgRows = computed(() => paged(sortByState(filteredRows.value, chgSort.value)))
/** 估值红绿灯：无排序列，直接切当前页 */
const valRows = computed(() => paged(filteredRows.value))
/** 技术面与溢价：全量排序 → 切当前页 */
const premiumRows = computed(() => paged(sortByState(filteredRows.value, premiumSortState.value)))

// 筛选/换数据后页码回位，防越界空页（V5.36 持仓列表同款处理）
watch(selectedTags, () => {
  page.value = 1
})

async function load() {
  loading.value = true
  try {
    const data = await marketSignalOverview(peWindow.value)
    rows.value = data.rows
    page.value = 1
  } finally {
    loading.value = false
  }
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

onMounted(load)
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
