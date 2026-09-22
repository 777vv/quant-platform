<template>
  <Teleport to="body">
    <!-- 悬浮球（折叠态）：可拖动，默认停在右下角；拖动超过阈值就不算点击（不会误开面板） -->
    <div
      v-if="!open"
      class="ai-ball"
      :style="ballStyle"
      title="AI 投资助手（可拖动）"
      @mousedown="startBallDrag"
    >
      <el-icon :size="22"><ChatDotRound /></el-icon>
      <span class="ai-ball-text">AI</span>
    </div>

    <!-- 对话面板 -->
    <div
      v-else
      ref="panelEl"
      class="ai-panel"
      :style="panelStyle"
      :class="{ 'ai-panel--dragging': dragging }"
    >
      <div class="ai-header" @mousedown="startDrag">
        <div class="ai-title">
          <el-icon><ChatDotRound /></el-icon>
          <span>AI 投资助手</span>
          <el-tag v-if="config?.model" size="small" type="info" effect="plain">{{ config.model }}</el-tag>
        </div>
        <div class="ai-header-actions" @mousedown.stop>
          <el-button link size="small" title="历史会话" @click="toggleSidebar">
            <el-icon><Clock /></el-icon>
          </el-button>
          <el-button link size="small" title="新会话" @click="newSession">
            <el-icon><Plus /></el-icon>
          </el-button>
          <el-button link size="small" title="收起" @click="collapsePanel">
            <el-icon><Minus /></el-icon>
          </el-button>
        </div>
      </div>

      <!-- 今日用量条（V3.9）：正常品牌蓝 / 达预警线琥珀 / 超限红色并禁用输入 -->
      <div v-if="usage" class="ai-usage" :class="`ai-usage--${usageLevel}`" :title="usageTitle">
        <span class="ai-usage-text">{{ usageText }}</span>
        <span class="ai-usage-track">
          <span class="ai-usage-fill" :style="{ width: `${usageWidth}%` }" />
        </span>
      </div>

      <div class="ai-body">
        <!-- 会话侧栏 -->
        <div v-if="sidebarOpen" class="ai-sidebar">
          <div class="ai-sidebar-head">历史会话</div>
          <ul class="ai-session-list">
            <li
              v-for="item in sessions"
              :key="item.sessionId"
              class="ai-session-item"
              :class="{ 'is-active': item.sessionId === sessionId }"
              @click="loadSession(item.sessionId)"
            >
              <span class="ai-session-title">{{ item.title || '未命名会话' }}</span>
              <el-icon class="ai-session-del" title="删除会话" @click.stop="removeSession(item.sessionId)">
                <Delete />
              </el-icon>
            </li>
            <li v-if="sessions.length === 0" class="ai-session-empty">暂无历史会话</li>
          </ul>
        </div>

        <!-- 消息区 -->
        <div ref="listEl" class="ai-messages">
          <div v-if="messages.length === 0" class="ai-welcome">
            <p>可以问我：</p>
            <ul>
              <li v-for="tip in SUGGESTIONS" :key="tip" @click="applySuggestion(tip)">{{ tip }}</li>
            </ul>
            <el-alert
              v-if="config && !config.configured"
              type="warning"
              :closable="false"
              show-icon
              :title="config.extras.hint || '尚未配置 API Key'"
            />
          </div>

          <div v-for="(message, index) in messages" :key="index" class="ai-message" :class="`ai-message--${message.role}`">
            <div class="ai-message-role">{{ message.role === 'user' ? '我' : 'AI' }}</div>
            <div class="ai-message-content">
              <!-- 用户消息按纯文本渲染；助手回答经 Markdown 渲染并做 XSS 过滤 -->
              <template v-if="message.role === 'user'">{{ message.content }}</template>
              <div v-else class="ai-markdown" v-html="renderMarkdown(message.content)" />
              <span v-if="message.streaming" class="ai-cursor">▍</span>
              <!-- 本次用量（结束帧带回；估算值会显式标注，费用为 0 时不显示以免误读成"免费"） -->
              <div v-if="message.usage" class="ai-usage-note">
                本次 {{ formatTokens(message.usage.totalTokens) }} token（{{ message.usage.rounds }} 轮<template
                  v-if="message.usage.estimated"
                >，估算</template>）
                <template v-if="showCost(message.usage.cost)"> ≈ ¥{{ message.usage.cost }}</template>
              </div>
            </div>
          </div>

          <!-- 数据查询过程提示：优先展示服务端 tool 事件，未收到时用"等待首个 token"兜底 -->
          <div v-if="hintText" class="ai-tool-hint">
            <el-icon class="is-loading"><Loading /></el-icon>
            <span>{{ hintText }}</span>
          </div>
        </div>
      </div>

      <div class="ai-input">
        <el-input
          v-model="question"
          type="textarea"
          :rows="2"
          resize="none"
          maxlength="2000"
          :disabled="overLimit"
          :placeholder="overLimit ? '今日额度已用完，明日 00:00 自动恢复' : '输入问题，Enter 发送 / Shift+Enter 换行'"
          @keydown.enter.exact.prevent="send"
        />
        <div class="ai-input-actions">
          <span class="ai-hint" :class="{ 'is-over': overLimit }">
            {{ overLimit ? '今日 AI 用量已达上限' : `上下文窗口 ${config?.memoryWindow ?? 20} 条` }}
          </span>
          <el-button v-if="streaming" size="small" @click="stop">停止</el-button>
          <el-button
            v-else
            type="primary"
            size="small"
            :disabled="!question.trim() || overLimit || (config ? !config.configured : false)"
            @click="send"
          >
            发送
          </el-button>
        </div>
        <div class="ai-disclaimer">回答由 AI 生成，仅供参考，不构成投资建议；投资决策与风险由本人承担。</div>
      </div>

      <!-- 右下角缩放手柄 -->
      <!-- 四角缩放手柄：拖任一角改变大小，对角保持锚定（se 右下 / nw 左上 / ne 右上 / sw 左下） -->
      <span
        v-for="dir in RESIZE_DIRS"
        :key="dir"
        class="ai-resize"
        :class="`ai-resize--${dir}`"
        :title="RESIZE_TITLE[dir]"
        @mousedown.stop.prevent="startResize($event, dir)"
      />
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ChatDotRound, Clock, Delete, Loading, Minus, Plus } from '@element-plus/icons-vue'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import {
  aiChatStream,
  aiConfig,
  aiMessages,
  aiSessions,
  aiUsageToday,
  deleteAiSession,
  type AiAnswerUsage,
  type AiConfigVO,
  type AiSessionVO,
  type AiUsageTodayVO
} from '@/api/ai'
import { formatCost, formatTokens } from '@/utils/format'

