import { get, post } from './request'
import type { PageResult } from '@/types/api'

/** PE 分位窗口：3y=近3年 / 5y=近5年 / 10y=近10年 / all=全历史 */
export type PeWindow = '3y' | '5y' | '10y' | 'all'

/** 市场信号单行（与后端 MarketSignalVO.Row 字段一一对应） */
export interface MarketSignalRow {
  /** 基金代码 */
  fundCode: string
  /** 基金名称 */
  fundName: string
  /** 1=场内ETF 2=场外指数基金（决定是否有溢价率） */
  fundType: number
  /** 基金标签名列表（前端标签筛选用） */
  tags: string[]

  /** 最新收盘价/复权净值 */
  lastClose: number | null
  /** 价格数据截至日（K线最新交易日 / 净值最新日） */
  lastDate: string | null

  /** 近 7 个交易日涨跌幅%（短期反转区：领涨≠能追） */
  chg5d: number | null
  /** 近 15 个交易日涨跌幅%（短期反转区：领跌=超跌关注） */
  chg10d: number | null
  /** 近 30 个交易日涨跌幅%（中期动量） */
  chg20d: number | null
  chg30d: number | null
  /** 近 60 个交易日涨跌幅%（中期动量） */
  chg60d: number | null
  chg90d: number | null
  chg120d: number | null
  /** 近 250 个交易日涨跌幅%（52 周位置，历史不足为 null） */
  chg250d: number | null

  /** 现价是否在 MA20 上方（1=上 0=下，均线不足为 null） */
  aboveMa20: number | null
  /** 现价是否在 MA60 上方（1=上 0=下，均线不足为 null） */
  aboveMa60: number | null
  /** 现价是否在 MA200 上方（1=上 0=下，历史不足 200 根为 null） */
  aboveMa200: number | null
  /** 趋势汇总：多头排列/空头排列/震荡；任一均线缺失为 null */
  maSummary: string | null

  /** 当前回撤深度%（相对历史最高收盘的最大跌幅，0=处于高点） */
  drawdownPct: number | null
  /** 回撤分位（0-100）：历史全部交易日回撤中小于当前回撤的占比，越大越极端 */
  drawdownPctile: number | null

  /** 跟踪指数名称（未匹配为 null，估值整块为 null） */
  indexName: string | null
  /** 跟踪指数最新 PE */
  pe: number | null
  /** PE 百分位（0-100，按请求窗口计算） */
  pePctile: number | null
  /** PE 数据截至日 */
  peDate: string | null
  /** 分位窗口内的 PE 样本天数 */
  peSamples: number | null

  /** 股息率 TTM%（仅展示，不参与灯色判断） */
  dyTtm: number | null

  /** 溢价率%（仅场内 ETF，场外为 null） */
  premiumPct: number | null
  /** 溢价率对应净值日 */
  premiumDate: string | null
}

/** 市场信号总览响应 */
export interface MarketSignalOverview {
  /** 本次计算使用的 PE 分位窗口（回显核对） */
  peWindow: string
  /** 每基金一行，按基金代码升序 */
  rows: MarketSignalRow[]
}

/** 市场信号总览（纯只读、库内计算） */
export function marketSignalOverview(peWindow: PeWindow) {
  return get<MarketSignalOverview>('/market-signals/overview', { peWindow })
}

/**
 * 带 5 分钟缓存的总览（V5.44 用户口径：菜单往返 5 分钟内不重复加载）。
 *
 * 缓存必须放在**这个 .ts 模块**里而不是页面的 `<script setup>`：SFC 的 script setup 是 setup 函数体，
 * 每次组件实例化都会重新求值，放在那里的"模块级变量"其实每次都被重置（实测踩过：
 * 切菜单回来仍重新请求）。ES 模块在同一个页面生命周期内只求值一次，才是真正的单例；
 * 浏览器 F5 重建模块 → 视为主动要新数据，自然重新拉取。
 */
const CACHE_TTL_MS = 5 * 60 * 1000
let cache: { key: PeWindow; at: number; data: MarketSignalOverview } | null = null

/**
 * 取总览（默认走缓存，force=true 强制拉取）。
 *
 * @param peWindow PE 分位窗口（作为缓存键，切窗口各自复用）
 * @param force    是否强制刷新（页面「刷新」按钮用）
 * @returns data=数据；at=取数时间戳（缓存命中时仍是首次取数时间）
 */
export async function marketSignalOverviewCached(peWindow: PeWindow, force = false)
    : Promise<{ data: MarketSignalOverview; at: number }> {
  if (!force && cache && cache.key === peWindow && Date.now() - cache.at < CACHE_TTL_MS) {
    return { data: cache.data, at: cache.at }
  }
  const data = await marketSignalOverview(peWindow)
  cache = { key: peWindow, at: Date.now(), data }
  return { data, at: cache.at }
}

/** 【均价】页签行（V5.68）：基金最新一条均线快照 + 现价/均线比值（3 位小数，>1 在均线上方） */
export interface FundMaRow {
  fundCode: string
  fundName: string
  dataDate: string
  closePrice: number
  ma5: number | null
  ma10: number | null
  ma20: number | null
  ma30: number | null
  ma60: number | null
  ma90: number | null
  ma120: number | null
  ma250: number | null
  ratio5: number | null
  ratio10: number | null
  ratio20: number | null
  ratio30: number | null
  ratio60: number | null
  ratio90: number | null
  ratio120: number | null
  ratio250: number | null
}

/** 均线刷新/回跑结果 */
export interface MaRunResult {
  fundCount: number
  rowCount: number
  dateFrom: string | null
  dateTo: string | null
}

/** 【均价】页签数据：每基金最新一条快照（读 fund_ma_daily 表） */
export function fundMaRows() {
  return get<FundMaRow[]>('/market-signals/ma')
}

/** 手动刷新均线（仅交易日；写全局表需 ACTION_SYNC 权限） */
export function refreshMa() {
  return post<MaRunResult>('/market-signals/ma/refresh')
}

/** 回跑均线数据：回退 N 个交易日逐日计算落表（幂等覆盖；需 ACTION_SYNC 权限） */
export function backfillMa(days: number) {
  return post<MaRunResult>(`/market-signals/ma/backfill/${days}`, {})
}

/** 均线信号行（V5.70）：短期均线上穿/下穿长期均线 */
export interface MaSignalItem {
  id: number
  fundCode: string
  fundName: string
  signalDate: string
  maShort: number
  maLong: number
  direction: 'UP' | 'DOWN'
  /** 信号描述：如「5日均线上穿10日均线」 */
  signalDesc: string
  priceAt: number | null
  maShortVal: number | null
  maLongVal: number | null
}

/** 均线信号分页（新→旧），可按基金代码过滤 */
export function maSignalsPage(params: { fundCode?: string; keyword?: string; direction?: string; page: number; size: number }) {
  return get<PageResult<MaSignalItem>>('/ma-signals/page', params)
}

/** 某基金的全部均线信号（基金详情【信号查询】页签用） */
export function maSignalsByFund(fundCode: string) {
  return get<MaSignalItem[]>(`/ma-signals/by-fund/${fundCode}`)
}
