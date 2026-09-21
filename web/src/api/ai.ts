import { del, get, post, put } from './request'

/** AI 配置视图（模型名 / 是否已配置 Key） */
export interface AiConfigVO {
  /** AI 助手是否启用 */
  enabled: boolean
  /** 是否已配置 API Key */
  configured: boolean
  /** 当前模型名 */
  model: string
  /** 会话记忆窗口大小 */
  memoryWindow: number
  /** 附加提示（未配置原因等） */
  extras: Record<string, string>
}

/** AI 会话条目 */
export interface AiSessionVO {
  sessionId: string
  title: string
  updatedAt: string
}

/** AI 历史消息 */
export interface AiMessageVO {
  role: string
  content: string
  createdAt: string
}

/** 单次回答的用量（结束帧 done 带回，V3.9） */
export interface AiAnswerUsage {
  /** 上游请求轮数（工具调用会多轮） */
  rounds: number
  /** 输入 token（含系统提示词与记忆窗口） */
  promptTokens: number
  /** 输入中命中提示缓存的 token */
  cachedTokens: number
  /** 输出 token */
  completionTokens: number
  /** 总 token */
  totalTokens: number
  /** 是否按字符估算（上游未回 usage 时为 true，界面需标注「估算」） */
  estimated: boolean
  /** 本次费用（元；空串表示未算出，如记账失败） */
  cost: string
}

/** 流式对话的事件帧类型 */
export type AiStreamEvent =
  | { type: 'session'; sessionId: string; model: string }
  | { type: 'tool'; name: string; arguments: string }
  | { type: 'token'; text: string }
  | { type: 'error'; message: string }
  | { type: 'done'; sessionId: string; chars: string; usage: AiAnswerUsage | null }

/** AI 配置与状态 */
export function aiConfig() {
  return get<AiConfigVO>('/ai/config')
}

/** 会话列表 */
export function aiSessions() {
  return get<AiSessionVO[]>('/ai/sessions')
}

/** 会话历史消息 */
export function aiMessages(sessionId: string) {
  return get<AiMessageVO[]>(`/ai/sessions/${sessionId}/messages`)
}

/** 删除会话 */
export function deleteAiSession(sessionId: string) {
  return del<void>(`/ai/sessions/${sessionId}`)
}

/**
 * 非流式对话（降级路径）
 */
export function aiChatSync(question: string, sessionId?: string) {
  return post<string>('/ai/chat/sync', { question, sessionId })
}

/** 取字符串字段（字段缺失或类型不符时给安全默认值） */
function str(value: unknown, fallback = ''): string {
  return typeof value === 'string' ? value : fallback
}

/** 解析结束帧里的用量对象（事件帧里的值统一是字符串，故逐项转数字） */
function parseUsage(value: unknown): AiAnswerUsage | null {
  if (!value || typeof value !== 'object') {
    return null
  }
  const raw = value as Record<string, unknown>
  return {
    rounds: Number(str(raw.rounds, '0')),
    promptTokens: Number(str(raw.promptTokens, '0')),
    cachedTokens: Number(str(raw.cachedTokens, '0')),
    completionTokens: Number(str(raw.completionTokens, '0')),
    totalTokens: Number(str(raw.totalTokens, '0')),
    estimated: str(raw.estimated) === 'true',
    cost: str(raw.cost)
  }
}

/** SSE 事件名 → 解析后的帧对象（data 为 JSON，字段缺省时给出安全默认值） */
function parseFrame(event: string, data: string): AiStreamEvent | null {
  let payload: Record<string, unknown> = {}
  try {
    payload = JSON.parse(data) as Record<string, unknown>
  } catch {
    return null
  }
  switch (event) {
    case 'session':
      return { type: 'session', sessionId: str(payload.sessionId), model: str(payload.model) }
    case 'tool':
      return { type: 'tool', name: str(payload.name), arguments: str(payload.arguments) }
    case 'token':
      return { type: 'token', text: str(payload.text) }
    case 'error':
      return { type: 'error', message: str(payload.message, '未知错误') }
    case 'done':
      return {
        type: 'done',
        sessionId: str(payload.sessionId),
        chars: str(payload.chars, '0'),
        usage: parseUsage(payload.usage)
      }
    default:
      return null
  }
}