/** 首屏引导问题 */
const SUGGESTIONS = ['我的持仓现在怎么样？', '510300 现在什么价位？', '最近有什么买卖信号？', '沪深300 估值贵不贵？']

/** 工具名 → 中文过程提示 */
const TOOL_LABELS: Record<string, string> = {
  getFundQuote: '查询基金最新行情',
  listWatchlistQuotes: '查询自选池行情',
  getFundInfo: '查询基金档案',
  getFundHistory: '查询历史行情',
  getFundValuation: '查询指数估值',
  getMyPositions: '查询我的持仓',
  getAssetSummary: '查询资产总览',
  getHoldingTrend: '查询持仓走势',
  getRecentSignals: '查询策略信号',
  getGlobalIndexQuotes: '查询全球指数',
  getIndexQuoteByName: '查询指数行情'
}

/** 会话 ID 本地留存键（刷新后仍可继续同一会话） */
const SESSION_KEY = 'quant_ai_session'

interface ChatMessage {
  role: 'user' | 'assistant'
  content: string
  streaming?: boolean
  /** 本次回答的用量（结束帧带回；仅界面展示，不持久化） */
  usage?: AiAnswerUsage | null
}

const open = ref(false)
const sidebarOpen = ref(false)
const question = ref('')
const messages = ref<ChatMessage[]>([])
const sessions = ref<AiSessionVO[]>([])
const config = ref<AiConfigVO | null>(null)
const sessionId = ref<string>(localStorage.getItem(SESSION_KEY) ?? '')
const streaming = ref(false)
const toolHint = ref('')
/** 今日用量与额度（用量条 + 超限拦截的依据；查询失败不阻塞对话） */
const usage = ref<AiUsageTodayVO | null>(null)
/** 已发出请求但还没收到首个 token（此期间模型通常正在调用工具查数据） */
const awaitingFirstToken = ref(false)
const panelEl = ref<HTMLElement>()
const listEl = ref<HTMLElement>()

