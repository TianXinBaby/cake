import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { AxiosError } from 'axios'
import { ElMessage } from 'element-plus'
import http, { RequestError } from './http.js'
import { login, register, logout } from './auth.js'
import { getCurrentUser, updateNickname, updatePassword } from './users.js'
import { useAuthStore } from '../stores/auth.js'

const originalAdapter = http.defaults.adapter
const user = {
  id: '9007199254740993', username: '13800138000', nickname: '用户', phone: '13800138000',
  role: 'CUSTOMER', status: 1, createTime: '2026-11-25T19:00:00.000+08:00',
}
const session = {
  token: 'test-session-token', tokenType: 'Bearer',
  expiresAt: '2026-11-25T21:00:00.000+08:00', user,
}
let store
let messageSpy

function respond(data, status = 200) {
  const adapter = vi.fn(async (config) => ({
    data: { code: 200, msg: 'success', data }, status, headers: {}, config,
  }))
  http.defaults.adapter = adapter
  return adapter
}

function rejectWith(code, status, data = null) {
  const adapter = vi.fn(async (config) => {
    const response = {
      data: { code, msg: '需求规定的错误提示', data }, status, headers: {}, config,
    }
    throw new AxiosError('private transport detail', 'ERR_BAD_RESPONSE', config, null, response)
  })
  http.defaults.adapter = adapter
  return adapter
}

function requestFrom(adapter) {
  const [config] = adapter.mock.calls[0]
  return { config, body: config.data === undefined ? undefined : JSON.parse(config.data) }
}

beforeEach(() => {
  setActivePinia(createPinia())
  store = useAuthStore()
  messageSpy = vi.spyOn(ElMessage, 'error').mockImplementation(() => ({ close() {} }))
})

afterEach(() => { http.defaults.adapter = originalAdapter })

describe('六个认证与本人资料 API', () => {
  it('登录请求不发送旧 Bearer 或角色，成功保存服务端登录结果', async () => {
    store.saveLogin({ ...session, token: 'previous-session-token' })
    const adapter = respond(session)

    await expect(login({ username: user.username, password: 'test-password', role: 'ADMIN' }))
      .resolves.toBe(session)

    const { config, body } = requestFrom(adapter)
    expect(config).toMatchObject({ method: 'post', url: '/auth/login' })
    expect(config.headers.Authorization).toBeUndefined()
    expect(body).toEqual({ username: user.username, password: 'test-password' })
    expect(store.token).toBe(session.token)
    expect(store.user.role).toBe('CUSTOMER')
  })

  it('注册 HTTP 201 返回 User，白名单无提权字段且不自动登录', async () => {
    const adapter = respond(user, 201)

    await expect(register({
      phone: user.phone, nickname: user.nickname, password: 'test-password',
      role: 'ADMIN', status: 0, merchantId: '99', tokenVersion: 1,
    })).resolves.toBe(user)

    const { config, body } = requestFrom(adapter)
    expect(config).toMatchObject({ method: 'post', url: '/auth/register' })
    expect(config.headers.Authorization).toBeUndefined()
    expect(body).toEqual({ phone: user.phone, nickname: user.nickname, password: 'test-password' })
    expect(store.isAuthenticated).toBe(false)
  })

  it.each(['ADMIN', 'MERCHANT', 'CUSTOMER'])('读取 %s 本人资料，无用户 ID 参数并刷新当前资料', async (role) => {
    store.saveLogin({ ...session, user: { ...user, role, merchantId: '12' } })
    const refreshed = { ...store.user, nickname: '服务端当前昵称' }
    const adapter = respond(refreshed)

    await expect(getCurrentUser('other-user-id')).resolves.toBe(refreshed)

    const { config, body } = requestFrom(adapter)
    expect(config).toMatchObject({ method: 'get', url: '/users/me' })
    expect(config.params).toBeUndefined()
    expect(body).toBeUndefined()
    expect(config.headers.Authorization).toBe(`Bearer ${session.token}`)
    expect(store.user.nickname).toBe('服务端当前昵称')
  })

  it('昵称更新仅发送 nickname 并保存服务端返回资料', async () => {
    store.saveLogin(session)
    const updated = { ...user, nickname: '新昵称' }
    const adapter = respond(updated)

    await expect(updateNickname('新昵称')).resolves.toBe(updated)

    const { config, body } = requestFrom(adapter)
    expect(config).toMatchObject({ method: 'patch', url: '/users/me' })
    expect(body).toEqual({ nickname: '新昵称' })
    expect(store.user.nickname).toBe('新昵称')
    expect(store.token).toBe(session.token)
  })

  it('退出无请求体并携带 Bearer，成功清理全部本地认证状态', async () => {
    store.saveLogin(session)
    const adapter = respond(null)

    await expect(logout()).resolves.toBeNull()

    const { config, body } = requestFrom(adapter)
    expect(config).toMatchObject({ method: 'post', url: '/auth/logout' })
    expect(body).toBeUndefined()
    expect(config.headers.Authorization).toBe(`Bearer ${session.token}`)
    expect(store).toMatchObject({ token: null, tokenType: null, expiresAt: null, user: null, isAuthenticated: false })
  })

  it('改密只发送原/新密码，成功清理状态且不保存新密码或签发新令牌', async () => {
    store.saveLogin(session)
    const adapter = respond(null)

    await expect(updatePassword({ oldPassword: 'test-old', newPassword: 'test-new', role: 'ADMIN' }))
      .resolves.toBeNull()

    const { config, body } = requestFrom(adapter)
    expect(config).toMatchObject({ method: 'patch', url: '/users/me/password' })
    expect(config.headers.Authorization).toBe(`Bearer ${session.token}`)
    expect(body).toEqual({ oldPassword: 'test-old', newPassword: 'test-new' })
    expect(store).toMatchObject({ token: null, tokenType: null, expiresAt: null, user: null, isAuthenticated: false })
  })

  it('改密原密码错误 40101 保留当前会话与既有统一错误行为', async () => {
    store.saveLogin(session)
    const adapter = rejectWith(40101, 401)

    await expect(updatePassword({ oldPassword: 'test-wrong', newPassword: 'test-new' }))
      .rejects.toMatchObject({ code: 40101, status: 401, message: '需求规定的错误提示' })

    expect(store.isAuthenticated).toBe(true)
    expect(adapter).toHaveBeenCalledTimes(1)
    expect(messageSpy).toHaveBeenCalledExactlyOnceWith('需求规定的错误提示')
  })

  it('普通网络退出失败保留登录态，不误报服务端退出成功', async () => {
    store.saveLogin(session)
    http.defaults.adapter = async () => { throw new AxiosError('private detail', 'ERR_NETWORK') }

    await expect(logout()).rejects.toMatchObject({ message: '网络连接异常，请稍后重试' })
    expect(store.token).toBe(session.token)
  })
})

