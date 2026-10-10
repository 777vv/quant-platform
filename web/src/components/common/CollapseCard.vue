<template>
  <el-card shadow="never" class="collapse-card" :class="{ 'is-collapsed': !open }">
    <!-- 标题条整体可点：点击（或键盘 Enter/空格）收起、再点展开 -->
    <template #header>
      <div
        class="collapse-head"
        role="button"
        tabindex="0"
        :aria-expanded="open"
        @click="toggle"
        @keydown.enter.prevent="toggle"
        @keydown.space.prevent="toggle"
      >
        <span class="collapse-title">{{ title }}</span>
        <el-tooltip :content="open ? '收起' : '展开'" placement="top">
          <el-icon class="collapse-caret" :class="{ 'is-open': open }"><CaretBottom /></el-icon>
        </el-tooltip>
      </div>
    </template>
    <div v-show="open" class="collapse-body">
      <slot />
    </div>
  </el-card>
</template>

<script setup lang="ts">
/**
 * 可收起/展开的卡片（默认展开，收起后只留标题条）。
 *
 * 用途：长页面上让用户把不需要看的模块折起来，减少滚动距离。
 * 收起用 v-show 而不是 v-if——卡内的图表/表格 DOM 与 ECharts 实例保留，
 * 重新展开时 ChartPanel 内置的 ResizeObserver 会按恢复后的尺寸自动 resize，
 * 不重建画布、不丢 dataZoom 等交互状态。
 */
import { ref } from 'vue'
import { CaretBottom } from '@element-plus/icons-vue'

defineProps<{
  /** 卡片标题（收起后仅保留这一行） */
  title: string
}>()

/** 展开态（默认展开） */
const open = ref(true)

/** 收起/展开切换 */
function toggle() {
  open.value = !open.value
}
</script>

<style scoped>
.collapse-head {
  display: flex;
  align-items: center;
  gap: var(--q-space-2);
  cursor: pointer;
  user-select: none;
}

.collapse-caret {
  margin-left: auto;
  font-size: var(--q-font-lg);
  color: var(--q-text-muted);
  /* 展开向下指、收起向右指，一眼看出该点哪里 */
  transform: rotate(-90deg);
  transition: transform 0.2s ease, color 0.2s ease;
}

.collapse-caret.is-open {
  transform: rotate(0deg);
}

.collapse-head:hover .collapse-caret {
  color: var(--q-color-primary);
}

/* 收起时连卡体留白（EP 默认上下各 20px）一起去掉，只留标题条；标题的底边线也无需再画 */
.collapse-card.is-collapsed :deep(.el-card__body) {
  padding: 0;
}

.collapse-card.is-collapsed :deep(.el-card__header) {
  border-bottom: none;
}
</style>
