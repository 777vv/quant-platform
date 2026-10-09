import { del, get, post, put } from './request'

/** 基金代码校验结果（V4.1：池内状态拆成 inPool / removedFromPool 两个事实，不再用"库里有这行"糊在一起） */
export interface FundCheckVO {
  /** 基金代码 */
  code: string
  /** 基金简称 */
  name: string
  /** 1=场内ETF 2=场外指数基金（不支持时为 null） */
  fundType: number | null
  /** 交易所市场 SH/SZ（场外为 null） */
  market: string | null
  /** 东财基金类型原文（如"指数型-股票"） */
  fundTypeDesc: string
  /** 基金公司 */
  fundCompany: string | null
  /** 跟踪指数代码 */
  indexCode: string | null
  /** 跟踪指数名称 */
  indexName: string | null
  /** 成立日期（yyyy-MM-dd） */
  estabDate: string | null
  /** 是否允许导入（指数型基金才允许） */
  supported: boolean
  /** 不支持原因（supported=false 时展示） */
  reason: string | null
  /** 是否**在自选池**：是则本次导入＝重新导入并覆盖刷新历史数据 */
  inPool: boolean
  /** 是否**曾导入、已移出自选**：是则本次导入会把它恢复到自选池 */
  removedFromPool: boolean
  /** 本地已有历史数据的截止日（null＝没有历史数据） */
  lastSyncDate: string | null
}

export interface TaskProgressVO {
  taskId: string
  status: 'RUNNING' | 'DONE' | 'FAILED'
  step: string
  total: number
  imported: number
  message: string | null
}

/** 批量导入候选（ETF，按规模/上市年限筛选；V5.41） */
export interface EtfCandidate {
  /** 基金代码 */
  fundCode: string
  /** 基金名称 */
  fundName: string
  /** 规模（亿元，总市值口径） */
  scaleYi: number
  /** 上市日期（近似成立日期） */
  listedDate: string
  /** 是否已在自选池中（在池中的不可勾选） */
  inPool: boolean
}

/** 批量导入失败项（只级失败不中断批量，结束后可一键重试） */
export interface BatchFailItem {
  /** 基金代码 */
  code: string
  /** 基金名称（校验失败可能取不到） */
  name: string
  /** 失败原因 */
  reason: string
}

/** 批量导入任务进度（Redis 2h 过期，前端轮询渲染） */
export interface BatchImportProgress {
  taskId: string
  status: 'RUNNING' | 'DONE' | 'FAILED'
  /** 当前状态描述（含封堵暂停提示） */
  message: string
  total: number
  done: number
  success: number
  failed: number
  /** 正在导入的基金代码 */
  currentCode: string | null
  /** 正在导入的基金名称 */
  currentName: string | null
  failures: BatchFailItem[]
  startedAt: string | null
  finishedAt: string | null
}

/** 批量导入发起结果（铁律 11：回显真实的接收/跳过状态） */
export interface BatchStartResult {
  taskId: string
  /** 实际排队导入的代码 */
  accepted: string[]
  /** 已在自选池被跳过的代码 */
  skippedExisting: string[]
}

export interface WatchItemVO {
  /** 基金代码 */
  fundCode: string
  /** 基金简称 */
  fundName: string
  /** 1=场内ETF 2=场外指数基金 */
  fundType: number
  /** 类型展示名 */
  fundTypeDesc: string
  /** 跟踪指数名称 */
  indexName: string | null
  /** 跟踪指数代码（如 000922），未匹配到为 null */
  indexCode: string | null
  /** 最新价/净值 */
  lastPrice: number
  /** 涨跌幅% */
  changePct: number
  /** 跟踪指数近 10 年 PE 百分位 */
  valuationPercentile: number | null
  /** 本地最新数据日期 */
  lastSyncDate: string | null
  /** 净资产规模（亿元） */
  fundScale: number | null
  /** 规模数据截止日 */
  fundScaleDate: string | null
  /** 运作费率（%/年）= 管理费 + 托管费 + 销售服务费 */
  opFeeRate: number | null
  /** 管理费率（%/年） */
  mgmtFeeRate: number | null
  /** 托管费率（%/年） */
  custFeeRate: number | null
  /** 销售服务费率（%/年） */
  salesFeeRate: number | null
  /** 溢价率（%）=（当日收盘价 − 当日单位净值）/ 当日单位净值；仅场内 ETF 有值 */
  premiumRate: number | null
  /** 溢价率对应的净值日 */
  premiumDate: string | null
  /** 是否持仓（份额 > 0）；列表按"持仓优先"排序 */
  holding: boolean
  /** TTM 股息率（%）= 过去 12 个月每份分红 ÷ 最新真实价格；无分红或无价格为 null */
  dividendYieldTtm: number | null
}

