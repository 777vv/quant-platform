<template>
  <div class="page manual-page">
    <!-- 两栏：左侧目录（滚动正文时靠 sticky 钉住）+ 右侧正文（吃满剩余宽度） -->
    <div class="manual-layout">
      <aside class="manual-toc">
        <el-card shadow="never">
          <template #header>
            <div class="card-header"><span>目录</span></div>
          </template>
          <ul class="toc">
            <li v-for="item in toc" :key="item.index" :class="{ 'toc-sub': item.level === 3 }">
              <span class="toc-link" @click="scrollTo(item.index)">{{ item.text }}</span>
            </li>
          </ul>
        </el-card>
      </aside>

      <section class="manual-content">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <span>使用手册</span>
              <span class="muted">与 AI 助手读的是同一份手册：这里的操作步骤，直接问 AI 也一样</span>
            </div>
          </template>
          <el-alert
            type="info"
            :closable="false"
            show-icon
            class="manual-tip"
            title="本手册讲「这个平台怎么用」：功能在哪、怎么操作、参数含义、注意事项与常见问题。想快速找，也可以用右下角 AI 浮窗问（例如「回测参数什么意思」「怎么配置模型和 token」）。"
          />
          <div class="manual-body" v-html="html" />
        </el-card>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import DOMPurify from 'dompurify'
import { marked } from 'marked'
import { aiManual } from '@/api/ai'

/** 手册 Markdown 原文 */
const markdown = ref('')
/** 渲染后的 HTML（经 XSS 过滤；内容虽来自本仓库，仍统一消毒） */
const html = computed(() => DOMPurify.sanitize(marked.parse(markdown.value ?? '', { async: false }) as string))

/** 目录项（从 Markdown 的 ## / ### 标题提取，顺序与正文里的标题一一对应） */
interface TocItem {
  /** 标题在「全部标题」中的序号（用于滚动定位） */
  index: number
  /** 标题层级：2=章节、3=小节 */
  level: number
  /** 标题文本 */
  text: string
}

const toc = computed<TocItem[]>(() => {
  const items: TocItem[] = []
  let index = 0
  for (const line of (markdown.value ?? '').split('\n')) {
    if (line.startsWith('## ') || line.startsWith('### ')) {
      items.push({ index: index++, level: line.startsWith('### ') ? 3 : 2, text: line.replace(/^#+\s*/, '') })
    }
  }
  return items
})

/** 点击目录：滚动到正文里对应的标题（不依赖锚点 id，避免 marked 版本差异） */
function scrollTo(index: number) {
  const headings = document.querySelectorAll('.manual-body h2, .manual-body h3')
  const target = headings[index]
  if (target) {
    target.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }
}

onMounted(async () => {
  markdown.value = await aiManual()
})
</script>

<style scoped>
/* 铺满内容区：本页是"目录 + 正文"的阅读页，不再像设置页那样限宽居中（用户反馈两侧留白） */
.manual-page {
  width: 100%;
}

.manual-layout {
  display: flex;
  align-items: flex-start;
  gap: var(--q-space-3);
}

/* 目录：固定宽度的窄栏，滚动正文时钉在顶部不跟着滚（长目录在卡片内自己滚） */
.manual-toc {
  position: sticky;
  top: 0;
  flex: 0 0 232px;
  width: 232px;
}

/* 正文：吃掉全部剩余宽度 */
.manual-content {
  flex: 1;
  min-width: 0;
}

/* 卡片标题：品牌色短竖条 + 右侧次要信息（与全站一致） */
.card-header {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-left: var(--q-space-3);
  font-size: var(--q-font-base);
  font-weight: 600;
  color: var(--q-text-primary);
}

.card-header::before {
  content: '';
  position: absolute;
  left: 0;
  top: 50%;
  width: 3px;
  height: 14px;
  border-radius: 1px;
  background: var(--q-color-primary);
  transform: translateY(-50%);
}

.card-header .muted {
  font-weight: 400;
  font-size: var(--q-font-xs);
}

.manual-toc :deep(.el-card__body) {
  padding: var(--q-space-2) var(--q-space-3);
}

/* 目录列表：超长时卡片内滚动，卡片本身靠 sticky 停在顶部 */
.toc {
  max-height: calc(100vh - 180px);
  margin: 0;
  padding: 0;
  list-style: none;
  overflow-y: auto;
}

.toc li {
  margin: 2px 0;
}

.toc-sub {
  padding-left: var(--q-space-3);
}

.toc-link {
  display: inline-block;
  padding: 2px 0;
  font-size: var(--q-font-sm);
  color: var(--q-text-regular);
  cursor: pointer;
}

.toc-link:hover {
  color: var(--q-color-primary);
}

.manual-tip {
  margin-bottom: var(--q-space-3);
}

/* 正文排版（Markdown 渲染结果） */
.manual-body {
  font-size: var(--q-font-base);
  line-height: 1.85;
  color: var(--q-text-regular);
}

.manual-body :deep(h1) {
  margin: 0 0 var(--q-space-3);
  font-size: 22px;
  color: var(--q-text-primary);
}

.manual-body :deep(h2) {
  margin: var(--q-space-4) 0 var(--q-space-2);
  padding-bottom: 6px;
  border-bottom: 1px solid var(--q-border);
  font-size: 18px;
  color: var(--q-text-primary);
}

.manual-body :deep(h3) {
  margin: var(--q-space-3) 0 6px;
  font-size: 15px;
  color: var(--q-text-primary);
}

.manual-body :deep(p) {
  margin: 6px 0;
}

.manual-body :deep(ul),
.manual-body :deep(ol) {
  margin: 6px 0;
  padding-left: 22px;
}

.manual-body :deep(blockquote) {
  margin: var(--q-space-2) 0;
  padding: 6px 12px;
  border-left: 3px solid var(--q-color-primary-border);
  background: var(--q-bg-subtle);
  color: var(--q-text-secondary);
}

.manual-body :deep(table) {
  width: 100%;
  margin: var(--q-space-2) 0;
  border-collapse: collapse;
  font-size: var(--q-font-sm);
}

.manual-body :deep(th),
.manual-body :deep(td) {
  border: 1px solid var(--q-border);
  padding: 6px 10px;
  text-align: left;
}

.manual-body :deep(th) {
  background: var(--q-bg-subtle);
  color: var(--q-text-primary);
}

.manual-body :deep(code) {
  padding: 0 4px;
  border-radius: 3px;
  background: var(--q-bg-subtle);
}

.manual-body :deep(pre) {
  padding: var(--q-space-2) var(--q-space-3);
  border-radius: var(--q-radius-sm);
  background: var(--q-bg-subtle);
  overflow-x: auto;
}

.manual-body :deep(pre code) {
  padding: 0;
  background: transparent;
}

/* 窄屏（平板/手机）：目录不再钉住，改为整宽叠在正文上方 */
@media (max-width: 900px) {
  .manual-layout {
    flex-direction: column;
  }

  .manual-toc {
    position: static;
    flex: none;
    width: 100%;
  }

  .toc {
    max-height: 240px;
  }
}
</style>
