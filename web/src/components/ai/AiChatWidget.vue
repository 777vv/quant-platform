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
      <AiAvatar :size="30" animated class="ai-ball-logo" />
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
          <span class="ai-title-badge"><AiAvatar :size="17" /></span>
          <span>AI 投资助手</span>
        </div>
        <div class="ai-header-actions" @mousedown.stop>
          <!-- 历史会话：再点一次收起侧栏；侧栏展开时按钮保持高亮（可感知当前状态） -->
          <el-button
            link
            size="small"
            :class="{ 'is-on': sidebarOpen }"
            :title="sidebarOpen ? '收起历史会话' : '历史会话'"
            @click="toggleSidebar"
          >
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
            <!-- 首屏问候：头像 + 一句话定位，避免"只有一个空输入框"的冷启动观感 -->
            <div class="ai-welcome-hero">
              <span class="ai-welcome-avatar"><AiAvatar :size="26" animated /></span>
              <div>
                <div class="ai-welcome-title">你好，我是 AI 投资助手</div>
                <div class="ai-welcome-sub">只给买卖建议、不做自动交易；可以从下面这些问题开始</div>
              </div>
            </div>
            <div class="ai-welcome-chips">
              <button
                v-for="tip in SUGGESTIONS"
                :key="tip"
                type="button"
                class="ai-chip"
                @click="applySuggestion(tip)"
              >
                {{ tip }}
              </button>
            </div>
            <el-alert
              v-if="config && !config.configured"
              type="warning"
              :closable="false"
              show-icon
              :title="config.extras.hint || '尚未配置AI模型'"
            />
          </div>

          <div v-for="(message, index) in messages" :key="index" class="ai-message" :class="`ai-message--${message.role}`">
            <div class="ai-message-role">
              <AiAvatar v-if="message.role === 'assistant'" :size="15" />
              <template v-else>我</template>
            </div>
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
          @focus="onInputFocus"
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
import { Clock, Delete, Loading, Minus, Plus } from '@element-plus/icons-vue'
import AiAvatar from './AiAvatar.vue'
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
import { useUserStore } from '@/stores/user'
import { PERM } from '@/utils/permissions'

const userStore = useUserStore()

/** 首屏引导问题 */
const SUGGESTIONS = [
  '我的持仓现在怎么样？',
  '现在平台配置了哪些策略？',
  '510300 现在什么价位？',
  '最近有什么买卖信号？',
  '沪深300 估值贵不贵？'
]

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

/**
 * 会话 ID 本地留存键：**按账号分键**（V6.21 会话隔离）。
 * 同一浏览器换账号登录时各读各的键，不会把上一个账号的会话带进新账号。
 */
const sessionKey = computed(() => `quant_ai_session::${userStore.userInfo?.username ?? 'anonymous'}`)

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
const sessionId = ref<string>('')
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
  // V5.60：用量接口需要「AI用量统计」菜单权限，无权限就不发起请求（拦截器会对 403 弹错误提示）
  if (!userStore.can(PERM.MENU_AI_USAGE)) {
    return
  }
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

/**
 * 读回"本账号"上次的会话 ID（刷新后继续同一会话）。
 * 按需读取而不是 setup 顶层读一次：userInfo 由路由守卫拉取，可能在组件挂载之后才就绪。
 * 兼容升级前的全局键 `quant_ai_session`（V6.21 前不分账号）：仅管理员继承一次并清掉旧键，
 * 避免升级后"当前会话突然没了"；其他账号不继承，防止串会话。
 */
function restoreSession() {
  if (sessionId.value) {
    return
  }
  const stored = localStorage.getItem(sessionKey.value)
  const legacyKey = 'quant_ai_session'
  const legacy = userStore.userInfo?.username === 'admin' ? localStorage.getItem(legacyKey) : null
  sessionId.value = stored ?? legacy ?? ''
  if (!stored && legacy) {
    localStorage.setItem(sessionKey.value, legacy)
    localStorage.removeItem(legacyKey)
  }
}

async function openPanel() {
  open.value = true
  restoreSession()
  if (!config.value) {
    await loadConfig()
  }
  await loadUsage()
  await restoreMessages()
}

/** 恢复上次会话的消息（无本地会话 ID 时不动） */
async function restoreMessages() {
  if (!sessionId.value || messages.value.length > 0) {
    return
  }
  try {
    const rows = await aiMessages(sessionId.value)
    messages.value = rows.map((row) => ({ role: row.role as 'user' | 'assistant', content: row.content }))
    scrollToBottom()
  } catch {
    // 会话不存在或不属于当前账号（服务端已按账号隔离）：丢弃本地 ID，回到新会话状态
    sessionId.value = ''
    localStorage.removeItem(sessionKey.value)
  }
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
  localStorage.setItem(sessionKey.value, id)
  const rows = await aiMessages(id)
  messages.value = rows.map((row) => ({ role: row.role as 'user' | 'assistant', content: row.content }))
  scrollToBottom()
}