/** 面板位置与尺寸（拖拽/缩放后写回，刷新不保留——浮窗位置属于临时状态） */
const layout = reactive({ x: window.innerWidth - 480, y: window.innerHeight - 600, w: 460, h: 520 })
const dragging = ref(false)

/** 悬浮球尺寸与默认边距（登录进来默认停在最右下角） */
const BALL_SIZE = 56
const BALL_MARGIN = 24

/** 悬浮球左上角坐标（可拖动；面板收起时与面板右下角对齐，视觉锚点跟随） */
const ballPos = reactive({
  x: window.innerWidth - BALL_SIZE - BALL_MARGIN,
  y: window.innerHeight - BALL_SIZE - BALL_MARGIN
})

const ballStyle = computed(() => ({ left: `${ballPos.x}px`, top: `${ballPos.y}px` }))

/** 拖动悬浮球：位移小于阈值按点击处理（打开面板），否则只移动位置 */
function startBallDrag(event: MouseEvent) {
  const startX = event.clientX
  const startY = event.clientY
  const originX = ballPos.x
  const originY = ballPos.y
  let moved = false
  const onMove = (moveEvent: MouseEvent) => {
    const dx = moveEvent.clientX - startX
    const dy = moveEvent.clientY - startY
    if (!moved && Math.abs(dx) + Math.abs(dy) < 5) {
      return
    }
    moved = true
    ballPos.x = Math.min(Math.max(0, originX + dx), window.innerWidth - BALL_SIZE)
    ballPos.y = Math.min(Math.max(0, originY + dy), window.innerHeight - BALL_SIZE)
  }
  const onUp = () => {
    window.removeEventListener('mousemove', onMove)
    window.removeEventListener('mouseup', onUp)
    if (moved) {
      // 拖过就同步面板锚点：面板展开时贴在球所在角落
      layout.x = Math.min(Math.max(0, ballPos.x + BALL_SIZE - layout.w), window.innerWidth - layout.w)
      layout.y = Math.min(Math.max(0, ballPos.y + BALL_SIZE - layout.h), window.innerHeight - layout.h)
    } else {
      openPanel()
    }
  }
  window.addEventListener('mousemove', onMove)
  window.addEventListener('mouseup', onUp)
}
let controller: AbortController | null = null

const panelStyle = computed(() => ({
  left: `${layout.x}px`,
  top: `${layout.y}px`,
  width: `${layout.w}px`,
  height: `${layout.h}px`
}))

/** 过程提示文案：服务端 tool 事件优先，否则在等待首个 token 时兜底显示 */
const hintText = computed(() => {
  if (toolHint.value) {
    return toolHint.value
  }
  return awaitingFirstToken.value ? '正在查询数据…' : ''
})

/** 是否已超限：超限后禁用输入（服务端也会在调模型前再拦一次） */
const overLimit = computed(() => usage.value?.overLimit === true)

/** 用量条状态：超限红 / 达预警线琥珀 / 正常品牌蓝 */
const usageLevel = computed(() => {
  if (usage.value?.overLimit) {
    return 'over'
  }
  return usage.value?.warn ? 'warn' : 'normal'
})

/** 进度条宽度（未配上限时为 0，不显示虚假进度） */
const usageWidth = computed(() => Math.min(Math.max(usage.value?.usedPercent ?? 0, 0), 100))

/** 用量条文案：已用 token 与费用，配了上限则一并显示上限 */
const usageText = computed(() => {
  const item = usage.value
  if (!item) {
    return ''
  }
  const used = [`今日 ${formatTokens(item.totalTokens)} token`, `¥${formatCost(item.cost)}`]
  const limits: string[] = []
  if (limitOf(item.dailyTokenLimit)) {
    limits.push(`${formatTokens(item.dailyTokenLimit ?? 0)} token`)
  }
  if (limitOf(item.dailyCostLimit)) {
    limits.push(`¥${formatCost(item.dailyCostLimit ?? 0)}`)
  }
  return limits.length ? `${used.join(' · ')} / 上限 ${limits.join(' · ')}` : used.join(' · ')
})

