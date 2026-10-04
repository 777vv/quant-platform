import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { login as loginApi, logout as logoutApi, me } from '@/api/auth'
import type { UserVO } from '@/types/api'

const TOKEN_KEY = 'quant_token'

export const useUserStore = defineStore('user', () => {
  const token = ref<string>(localStorage.getItem(TOKEN_KEY) || '')
  const userInfo = ref<UserVO | null>(null)

  async function login(username: string, password: string) {
    const result = await loginApi({ username, password })
    token.value = result.token
    userInfo.value = result.user
    localStorage.setItem(TOKEN_KEY, result.token)
  }

  async function logout() {
    try {
      await logoutApi()
    } finally {
      clear()
    }
  }

  async function fetchUser() {
    userInfo.value = await me()
  }

  /** 是否管理员（V5.58）：管理员不看权限码清单，恒为全量 */
  const isAdmin = computed(() => userInfo.value?.admin === true)

  /**
   * 是否持有某权限码（V5.58）：管理员恒真；临时账号看所分配清单。
   * userInfo 未加载（刷新后首跳）时保守返回 false，由路由守卫先拉资料再放行。
   */
  function can(code: string): boolean {
    if (isAdmin.value) {
      return true
    }
    return userInfo.value?.permissions?.includes(code) ?? false
  }

  function clear() {
    token.value = ''
    userInfo.value = null
    localStorage.removeItem(TOKEN_KEY)
  }

  return { token, userInfo, login, logout, fetchUser, clear, isAdmin, can }
})
