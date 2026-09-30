import { createRouter, createWebHistory } from 'vue-router'

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
        { path: 'funds', name: 'funds', component: () => import('@/views/fund/FundListView.vue'), meta: { title: '基金池' } },
        { path: 'funds/:code', name: 'fundDetail', component: () => import('@/views/fund/FundDetailView.vue'), meta: { title: '基金详情' } },
        { path: 'backtest/:id', name: 'backtestResult', component: () => import('@/views/fund/BacktestResultView.vue'), meta: { title: '回测结果' } },
        { path: 'trades', name: 'trades', component: () => import('@/views/trade/TradeListView.vue'), meta: { title: '交易流水' } },
        { path: 'signals', name: 'signals', component: () => import('@/views/signal/SignalListView.vue'), meta: { title: '信号查询' } },
        { path: 'market-signals', name: 'marketSignals', component: () => import('@/views/signal/MarketSignalView.vue'), meta: { title: '市场信号' } },
        { path: 'compare', name: 'compare', component: () => import('@/views/compare/FundCompareView.vue'), meta: { title: '基金对比' } },
        { path: 'import', name: 'import', component: () => import('@/views/import/ImportView.vue'), meta: { title: '数据导入' } },
        { path: 'ai-usage', name: 'aiUsage', component: () => import('@/views/ai/AiUsageView.vue'), meta: { title: 'AI用量统计' } },
        { path: 'manual', name: 'manual', component: () => import('@/views/ai/ManualView.vue'), meta: { title: '使用手册' } },
        { path: 'user', name: 'user', component: () => import('@/views/user/UserCenterView.vue'), meta: { title: '平台配置' } },
        { path: 'login-logs', name: 'loginLogs', component: () => import('@/views/user/LoginLogView.vue'), meta: { title: '登录日志' } }
      ]
    },
    { path: '/:pathMatch(.*)*', redirect: '/' }
  ]
})

router.beforeEach((to) => {
  const token = localStorage.getItem(TOKEN_KEY)
  if (!to.meta.public && !token) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (to.path === '/login' && token) {
    return '/'
  }
  return true
})

export default router
