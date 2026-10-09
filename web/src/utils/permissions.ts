/**
 * 权限码常量（前端镜像，V5.58）。
 * 单一来源在后端 quant-common 的 PermissionCodes（经「权限字典」接口下发中文名）；
 * 这里只放码常量供路由 meta、菜单过滤与按钮显隐引用，禁止在页面里手写字符串散落各处。
 */
export const PERM = {
  MENU_DASHBOARD: 'menu:dashboard',
  MENU_FUNDS: 'menu:funds',
  MENU_TRADES: 'menu:trades',
  MENU_SIGNALS: 'menu:signals',
  MENU_MARKET_SIGNALS: 'menu:marketSignals',
  MENU_COMPARE: 'menu:compare',
  MENU_IMPORT: 'menu:import',
  MENU_AI_USAGE: 'menu:aiUsage',
  MENU_MANUAL: 'menu:manual',
  MENU_PLATFORM_CONFIG: 'menu:platformConfig',
  MENU_LOGIN_LOGS: 'menu:loginLogs',
  MENU_BATCH_BACKTEST: 'menu:batchBacktest',
  ACTION_SYNC: 'action:sync',
  ACTION_IMPORT_FUND: 'action:importFund',
  ACTION_TRADE: 'action:trade',
  ACTION_TAG: 'action:tag',
  ACTION_STRATEGY: 'action:strategy',
  ACTION_AI_CHAT: 'action:aiChat',
  ACTION_BATCH_BACKTEST: 'action:batchBacktest'
} as const