/** 悬浮说明：优先展示服务端提示（未填单价/已超限），否则给当日次数 */
const usageTitle = computed(() => {
  const item = usage.value
  if (!item) {
    return ''
  }
  return item.hint ?? `今日咨询 ${item.requestCount} 次（其中对话 ${item.chatCount} 次）`
})

/** 是否配置了该维度的上限（0 与 null 都表示不限制） */
function limitOf(value: number | null | undefined): boolean {
  return typeof value === 'number' && value > 0
}

/** 是否展示本次费用：0 元不显示（未填单价或免费模型时，显示"≈ ¥0"容易被误读成免费） */
function showCost(cost: string): boolean {
  return Number(cost) > 0
}

/** 拉取今日用量（失败静默：发送时服务端仍会拦额度，不因此打断对话） */
async function loadUsage() {
  try {
    usage.value = await aiUsageToday()
  } catch {
    // 忽略：用量条不显示，但功能不受影响
  }
}

/** Markdown 渲染 + XSS 过滤（模型输出不可信，必须消毒后插入） */
function renderMarkdown(text: string): string {
  const html = marked.parse(text ?? '', { async: false }) as string
  return DOMPurify.sanitize(html)
}

async function openPanel() {
  open.value = true
  if (!config.value) {
    await loadConfig()
  }
  await loadUsage()
}

async function loadConfig() {
  try {
    config.value = await aiConfig()
  } catch {
    // 配置查询失败不阻塞浮窗使用（发送时会给出具体错误）
  }
}

async function loadSessions() {
  sessions.value = await aiSessions()
}

/** 载入某个历史会话的消息 */
async function loadSession(id: string) {
  sessionId.value = id
  localStorage.setItem(SESSION_KEY, id)
  const rows = await aiMessages(id)
  messages.value = rows.map((row) => ({ role: row.role as 'user' | 'assistant', content: row.content }))
  scrollToBottom()
}

/** 新建会话：清空当前消息与本地会话 ID（下一条提问会自动建会话） */
function newSession() {
  sessionId.value = ''
  localStorage.removeItem(SESSION_KEY)
  messages.value = []
  toolHint.value = ''
}

/** 删除会话（二次确认） */
async function removeSession(id: string) {
  await ElMessageBox.confirm('删除后该会话的历史记录不可恢复，确认删除？', '删除会话', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消'
  })
  await deleteAiSession(id)
  ElMessage.success('会话已删除')
  if (id === sessionId.value) {
    newSession()
  }
  await loadSessions()
}

async function toggleSidebar() {
  sidebarOpen.value = !sidebarOpen.value
  if (sidebarOpen.value) {
    await loadSessions()
  }
}

function applySuggestion(text: string) {
  question.value = text
  send()
}

function scrollToBottom() {
  nextTick(() => {
    if (listEl.value) {
      listEl.value.scrollTop = listEl.value.scrollHeight
    }
  })
}

/** 发送提问：流式渲染回答，工具调用期间展示过程提示 */
async function send() {
  const text = question.value.trim()
  if (!text || streaming.value) {
    return
  }
  if (overLimit.value) {
    ElMessage.warning(usage.value?.hint ?? '今日 AI 用量已达上限，明日 00:00 自动恢复')
    return
  }
  if (config.value && !config.value.configured) {
    ElMessage.warning(config.value.extras.hint ?? '尚未配置模型：请在【平台配置 → AI 模型配置】完成配置后保存')
    return
  }
  question.value = ''
  messages.value.push({ role: 'user', content: text })
  messages.value.push({ role: 'assistant', content: '', streaming: true })
  const answer = messages.value[messages.value.length - 1]
  streaming.value = true
  toolHint.value = ''
  awaitingFirstToken.value = true
  controller = new AbortController()
  scrollToBottom()

  try {
    await aiChatStream(
      text,
      sessionId.value || undefined,
      (event) => {
        switch (event.type) {
          case 'session':
            sessionId.value = event.sessionId
            localStorage.setItem(SESSION_KEY, event.sessionId)
            break
          case 'tool':
            toolHint.value = `${TOOL_LABELS[event.name] ?? '查询数据'}…`
            break
          case 'token':
            toolHint.value = ''
            awaitingFirstToken.value = false
            answer.content += event.text
            scrollToBottom()
            break
          case 'error':
            answer.content = answer.content || `⚠️ ${event.message}`
            ElMessage.error(event.message)
            break
          case 'done':
            toolHint.value = ''
            // 本次用量挂在回答下方；同时刷新今日用量条（服务端已落库，取回即最新）
            answer.usage = event.usage
            loadUsage()
            break
          default:
            break
        }
      },
      controller.signal
    )
  } catch (error) {
    if ((error as Error).name !== 'AbortError') {
      answer.content = answer.content || '⚠️ 请求失败，请稍后重试'
    }
  } finally {
    answer.streaming = false
    streaming.value = false
    toolHint.value = ''
    awaitingFirstToken.value = false
    controller = null
    if (sidebarOpen.value) {
      await loadSessions()
    }
  }
}