/** 新建会话：清空当前消息与本地会话 ID（下一条提问会自动建会话） */
function newSession() {
  sessionId.value = ''
  localStorage.removeItem(sessionKey.value)
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

/**
 * 历史会话开关：展开时拉一次列表，**再点一次即收起**（按钮同步给出高亮态）。
 */
async function toggleSidebar() {
  sidebarOpen.value = !sidebarOpen.value
  if (sidebarOpen.value) {
    await loadSessions()
  }
}

/** 输入框获得焦点：自动收起历史会话侧栏，把宽度让给对话（用户要求） */
function onInputFocus() {
  sidebarOpen.value = false
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
            localStorage.setItem(sessionKey.value, event.sessionId)
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
  // 刷新后恢复本账号上次会话的历史（FR6：刷新后会话保留；V6.21 起按账号取键）
  restoreSession()
  restoreMessages()
})
</script>

<style scoped>
.ai-ball {
  position: fixed;
  /* 位置由 ballStyle 的 left/top 决定（可拖拽，默认右下角） */
  z-index: 2000;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: var(--q-ai-ball-bg);
  color: var(--q-text-inverse);
  cursor: pointer;
  box-shadow: var(--q-shadow-primary), var(--q-ai-ball-inner-ring);
  transition: transform 0.18s ease, box-shadow 0.18s ease;
}

/* 球面高光：左上柔光叠在渐变之上，做出玻璃球的立体感 */
.ai-ball::before {
  content: '';
  position: absolute;
  inset: 0;
  border-radius: 50%;
  background: var(--q-ai-ball-sheen);
  pointer-events: none;
}

/* 呼吸光环：向外扩散的两圈脉冲，提示"助手在线"（纯 box-shadow，不占布局） */
.ai-ball::after {
  content: '';
  position: absolute;
  inset: 0;
  border-radius: 50%;
  box-shadow: 0 0 0 0 var(--q-ai-ball-halo);
  animation: ai-ball-pulse 2.6s ease-out infinite;
  pointer-events: none;
}

@keyframes ai-ball-pulse {
  0% {
    box-shadow: 0 0 0 0 var(--q-ai-ball-halo);
  }

  70%,
  100% {
    box-shadow: 0 0 0 14px var(--q-ai-ball-halo-fade);
  }
}

.ai-ball:hover {
  transform: scale(1.06);
  box-shadow: var(--q-ai-ball-shadow-hover), var(--q-ai-ball-inner-ring);
}

/* 图标压在高光/光环之上（两者都是定位元素，不给 z-index 会盖住内容） */
.ai-ball-logo {
  position: relative;
  z-index: 1;
}

/* 尊重系统"减弱动态效果"设置：关掉呼吸光环 */
@media (prefers-reduced-motion: reduce) {
  .ai-ball::after {
    animation: none;
  }
}

.ai-panel {
  position: fixed;
  z-index: 2000;
  display: flex;
  flex-direction: column;
  background: var(--q-bg-card);
  border: 1px solid var(--q-border);
  border-radius: var(--q-radius-lg);
  box-shadow: var(--q-shadow-float);
  overflow: hidden;
}

.ai-panel--dragging {
  user-select: none;
}

/* 标题栏：深色侧栏色 → 品牌深蓝的渐变，底部一条半透明分隔线 */
.ai-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--q-space-2) var(--q-space-3);
  background: var(--q-ai-head-bg);
  border-bottom: 1px solid var(--q-ai-head-line);
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

/* 头像角标：深底上给头像一个半透明圆形底座，避免线稿"飘"在渐变上 */
.ai-title-badge {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  border-radius: 50%;
  background: var(--q-ai-head-badge);
}

.ai-header-actions :deep(.el-button) {
  color: var(--q-sidebar-text);
}

.ai-header-actions :deep(.el-button:hover) {
  color: var(--q-text-inverse);
  background: var(--q-ai-head-badge);
}

/* 历史会话展开中：按钮保持高亮，明确"再点一次是收起" */
.ai-header-actions :deep(.el-button.is-on) {
  color: var(--q-text-inverse);
  background: var(--q-ai-head-badge);
}

/* 今日用量条（V3.9）：一行文字 + 细进度条；三档配色见 --normal/--warn/--over */
.ai-usage {
  display: flex;
  flex: none;
  align-items: center;
  gap: var(--q-space-2);
  padding: var(--q-space-1) var(--q-space-3);
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
  margin-top: var(--q-space-1);
  padding-top: var(--q-space-1);
  border-top: 1px dashed var(--q-border);
  font-size: var(--q-font-xs);
  color: var(--q-text-muted);
}

.ai-body {
  display: flex;
  flex: 1;
  min-height: 0;
}

.ai-sidebar {
  width: 152px;
  flex: none;
  border-right: 1px solid var(--q-border);
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.ai-sidebar-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--q-space-2) var(--q-space-3);
  font-size: var(--q-font-xs);
  font-weight: 600;
  color: var(--q-text-secondary);
  border-bottom: 1px solid var(--q-border-light);
}

.ai-session-list {
  flex: 1;
  margin: 0;
  padding: var(--q-space-1) 0;
  list-style: none;
  overflow-y: auto;
}

