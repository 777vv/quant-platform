export interface R<T = unknown> {
  code: number
  message: string
  data: T
  traceId?: string
}

export interface PageResult<T> {
  total: number
  records: T[]
}

export interface UserVO {
  id: number
  username: string
  nickname: string
  email: string
  lastLoginAt?: string
}

export interface LoginResult {
  token: string
  user: UserVO
}
