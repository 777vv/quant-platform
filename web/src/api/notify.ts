import { get, post, put } from './request'

/** 邮件通知配置视图（V4.9 起入库可编辑；账号脱敏，授权码只回传打码值） */
export interface MailConfigVO {
  /** 通知总开关 */
  enabled: boolean
  /** SMTP 服务器地址 */
  host: string
  /** SMTP 端口 */
  port: number
  /** 发件账号（脱敏） */
  username: string
  /** 发件人地址 */
  from: string
  /** 实际收件人 */
  to: string | null
  /** SMTP 参数是否配置完整 */
  configured: boolean
}

/** 保存邮件配置的请求体（password 留空 = 不修改已存授权码） */
export interface MailConfigRequest {
  /** 通知总开关 */
  enabled?: boolean
  /** SMTP 服务器地址 */
  host?: string
  /** SMTP 端口 */
  port?: number
  /** SMTP 登录账号 */
  username?: string
  /** SMTP 授权码（留空/打码 = 保持原值） */
  password?: string
  /** 发件人地址 */
  fromAddr?: string
  /** 收件人地址 */
  toAddr?: string
}

/** 邮件配置视图 */
export function mailConfig() {
  return get<MailConfigVO>('/notify/mail/config')
}

/** 保存邮件配置（保存即生效，无需重启应用） */
export function saveMailConfig(data: MailConfigRequest) {
  return put<void>('/notify/mail/config', data)
}

/** 发送测试邮件（失败在界面提示原因） */
export function testMail() {
  return post<void>('/notify/mail/test')
}


/** 微信通知配置视图（V5.20；secret 不回传，configured=关键配置是否齐全） */
export interface WecomConfigVO {
  /** 微信通知总开关 */
  enabled: boolean
  /** 企业 ID（打码） */
  corpid: string
  /** 自建应用 AgentId */
  agentId: string
  /** 接收人（@all 或 userid 列表） */
  touser: string
  /** 关键配置是否齐全（corpid/agentId/secret） */
  configured: boolean
}

/** 保存微信通知配置的请求体（secret 留空 = 不修改已存 Secret） */
export interface WecomConfigRequest {
  /** 微信通知总开关 */
  enabled?: boolean
  /** 企业 ID */
  corpid?: string
  /** 自建应用 AgentId */
  agentId?: string
  /** 自建应用 Secret */
  secret?: string
  /** 接收人（@all 或 userid 列表） */
  touser?: string
}

/** 微信通知配置视图 */
export function wecomConfig() {
  return get<WecomConfigVO>('/notify/wecom/config')
}

/** 保存微信通知配置（保存即生效，无需重启） */
export function saveWecomConfig(data: WecomConfigRequest) {
  return put<WecomConfigVO>('/notify/wecom/config', data)
}

/** 发送微信测试消息（失败抛业务异常，界面提示原因） */
export function testWecom() {
  return post<void>('/notify/wecom/test')
}