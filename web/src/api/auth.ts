import { get, post, put } from './request'
import type { LoginResult, UserVO } from '@/types/api'

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