/**
 * 流式对话：用 fetch + ReadableStream 读取 SSE（浏览器 EventSource 不支持 POST 与自定义请求头）。
 *
 * @param question 用户提问
 * @param sessionId 会话 ID（为空则服务端新建，并通过 session 事件回传）
 * @param onEvent 每个事件帧的回调
 * @param signal 中断信号（用户点"停止"时取消）
 */
export async function aiChatStream(
  question: string,
  sessionId: string | undefined,
  onEvent: (event: AiStreamEvent) => void,
  signal?: AbortSignal
): Promise<void> {
  const token = localStorage.getItem('quant_token')
  const response = await fetch('/api/ai/chat', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json;charset=UTF-8',
      ...(token ? { satoken: token } : {})
    },
    body: JSON.stringify({ question, sessionId }),
    signal
  })

  // 预检失败（未配置 Key、参数非法等）会返回 JSON 而非事件流，此处转成 error 帧统一处理
  const contentType = response.headers.get('content-type') ?? ''
  if (!contentType.includes('text/event-stream')) {
    const body = await response.json().catch(() => null)
    onEvent({ type: 'error', message: body?.message ?? `请求失败（HTTP ${response.status}）` })
    return
  }

  const reader = response.body?.getReader()
  if (!reader) {
    onEvent({ type: 'error', message: '浏览器不支持流式读取' })
    return
  }
  const decoder = new TextDecoder('utf-8')
  let buffer = ''
  for (;;) {
    const { value, done } = await reader.read()
    if (done) {
      break
    }
    buffer += decoder.decode(value, { stream: true })
    // SSE 以空行分隔事件块
    const blocks = buffer.split('\n\n')
    buffer = blocks.pop() ?? ''
    for (const block of blocks) {
      let eventName = 'message'
      const dataLines: string[] = []
      for (const line of block.split('\n')) {
        if (line.startsWith('event:')) {
          eventName = line.slice(6).trim()
        } else if (line.startsWith('data:')) {
          dataLines.push(line.slice(5).trim())
        }
      }
      if (dataLines.length === 0) {
        continue
      }
      const frame = parseFrame(eventName, dataLines.join('\n'))
      if (frame) {
        onEvent(frame)
      }
    }
  }
}

/** AI 模型配置（厂商/端点/模型/Token；Token 只回传打码值，界面在平台配置页） */
/** 某个厂商的模型配置（V4.0：每个厂商一行，互不覆盖；Token 只回传打码值） */
export interface AiModelConfigVO {
  /** 厂商代码 */
  provider: string
  /** 厂商展示名 */
  providerName: string
  /** OpenAI 兼容端点 */
  baseUrl: string
  /** 模型名 */
  model: string
  /** 该厂商是否已配置 Token */
  hasKey: boolean
  /** Token 打码值（如 ****cdef），不回传明文 */
  keyMasked: string | null
  /** 该厂商是否已可对话 */
  configured: boolean
  /** 该厂商是否**当前启用**（对话走它） */
  active: boolean
  /** 输入单价（元/百万 token；0 = 不计算费用） */
  inputPrice: number | null
  /** 缓存命中输入单价（元/百万 token；0 = 按输入单价计） */
  cacheInputPrice: number | null
  /** 输出单价（元/百万 token；0 = 不计算费用） */
  outputPrice: number | null
  /** 该厂商配置的最近修改时间 */
  updatedAt: string | null
  /** 未配置时的指引文案 */
  hint: string | null
}

