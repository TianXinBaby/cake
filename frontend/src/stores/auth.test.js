import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useAuthStore } from './auth.js'

const user = {
  id: '9007199254740993', username: '13800138000', nickname: '用户',
  phone: '13800138000', role: 'CUSTOMER', status: 1,
  createTime: '2026-11-25T19:00:00.000+08:00',
}
const session = {
  token: 'test-session-token', tokenType: 'Bearer',
  expiresAt: '2026-11-25T21:00:00.000+08:00', user,
}

beforeEach(() => setActivePinia(createPinia()))

describe('Pinia 认证状态', () => {
  it('初始状态没有令牌或用户，未认证', () => {
    expect(useAuthStore()).toMatchObject({
      token: null, tokenType: null, expiresAt: null, user: null, isAuthenticated: false,
    })
  })

  it.each(['ADMIN', 'MERCHANT', 'CUSTOMER'])('仅保存 %s 的公开资料和服务端令牌字段', (role) => {
    const store = useAuthStore()
    store.saveLogin({
      ...session,
      user: { ...user, role, merchantId: '9007199254740995', password: 'not-stored', tokenVersion: 1 },
    })

    expect(store).toMatchObject({
      token: session.token, tokenType: 'Bearer', expiresAt: session.expiresAt, isAuthenticated: true,
    })
    expect(store.user.id).toBe('9007199254740993')
    expect(store.user.role).toBe(role)
    expect(store.user).not.toHaveProperty('password')
    expect(store.user).not.toHaveProperty('tokenVersion')
    if (role === 'MERCHANT') expect(store.user.merchantId).toBe('9007199254740995')
    else expect(store.user).not.toHaveProperty('merchantId')
  })

  it('用户和商家绑定只读，修改原响应对象不能改变身份展示', () => {
    const store = useAuthStore()
    const responseUser = { ...user, role: 'MERCHANT', merchantId: '12' }
    store.saveLogin({ ...session, user: responseUser })
    responseUser.role = 'ADMIN'
    responseUser.merchantId = '13'

    expect(() => { store.user.role = 'ADMIN' }).toThrow(TypeError)
    expect(() => { store.user.merchantId = '13' }).toThrow(TypeError)
    expect(store.user).toMatchObject({ role: 'MERCHANT', merchantId: '12' })
  })

  it('资料刷新只替换公开用户信息，清理会复位所有认证字段', () => {
    const store = useAuthStore()
    store.saveLogin(session)
    store.setCurrentUser({ ...user, nickname: '新昵称' })

    expect(store.user.nickname).toBe('新昵称')
    expect(store.token).toBe(session.token)
    store.clearAuthentication()
    expect(store).toMatchObject({
      token: null, tokenType: null, expiresAt: null, user: null, isAuthenticated: false,
    })
  })

  it('令牌仅在当前 Pinia 内存中，无浏览器存储写入或刷新恢复', () => {
    const localWrite = vi.spyOn(Storage.prototype, 'setItem')
    const store = useAuthStore()
    store.saveLogin(session)

    expect(localWrite).not.toHaveBeenCalled()
    expect(useAuthStore(createPinia()).isAuthenticated).toBe(false)
    localWrite.mockRestore()
  })
})