export interface HoldingVO {
  fundCode: string
  fundName: string
  fundType: number
  fundTypeDesc: string
  totalShare: number
  avgCostPrice: number
  lastPrice: number | null
  marketValue: number | null
  dayPnl: number | null
  floatingPnl: number | null
  floatingPnlPct: number | null
  realizedPnl: number
}

export interface FundDetailVO {
  fundCode: string
  fundName: string
  fundType: number
  fundTypeDesc: string
  market: string | null
  indexCode: string | null
  indexName: string | null
  inceptionDate: string | null
  fundCompany: string | null
  /** 净资产规模（亿元） */
  fundScale: number | null
  /** 规模数据截止日 */
  fundScaleDate: string | null
  /** 运作费率（%/年）= 管理费 + 托管费 + 销售服务费 */
  opFeeRate: number | null
  /** 管理费率（%/年） */
  mgmtFeeRate: number | null
  /** 托管费率（%/年） */
  custFeeRate: number | null
  /** 销售服务费率（%/年） */
  salesFeeRate: number | null
  /** 溢价率（%），仅场内 ETF 有值 */
  premiumRate: number | null
  /** 溢价率对应的净值日 */
  premiumDate: string | null
  lastPrice: number | null
  changePct: number | null
  priceDate: string | null
  lastSyncDate: string | null
}

export interface SeriesPoint {
  date: string
  open: number | null
  close: number | null
  high: number | null
  low: number | null
  volume: number | null
  unitNav: number | null
  accNav: number | null
  adjNav: number | null
  pe: number | null
}

export interface ValuationSeriesVO {
  indexCode: string | null
  indexName: string | null
  metric: string
  series: SeriesPoint[]
  latestPe: number | null
  currentPercentile: number | null
  hasData: boolean
}

export interface TradeFlow {
  id: number
  fundCode: string
  tradeType: number
  tradeDate: string
  price: number
  share: number
  amount: number
  fee: number
  note: string
  /** 录入时间（后端回填） */
  createdAt?: string
}

export interface TradeFlowRequest {
  fundCode: string
  tradeType: number
  tradeDate: string
  price: number
  share: number
  amount: number
  fee: number
  note: string
}

export function checkFund(code: string) {
  return get<FundCheckVO>('/funds/check', { code })
}

export function importFund(code: string) {
  return post<{ taskId: string }>('/funds/import', { code })
}

export function importProgress(taskId: string) {
  return get<TaskProgressVO>('/funds/import/progress', { taskId })
}

/** 批量导入候选：全市场场内 ETF 按规模（亿）/上市年限筛选（V5.41，结果服务端缓存 10 分钟） */
export function etfCandidates(minScaleYi: number, minYears: number) {
  return get<EtfCandidate[]>('/funds/import/batch/candidates', { minScaleYi, minYears })
}

/** 发起批量导入：返回 taskId 与接收/跳过清单（已在池中的自动跳过） */
export function startBatchImport(codes: string[]) {
  return post<BatchStartResult>('/funds/import/batch', { codes })
}

/** 批量导入进度轮询 */
export function batchImportProgress(taskId: string) {
  return get<BatchImportProgress>('/funds/import/batch/progress', { taskId })
}

/** 基金下拉选项（V6.01）：仅代码+名称——筛选下拉/名称映射这类轻量场景专用 */
export interface FundOptionVO {
  fundCode: string
  fundName: string
  /** 1=场内ETF 2=场外指数基金 */
  fundType: number
}

