<template>
  <el-dialog
    :model-value="modelValue"
    width="620px"
    top="6vh"
    class="profit-calendar-dialog"
    @update:model-value="emit('update:modelValue', $event)"
    @open="onOpen"
  >
    <!-- 标题 + 口径问号（V6.08：底部口径说明改为标题旁悬浮查看） -->
    <template #header>
      <div class="cal-head">
        <span class="cal-head-title">收益日历</span>
        <el-tooltip placement="bottom-start" effect="dark" popper-class="profit-calendar-help-popper">
          <template #content>
            <div class="profit-calendar-help-text">
              <b>每日收益</b> = 相邻两个数据日的累计收益之差（累计收益 = 当日持仓市值 − 净投入）；
              一个自然月内各日相加正好等于指标卡的「本月收益」，可与上方卡片对账。<br />
              <b>收益率分母</b>取前一数据日的持仓市值（与指标卡的月/年收益率同口径）。<br />
              资金转入/转出<b>不产生收益</b>（不参与计算）；周六日不展示，工作日无数据（休市）显示 —；
              与平台其它收益口径一致，<b>已移出自选池的历史基金不计入</b>。
            </div>
          </template>
          <el-icon class="cal-help"><QuestionFilled /></el-icon>
        </el-tooltip>
      </div>
    </template>

    <!-- 工具条：视图（月/年）· 指标（收益额/收益率）· 导航 · 刷新 -->
    <div class="cal-toolbar">
      <el-radio-group v-model="view">
        <el-radio-button value="month">按月</el-radio-button>
        <el-radio-button value="year">按年</el-radio-button>
      </el-radio-group>
      <el-radio-group v-model="metric">
        <el-radio-button value="amount">收益额</el-radio-button>
        <el-radio-button value="pct">收益率</el-radio-button>
      </el-radio-group>
      <div class="cal-nav">
        <el-button circle :disabled="!canPrev" @click="shift(-1)">
          <el-icon><ArrowLeft /></el-icon>
        </el-button>
        <span class="cal-title num">{{ navTitle }}</span>
        <el-button circle :disabled="!canNext" @click="shift(1)">
          <el-icon><ArrowRight /></el-icon>
        </el-button>
      </div>
      <el-tooltip content="重新拉取本月/本年数据（正常切月切指标用的是缓存）" placement="top">
        <el-button circle :loading="loading" @click="load(true)">
          <el-icon><Refresh /></el-icon>
        </el-button>
      </el-tooltip>
    </div>

    <div v-loading="loading" class="cal-body">
      <el-empty v-if="!loading && !data?.hasData" :image-size="72" :description="emptyText" />

      <template v-else-if="data">
        <!-- 月视图：只排工作日（周一~周五；周六日不展示）；工作日无数据（休市）显示 — -->
        <table v-if="view === 'month'" class="cal-grid">
          <thead>
            <tr>
              <th v-for="w in WEEK_NAMES" :key="w">{{ w }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(row, ri) in monthRows" :key="ri">
              <td v-for="(cell, ci) in row" :key="ci" :class="cellClass(cell)">
                <span class="cell-day num">{{ cell.day }}</span>
                <span class="cell-val num" :class="cellColor(cell)">{{ cellText(cell) }}</span>
              </td>
            </tr>
          </tbody>
        </table>

        <!-- 年视图：12 个月的汇总块（V6.08 去掉热力图），点块切到该月明细 -->
        <div v-else class="year-grid">
          <div
            v-for="m in yearMonths"
            :key="m.month"
            class="year-block"
            :class="[monthTintClass(m), { 'year-block--active': m.month === activeMonth }]"
            @click="jumpToMonth(m.month)"
          >
            <div class="block-head">
              <span class="block-month num">{{ Number(m.month.substring(5)) }} 月</span>
              <span class="block-val num" :class="monthColor(m)">{{ monthText(m) }}</span>
            </div>
            <div class="block-foot muted num">{{ m.dayCount }} 个交易日</div>
          </div>
        </div>
      </template>
    </div>

    <!-- 汇总行（V6.09）：只显示"当前指标"的那一个值——按年就「2026 全年 +5,485.99 元」，
         按月就「2026-09 收益 +1,208.11 元」，切指标只是把数值换成收益率，行高恒定不跳动 -->
    <div v-if="data?.hasData" class="cal-summary">
      <span class="summary-label">{{ summaryLabel }}</span>
      <span class="summary-main num" :class="colorOf(summaryPnl, summaryPct)">{{ summaryText }}</span>
      <span class="muted num summary-days">{{ summaryDays }} 个交易日</span>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { ArrowLeft, ArrowRight, QuestionFilled, Refresh } from '@element-plus/icons-vue'
import { profitCalendar, type ProfitCalendarDay, type ProfitCalendarMonth, type ProfitCalendarVO } from '@/api/dashboard'
import { formatAmount } from '@/utils/format'

/**
 * 收益日历弹框（V6.07 新增，V6.08 视觉调整）：点仪表盘「总资产」卡片才打开、打开才加载。
 * 数据按年取（默认当前年），同一年内切月/切指标零请求（api 模块里还有按年缓存）。
 */
defineProps<{ modelValue: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [boolean] }>()

/** 列头只到周五：周六日不交易，展示它们只会占位（V6.08 用户要求） */
const WEEK_NAMES = ['一', '二', '三', '四', '五']

const view = ref<'month' | 'year'>('month')
const metric = ref<'amount' | 'pct'>('amount')
/** 当前查看的年/月（月份 1-12） */
const year = ref(new Date().getFullYear())
const month = ref(new Date().getMonth() + 1)
const data = ref<ProfitCalendarVO | null>(null)
const loading = ref(false)
/** 已加载过的年（决定"跳到未加载年份"时才发请求；同年切换零请求由 api 层缓存兜底） */
const loadedYear = ref<number | null>(null)
/**
 * 回看下界（"最早有数据的那一年"）——按需学习、不预探测：
 * 只有用户往回翻到某一年、后端明确返回"该年无数据"时，才把它记为下界（＝该年 + 1）。
 */
const earliestYear = ref<number | null>(null)

/** 弹框打开：加载当前年（只此一次；不放在仪表盘首屏） */
function onOpen() {
  if (data.value == null) {
    void load()
  }
}

async function load(force = false) {
  loading.value = true
  try {
    const result = await profitCalendar(year.value, force)
    data.value = result
    loadedYear.value = year.value
    if (!result.hasData && (earliestYear.value == null || result.year + 1 > earliestYear.value)) {
      earliestYear.value = result.year + 1
    }
  } catch {
    ElMessage.error('收益日历加载失败，可点「刷新」重试')
  } finally {
    loading.value = false
  }
}

// ===== 导航 =====
const navTitle = computed(() =>
  view.value === 'month' ? `${year.value}-${String(month.value).padStart(2, '0')}` : `${year.value} 年`
)
const canPrev = computed(() => {
  if (data.value && !data.value.hasData) {
    return false
  }
  const floor = earliestYear.value
  if (floor == null) {
    return true
  }
  return view.value === 'month' ? !(year.value <= floor && month.value === 1) : year.value > floor
})
const canNext = computed(() => {
  const now = new Date()
  return view.value === 'month'
    ? !(year.value === now.getFullYear() && month.value === now.getMonth() + 1)
    : year.value < now.getFullYear()
})

async function shift(delta: number) {
  if (view.value === 'month') {
    let m = month.value + delta
    let y = year.value
    if (m < 1) {
      m = 12
      y--
    } else if (m > 12) {
      m = 1
      y++
    }
    month.value = m
    year.value = y
  } else {
    year.value += delta
  }
  if (loadedYear.value !== year.value) {
    await load()
  }
}

/** 年视图点某月：切到该月的月视图 */
async function jumpToMonth(monthKey: string) {
  month.value = Number(monthKey.substring(5))
  year.value = Number(monthKey.substring(0, 4))
  if (loadedYear.value !== year.value) {
    await load()
  }
  view.value = 'month'
}

// ===== 月视图网格（周一~周五五列，按周分行） =====
interface DayCell {
  day: number
  date: string
  pnl: number | null
  pct: number | null
  isToday: boolean
}

const dayByDate = computed(() => {
  const map = new Map<string, ProfitCalendarDay>()
  data.value?.days.forEach((d) => map.set(d.date, d))
  return map
})

/** 当前月的行：每行 5 格（周一~周五），周六日不产出格子 */
const monthRows = computed<DayCell[][]>(() => {
  const rows: DayCell[][] = []
  let row: DayCell[] = []
  const y = year.value
  const m = month.value
  const daysInMonth = new Date(y, m, 0).getDate()
  const today = new Date()
  for (let d = 1; d <= daysInMonth; d++) {
    const weekday = (new Date(y, m - 1, d).getDay() + 6) % 7
    if (weekday >= 5) {
      continue
    }
    const date = `${y}-${String(m).padStart(2, '0')}-${String(d).padStart(2, '0')}`
    const hit = dayByDate.value.get(date)
    row.push({
      day: d,
      date,
      pnl: hit ? hit.pnl : null,
      pct: hit ? hit.pct : null,
      isToday: today.getFullYear() === y && today.getMonth() + 1 === m && today.getDate() === d
    })
    if (weekday === 4) {
      rows.push(row)
      row = []
    }
  }
  if (row.length > 0) {
    rows.push(row)
  }
  return rows
})

// ===== 年视图：12 个月的汇总块 =====
interface YearBlock {
  month: string
  pnl: number
  pct: number | null
  dayCount: number
}

const monthSummaryByKey = computed(() => {
  const map = new Map<string, ProfitCalendarMonth>()
  data.value?.months.forEach((m) => map.set(m.month, m))
  return map
})

const yearMonths = computed<YearBlock[]>(() =>
  Array.from({ length: 12 }, (_, idx) => {
    const m = idx + 1
    const key = `${year.value}-${String(m).padStart(2, '0')}`
    const summary = monthSummaryByKey.value.get(key)
    return {
      month: key,
      pnl: summary?.pnl ?? 0,
      pct: summary?.pct ?? null,
      dayCount: summary?.dayCount ?? 0
    }
  })
)

// ===== 展示：数值与颜色 =====
function formatSigned(value: number | null): string {
  if (value == null) {
    return '--'
  }
  return `${value > 0 ? '+' : ''}${formatAmount(value)}`
}

function formatPct(value: number | null): string {
  if (value == null) {
    return '--'
  }
  return `${value > 0 ? '+' : ''}${value.toFixed(2)}%`
}

/** 涨跌色（红涨绿跌；0 与无值用中性色） */
function colorOf(pnl: number | null, pct: number | null): string {
  const v = metric.value === 'pct' ? pct : pnl
  if (v == null || v === 0) {
    return 'cal-flat'
  }
  return v > 0 ? 'cal-up' : 'cal-down'
}

/** 月视图格子：有数据写数值；工作日但无数据（休市）写一横杠 */
function cellText(cell: DayCell): string {
  if (cell.pnl == null) {
    return '—'
  }
  return metric.value === 'amount' ? formatSigned(cell.pnl) : formatPct(cell.pct)
}

function cellColor(cell: DayCell): string {
  if (cell.pnl == null) {
    return 'cal-flat'
  }
  return colorOf(cell.pnl, cell.pct)
}

/** 格子底色：有数据按涨跌给淡色底（红涨绿跌）；休市日虚线框 */
function cellClass(cell: DayCell): string[] {
  const cls = ['cal-cell']
  if (cell.isToday) {
    cls.push('cal-cell--today')
  }
  if (cell.pnl == null) {
    cls.push('cal-cell--empty')
    return cls
  }
  const v = metric.value === 'pct' ? cell.pct : cell.pnl
  if (v == null || v === 0) {
    cls.push('cal-cell--flat')
  } else {
    cls.push(v > 0 ? 'cal-cell--up' : 'cal-cell--down')
  }
  return cls
}

function monthText(m: YearBlock): string {
  if (m.dayCount === 0) {
    return '--'
  }
  return metric.value === 'amount' ? formatSigned(m.pnl) : formatPct(m.pct)
}

/** 年视图卡片底色：与月视图格子同一套视觉语言（有数据按涨跌给淡色底） */
function monthTintClass(m: YearBlock): string {
  if (m.dayCount === 0) {
    return 'year-block--empty'
  }
  const v = metric.value === 'pct' ? m.pct : m.pnl
  if (v == null || v === 0) {
    return 'year-block--flat'
  }
  return v > 0 ? 'year-block--up' : 'year-block--down'
}

function monthColor(m: YearBlock): string {
  if (m.dayCount === 0) {
    return 'cal-flat'
  }
  return colorOf(m.pnl, m.pct)
}

// ===== 汇总（与当前视图同步） =====
const activeMonth = computed(() => `${year.value}-${String(month.value).padStart(2, '0')}`)

const summaryPnl = computed(() => {
  if (!data.value) {
    return null
  }
  if (view.value === 'year') {
    return data.value.yearPnl
  }
  return monthSummaryByKey.value.get(activeMonth.value)?.pnl ?? null
})

const summaryPct = computed(() => {
  if (!data.value) {
    return null
  }
  if (view.value === 'year') {
    return data.value.yearPct
  }
  return monthSummaryByKey.value.get(activeMonth.value)?.pct ?? null
})

const summaryDays = computed(() => {
  if (!data.value) {
    return 0
  }
  if (view.value === 'year') {
    return data.value.yearDayCount
  }
  return monthSummaryByKey.value.get(activeMonth.value)?.dayCount ?? 0
})

/** 汇总行左侧标签：按年「2026 全年」/ 按月「2026-09 收益」 */
const summaryLabel = computed(() =>
  view.value === 'year' ? `${year.value} 全年` : `${activeMonth.value} 收益`
)

/** 汇总行数值：只按当前指标渲染（切指标只换数值，行高不变） */
const summaryText = computed(() => {
  if (metric.value === 'amount') {
    return `${formatSigned(summaryPnl.value)} 元`
  }
  return formatPct(summaryPct.value)
})

const emptyText = computed(() =>
  data.value?.hasData === false ? `${year.value} 年暂无收益数据（该年还没有持仓流水）` : '加载中…'
)

watch(view, (v) => {
  if (v === 'year' && loadedYear.value !== year.value) {
    void load()
  }
})
</script>

<style scoped>
/* 标题行：标题 + 口径问号 */
.cal-head {
  display: flex;
  align-items: center;
  gap: var(--q-space-2);
}
.cal-head-title {
  font-size: var(--q-font-lg);
  font-weight: 600;
  color: var(--q-text-primary);
}
.cal-help {
  color: var(--q-text-muted);
  cursor: help;
  font-size: 15px;
}
.cal-help:hover {
  color: var(--q-color-primary);
}

/* 工具条 */
.cal-toolbar {
  display: flex;
  align-items: center;
  gap: var(--q-space-3);
  flex-wrap: wrap;
  padding-bottom: var(--q-space-3);
  border-bottom: 1px solid var(--q-border-light);
}
.cal-nav {
  display: flex;
  align-items: center;
  gap: var(--q-space-2);
  margin-left: auto;
}
.cal-title {
  min-width: 84px;
  text-align: center;
  font-weight: 600;
  color: var(--q-text-primary);
}
/* 日历体固定高度（V6.09 用户要求弹框不跳动；V6.10 修"内容与底部文案之间留白"）：
   月视图 5 行表格 ≈ 220px、年视图 3 行卡片（66px 行高）≈ 214px，两者都贴满这 220px 内容区；
   再让内容垂直居中，避免不同视图下把空隙堆在底部。 */
.cal-body {
  display: flex;
  flex-direction: column;
  justify-content: center;
  height: 232px;
  overflow-y: auto;
  padding-top: var(--q-space-3);
}

/* ---- 月视图：工作日五列，格子紧凑 ---- */
.cal-grid {
  width: 100%;
  border-collapse: separate;
  border-spacing: 4px;
  table-layout: fixed;
}
.cal-grid th {
  font-size: var(--q-font-xs);
  color: var(--q-text-muted);
  font-weight: 400;
  padding-bottom: var(--q-space-1);
}
.cal-cell {
  height: 32px;
  vertical-align: middle;
  padding: 0 var(--q-space-2);
  border-radius: var(--q-radius-sm);
  border: 1px solid transparent;
  transition: background 0.15s, border-color 0.15s;
}
.cal-cell .cell-day {
  float: left;
  font-size: var(--q-font-xs);
  color: var(--q-text-muted);
  line-height: 30px;
}
.cal-cell .cell-val {
  float: right;
  font-size: var(--q-font-xs);
  line-height: 30px;
  white-space: nowrap;
}
/* 有数据：淡色底 + 同色文字（红涨绿跌） */
.cal-cell--up {
  background: var(--q-color-up-soft);
}
.cal-cell--down {
  background: var(--q-color-down-soft);
}
.cal-cell--flat {
  background: var(--q-bg-subtle);
}
/* 工作日无数据（休市）：虚线框 + 横杠 */
.cal-cell--empty {
  background: transparent;
  border: 1px dashed var(--q-border);
}
/* 今天：主色描边 */
.cal-cell--today {
  border-color: var(--q-color-primary);
}
.cal-cell:hover {
  border-color: var(--q-color-primary-border);
}

/* ---- 年视图：12 个月汇总块 ---- */
.year-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  /* 66px 行高：3 行卡片 ≈ 214px，撑满与月视图表格相同的 220px 内容区（消灭底部留白） */
  grid-auto-rows: 66px;
  gap: var(--q-space-2);
}
.year-block {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 2px;
  border: 1px solid var(--q-border);
  border-radius: var(--q-radius-md);
  padding: 0 var(--q-space-2);
  cursor: pointer;
  transition: border-color 0.15s, box-shadow 0.15s, transform 0.15s;
}
.year-block:hover {
  border-color: var(--q-color-primary-border);
  box-shadow: var(--q-shadow-hover);
  transform: translateY(-1px);
}
.year-block--active {
  border-color: var(--q-color-primary);
}
/* 底色与月视图格子同一套视觉语言（V6.09 用户要求）：有数据按涨跌给淡色底 */
.year-block--up {
  background: var(--q-color-up-soft);
}
.year-block--down {
  background: var(--q-color-down-soft);
}
.year-block--flat {
  background: var(--q-bg-subtle);
}
.year-block--empty {
  background: transparent;
  border-style: dashed;
}
.block-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--q-space-2);
}
.block-month {
  font-size: var(--q-font-xs);
  color: var(--q-text-secondary);
  white-space: nowrap;
}
.block-val {
  font-size: var(--q-font-base);
  font-weight: 600;
  /* 金额（如 +3,117.51）绝不换行：放不下时由卡片宽度兜底（弹框 620px 下有余量） */
  white-space: nowrap;
}
.block-foot {
  font-size: var(--q-font-xs);
}

