/**
 * 图表调色板（V2.0）
 * 与 styles/tokens.css 的色值保持镜像——JS 无法直接读 CSS 变量做 ECharts 配置，
 * 故此处是图表侧唯一色值来源：所有 ECharts 配置必须从此引入，不得内联写色值。
 * 由 npm run check:style 白名单豁免（它是允许出现色值的两个文件之一）。
 */

/** 涨跌色（A 股习惯：红涨绿跌） */
export const UP = '#e5484d'
export const DOWN = '#12a150'
export const FLAT = '#6b7280'

/** 图表通用 */
export const PRIMARY = '#2563eb'
export const BENCHMARK = '#94a3b8'
export const PE_LINE = '#7c3aed'
export const VOLUME = '#cbd5e1'

/** 坐标轴与网格 */
export const AXIS_LINE = '#e5e7eb'
export const AXIS_LABEL = '#6b7280'
export const SPLIT_LINE = '#f1f3f7'

/** 文字（图例、标题） */
export const TEXT_PRIMARY = '#1f2937'
export const TEXT_SECONDARY = '#6b7280'

/** K 线：阳线红、阴线绿 */
export const CANDLE_UP = UP
export const CANDLE_DOWN = DOWN

/** 均线（V5.88：改为一组长周期 30/60/90/120/180/250）——6 色互不撞色，并与 K 线红/绿保持区分度 */
export const MA_COLORS = ['#f59e0b', '#0ea5e9', '#7c3aed', '#64748b', '#10b981', '#ec4899']

/** 布林带：中轨 + 上下轨（上下轨同色，中轨用主色区分） */
export const BOLL_MID = '#7c3aed'
export const BOLL_BAND = '#0ea5e9'

/** MACD：DIF 快线 / DEA 慢线（柱状涨跌沿用红涨绿跌） */
export const MACD_DIF = '#2563eb'
export const MACD_DEA = '#f59e0b'

/** 饼图配色（资产配置等，按序取用） */
export const PIE_PALETTE = ['#2563eb', '#7c3aed', '#0ea5e9', '#f59e0b', '#12a150', '#e5484d', '#64748b']

/** 侧栏（深色导航）——与 tokens.css 的 --q-sidebar-* 保持镜像，供 el-menu props 使用 */
// 行情图交易标记（买入 b / 卖出 s / 分红 q）：买用涨色、卖用跌色（A 股买红卖绿习惯），
// 分红用品牌蓝——三者都直接复用上面的常量，不引入新的色值
export const TEXT_INVERSE = '#ffffff'

export const TRADE_BUY = UP
export const TRADE_SELL = DOWN
export const TRADE_DIVIDEND = PRIMARY

export const SIDEBAR_BG = '#1e293b'
export const SIDEBAR_TEXT = '#a8b3c4'
export const SIDEBAR_TEXT_ACTIVE = '#ffffff'

/** 涨跌取色工具：正红、负绿、零灰 */
export function changeColor(value: number | null | undefined): string {
  if (value === null || value === undefined || Number.isNaN(value) || value === 0) {
    return FLAT
  }
  return value > 0 ? UP : DOWN
}
