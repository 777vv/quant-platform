/**
 * 策略参数展示的公共工具（V5.96 从 FundDetailView 抽出——批量回测页也要弹「策略配置详情」，
 * 参数中文名/枚举文案/排序规则必须单源，不能两处各抄一份）。
 */

/** 各策略参数的中文名（与 StrategyParamForm 的界面名一致；键序即展示顺序） */
export const PARAM_LABELS: Record<string, Record<string, string>> = {
  // 已下线策略：仅保留标签让历史回测/旧配置还能读懂（V5.28 从平台移除，不再可选、不可回测）
  GRID: {
    mode: '网格模式(已下线)', upper: '网格上沿(已下线)', lower: '网格下沿(已下线)', grids: '格数(已下线)',
    sharePerGrid: '每格份额(已下线)', basePosition: '底仓份额(已下线)', anchorPrice: '锚点价(已下线)'
  },
  VAL_PERCENTILE: {
    lowPct: '低估阈值%(已下线)', highPct: '高估阈值%(已下线)', steps: '分档数(已下线)',
    windowYears: '回看窗口(年)(已下线)', sharePerStep: '每档份额(已下线)', basePosition: '底仓份额(已下线)'
  },
  OSC_UP: {
    initialShare: '初始仓位份额', baseShare: '底仓份额', fullShare: '满仓份额', windowDays: 'K线天数',
    riseReducePct: '上涨减仓%', fallAddPct: '下跌加仓%',
    buyShare: '买入份额', sellShare: '卖出份额',
    sizingStepPct: '每档份额增减%', sizingBase: '档位基准', maxSizingMultiple: '单笔最大倍数'
  },
  MA_BREAK: {
    initialShare: '初始仓位份额', baseShare: '底仓份额', fullShare: '满仓份额',
    breakoutMaDays: '均线突破(日)', breakdownMaDays: '均线跌破(日)', cooldownDays: '冷静天数'
  },
  DIV_GRID: {
    mode: '网格模式', lower: '网格下沿', upper: '网格上沿', grids: '格数',
    perGridMode: '每格单位', sharePerGrid: '每格份额', amountPerGrid: '每格金额',
    baseShare: '底仓份额', fullShare: '满仓份额', initialShare: '初始仓位份额',
    breakoutMode: '涨破上沿', breakdownMode: '跌破下沿', maxGridsPerBar: '单根最多成交格数',
    trendMaDays: '趋势均线天数', premiumBuyMaxPct: '溢价率买入上限%',
    premiumStaleDays: '溢价率容忍滞后(天)', backtestPremiumPct: '回测假设溢价率%',
    peBuyMax: 'PE 买入上限', peBuyMin: 'PE 买入下限', peBoostMultiplier: '低估买入倍数'
  },
  NDX_GRID: {
    mode: '网格模式', lower: '网格下沿', upper: '网格上沿', grids: '格数',
    perGridMode: '每格单位', sharePerGrid: '每格份额', amountPerGrid: '每格金额',
    baseShare: '底仓份额', fullShare: '满仓份额', initialShare: '初始仓位份额',
    breakoutMode: '涨破上沿', breakdownMode: '跌破下沿', maxGridsPerBar: '单根最多成交格数',
    trendMaDays: '趋势均线天数', premiumBuyMaxPct: '溢价率买入上限%',
    premiumStaleDays: '溢价率容忍滞后(天)', backtestPremiumPct: '回测假设溢价率%',
    peBuyMax: 'PE 买入上限', peBuyMin: 'PE 买入下限', peBoostMultiplier: '低估买入倍数'
  },
  PYRAMID_GRID: {
    mode: '网格模式', lower: '网格下沿', upper: '网格上沿', grids: '格数',
    perGridMode: '每格单位', sharePerGrid: '每格份额', amountPerGrid: '每格金额', pyramidStep: '每格增减',
    baseShare: '底仓份额', fullShare: '满仓份额', initialShare: '初始仓位份额',
    breakoutMode: '涨破上沿', breakdownMode: '跌破下沿', maxGridsPerBar: '单根最多成交格数',
    trendMaDays: '趋势均线天数', premiumBuyMaxPct: '溢价率买入上限%',
    premiumStaleDays: '溢价率容忍滞后(天)', backtestPremiumPct: '回测假设溢价率%',
    peBuyMax: 'PE 买入上限', peBuyMin: 'PE 买入下限', peBoostMultiplier: '低估买入倍数'
  },
  INV_PYRAMID_GRID: {
    mode: '网格模式', lower: '网格下沿', upper: '网格上沿', grids: '格数',
    perGridMode: '每格单位', sharePerGrid: '每格份额', amountPerGrid: '每格金额', pyramidStep: '每格增减',
    baseShare: '底仓份额', fullShare: '满仓份额', initialShare: '初始仓位份额',
    breakoutMode: '涨破上沿', breakdownMode: '跌破下沿', maxGridsPerBar: '单根最多成交格数',
    trendMaDays: '趋势均线天数', premiumBuyMaxPct: '溢价率买入上限%',
    premiumStaleDays: '溢价率容忍滞后(天)', backtestPremiumPct: '回测假设溢价率%',
    peBuyMax: 'PE 买入上限', peBuyMin: 'PE 买入下限', peBoostMultiplier: '低估买入倍数'
  },
  MA_TP_GRID: {
    initialShare: '初始仓位份额', baseShare: '底仓份额', fullShare: '满仓份额',
    baselineMaDays: '基准均线(日)', reboundMaDays: '回踩均线(日)',
    upperPct: '上限百分比', lowerPct: '下限百分比', cooldownDays: '冷静天数'
  }
}

