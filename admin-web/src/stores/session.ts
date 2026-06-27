import { defineStore } from 'pinia'

const TOKEN_KEY = 'reelshort.admin.token'
const ADMIN_NAME_KEY = 'reelshort.admin.name'

export const useSessionStore = defineStore('session', {
  state: () => ({
    token: localStorage.getItem(TOKEN_KEY) ?? '',
    adminName: localStorage.getItem(ADMIN_NAME_KEY) ?? '',
  }),
  getters: {
    isAuthenticated: (state) => Boolean(state.token),
  },
  actions: {
    setSession(adminName: string, token: string) {
      this.adminName = adminName
      this.token = token
      localStorage.setItem(ADMIN_NAME_KEY, adminName)
      localStorage.setItem(TOKEN_KEY, token)
    },
    clearSession() {
      this.adminName = ''
      this.token = ''
      localStorage.removeItem(ADMIN_NAME_KEY)
      localStorage.removeItem(TOKEN_KEY)
    },
  },
})