/** 可选厂商（含默认 Base URL、已知免费模型与"已配置/当前使用"状态） */
export interface AiProviderVO {
  /** 厂商代码 */
  code: string
  /** 厂商展示名 */
  name: string
  /** 默认 Base URL */
  defaultBaseUrl: string
  /** 已知免费模型清单 */
  freeModels: string[]
  /** 该厂商已保存的模型名（未配置为空串） */
  model: string
  /** 该厂商是否已可对话 */
  configured: boolean
  /** 该厂商是否当前启用 */
  active: boolean
}

/** 可选模型项 */
export interface AiModelOptionVO {
  /** 模型名 */
  id: string
  /** 是否属于内置的已知免费清单 */
  free: boolean
}

/**
 * 保存某个厂商配置的请求体。
 * 保存成功即把该厂商设为"当前启用厂商"——所以"选中厂商 + 保存"就是切换厂商；
 * apiKey 留空或回传打码值表示不修改该厂商已存的 Token。
 */
export interface AiModelConfigRequest {
  /** 厂商代码 */
  provider: string
  /** OpenAI 兼容端点 */
  baseUrl: string
  /** 模型名 */
  model: string
  /** API Token（留空 = 保持该厂商已存值） */
  apiKey: string
  /** 该厂商输入单价（元/百万 token） */
  inputPrice?: number | null
  /** 该厂商缓存命中输入单价（元/百万 token） */
  cacheInputPrice?: number | null
  /** 该厂商输出单价（元/百万 token） */
  outputPrice?: number | null
}

/**
 * 查询某厂商的 AI 模型配置（Token 打码）。
 * @param provider 厂商代码；不传 = 当前启用的厂商
 */
export function aiModelConfig(provider?: string) {
  return get<AiModelConfigVO>('/ai/model-config', provider ? { provider } : undefined)
}

/** 可选厂商清单（含各厂商"已配置/当前使用"状态） */
export function aiModelProviders() {
  return get<AiProviderVO[]>('/ai/model-config/providers')
}

/**
 * 拉取某厂商的模型列表（GET {baseUrl}/models）。
 * @param params baseUrl/apiKey/provider；未保存时可把输入框里的值直接传进来
 */
export function aiModelList(params: { baseUrl?: string; apiKey?: string; provider?: string }) {
  return get<AiModelOptionVO[]>('/ai/model-config/models', params)
}

/** 保存模型配置（保存即生效，无需重启） */
export function saveAiModelConfig(data: AiModelConfigRequest) {
  return put<void>('/ai/model-config', data)
}

/**
 * 连通性自检（可在保存前先测）。
 * @param data 与保存同构；baseUrl/apiKey/model 留空表示用库内当前配置
 */
export function testAiModelConfig(data: Partial<AiModelConfigRequest>) {
  return post<string>('/ai/model-config/test', data)
}

/** 今日 AI 用量概览（浮窗用量条与平台配置用量卡） */
export interface AiUsageTodayVO {
  /** 统计日期（yyyy-MM-dd） */
  date: string
  /** 今日咨询次数（含自检与护栏拒答） */
  requestCount: number
  /** 今日对话次数（真正计费的次数） */
  chatCount: number
  /** 今日总 token */
  totalTokens: number
  /** 今日输入 token */
  promptTokens: number
  /** 今日缓存命中 token */
  cachedTokens: number
  /** 今日输出 token */
  completionTokens: number
  /** 今日估算费用（元） */
  cost: number
  /** 今日是否含按字符估算的流水 */
  estimated: boolean
  /** 每日 token 上限（0 或 null = 不限制） */
  dailyTokenLimit: number | null
  /** 每日费用上限（元；0 或 null = 不限制） */
  dailyCostLimit: number | null
  /** 预警百分比（0 = 不预警） */
  warnPercent: number
  /** 已用比例（token 与费用取大者，可能大于 100） */
  usedPercent: number
  /** 是否已达预警线 */
  warn: boolean
  /** 是否已超限（超限后新提问会被拒绝） */
  overLimit: boolean
  /** 是否未填单价（费用恒为 0） */
  priceMissing: boolean
  /** 附加提示文案 */
  hint: string | null
}

