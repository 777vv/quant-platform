import { get, post } from './request'

/** 邮件通知配置只读视图（FR4 平台配置页卡片，账号脱敏） */
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

/** 邮件配置只读视图 */
export function mailConfig() {
  return get<MailConfigVO>('/notify/mail/config')
}

/** 发送测试邮件（失败在界面提示原因） */
export function testMail() {
  return post<void>('/notify/mail/test')
}
