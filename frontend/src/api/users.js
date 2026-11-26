import http from './http.js'
import { useAuthStore } from '../stores/auth.js'

export async function getCurrentUser() {
  const store = useAuthStore()
  const currentToken = store.token
  const user = await http.get('/users/me')
  if (store.token === currentToken) store.setCurrentUser(user)
  return user
}

export async function updateNickname(nickname) {
  const store = useAuthStore()
  const currentToken = store.token
  const user = await http.patch('/users/me', { nickname })
  if (store.token === currentToken) store.setCurrentUser(user)
  return user
}

export async function updatePassword({ oldPassword, newPassword }) {
  const store = useAuthStore()
  const currentToken = store.token
  const data = await http.patch('/users/me/password', { oldPassword, newPassword })
  // 服务端同时更新密码和令牌版本；完成后必须重新登录，不保留旧 JWT。
  if (store.token === currentToken) store.clearAuthentication()
  return data
}
