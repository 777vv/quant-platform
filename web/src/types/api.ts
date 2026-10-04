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
  /** 角色：ADMIN=管理员 GUEST=临时账号（V5.58） */
  role?: string
  /** 是否管理员（菜单与按钮显隐判定） */
  admin?: boolean
  /** 权限码清单（管理员为全量；V5.58） */
  permissions?: string[]
}

export interface LoginResult {
  token: string
  user: UserVO
}