/** 枚举型参数的展示文案：值 → 中文，未命中回退原值 */
export const ENUM_LABELS: Record<string, Record<string, string>> = {
  mode: { arithmetic: '等差', geometric: '等比' },
  perGridMode: { share: '按份额', amount: '按金额' },
  breakoutMode: { shift: '区间上移', hold: '保留底仓不动', clear: '清到只剩底仓' },
  breakdownMode: { hold: '买 1 格后观望', buy: '区间下方继续按格买入' },
  sizingBase: { anchor: '锚点窗口', window: 'K线窗口' }
}

/** 策略类型码 → 中文名（注册表之外的下线策略也保留可读名；页面拉到注册表后优先用注册表的） */
export const STRATEGY_TYPE_NAMES: Record<string, string> = {
  OSC_UP: '震荡向上',
  MA_BREAK: '均线突破',
  MA_TP_GRID: '均线止盈/加仓',
  DIV_GRID: '红利网格',
  NDX_GRID: '纳指网格',
  PYRAMID_GRID: '金字塔网格',
  INV_PYRAMID_GRID: '倒金字塔网格',
  GRID: '网格交易(已下线)',
  VAL_PERCENTILE: '估值百分位(已下线)'
}

/** 按标签表的键序排列参数（标签表里有的排前面、保持阅读顺序，未知键排在后面） */
function orderByLabels(parsed: Record<string, unknown>, labels: Record<string, string>): [string, unknown][] {
  const known = Object.keys(labels)
  return Object.entries(parsed).sort((a, b) => {
    const ia = known.indexOf(a[0])
    const ib = known.indexOf(b[0])
    return (ia < 0 ? Number.MAX_SAFE_INTEGER : ia) - (ib < 0 ? Number.MAX_SAFE_INTEGER : ib)
  })
}

/** 参数值展示：枚举值翻中文，其余原样 */
function displayValue(key: string, value: unknown): string {
  const text = String(value)
  return ENUM_LABELS[key]?.[text] ?? text
}

/**
 * 解析策略参数 JSON → 带中文标签的键值列表（未知键回退原始键名；JSON 非法时返回空列表）。
 * 策略配置详情弹框（基金详情/批量回测共用）的数据源。
 */
export function describeStrategyParams(strategyType: string, paramsJson: string): { label: string; value: string }[] {
  const labels = PARAM_LABELS[strategyType] ?? {}
  let parsed: Record<string, unknown> = {}
  try {
    parsed = JSON.parse(paramsJson) as Record<string, unknown>
  } catch {
    parsed = {}
  }
  return orderByLabels(parsed, labels).map(([key, value]: [string, unknown]) => ({
    label: labels[key] ?? key,
    value: displayValue(key, value)
  }))
}