/* ---- 汇总行 ---- */
.cal-summary {
  display: flex;
  align-items: baseline;
  gap: var(--q-space-3);
  flex-wrap: nowrap;
  /* 行高显式统一（V6.11）：默认行盒高度被"元"(CJK) 与 "%"(ASCII) 的字形差异带偏（实测 23 vs 20px），
     基线对齐时整行上下跳 3px；固定 line-height 后各状态几何完全一致 */
  line-height: 24px;
  min-height: 40px;
  margin-top: var(--q-space-3);
  padding-top: var(--q-space-3);
  border-top: 1px solid var(--q-border-light);
}
.summary-days {
  margin-left: auto;
}
.summary-label {
  color: var(--q-text-secondary);
}
.summary-main {
  font-size: var(--q-font-lg);
  font-weight: 600;
}

/* 涨跌色 */
.cal-up {
  color: var(--q-color-up);
}
.cal-down {
  color: var(--q-color-down);
}
.cal-flat {
  color: var(--q-text-muted);
}
</style>

<!-- 浮层/弹框被 teleport 到 body，scoped 样式命中不到的部分单独放这里 -->
<style>
.profit-calendar-dialog .el-dialog__header {
  margin-right: 0;
}

/* 口径问号浮层：限宽 + 换行（自带宽度的原因：param-help-popper 的样式定义在参数表单组件里，
   那个组件在仪表盘不一定被加载） */
.profit-calendar-help-popper {
  max-width: 360px;
}
.profit-calendar-help-popper .profit-calendar-help-text {
  max-width: 336px;
  white-space: normal;
  word-break: break-word;
  line-height: 1.75;
  text-align: left;
}
.profit-calendar-help-popper .profit-calendar-help-text b {
  color: var(--q-color-up-soft);
}
</style>