/** 停止生成（中断流式读取，已生成内容保留） */
function stop() {
  controller?.abort()
}

/** 拖拽面板（限制在视口内，避免拖出可视区域） */
function startDrag(event: MouseEvent) {
  dragging.value = true
  const startX = event.clientX
  const startY = event.clientY
  const originX = layout.x
  const originY = layout.y
  const onMove = (moveEvent: MouseEvent) => {
    layout.x = Math.min(Math.max(0, originX + moveEvent.clientX - startX), window.innerWidth - layout.w)
    layout.y = Math.min(Math.max(0, originY + moveEvent.clientY - startY), window.innerHeight - 48)
  }
  const onUp = () => {
    dragging.value = false
    window.removeEventListener('mousemove', onMove)
    window.removeEventListener('mouseup', onUp)
  }
  window.addEventListener('mousemove', onMove)
  window.addEventListener('mouseup', onUp)
}

/** 收起面板：悬浮球移到面板右下角，保持"同一个角落"的视觉锚点 */
function collapsePanel() {
  ballPos.x = Math.min(Math.max(0, layout.x + layout.w - BALL_SIZE), window.innerWidth - BALL_SIZE)
  ballPos.y = Math.min(Math.max(0, layout.y + layout.h - BALL_SIZE), window.innerHeight - BALL_SIZE)
  open.value = false
}

/** 缩放手柄的四个方向（模板 v-for 用） */
const RESIZE_DIRS = ['se', 'nw', 'ne', 'sw'] as const

/** 各方向的悬浮提示 */
const RESIZE_TITLE: Record<(typeof RESIZE_DIRS)[number], string> = {
  se: '拖动缩放（右下角）',
  nw: '拖动缩放（左上角）',
  ne: '拖动缩放（右上角）',
  sw: '拖动缩放（左下角）'
}

/** 缩放方向类型 */
type ResizeDir = (typeof RESIZE_DIRS)[number]

/** 面板最小尺寸（与原右下角单手柄时一致） */
const MIN_RESIZE_W = 360
const MIN_RESIZE_H = 320

/** 面板距视口边缘的最小留白 */
const VIEWPORT_GAP = 8

/**
 * 四角缩放：被拖的角跟随鼠标，对角保持锚定。
 * 拖西/北两角时同步移动面板原点（左/上缘跟着鼠标走、右/下缘不动），
 * 宽高天然不小于最小值，无需再对尺寸做二次钳制。
 */
function startResize(event: MouseEvent, dir: ResizeDir) {
  const startX = event.clientX
  const startY = event.clientY
  const origin = { x: layout.x, y: layout.y, w: layout.w, h: layout.h }
  const onMove = (moveEvent: MouseEvent) => {
    const dx = moveEvent.clientX - startX
    const dy = moveEvent.clientY - startY
    let { x, y, w, h } = origin
    if (dir.includes('e')) {
      // 右缘跟鼠标：不越过屏幕右界，也不小于最小宽
      w = Math.min(Math.max(MIN_RESIZE_W, origin.w + dx), window.innerWidth - origin.x - VIEWPORT_GAP)
    }
    if (dir.includes('s')) {
      h = Math.min(Math.max(MIN_RESIZE_H, origin.h + dy), window.innerHeight - origin.y - VIEWPORT_GAP)
    }
    if (dir.includes('w')) {
      // 左缘跟鼠标：不越过屏幕左界，且保证右缘不动时宽度不小于最小值
      x = Math.min(Math.max(0, origin.x + dx), origin.x + origin.w - MIN_RESIZE_W)
      w = origin.x + origin.w - x
    }
    if (dir.includes('n')) {
      y = Math.min(Math.max(0, origin.y + dy), origin.y + origin.h - MIN_RESIZE_H)
      h = origin.y + origin.h - y
    }
    layout.x = x
    layout.y = y
    layout.w = w
    layout.h = h
  }
  const onUp = () => {
    window.removeEventListener('mousemove', onMove)
    window.removeEventListener('mouseup', onUp)
  }
  window.addEventListener('mousemove', onMove)
  window.addEventListener('mouseup', onUp)
}

