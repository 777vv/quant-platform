<template>
  <el-container class="app-layout">
    <el-aside :width="SIDEBAR_WIDTH" class="app-aside">
      <div class="app-logo">个人量化投资助手</div>
      <el-menu
router
        :default-active="route.path"
        :background-color="SIDEBAR_BG"
        :text-color="SIDEBAR_TEXT"
        :active-text-color="SIDEBAR_TEXT_ACTIVE">
        <!-- V5.58 菜单按权限过滤：管理员全量可见；临时账号只看所分配的菜单码 -->
        <el-menu-item v-for="item in visibleMenus" :key="item.path" :index="item.path">
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.label }}</span>
        </el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="app-header">
        <div class="header-title">{{ route.meta.title }}</div>
        <el-dropdown @command="handleCommand">
          <span class="header-user">
            <el-icon><UserFilled /></el-icon>
            {{ userStore.userInfo?.nickname || userStore.userInfo?.username || '用户' }}
            <el-icon><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <!-- V5.45 用户口径：只保留退出登录；平台配置走侧栏菜单，此处不再重复入口 -->
            <el-dropdown-menu>
              <el-dropdown-item command="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </el-header>
      <el-main class="app-main">
        <router-view />
      </el-main>
    </el-container>
    <!-- AI 投资助手浮窗（FR6）：登录后可用；V5.58 未获 AI 对话权限的临时账号不渲染入口球 -->
    <AiChatWidget v-if="userStore.can(PERM.ACTION_AI_CHAT)" />
  </el-container>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { SIDEBAR_BG, SIDEBAR_TEXT, SIDEBAR_TEXT_ACTIVE } from '@/utils/palette'
import { PERM } from '@/utils/permissions'

/** 侧栏宽度（与 tokens.css 的 --q-layout-sidebar-width 一致，供 el-aside 属性使用） */
const SIDEBAR_WIDTH = '200px'

/** 侧栏菜单清单（V5.58 数据驱动 + 权限过滤）：perm 为空 = 登录即可见（仪表盘）；icon 用全局注册的图标名 */
const MENUS = [
  { path: '/', label: '仪表盘', icon: 'Odometer', perm: '' },
  { path: '/funds', label: '基金池', icon: 'Coin', perm: PERM.MENU_FUNDS },
  { path: '/batch-backtest', label: '批量回测', icon: 'VideoPlay', perm: PERM.MENU_BATCH_BACKTEST },
  { path: '/trades', label: '交易流水', icon: 'Tickets', perm: PERM.MENU_TRADES },
  { path: '/signals', label: '信号查询', icon: 'Bell', perm: PERM.MENU_SIGNALS },
  { path: '/market-signals', label: '市场信号', icon: 'Histogram', perm: PERM.MENU_MARKET_SIGNALS },
  { path: '/compare', label: '基金对比', icon: 'DataAnalysis', perm: PERM.MENU_COMPARE },
  { path: '/import', label: '数据导入', icon: 'Download', perm: PERM.MENU_IMPORT },
  { path: '/ai-usage', label: 'AI用量统计', icon: 'TrendCharts', perm: PERM.MENU_AI_USAGE },
  { path: '/manual', label: '使用手册', icon: 'Reading', perm: PERM.MENU_MANUAL },
  { path: '/user', label: '平台配置', icon: 'UserFilled', perm: PERM.MENU_PLATFORM_CONFIG },
  { path: '/login-logs', label: '登录日志', icon: 'Clock', perm: PERM.MENU_LOGIN_LOGS },
  { path: '/user-manage', label: '用户管理', icon: 'Setting', perm: PERM.MENU_PLATFORM_CONFIG, adminOnly: true }
]

/** 按角色与权限码过滤后的菜单（adminOnly 项仅管理员可见） */
const visibleMenus = computed(() =>
  MENUS.filter((item) => (item.adminOnly ? userStore.isAdmin : item.perm === '' || userStore.can(item.perm)))
)
import AiChatWidget from '@/components/ai/AiChatWidget.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

onMounted(() => {
  if (!userStore.userInfo) {
    userStore.fetchUser()
  }
})

/** 用户下拉菜单（当前只有退出登录；平台配置已收归侧栏菜单） */
async function handleCommand(command: string) {
  if (command === 'logout') {
    await userStore.logout()
    await router.push('/login')
  }
}
</script>

<style scoped>
.app-layout {
  height: 100%;
}

/* 侧栏：深色渐变 + 与内容区之间不用阴影，靠色差分层 */
.app-aside {
  background: linear-gradient(180deg, var(--q-sidebar-bg) 0%, var(--q-sidebar-bg-deep) 100%);
}

.app-logo {
  height: var(--q-layout-header-height);
  line-height: var(--q-layout-header-height);
  text-align: center;
  color: var(--q-text-inverse);
  font-size: var(--q-font-lg);
  font-weight: 600;
  letter-spacing: 2px;
  border-bottom: 1px solid var(--q-sidebar-hover-bg);
}

.app-aside :deep(.el-menu) {
  border-right: none;
  background: transparent;
  padding: var(--q-space-2);
  gap: 2px;
}

/* 菜单项：圆角胶囊高亮，替代 EP 默认整行变色 */
.app-aside :deep(.el-menu-item) {
  height: 40px;
  line-height: 40px;
  margin-bottom: var(--q-space-1);
  border-radius: var(--q-radius-sm);
  /* 侧栏菜单字号 13 → 14（用户反馈偏小）【V2.3】 */
  font-size: var(--q-font-base);
}

.app-aside :deep(.el-menu-item:hover) {
  background-color: var(--q-sidebar-hover-bg);
}

.app-aside :deep(.el-menu-item.is-active) {
  background-color: var(--q-sidebar-active-bg);
  color: var(--q-sidebar-text-active);
  font-weight: 500;
}

/* 顶栏：白底 + 1px 细线，不用阴影（避免与卡片阴影打架） */
.app-header {
  height: var(--q-layout-header-height);
  background-color: var(--q-bg-card);
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid var(--q-border);
}

.header-title {
  font-size: var(--q-font-lg);
  font-weight: 600;
  color: var(--q-text-primary);
}

.header-user {
  display: flex;
  align-items: center;
  gap: var(--q-space-1);
  cursor: pointer;
  color: var(--q-text-regular);
  font-size: var(--q-font-sm);
}

.header-user:hover {
  color: var(--q-color-primary);
}

/* 内容区：浅灰渐变底，为白卡片提供层次 */
.app-main {
  background: var(--q-bg-page-gradient);
  padding: var(--q-layout-page-padding);
  overflow-y: auto;
}
</style>
