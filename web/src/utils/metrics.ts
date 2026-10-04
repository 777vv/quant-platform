/**
 * 区间表现指标（纯函数，基金详情与基金对比共用，避免同一算法两处各写一份）。
 * 输入均为按时间升序的序列（价格/净值/归一化值口径不限，指标只看相对变化）。
 */

/** 区间涨跌幅（%）：末值相对首值的变化 */
export function rangeReturnPct(values: number[]): number | null {
  if (values.length < 2 || !Number.isFinite(values[0]) || values[0] === 0) {
    return null
  }
  return Number(((values[values.length - 1] / values[0] - 1) * 100).toFixed(2))
}

/** 最大回撤（%）：峰值到谷底的最大跌幅，返回负数；数据不足返回 null */
export function maxDrawdownPct(values: number[]): number | null {
  if (values.length < 2) {
    return null
  }
  let peak = values[0]
  let maxDrawdown = 0
  for (const value of values) {
    peak = Math.max(peak, value)
    maxDrawdown = Math.min(maxDrawdown, (value / peak - 1) * 100)
  }
  return Number(maxDrawdown.toFixed(2))
}

/** 年化波动率（%）：日收益率标准差 × √244（一年交易日）；数据不足返回 null */
export function annualizedVolatilityPct(values: number[]): number | null {
  const returns: number[] = []
  for (let i = 1; i < values.length; i++) {
    if (values[i - 1] !== 0) {
      returns.push(values[i] / values[i - 1] - 1)
    }
  }
  if (returns.length < 2) {
    return null
  }
  const mean = returns.reduce((sum, value) => sum + value, 0) / returns.length
  const variance = returns.reduce((sum, value) => sum + (value - mean) ** 2, 0) / (returns.length - 1)
  return Number((Math.sqrt(variance) * Math.sqrt(244) * 100).toFixed(2))
}

/** 年化收益率（%）：按自然日复利折算 (末值/首值)^(365/自然日数) − 1，数据不足或区间为空返回 null。
 *  口径说明：收益率按自然日折算（行业惯例，选满一年时年化=区间收益）；与年化波动率的 √244 交易日口径并存
 *  属金融惯例——波动率按交易日放大、收益率按日历时间折算。 */
export function annualizedReturnPct(values: number[], fromDate: string, toDate: string): number | null {
  const first = values[0]
  const last = values[values.length - 1]
  if (values.length < 2 || !Number.isFinite(first) || first <= 0 || !Number.isFinite(last) || last <= 0) {
    return null
  }
  const naturalDays = Math.round((new Date(toDate).getTime() - new Date(fromDate).getTime()) / 86400000)
  if (!Number.isFinite(naturalDays) || naturalDays <= 0) {
    return null
  }
  return Number(((Math.pow(last / first, 365 / naturalDays) - 1) * 100).toFixed(2))
}
