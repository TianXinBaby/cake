import http from './http.js'
import { useAuthStore } from '../stores/auth.js'

export async function login({ username, password }) {
  const store = useAuthStore()
  const data = await http.post('/auth/login', { username, password }, { skipAuthentication: true })
  store.saveLogin(data)
  return data
}

export function register({ phone, nickname, password }) {
  // 注册不自动登录；请求白名单也不提供选择角色或商家绑定的入口。
  return http.post('/auth/register', { phone, nickname, password }, { skipAuthentication: true })
}

export async function logout() {
  const store = useAuthStore()
  const currentToken = store.token
  const data = await http.post('/auth/logout')
  if (store.token === currentToken) store.clearAuthentication()
  return data
}