onMounted(() => {
  loadConfig()
  loadUsage()
  // 刷新后恢复上次会话的历史（FR6：刷新后会话保留）
  if (sessionId.value) {
    aiMessages(sessionId.value)
      .then((rows) => {
        messages.value = rows.map((row) => ({ role: row.role as 'user' | 'assistant', content: row.content }))
        scrollToBottom()
      })
      .catch(() => {
        sessionId.value = ''
        localStorage.removeItem(SESSION_KEY)
      })
  }
})
</script>

<style scoped>
.ai-ball {
  position: fixed;
  /* 位置由 ballStyle 的 left/top 决定（可拖拽，默认右下角） */
  z-index: 2000;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: linear-gradient(135deg, var(--q-color-primary), var(--q-color-primary-active));
  color: var(--q-text-inverse);
  cursor: pointer;
  box-shadow: var(--q-shadow-primary);
  transition: transform 0.15s;
}

.ai-ball:hover {
  transform: scale(1.06);
}

.ai-ball-text {
  font-size: 11px;
  line-height: 1;
}

.ai-panel {
  position: fixed;
  z-index: 2000;
  display: flex;
  flex-direction: column;
  background: var(--q-bg-card);
  border-radius: var(--q-radius-md);
  box-shadow: var(--q-shadow-float);
  overflow: hidden;
}

.ai-panel--dragging {
  user-select: none;
}

.ai-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--q-space-2) var(--q-space-3);
  background: var(--q-sidebar-bg-deep);
  color: var(--q-text-inverse);
  cursor: move;
}

.ai-title {
  display: flex;
  align-items: center;
  gap: var(--q-space-2);
  font-size: var(--q-font-base);
  font-weight: 600;
}

.ai-header-actions :deep(.el-button) {
  color: var(--q-border);
}

/* 今日用量条（V3.9）：一行文字 + 细进度条；三档配色见 --normal/--warn/--over */
.ai-usage {
  display: flex;
  flex: none;
  align-items: center;
  gap: var(--q-space-2);
  padding: 4px 10px;
  font-size: var(--q-font-xs);
  color: var(--q-text-secondary);
  background: var(--q-bg-subtle);
  border-bottom: 1px solid var(--q-border);
}

.ai-usage-text {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ai-usage-track {
  flex: none;
  width: 64px;
  height: 4px;
  border-radius: 2px;
  background: var(--q-border);
  overflow: hidden;
}

.ai-usage-fill {
  display: block;
  height: 100%;
  background: var(--q-color-primary);
}

.ai-usage--warn {
  color: var(--q-color-warn);
  background: var(--q-color-warn-soft);
}

.ai-usage--warn .ai-usage-fill {
  background: var(--q-color-warn);
}

.ai-usage--over {
  color: var(--q-color-up);
  background: var(--q-color-up-soft);
}

.ai-usage--over .ai-usage-fill {
  background: var(--q-color-up);
}

/* 单条回答的用量脚注 */
.ai-usage-note {
  margin-top: 6px;
  padding-top: 4px;
  border-top: 1px dashed var(--q-border);
  font-size: 11px;
  color: var(--q-text-muted);
}

.ai-body {
  display: flex;
  flex: 1;
  min-height: 0;
}

.ai-sidebar {
  width: 150px;
  flex: none;
  border-right: 1px solid var(--q-border);
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.ai-sidebar-head {
  padding: 8px 10px;
  font-size: var(--q-font-xs);
  color: var(--q-text-secondary);
  border-bottom: 1px solid var(--q-border);
}

.ai-session-list {
  flex: 1;
  margin: 0;
  padding: 4px 0;
  list-style: none;
  overflow-y: auto;
}

.ai-session-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--q-space-1);
  padding: 7px 10px;
  font-size: var(--q-font-xs);
  cursor: pointer;
}

