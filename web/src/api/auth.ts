import { get, post, put } from './request'
import type { LoginResult, PageResult, UserVO } from '@/types/api'

/** 登录日志行（V5.46） */
export interface LoginLogItem {
  /** 主键 */
  id: number
  /** 登录用户名 */
  username: string
  /** 是否登录成功 */
  success: boolean
  /** 失败原因（成功时为 null） */
  failReason: string | null
  /** 客户端 IP（已按反向代理链取真实来源） */
  ip: string | null
  /** IP 归属地（内网/本机等） */
  ipLocation: string | null
  /** 客户端 UA */
  userAgent: string | null
  /** 登录时间 */
  createdAt: string
}

/**
 * 登录日志分页查询（新→旧）
 *
 * @param username 用户名关键字（模糊，可空）
 * @param success  结果筛选：1=只看成功 0=只看失败，不传=全部
 */
export function loginLogs(params: { username?: string; success?: number; page: number; size: number }) {
  return get<PageResult<LoginLogItem>>('/auth/login-logs', params)
}

export function login(data: { username: string; password: string }) {
  return post<LoginResult>('/auth/login', data)
}

export function logout() {
  return post<void>('/auth/logout')
}

export function me() {
  return get<UserVO>('/auth/me')
}

export function updatePassword(data: { oldPassword: string; newPassword: string; confirmPassword: string }) {
  return put<void>('/auth/password', data)
}

export function getProfile() {
  return get<UserVO>('/user/profile')
}

export function updateProfile(data: { nickname: string; email: string }) {
  return put<void>('/user/profile', data)
}