/**
 * 基金下拉选项（后端同样有 15 分钟缓存）。缓存放在**这个 .ts 模块**而不是页面里：
 * SFC 的 script setup 每次实例化都会重新求值，模块级变量才是页面生命周期内的单例
 * （与 marketSignals 的缓存同一教训与做法）——菜单往返 15 分钟内不再发请求。
 */
const FUND_OPTIONS_TTL_MS = 15 * 60 * 1000
let fundOptionsCache: { at: number; data: FundOptionVO[] } | null = null

export async function fundOptions(): Promise<FundOptionVO[]> {
  if (fundOptionsCache && Date.now() - fundOptionsCache.at < FUND_OPTIONS_TTL_MS) {
    return fundOptionsCache.data
  }
  const data = await get<FundOptionVO[]>('/funds/options')
  fundOptionsCache = { at: Date.now(), data }
  return data
}

export function watchlist() {
  return get<WatchItemVO[]>('/funds/watchlist')
}

export function holdings() {
  return get<HoldingVO[]>('/funds/holdings')
}

export function fundDetail(code: string) {
  return get<FundDetailVO>(`/funds/${code}/detail`)
}

export function fundKline(code: string, range = 365, start?: string, end?: string) {
  return get<SeriesPoint[]>(`/funds/${code}/kline`, { range, start, end })
}

export function fundNav(code: string, range = 365, start?: string, end?: string) {
  return get<SeriesPoint[]>(`/funds/${code}/nav`, { range, start, end })
}

/**
 * 分红股息率（单次分红口径 + TTM 滚动 12 个月口径）。
 * 分母为**真实价格**（场内未复权收盘价、场外单位净值）；缺失时 yieldPct 为 null，按"--"展示。
 */
export interface DividendYieldVO {
  /** 基金代码 */
  fundCode: string
  /** 基金名称 */
  fundName: string
  /** 区间内每次分红的股息率 */
  events: {
    /** 除息日 */
    date: string
    /** 每份分红（元） */
    perShare: number
    /** 除息日真实价格 */
    price: number | null
    /** 当日股息率（%） */
    yieldPct: number | null
  }[]
  /** TTM 股息率阶跃点（除息日 / 分红滚出 12 个月窗口之日） */
  ttm: { date: string; yieldPct: number | null }[]
  /** 历史真实价格是否可用 */
  priceAvailable: boolean
  /** 最新 TTM 股息率（%） */
  latestTtm: number | null
  /** 数据缺失时的说明 */
  hint: string | null
}

/** 查询分红股息率序列（单次 + TTM） */
export function fundDividendYield(code: string, range = 3650) {
  return get<DividendYieldVO>(`/funds/${code}/dividend-yield`, { range })
}

/** 基金规模历史点（每日档案刷新成功后逐日积累，自 V5.3 上线日起） */
export interface FundScalePoint {
  /** 统计日期（档案刷新成功那天） */
  date: string
  /** 净资产规模（亿元） */
  scale: number
}

/** 查询基金规模历史（按日期升序；行情图「规模副图」数据源） */
export function fundScaleHistory(code: string) {
  return get<FundScalePoint[]>(`/funds/${code}/scale-history`)
}

/** 行情图交易标记（买入 b / 卖出 s / 分红 q） */
export interface FundMarkVO {
  /** 标记日期（与 K 线交易日对齐） */
  date: string
  /** 标记类型：BUY 买入 / SELL 卖出 / DIVIDEND 分红（基金除息日） */
  kind: 'BUY' | 'SELL' | 'DIVIDEND'
  /** 悬浮/说明文案 */
  text: string
}

/**
 * 查询某只基金的交易标记。
 * 后端已按"同一天同类型"合并，返回体仅是日期与一句话文案（单只基金十年也就几十条），
 * 前端取一次缓存复用，缩放/框选/换指标都不会重新请求。
 */
export function fundMarks(code: string) {
  return get<FundMarkVO[]>(`/funds/${code}/marks`)
}

