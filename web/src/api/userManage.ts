import { get, post, put, del } from './request'

/** 权限码字典项（勾选面板一条选项；后端 PermissionCodes 单一来源下发） */
export interface PermissionOption {
  /** 权限码（如 menu:funds / action:sync） */
  code: string
  /** 中文名 */
  label: string
  /** 分组：menu=可见菜单 / action=允许操作 */
  group: string
  /** 分组中文名 */
  groupLabel: string
}

/** 用户管理列表行（不含密码） */
export interface UserManageItem {
  id: number
  username: string
  nickname: string
  role: 'ADMIN' | 'GUEST'
  permissions: string[]
  enabled: number
  expiresAt: string | null
  remark: string | null
  lastLoginAt: string | null
  createdAt: string
}

/** 创建临时账号请求（角色固定 GUEST） */
export interface UserCreateRequest {
  username: string
  nickname: string
  password: string
  permissions: string[]
  /** 有效期至（yyyy-MM-dd，当日 23:59:59 失效；不传=永久） */
  expiresOn?: string
  remark?: string
}

/** 编辑临时账号请求（用户名与角色不可改；permissions=null 表示不改权限） */
export interface UserUpdateRequest {
  nickname?: string
  permissions?: string[]
  expiresOn?: string
  remark?: string
}

/** 全部账号列表 */
export function listUsers() {
  return get<UserManageItem[]>('/user/manage/list')
}

/** 权限码字典 */
export function permissionOptions() {
  return get<PermissionOption[]>('/user/manage/permission-options')
}

/** 「一键只读访客」默认模板的权限码 */
export function guestDefault() {
  return get<string[]>('/user/manage/guest-default')
}

/** 创建临时账号 */
export function createUser(data: UserCreateRequest) {
  return post<UserManageItem>('/user/manage', data)
}

/** 编辑临时账号（权限/昵称/有效期/备注，改完即时生效） */
export function updateUser(id: number, data: UserUpdateRequest) {
  return put<UserManageItem>(`/user/manage/${id}`, data)
}

/** 启用/停用（停用立即踢下线） */
export function updateUserStatus(id: number, enabled: number) {
  return put<void>(`/user/manage/${id}/status`, { enabled })
}

/** 重置密码（重置后该账号全端下线） */
export function resetUserPassword(id: number, password: string) {
  return put<void>(`/user/manage/${id}/password`, { password })
}

/** 删除账号 */
export function deleteUser(id: number) {
  return del<void>(`/user/manage/${id}`)
}
