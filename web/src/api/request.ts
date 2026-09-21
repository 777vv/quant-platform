import axios from 'axios'
import { ElMessage } from 'element-plus'
import type { R } from '@/types/api'

const TOKEN_KEY = 'quant_token'

const request = axios.create({
  baseURL: '/api',
  timeout: 60000
})

request.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY)
  if (token) {
    config.headers.satoken = token
  }
  return config
})

function toLogin() {
  localStorage.removeItem(TOKEN_KEY)
  if (!location.pathname.startsWith('/login')) {
    location.href = '/login'
  }
}

function showError(message: string, traceId?: string) {
  ElMessage.error(traceId ? `${message}（traceId: ${traceId}）` : message)
}

request.interceptors.response.use(
  (response) => {
    const res = response.data as R
    if (res.code === 401) {
      toLogin()
      return Promise.reject(new Error(res.message))
    }
    if (res.code !== 0) {
      showError(res.message || '请求失败', res.traceId)
      return Promise.reject(new Error(res.message))
    }
    return res.data as unknown as typeof response
  },
  (error) => {
    const data = error.response?.data as R | undefined
    if (error.response?.status === 401) {
      toLogin()
    } else {
      showError(data?.message || error.message || '网络异常', data?.traceId)
    }
    return Promise.reject(error)
  }
)

export function get<T>(url: string, params?: object): Promise<T> {
  return request.get(url, { params }) as Promise<T>
}

export function post<T>(url: string, data?: object): Promise<T> {
  return request.post(url, data) as Promise<T>
}

export function put<T>(url: string, data?: object): Promise<T> {
  return request.put(url, data) as Promise<T>
}

export function del<T>(url: string): Promise<T> {
  return request.delete(url) as Promise<T>
}

export default request
