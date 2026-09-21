/** 金额格式化：保留2位小数，千分位 */
export function formatAmount(value: number | null | undefined): string {
  if (value === null || value === undefined || Number.isNaN(value)) {
    return '--'
  }
  return value.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

/** 百分比格式化：保留2位小数 */
export function formatPercent(value: number | null | undefined): string {
  if (value === null || value === undefined || Number.isNaN(value)) {
    return '--'
  }
  return `${value.toFixed(2)}%`
}

/** 涨跌颜色 class（红涨绿跌） */
export function changeColorClass(value: number | null | undefined): string {
  if (value === null || value === undefined || Number.isNaN(value) || value === 0) {
    return ''
  }
  return value > 0 ? 'text-up' : 'text-down'
}

/**
 * token 数量格式化（V3.9）：一万以上折成「x.x万」，窄条里也看得清；以下用千分位。
 * 浮窗用量条与平台配置用量卡共用，避免两处口径走散。
 */
export function formatTokens(value: number | null | undefined): string {
  if (value === null || value === undefined || Number.isNaN(value)) {
    return '--'
  }
  return value >= 10000 ? `${(value / 10000).toFixed(1)}万` : value.toLocaleString('zh-CN')
}

/**
 * 费用格式化（V3.9）：保留 4 位小数并去掉尾零——单次咨询常不足 1 分钱，
 * 用金额的 2 位小数会全部显示成 0.00，看不出差别。
 */
export function formatCost(value: number | null | undefined): string {
  if (value === null || value === undefined || Number.isNaN(value)) {
    return '--'
  }
  return value.toFixed(4).replace(/\.?0+$/, '') || '0'
}
