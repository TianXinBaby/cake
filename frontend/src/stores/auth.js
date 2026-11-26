import { defineStore } from 'pinia'

// Phase 0 未约定浏览器持久化方案。令牌仅保存在内存，刷新页面后重新登录；
// 不保存密码，不从 localStorage/sessionStorage 恢复可被修改的角色或商家绑定。
function publicUser(user) {
  const { id, username, nickname, phone, role, status, createTime } = user
  return Object.freeze({
    id, username, nickname, phone, role, status, createTime,
    ...(role === 'MERCHANT' ? { merchantId: user.merchantId } : {}),
  })
}

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: null,
    tokenType: null,
    expiresAt: null,
    user: null,
  }),
  getters: {
    // 仅表示当前客户端已有登录状态，不能代替服务器每次请求的鉴权。
    isAuthenticated: (state) => Boolean(state.token && state.tokenType === 'Bearer' && state.user),
  },
  actions: {
    saveLogin({ token, tokenType, expiresAt, user }) {
      this.token = token
      this.tokenType = tokenType
      this.expiresAt = expiresAt
      this.user = publicUser(user)
    },
    setCurrentUser(user) {
      // 资料只能来自本人接口的响应；没有前端修改 role/merchantId 的独立方法。
      this.user = publicUser(user)
    },
    clearAuthentication() {
      this.$reset()
    },
  },
})