.ai-session-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--q-space-1);
  padding: var(--q-space-2) var(--q-space-3);
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
  padding: var(--q-space-3);
  font-size: var(--q-font-xs);
  color: var(--q-text-muted);
  text-align: center;
}

.ai-messages {
  flex: 1;
  min-width: 0;
  padding: var(--q-space-3);
  overflow-y: auto;
  background: var(--q-bg-subtle);
}

.ai-welcome {
  font-size: var(--q-font-sm);
  color: var(--q-text-regular);
}

/* 首屏问候：圆形头像底座 + 标题 + 一句定位说明 */
.ai-welcome-hero {
  display: flex;
  align-items: center;
  gap: var(--q-space-3);
  padding: var(--q-space-3);
  border: 1px solid var(--q-border-light);
  border-radius: var(--q-radius-md);
  background: var(--q-bg-card);
}

.ai-welcome-avatar {
  display: flex;
  flex: none;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  border-radius: 50%;
  color: var(--q-text-inverse);
  background: var(--q-ai-ball-bg);
  box-shadow: var(--q-ai-ball-inner-ring);
}

.ai-welcome-title {
  font-size: var(--q-font-base);
  font-weight: 600;
  color: var(--q-text-primary);
}

.ai-welcome-sub {
  margin-top: 2px;
  font-size: var(--q-font-xs);
  color: var(--q-text-muted);
}

/* 建议问题：药丸按钮（可悬停、可键盘聚焦），比纯文字链接更"可点" */
.ai-welcome-chips {
  display: flex;
  flex-wrap: wrap;
  gap: var(--q-space-2);
  margin: var(--q-space-3) 0;
}

.ai-chip {
  padding: var(--q-space-1) var(--q-space-3);
  border: 1px solid var(--q-border);
  border-radius: var(--q-radius-lg);
  background: var(--q-bg-card);
  color: var(--q-text-regular);
  font-family: inherit;
  font-size: var(--q-font-xs);
  line-height: 1.7;
  cursor: pointer;
  transition: color 0.16s ease, border-color 0.16s ease, background 0.16s ease, transform 0.16s ease;
}

.ai-chip:hover {
  color: var(--q-color-primary);
  border-color: var(--q-color-primary-border);
  background: var(--q-color-primary-soft);
  transform: translateY(-1px);
}

.ai-message {
  display: flex;
  gap: var(--q-space-2);
  margin-bottom: var(--q-space-3);
}

/* 我的角标：中性灰底圆片 */
.ai-message-role {
  display: flex;
  flex: none;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  font-size: var(--q-font-xs);
  color: var(--q-text-secondary);
  background: var(--q-bg-hover);
}

/* 助手角标：与浮球同款渐变球 + 白色头像，强化"始终是同一个助手" */
.ai-message--assistant .ai-message-role {
  color: var(--q-text-inverse);
  background: var(--q-ai-ball-bg);
  box-shadow: var(--q-ai-ball-inner-ring);
}

.ai-message-content {
  flex: 1;
  min-width: 0;
  padding: var(--q-space-2) var(--q-space-3);
  border: 1px solid var(--q-border-light);
  border-radius: var(--q-radius-md);
  /* 靠近角标的一角收小，气泡"朝向"说话人 */
  border-top-left-radius: var(--q-radius-sm);
  background: var(--q-bg-card);
  font-size: var(--q-font-sm);
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}

/* 我的消息：品牌浅底 + 品牌描边，与助手的白底气泡一眼分开 */
.ai-message--user .ai-message-content {
  background: var(--q-color-primary-soft);
  border-color: var(--q-color-primary-border);
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
  padding: var(--q-space-3);
  border-top: 1px solid var(--q-border-light);
  background: var(--q-bg-card);
}

/* 聚焦时给输入框一圈品牌柔光，明确"现在可以打字" */
.ai-input :deep(.el-textarea__inner:focus) {
  border-color: var(--q-color-primary);
  box-shadow: 0 0 0 3px var(--q-color-primary-soft);
}

.ai-input-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: var(--q-space-2);
}

.ai-hint {
  font-size: var(--q-font-xs);
  color: var(--q-text-muted);
}

.ai-hint.is-over {
  color: var(--q-color-up);
}

.ai-disclaimer {
  margin-top: var(--q-space-1);
  font-size: var(--q-font-xs);
  color: var(--q-text-muted);
  line-height: 1.5;
}

/* 细滚动条：默认滑块偏粗偏亮，换成令牌灰的窄条，长列表也不刺眼 */
.ai-messages,
.ai-session-list {
  scrollbar-width: thin;
  scrollbar-color: var(--q-border) transparent;
}

.ai-messages::-webkit-scrollbar,
.ai-session-list::-webkit-scrollbar {
  width: 8px;
  height: 8px;
}

.ai-messages::-webkit-scrollbar-thumb,
.ai-session-list::-webkit-scrollbar-thumb {
  border-radius: var(--q-radius-sm);
  background: var(--q-border);
}

.ai-messages::-webkit-scrollbar-track,
.ai-session-list::-webkit-scrollbar-track {
  background: transparent;
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
