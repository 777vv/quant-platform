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
        <el-menu-item index="/">
          <el-icon><Odometer /></el-icon>
          <span>仪表盘</span>
        </el-menu-item>
        <el-menu-item index="/funds">
          <el-icon><Coin /></el-icon>
          <span>基金池</span>
        </el-menu-item>
        <el-menu-item index="/trades">
          <el-icon><Tickets /></el-icon>
          <span>交易流水</span>
        </el-menu-item>
        <el-menu-item index="/signals">
          <el-icon><Bell /></el-icon>
          <span>信号查询</span>
        </el-menu-item>
        <el-menu-item index="/compare">
          <el-icon><DataAnalysis /></el-icon>
          <span>基金对比</span>
        </el-menu-item>
        <el-menu-item index="/import">
          <el-icon><Download /></el-icon>
          <span>数据导入</span>
        </el-menu-item>
        <el-menu-item index="/ai-usage">
          <el-icon><TrendCharts /></el-icon>
          <span>AI用量统计</span>
        </el-menu-item>
        <el-menu-item index="/manual">
          <el-icon><Reading /></el-icon>
          <span>使用手册</span>
        </el-menu-item>
        <el-menu-item index="/user">
          <el-icon><UserFilled /></el-icon>
          <span>平台配置</span>
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
            <el-dropdown-menu>
              <el-dropdown-item command="user">平台配置</el-dropdown-item>
              <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </el-header>
      <el-main class="app-main">
        <router-view />
      </el-main>
    </el-container>
    <!-- AI 投资助手浮窗（FR6）：登录后全局可用 -->
    <AiChatWidget />
  </el-container>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { SIDEBAR_BG, SIDEBAR_TEXT, SIDEBAR_TEXT_ACTIVE } from '@/utils/palette'

/** 侧栏宽度（与 tokens.css 的 --q-layout-sidebar-width 一致，供 el-aside 属性使用） */
const SIDEBAR_WIDTH = '200px'
import AiChatWidget from '@/components/ai/AiChatWidget.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

onMounted(() => {
  if (!userStore.userInfo) {
    userStore.fetchUser()
  }
})

async function handleCommand(command: string) {
  if (command === 'logout') {
    await userStore.logout()
    await router.push('/login')
  } else if (command === 'user') {
    await router.push('/user')
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
