import { ref } from 'vue'
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

  function clear() {
    token.value = ''
    userInfo.value = null
    localStorage.removeItem(TOKEN_KEY)
  }

  return { token, userInfo, login, logout, fetchUser, clear }
})
