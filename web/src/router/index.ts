import { createRouter, createWebHistory } from 'vue-router'
import { PERM } from '@/utils/permissions'
import { useUserStore } from '@/stores/user'

const TOKEN_KEY = 'quant_token'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/login/LoginView.vue'),
      meta: { public: true }
    },
    {
      path: '/',
      component: () => import('@/components/layout/AppLayout.vue'),
      children: [
        { path: '', name: 'dashboard', component: () => import('@/views/dashboard/DashboardView.vue'), meta: { title: '仪表盘' } },
        { path: 'funds', name: 'funds', component: () => import('@/views/fund/FundListView.vue'), meta: { title: '基金池', permission: PERM.MENU_FUNDS } },
        { path: 'batch-backtest', name: 'batchBacktest', component: () => import('@/views/batch/BatchBacktestView.vue'), meta: { title: '批量回测', permission: PERM.MENU_BATCH_BACKTEST } },
        { path: 'funds/:code', name: 'fundDetail', component: () => import('@/views/fund/FundDetailView.vue'), meta: { title: '基金详情', permission: PERM.MENU_FUNDS } },
        { path: 'backtest/:id', name: 'backtestResult', component: () => import('@/views/fund/BacktestResultView.vue'), meta: { title: '回测结果', permission: PERM.MENU_FUNDS } },
        { path: 'trades', name: 'trades', component: () => import('@/views/trade/TradeListView.vue'), meta: { title: '交易流水', permission: PERM.MENU_TRADES } },
        { path: 'signals', name: 'signals', component: () => import('@/views/signal/SignalListView.vue'), meta: { title: '信号查询', permission: PERM.MENU_SIGNALS } },
        { path: 'market-signals', name: 'marketSignals', component: () => import('@/views/signal/MarketSignalView.vue'), meta: { title: '市场信号', permission: PERM.MENU_MARKET_SIGNALS } },
        { path: 'compare', name: 'compare', component: () => import('@/views/compare/FundCompareView.vue'), meta: { title: '基金对比', permission: PERM.MENU_COMPARE } },
        { path: 'import', name: 'import', component: () => import('@/views/import/ImportView.vue'), meta: { title: '数据导入', permission: PERM.MENU_IMPORT } },
        { path: 'ai-usage', name: 'aiUsage', component: () => import('@/views/ai/AiUsageView.vue'), meta: { title: 'AI用量统计', permission: PERM.MENU_AI_USAGE } },
        { path: 'manual', name: 'manual', component: () => import('@/views/ai/ManualView.vue'), meta: { title: '使用手册', permission: PERM.MENU_MANUAL } },
        { path: 'user', name: 'user', component: () => import('@/views/user/UserCenterView.vue'), meta: { title: '平台配置', permission: PERM.MENU_PLATFORM_CONFIG } },
        { path: 'login-logs', name: 'loginLogs', component: () => import('@/views/user/LoginLogView.vue'), meta: { title: '登录日志', permission: PERM.MENU_LOGIN_LOGS } },
        { path: 'user-manage', name: 'userManage', component: () => import('@/views/user/UserManageView.vue'), meta: { title: '用户管理', adminOnly: true } }
      ]
    },
    { path: '/:pathMatch(.*)*', redirect: '/' }
  ]
})

router.beforeEach(async (to) => {
  const token = localStorage.getItem(TOKEN_KEY)
  if (!to.meta.public && !token) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (to.path === '/login' && token) {
    return '/'
  }
  // V5.58 权限路由：刷新后 store 是空的，先拉一次当前用户（含角色与权限码），失败按未登录处理
  const userStore = useUserStore()
  if (token && !userStore.userInfo) {
    try {
      await userStore.fetchUser()
    } catch {
      userStore.clear()
      return { path: '/login', query: { redirect: to.fullPath } }
    }
  }
  // admin 专属页（用户管理）
  if (to.meta.adminOnly && !userStore.isAdmin) {
    return { path: '/' }
  }
  // 菜单权限页：无对应菜单码就回仪表盘（后端接口另有注解兜底，这里只控入口）
  if (to.meta.permission && !userStore.can(to.meta.permission as string)) {
    return { path: '/' }
  }
  return true
})

export default router