export function fundValuation(code: string, range = 3650) {
  return get<ValuationSeriesVO>(`/funds/${code}/valuation`, { range })
}

export function syncFund(code: string) {
  return post<{ taskId: string }>(`/funds/${code}/sync`)
}

export function syncProgress(taskId: string) {
  return get<TaskProgressVO>('/sync/progress', { taskId })
}

export function removeFund(code: string) {
  return del<void>(`/funds/${code}`)
}

/** 交易流水查询条件（全部可选；服务端分页与筛选） */
export interface TradeQuery {
  /** 基金代码（精确；基金详情页用）；为空表示不限（账户级划转没有基金） */
  fundCode?: string
  /** 关键词（V6.02）：按基金代码或名称模糊匹配；为空表示不限（列表页筛选用） */
  keyword?: string
  /** 交易类型（1 买 / 2 卖 / 3 分红 / 4 转入 / 5 转出）；为空表示不限 */
  tradeType?: number
  /** 交易日期下限（yyyy-MM-dd，含） */
  startDate?: string
  /** 交易日期上限（yyyy-MM-dd，含） */
  endDate?: string
  /** 页码（从 1 开始） */
  page?: number
  /** 每页条数 */
  size?: number
}

/**
 * 交易流水分页查询（条件全部可选）。
 * @param query 查询条件
 */
/** 自选池分页查询条件（服务端分页 + 服务端筛选） */
export interface WatchQuery {
  /** 关键词（基金代码或名称） */
  keyword?: string
  /** 标签名（预定义标签库中的名称） */
  tag?: string
  /** 页码（从 1 开始） */
  page?: number
  /** 每页条数 */
  size?: number
}

/**
 * 自选基金分页查询（关键词与标签均为服务端筛选，作用于全部分页数据）。
 * @param query 查询条件
 */
export function watchlistPage(query: WatchQuery) {
  return get<{ total: number; records: WatchItemVO[] }>('/funds/watchlist/page', query)
}

export function pageTrades(query: TradeQuery) {
  return get<{ total: number; records: TradeFlow[] }>('/trades', query)
}

/** 账户现金口径（转出额度提示用；比资产总览轻，不含区间收益计算） */
export interface CashBalanceVO {
  /** 现金余额（元）= 净转入 − 净投入 */
  cashBalance: number
  /** 账户级净转入（元） */
  transferNetIn: number
  /** 净投入（元） */
  netInvested: number
  /** 可转出上限（元）= max(现金余额, 0) */
  transferableLimit: number
}

/** 查询账户现金口径（转出额度校验提示） */
export function tradeCashBalance() {
  return get<CashBalanceVO>('/trades/cash-balance')
}

export function addTrade(data: TradeFlowRequest) {
  return post<void>('/trades', data)
}

export function updateTrade(id: number, data: TradeFlowRequest) {
  return put<void>(`/trades/${id}`, data)
}

export function deleteTrade(id: number) {
  return del<void>(`/trades/${id}`)
}

/** 标签库条目（含各标签下基金数量） */
export interface FundTagVO {
  id: number
  name: string
  sortNo: number
  fundCount: number | null
}

/** 标签库列表 */
export function tagLibrary() {
  return get<FundTagVO[]>('/tags')
}

/** 新建标签 */
export function createTag(name: string) {
  return post<void>('/tags', { name })
}

/** 重命名标签 */
export function renameTag(id: number, name: string) {
  return put<void>(`/tags/${id}`, { name })
}

/** 删除标签（同时清理关联） */
export function deleteTag(id: number) {
  return del<void>(`/tags/${id}`)
}

/** 某基金的标签 */
export function fundTags(code: string) {
  return get<{ fundCode: string; tags: FundTagVO[] }>(`/funds/${code}/tags`)
}

/** 覆盖式设置某基金的标签 */
export function setFundTags(code: string, tagIds: number[]) {
  return put<void>(`/funds/${code}/tags`, { tagIds })
}

/** 全部基金的标签映射（自选列表批量展示） */
export function allFundTags() {
  return get<Record<string, string[]>>('/funds/tags')
}