describe('Axios 登录失效统一处理', () => {
  it.each(['business', 'http'])('%s 响应的 40102 清理 Pinia，保留业务码和 data 并只提示一次', async (kind) => {
    store.saveLogin(session)
    const data = { requestId: 'auth-request' }
    if (kind === 'http') rejectWith(40102, 401, data)
    else http.defaults.adapter = async (config) => ({
      data: { code: 40102, msg: '需求规定的错误提示', data }, status: 200, headers: {}, config,
    })

    const error = await getCurrentUser().catch((reason) => reason)

    expect(error).toBeInstanceOf(RequestError)
    expect(error).toMatchObject({ code: 40102, data, message: '需求规定的错误提示' })
    expect(store.isAuthenticated).toBe(false)
    expect(messageSpy).toHaveBeenCalledExactlyOnceWith('需求规定的错误提示')
  })

  it('退出收到 HTTP 401 即清理登录态，保持需求原有退出语义', async () => {
    store.saveLogin(session)
    rejectWith(40101, 401)

    await expect(logout()).rejects.toMatchObject({ code: 40101, status: 401 })
    expect(store.isAuthenticated).toBe(false)
  })

  it('未登录的保护请求不伪造 Token，收到 40102 仍通过既有错误通道', async () => {
    const adapter = rejectWith(40102, 401)

    await expect(getCurrentUser()).rejects.toMatchObject({ code: 40102 })
    expect(requestFrom(adapter).config.headers.Authorization).toBeUndefined()
    expect(store.isAuthenticated).toBe(false)
  })

  it('延迟返回的旧会话 40102 不清除后来登录的会话', async () => {
    store.saveLogin(session)
    let rejectOld
    http.defaults.adapter = (config) => new Promise((resolve, reject) => {
      rejectOld = () => reject(new AxiosError('private detail', 'ERR_BAD_RESPONSE', config, null, {
        data: { code: 40102, msg: '旧会话失效', data: null }, status: 401, headers: {}, config,
      }))
    })
    const pending = getCurrentUser()
    await vi.waitFor(() => expect(rejectOld).toBeTypeOf('function'))
    store.saveLogin({ ...session, token: 'new-session-token' })
    rejectOld()

    await expect(pending).rejects.toMatchObject({ code: 40102 })
    expect(store.token).toBe('new-session-token')
    expect(store.isAuthenticated).toBe(true)
  })

  it.each(['getCurrentUser', 'logout', 'updatePassword'])('旧会话 %s 的成功响应不覆盖或清除新登录状态', async (method) => {
    store.saveLogin(session)
    let resolveOld
    http.defaults.adapter = (config) => new Promise((resolve) => {
      resolveOld = () => resolve({
        data: { code: 200, msg: 'success', data: method === 'getCurrentUser' ? user : null },
        status: 200, headers: {}, config,
      })
    })
    const pending = method === 'getCurrentUser' ? getCurrentUser()
      : method === 'logout' ? logout() : updatePassword({ oldPassword: 'test-old', newPassword: 'test-new' })
    await vi.waitFor(() => expect(resolveOld).toBeTypeOf('function'))
    store.saveLogin({ ...session, token: 'new-session-token', user: { ...user, nickname: '新会话昵称' } })
    resolveOld()

    await pending
    expect(store.token).toBe('new-session-token')
    expect(store.user.nickname).toBe('新会话昵称')
  })

  it('认证失败不会向 console 输出 JWT、密码或运输异常', async () => {
    const log = vi.spyOn(console, 'log').mockImplementation(() => {})
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {})
    const error = vi.spyOn(console, 'error').mockImplementation(() => {})
    rejectWith(40101, 401)

    await expect(login({ username: user.username, password: 'test-password' })).rejects.toMatchObject({ code: 40101 })
    expect(log).not.toHaveBeenCalled()
    expect(warn).not.toHaveBeenCalled()
    expect(error).not.toHaveBeenCalled()
    expect(store.isAuthenticated).toBe(false)
  })
})