.ai-session-item:hover,
.ai-session-item.is-active {
  background: var(--q-color-primary-soft);
}

.ai-session-title {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ai-session-del {
  color: var(--q-text-muted);
}

.ai-session-del:hover {
  color: var(--q-color-up);
}

.ai-session-empty {
  padding: 10px;
  font-size: var(--q-font-xs);
  color: var(--q-text-muted);
  text-align: center;
}

.ai-messages {
  flex: 1;
  min-width: 0;
  padding: 12px;
  overflow-y: auto;
  background: var(--q-bg-subtle);
}

.ai-welcome {
  font-size: var(--q-font-sm);
  color: var(--q-text-regular);
}

.ai-welcome ul {
  margin: 8px 0 12px;
  padding-left: 18px;
}

.ai-welcome li {
  margin: 4px 0;
  color: var(--q-color-primary);
  cursor: pointer;
}

.ai-message {
  display: flex;
  gap: var(--q-space-2);
  margin-bottom: var(--q-space-3);
}

.ai-message-role {
  flex: none;
  width: 24px;
  height: 24px;
  border-radius: var(--q-radius-sm);
  font-size: var(--q-font-xs);
  line-height: 24px;
  text-align: center;
  color: var(--q-text-inverse);
  background: var(--q-text-secondary);
}

.ai-message--assistant .ai-message-role {
  background: var(--q-color-primary);
}

.ai-message-content {
  flex: 1;
  min-width: 0;
  padding: 8px 10px;
  border-radius: var(--q-radius-sm);
  background: var(--q-bg-card);
  font-size: var(--q-font-sm);
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}

.ai-message--assistant .ai-message-content {
  white-space: normal;
}

.ai-markdown :deep(p) {
  margin: 0 0 6px;
}

.ai-markdown :deep(ul),
.ai-markdown :deep(ol) {
  margin: 4px 0;
  padding-left: 20px;
}

.ai-markdown :deep(table) {
  border-collapse: collapse;
  font-size: var(--q-font-xs);
}

.ai-markdown :deep(th),
.ai-markdown :deep(td) {
  border: 1px solid var(--q-border);
  padding: 3px 6px;
}

.ai-markdown :deep(code) {
  background: var(--q-bg-subtle);
  padding: 0 3px;
  border-radius: 3px;
}

.ai-cursor {
  color: var(--q-color-primary);
  animation: ai-blink 1s steps(2) infinite;
}

@keyframes ai-blink {
  50% {
    opacity: 0;
  }
}

.ai-tool-hint {
  display: flex;
  align-items: center;
  gap: var(--q-space-2);
  font-size: var(--q-font-xs);
  color: var(--q-text-secondary);
}

.ai-input {
  flex: none;
  padding: 8px 10px;
  border-top: 1px solid var(--q-border);
  background: var(--q-bg-card);
}

.ai-input-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 6px;
}

.ai-hint {
  font-size: var(--q-font-xs);
  color: var(--q-text-muted);
}

.ai-hint.is-over {
  color: var(--q-color-up);
}

.ai-disclaimer {
  margin-top: 6px;
  font-size: 11px;
  color: var(--q-text-muted);
  line-height: 1.5;
}

/* 四角缩放手柄：基类只定尺寸，位置与对角线光标由方向修饰类给出 */
.ai-resize {
  position: absolute;
  z-index: 10;
  width: 14px;
  height: 14px;
}

/* 每角画一个小三角提示可拖（斜向渐变指向面板外侧） */
.ai-resize--se {
  right: 0;
  bottom: 0;
  cursor: nwse-resize;
  background: linear-gradient(135deg, transparent 50%, var(--q-text-muted) 50%);
}

.ai-resize--nw {
  left: 0;
  top: 0;
  cursor: nwse-resize;
  background: linear-gradient(315deg, transparent 50%, var(--q-text-muted) 50%);
}

.ai-resize--ne {
  right: 0;
  top: 0;
  cursor: nesw-resize;
  background: linear-gradient(45deg, transparent 50%, var(--q-text-muted) 50%);
}

.ai-resize--sw {
  left: 0;
  bottom: 0;
  cursor: nesw-resize;
  background: linear-gradient(225deg, transparent 50%, var(--q-text-muted) 50%);
}
</style>
