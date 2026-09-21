/**
 * 技术指标计算（V2.1）
 * 纯函数实现，输入收盘价/净值序列（按时间升序），输出与输入等长的数组；前置不足的位置为 null。
 * 参数取行业默认值：MA 5/10/20/60、BOLL(20, 2)、MACD(12, 26, 9)。
 * 计算在前端完成——数据量小（千级）且指标纯属展示，避免为此增加后端接口。
 */

/** 指标值序列（与输入等长，不足处为 null，供 ECharts 直接消费） */
export type IndicatorSeries = (number | null)[]

/** 简单移动平均：窗口内算术平均，前 window-1 个位置为 null */
export function ma(values: number[], window: number): IndicatorSeries {
  const result: IndicatorSeries = new Array(values.length).fill(null)
  if (window <= 0) {
    return result
  }
  let sum = 0
  for (let i = 0; i < values.length; i++) {
    sum += values[i]
    if (i >= window) {
      sum -= values[i - window]
    }
    if (i >= window - 1) {
      result[i] = Number((sum / window).toFixed(4))
    }
  }
  return result
}

/** 指数移动平均：alpha = 2/(window+1)，首值以第一个样本初始化（标准 EMA 递推） */
export function ema(values: number[], window: number): number[] {
  const result: number[] = []
  const alpha = 2 / (window + 1)
  let prev = 0
  for (let i = 0; i < values.length; i++) {
    prev = i === 0 ? values[0] : alpha * values[i] + (1 - alpha) * prev
    result.push(prev)
  }
  return result
}

/** 布林带结果：中轨为 MA(period)，上下轨为中轨 ± k × 标准差 */
export interface BollResult {
  mid: IndicatorSeries
  upper: IndicatorSeries
  lower: IndicatorSeries
}

/**
 * 布林带（Bollinger Bands）
 *
 * @param values 收盘价/净值序列
 * @param period 周期（默认 20）
 * @param k      标准差倍数（默认 2）
 */
export function boll(values: number[], period = 20, k = 2): BollResult {
  const mid = ma(values, period)
  const upper: IndicatorSeries = new Array(values.length).fill(null)
  const lower: IndicatorSeries = new Array(values.length).fill(null)
  for (let i = period - 1; i < values.length; i++) {
    const mean = mid[i]
    if (mean === null) {
      continue
    }
    let variance = 0
    for (let j = i - period + 1; j <= i; j++) {
      variance += (values[j] - mean) ** 2
    }
    const sd = Math.sqrt(variance / period)
    upper[i] = Number((mean + k * sd).toFixed(4))
    lower[i] = Number((mean - k * sd).toFixed(4))
  }
  return { mid, upper, lower }
}

/** MACD 结果：DIF、DEA（信号线）、MACD 柱（(DIF−DEA)×2，国内常用画法） */
export interface MacdResult {
  dif: IndicatorSeries
  dea: IndicatorSeries
  bar: IndicatorSeries
}

/**
 * MACD（Moving Average Convergence Divergence）
 *
 * @param values   收盘价/净值序列
 * @param fast     快线周期（默认 12）
 * @param slow     慢线周期（默认 26）
 * @param signal   信号线周期（默认 9）
 */
export function macd(values: number[], fast = 12, slow = 26, signal = 9): MacdResult {
  const empty: IndicatorSeries = new Array(values.length).fill(null)
  if (values.length === 0) {
    return { dif: empty, dea: empty, bar: empty }
  }
  const emaFast = ema(values, fast)
  const emaSlow = ema(values, slow)
  const difRaw = values.map((_, i) => emaFast[i] - emaSlow[i])
  const deaRaw = ema(difRaw, signal)
  // 慢线周期之前 DIF 不可靠，统一置 null，避免图上一段虚假信号
  const start = slow - 1
  const dif: IndicatorSeries = difRaw.map((v, i) => (i >= start ? Number(v.toFixed(4)) : null))
  const dea: IndicatorSeries = deaRaw.map((v, i) => (i >= start ? Number(v.toFixed(4)) : null))
  const bar: IndicatorSeries = difRaw.map((v, i) =>
    i >= start ? Number(((v - deaRaw[i]) * 2).toFixed(4)) : null
  )
  return { dif, dea, bar }
}
