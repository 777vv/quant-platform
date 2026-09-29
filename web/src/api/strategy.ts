import { del, get, post, put } from './request'
import type { PageResult } from '@/types/api'

export interface StrategyConfig {
  id: number
  fundCode: string
  strategyType: string
  strategyName: string
  params: string
  enabled: number
  /** 备注（用户自填） */
  remark?: string
}

export interface StrategyTypeVO {
  type: string
  name: string
}

export interface BacktestRecord {
  id: number
  fundCode: string
  strategyType: string
  params: string
  startDate: string
  endDate: string
  initialCapital: number
  finalAssets: number | null
  totalReturnPct: number | null
  annualizedPct: number | null
  maxDrawdownPct: number | null
  ddPeakDate: string | null
  ddTroughDate: string | null
  ddRecoverDate: string | null
  sharpe: number | null
  winRate: number | null
  tradeCount: number

  /** 平均仓位份额：决策期逐日持仓份额均值（老记录为 null） */
  avgPositionShare: number | null
  /** 持有总收益%：买入持有基准的区间总收益率（老记录为 null） */
  benchTotalReturnPct: number | null
  /** 持有最大回撤%：买入持有基准的最大回撤（老记录为 null） */
  benchMaxDrawdownPct: number | null
  /** 平均持仓市值：决策期逐日持仓市值均值（老记录为 null） */
  avgPositionValue: number | null
  /** 平均持仓成本：决策期逐日"摊薄成本×份额"的均值（＝剩余持仓的实际投入）（老记录为 null） */
  avgPositionCost: number | null
  /** 持仓资产收益率%：（期末资产−初始资金）÷平均持仓成本，衡量实际投出资金的决策质量（老记录为 null） */
  positionReturnPct: number | null
  status: number
  errorMsg: string | null
  equityCurve: string | null
  drawdownCurve: string | null
  benchmarkCurve: string | null
}

export interface BacktestTrade {
  id: number
  backtestId: number
  tradeDate: string
  direction: string
  price: number
  share: number
  amount: number
  fee: number
  cashAfter: number
  positionAfter: number
  reason: string
}

export interface BacktestRequest {
  fundCode: string
  strategyType: string
  params: Record<string, unknown>
  startDate: string
  endDate: string
  initialCapital: number
}

export function strategyTypes() {
  return get<StrategyTypeVO[]>('/strategies/types')
}

export function fundStrategies(code: string) {
  return get<StrategyConfig[]>(`/funds/${code}/strategies`)
}

export function addStrategy(code: string, data: { strategyType: string; params: Record<string, unknown>; remark?: string }) {
  return post<void>(`/funds/${code}/strategies`, data)
}

/** 修改策略配置：类型不可改——不传=沿用已存，传了必须与已存一致（后端按已存类型校验 params） */
export function updateStrategy(id: number, data: { strategyType?: string; params?: Record<string, unknown>; enabled?: number; remark?: string; strategyName?: string }) {
  return put<void>(`/strategies/${id}`, data)
}

export function deleteStrategy(id: number) {
  return del<void>(`/strategies/${id}`)
}

export function createBacktest(data: BacktestRequest) {
  return post<{ id: number }>('/backtest', data)
}

export function pageBacktest(fundCode: string | null, page = 1, size = 20) {
  return get<PageResult<BacktestRecord>>('/backtest', { fundCode, page, size })
}

export function backtestDetail(id: number) {
  return get<BacktestRecord>(`/backtest/${id}`)
}

export function backtestTrades(id: number, page = 1, size = 100) {
  return get<PageResult<BacktestTrade>>(`/backtest/${id}/trades`, { page, size })
}

/** 策略信号记录（每日 21:00 任务生成） */
export interface SignalRecord {
  id: number
  fundCode: string
  strategyType: string
  signalDate: string
  direction: string
  priceAt: number | null
  suggestDesc: string
  readFlag: number
  notifiedFlag: number
}

/** 近 N 天信号列表（新→旧） */
export function recentSignals(days = 7) {
  return get<SignalRecord[]>('/strategies/signals', { days })
}

/** 手动触发一轮信号计算（返回生成条数；与定时任务共用锁） */
export function runSignals() {
  return post<number>('/strategies/signals/run')
}

/** 标记信号为已读（仪表盘未读红点消除） */
export function markSignalsRead(ids: number[]) {
  return post<void>('/strategies/signals/read', { ids })
}

/** 信号行视图（【信号查询】页用；后端已补齐展示字段） */
export interface SignalItem extends SignalRecord {
  /** 基金名称（库里查不到时为空串，界面回退显示代码） */
  fundName: string
  /** 策略展示名（网格交易 / 估值百分位；未知类型回退类型码） */
  strategyName: string
}

/** 信号分页查询参数（筛选条件都可空 = 不限） */
export interface SignalPageQuery {
  /** 基金代码（精确匹配） */
  fundCode?: string
  /** 方向（BUY/SELL/HOLD） */
  direction?: string
  /** 策略类型（GRID/VAL_PERCENTILE） */
  strategyType?: string
  /** 信号日期下界（含，yyyy-MM-dd） */
  startDate?: string
  /** 信号日期上界（含，yyyy-MM-dd） */
  endDate?: string
  /** 页码（1 起） */
  page: number
  /** 每页条数 */
  size: number
}

/** 信号分页查询（新→旧） */
export function signalsPage(params: SignalPageQuery) {
  return get<PageResult<SignalItem>>('/strategies/signals/page', params)
}

/** 全部策略配置（自选列表批量展示"已配置策略"列） */
export function allStrategyConfigs() {
  return get<StrategyConfig[]>('/strategies/configs')
}
