import { get, post } from './request'

/** 资产总览卡片（M4-01） */
export interface AssetSummaryVO {
  /** 总资产（元）= 持仓市值 + 现金余额 */
  totalAssets: number
  /** 现金余额（元）= 转入 - 转出 - 净投入 */
  cashBalance: number
  /** 持仓总市值（元） */
  marketValue: number
  /** 持仓摊薄总成本（元） */
  totalCost: number
  /** 当日盈亏（元） */
  dayPnl: number
  /** 浮动盈亏（元） */
  floatingPnl: number
  /** 浮动盈亏率（%） */
  floatingPnlPct: number | null
  /** 累计已实现盈亏（元） */
  realizedPnl: number
  /** 累计收益（元） */
  totalPnl: number
  /** 累计收益率（%） */
  totalPnlPct: number | null
  /** 近 7 日收益（元） */
  weekPnl: number
  /** 本月收益（元） */
  monthPnl: number
  /** 本月收益率（%） */
  monthPnlPct: number | null
  /** 本年收益（元） */
  yearPnl: number
  /** 本年收益率（%） */
  yearPnlPct: number | null
  /** 持仓基金数量 */
  holdingCount: number
  /** 自选基金数量 */
  watchCount: number
}

/** 月度收益条目 */
export interface MonthlyPnl {
  /** 月份 yyyy-MM */
  month: string
  /** 当月收益（元） */
  pnl: number
}

/** 收益曲线（M4-02） */
export interface ProfitCurveVO {
  startDate: string
  endDate: string
  dates: string[]
  pnl: number[]
  benchmarkPct: (number | null)[]
  monthly: MonthlyPnl[]
  hasData: boolean
}

/** 持仓概览条目 */
export interface HoldingBrief {
  fundCode: string
  fundName: string
  marketValue: number
  weightPct: number
  dayPnl: number
  floatingPnl: number
}

/** 自选 7 日涨跌条目 */
export interface MoverItem {
  fundCode: string
  fundName: string
  changePct7d: number
}

/** 配置占比条目 */
export interface AllocationItem {
  fundCode: string
  fundName: string
  marketValue: number
  pct: number
}

/** 同步状态条目 */
export interface SyncStatusItem {
  fundCode: string
  fundName: string
  fundType: number
  lastDataDate: string | null
  expectedDate: string
  status: string
}

/** 速览区（M4-08） */
export interface DashboardOverviewVO {
  holdings: HoldingBrief[]
  movers: MoverItem[]
  allocation: AllocationItem[]
  syncStatus: SyncStatusItem[]
  syncSummary: string
}

/** 指数走势采样点 */
export interface TrendPoint {
  time: string
  price: number
}

/** 全球指数看板条目（M4-06） */
export interface IndexQuoteVO {
  indexCode: string
  indexName: string
  region: string
  lastPrice: number
  changeAmt: number
  changePct: number
  quoteTime: string
  trend: TrendPoint[]
}

/** 资产总览卡片 */
export function dashboardAssets() {
  return get<AssetSummaryVO>('/dashboard/assets')
}

/** 收益曲线（range=1M/3M/6M/1Y/3Y/ALL） */
export function profitCurve(range: string) {
  return get<ProfitCurveVO>('/dashboard/profit/curve', { range })
}

/** 速览区数据 */
export function dashboardOverview() {
  return get<DashboardOverviewVO>('/dashboard/overview')
}

/** 指数看板响应（含数据源降级标记） */
export interface IndexBoardVO {
  /** 指数行情列表 */
  quotes: IndexQuoteVO[]
  /** 行情是否来自库内快照（外部拉取失败） */
  degraded: boolean
  /** 是否存在迷你线缺失 */
  trendMissing: boolean
}

/** 全球指数看板（只读库内快照，秒开） */
export function dashboardIndices() {
  return get<IndexBoardVO>('/dashboard/indices')
}

/** 强制刷新全球指数（页面"刷新"按钮：立即拉取东财并返回新数据） */
export function refreshDashboardIndices() {
  return post<IndexBoardVO>('/dashboard/indices/refresh')
}