/** 单日用量（近 N 日趋势） */
export interface AiUsageDayVO {
  /** 日期（yyyy-MM-dd） */
  date: string
  /** 当日咨询次数 */
  requestCount: number
  /** 当日总 token */
  totalTokens: number
  /** 当日估算费用（元） */
  cost: number
}

/** 用量明细行 */
export interface AiUsageLogVO {
  /** 流水 ID */
  id: number
  /** 会话 ID */
  sessionId: string | null
  /** 会话标题 */
  sessionTitle: string | null
  /** 用途代码（CHAT/HEALTH/GUARD） */
  biz: string
  /** 用途中文名 */
  bizName: string
  /** 厂商代码 */
  provider: string | null
  /** 厂商展示名 */
  providerName: string
  /** 模型名 */
  model: string | null
  /** 上游请求轮数（工具调用会多轮） */
  rounds: number
  /** 输入 token */
  promptTokens: number
  /** 缓存命中输入 token */
  cachedTokens: number
  /** 输出 token */
  completionTokens: number
  /** 总 token */
  totalTokens: number
  /** 是否按字符估算 */
  estimated: boolean
  /** 估算费用（元） */
  cost: number
  /** 提问字数 */
  questionChars: number
  /** 回答字数 */
  answerChars: number
  /** 耗时（毫秒） */
  durationMs: number | null
  /** 是否成功 */
  success: boolean
  /** 失败原因 */
  errorMsg: string | null
  /** 发生时间 */
  createdAt: string
}

/** 今日用量概览（额度超限时新提问会被拒绝，原因见 hint） */
export function aiUsageToday() {
  return get<AiUsageTodayVO>('/ai/usage/today')
}

/**
 * 近 N 日用量趋势（含今日，无数据的日期补 0）。
 * @param days 天数（1~90）
 */
export function aiUsageSummary(days = 7) {
  return get<AiUsageDayVO[]>('/ai/usage/summary', { days })
}

/**
 * 用量明细分页（按发生时间倒序）。
 * @param query date 为 yyyy-MM-dd（不传表示不限日期）
 */
export function aiUsageLogs(query: { page?: number; size?: number; date?: string }) {
  return get<{ total: number; records: AiUsageLogVO[] }>('/ai/usage/logs', query)
}

/** 每日额度（全局一份，与厂商无关；界面在【AI用量统计】页） */
export interface AiQuotaVO {
  /** 每日 token 上限（0 或 null = 不限制） */
  dailyTokenLimit: number | null
  /** 每日费用上限（元；0 或 null = 不限制） */
  dailyCostLimit: number | null
  /** 预警百分比（0 = 不预警） */
  warnPercent: number | null
}

/** 保存每日额度的请求体（字段为 null 表示保持原值，0 表示该维度不限制） */
export interface AiQuotaRequest {
  /** 每日 token 上限 */
  dailyTokenLimit?: number | null
  /** 每日费用上限（元） */
  dailyCostLimit?: number | null
  /** 预警百分比（0~100） */
  warnPercent?: number | null
}

/** 查询每日额度（全局一份） */
export function aiQuota() {
  return get<AiQuotaVO>('/ai/usage/quota')
}

/** 保存每日额度（全局一份，保存即生效） */
export function saveAiQuota(data: AiQuotaRequest) {
  return put<void>('/ai/usage/quota', data)
}

/**
 * 平台使用手册全文（Markdown 原文）。
 * 与 AI 助手的 getPlatformManual 工具**同源**（同一份随包发布的手册），所以页面与 AI 的说法一致。
 */
export function aiManual() {
  return get<string>('/ai/manual')
}
