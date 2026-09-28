import { get, post, put } from './request'

/** 全局仓位配置（V5.36：五类资产的目标占比范围，%） */
export interface AllocationConfig {
  /** 是否启用每周二 09:00 检查 */
  enabled: number
  cashMin: number
  cashMax: number
  aShareMin: number
  aShareMax: number
  usMin: number
  usMax: number
  asiaMin: number
  asiaMax: number
  euMin: number
  euMax: number
}

/** 保存请求体（全部必填，min ≤ max 由后端校验） */
export interface AllocationConfigRequest {
  enabled?: boolean
  cashMin: number
  cashMax: number
  aShareMin: number
  aShareMax: number
  usMin: number
  usMax: number
  asiaMin: number
  asiaMax: number
  euMin: number
  euMax: number
}

/** 单类别检查行 */
export interface AllocationRow {
  key: string
  label: string
  amount: number
  currentPct: number
  minPct: number
  maxPct: number
  ok: boolean
}

/** 检查结果 */
export interface AllocationCheckResult {
  evaluated: boolean
  totalAssets: number
  cashBalance: number
  rows: AllocationRow[]
}

/** 当前仓位配置 */
export function allocationConfig() {
  return get<AllocationConfig>('/allocation/config')
}

/** 保存仓位配置（返回保存后的配置便于核对） */
export function saveAllocationConfig(data: AllocationConfigRequest) {
  return put<AllocationConfig>('/allocation/config', data)
}

/** 手动触发一次检查（返回是否发生告警 + 明细） */
export function checkAllocation() {
  return post<{ alerted: boolean; result: AllocationCheckResult }>('/allocation/check')
}
