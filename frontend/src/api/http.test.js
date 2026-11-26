import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AxiosError } from 'axios'
import { ElMessage } from 'element-plus'
import { createPinia, setActivePinia } from 'pinia'
import http, { RequestError } from './http.js'
import { useAuthStore } from '../stores/auth.js'

const originalAdapter = http.defaults.adapter
let messageSpy

function respond(body, status = 200) {
  http.defaults.adapter = async (config) => ({
    data: body,
    status,
    statusText: String(status),
    headers: {},
    config,
  })
}

function rejectHttp(body, status) {
  http.defaults.adapter = async (config) => {
    const response = { data: body, status, statusText: String(status), headers: {}, config }
    throw new AxiosError('internal transport detail', 'ERR_BAD_RESPONSE', config, null, response)
  }
}

beforeEach(() => {
  setActivePinia(createPinia())
  messageSpy = vi.spyOn(ElMessage, 'error').mockImplementation(() => ({ close() {} }))
})

afterEach(() => {
  http.defaults.adapter = originalAdapter
})

describe('Axios 唯一请求实例', () => {
  it('统一 /api、超时和默认非 2xx 拒绝规则，不注入 JWT', async () => {
    const adapter = vi.fn(async (config) => ({
      data: { code: 200, msg: 'success', data: null },
      status: 200,
      headers: {},
      config,
    }))
    http.defaults.adapter = adapter

    await http.get('/merchants')

    const [config] = adapter.mock.calls[0]
    expect(config.baseURL).toBe('/api')
    expect(config.timeout).toBe(10000)
    expect(config.headers.Authorization).toBeUndefined()
    expect(config.validateStatus(201)).toBe(true)
    expect(config.validateStatus(400)).toBe(false)
    expect(adapter).toHaveBeenCalledTimes(1)
  })

  it.each([200, 201, 202])('HTTP %i 且业务 code=200 返回原始 data', async (status) => {
    const data = { id: '9007199254740993', amount: '12.30' }
    respond({ code: 200, msg: 'success', data }, status)

    await expect(http.get('/merchants')).resolves.toBe(data)
    expect(messageSpy).not.toHaveBeenCalled()
  })

  it('成功 data=null 原样保留', async () => {
    respond({ code: 200, msg: 'success', data: null })

    await expect(http.delete('/merchants/1')).resolves.toBeNull()
    expect(messageSpy).not.toHaveBeenCalled()
  })

  it('2xx 业务失败拒绝并统一提示一次，data 内对象不加工', async () => {
    const data = { fieldErrors: { phone: ['格式错误'] }, requestId: 'request-1' }
    respond({ code: 40001, msg: '参数错误', data })

    const error = await http.get('/merchants').catch((reason) => reason)

    expect(error).toBeInstanceOf(RequestError)
    expect(error).toMatchObject({ code: 40001, message: '参数错误', status: 200 })
    expect(error.data).toBe(data)
    expect(messageSpy).toHaveBeenCalledExactlyOnceWith('参数错误')
  })

  it.each([null, {}, '<html>stack trace</html>', { code: '200', msg: 'success', data: {} },
    { code: 200, msg: 'success' }])('无效成功响应安全失败：%j', async (body) => {
    respond(body)

    await expect(http.get('/merchants')).rejects.toMatchObject({ message: '操作失败，请稍后重试' })
    expect(messageSpy).toHaveBeenCalledExactlyOnceWith('操作失败，请稍后重试')
  })

  it.each([400, 401, 403, 404, 409, 423])('HTTP %i 保留后端业务错误，无跳转或重试', async (status) => {
    const data = { requestId: 'request-2' }
    rejectHttp({ code: status * 100 + 1, msg: '需求规定的业务提示', data }, status)

    const error = await http.get('/merchants').catch((reason) => reason)

    expect(error).toMatchObject({ code: status * 100 + 1, status, message: '需求规定的业务提示' })
    expect(error.data).toBe(data)
    expect(messageSpy).toHaveBeenCalledExactlyOnceWith('需求规定的业务提示')
  })

  it('系统错误只显示安全提示，并保留 data 内追踪对象', async () => {
    const data = { requestId: 'request-3' }
    rejectHttp({ code: 50001, msg: 'SQL password stack trace', data }, 500)

    const error = await http.get('/merchants').catch((reason) => reason)

    expect(error.message).toBe('操作失败，请稍后重试')
    expect(error.data).toBe(data)
    expect(messageSpy).toHaveBeenCalledExactlyOnceWith('操作失败，请稍后重试')
  })

  it('非 2xx 的非法错误体不显示服务器 HTML 或内部异常', async () => {
    rejectHttp('<html>SQL internal exception</html>', 502)

    await expect(http.get('/merchants')).rejects.toMatchObject({
      message: '操作失败，请稍后重试', status: 502,
    })
    expect(messageSpy).toHaveBeenCalledExactlyOnceWith('操作失败，请稍后重试')
  })

  it('网络失败只请求一次并通过统一入口提示', async () => {
    const adapter = vi.fn(async () => { throw new AxiosError('private detail', 'ERR_NETWORK') })
    http.defaults.adapter = adapter

    await expect(http.get('/merchants')).rejects.toMatchObject({ message: '网络连接异常，请稍后重试' })
    expect(adapter).toHaveBeenCalledTimes(1)
    expect(messageSpy).toHaveBeenCalledExactlyOnceWith('网络连接异常，请稍后重试')
  })

  it.each(['ECONNABORTED', 'ETIMEDOUT'])('超时 %s 使用安全提示', async (code) => {
    http.defaults.adapter = async () => { throw new AxiosError('private detail', code) }

    await expect(http.get('/merchants')).rejects.toMatchObject({ message: '请求超时，请稍后重试' })
    expect(messageSpy).toHaveBeenCalledExactlyOnceWith('请求超时，请稍后重试')
  })

  it('未知异常不暴露原始 message 或堆栈', async () => {
    http.defaults.adapter = async () => { throw new Error('secret stack trace') }

    const error = await http.get('/merchants').catch((reason) => reason)

    expect(error.message).toBe('操作失败，请稍后重试')
    expect(error.stack).not.toContain('secret stack trace')
    expect(messageSpy).toHaveBeenCalledExactlyOnceWith('操作失败，请稍后重试')
  })
})

describe('Phase 3.1 参数错误公共契约兼容', () => {
  it('HTTP 400 的单字段字符串数组原样传递给调用方', async () => {
    const data = { fieldErrors: { phone: ['手机号格式不正确'] } }
    rejectHttp({ code: 40001, msg: '参数错误', data }, 400)

    const error = await http.post('/auth/register', {}).catch((reason) => reason)

    expect(error).toBeInstanceOf(RequestError)
    expect(error).toMatchObject({ code: 40001, message: '参数错误', status: 400 })
    expect(error.data).toBe(data)
    expect(error.data.fieldErrors.phone).toBe(data.fieldErrors.phone)
    expect(error.data.fieldErrors.phone).toEqual(['手机号格式不正确'])
    expect(messageSpy).toHaveBeenCalledExactlyOnceWith('参数错误')
  })

  it('多字段及同字段多消息不被响应拦截器覆盖、压平或丢弃', async () => {
    const data = {
      fieldErrors: {
        phone: ['手机号格式不正确'],
        nickname: ['昵称不能为空', '昵称长度不能超过30个字符'],
        newPassword: ['新密码长度必须为6到32位'],
      },
    }
    rejectHttp({ code: 40001, msg: '参数错误', data }, 400)

    const error = await http.patch('/users/me', {}).catch((reason) => reason)

    expect(error.data).toBe(data)
    expect(error.data.fieldErrors).toBe(data.fieldErrors)
    expect(error.data.fieldErrors.nickname).toEqual(['昵称不能为空', '昵称长度不能超过30个字符'])
    expect(Object.keys(error.data.fieldErrors)).toEqual(['phone', 'nickname', 'newPassword'])
    for (const messages of Object.values(error.data.fieldErrors)) {
      expect(Array.isArray(messages)).toBe(true)
      expect(messages.every((message) => typeof message === 'string')).toBe(true)
    }
    expect(error.data.fieldErrors).not.toHaveProperty('_global')
    expect(messageSpy).toHaveBeenCalledExactlyOnceWith('参数错误')
  })

  it('带 fieldErrors 的 40001 保留已登录会话和原有业务错误通道', async () => {
    const store = useAuthStore()
    store.saveLogin({
      token: 'field-errors-session', tokenType: 'Bearer', expiresAt: '2026-11-25T21:00:00+08:00',
      user: { id: '1', username: '13800138000', nickname: '用户', phone: '13800138000', role: 'CUSTOMER', status: 1 },
    })
    const data = { fieldErrors: { nickname: ['昵称不能为空'] } }
    rejectHttp({ code: 40001, msg: '参数错误', data }, 400)

    await expect(http.patch('/users/me', {})).rejects.toMatchObject({
      code: 40001, message: '参数错误', status: 400, data,
    })

    expect(store.token).toBe('field-errors-session')
    expect(store.isAuthenticated).toBe(true)
    expect(messageSpy).toHaveBeenCalledExactlyOnceWith('参数错误')
  })

  it('无 fieldErrors 的 40102 继续清空会话并透传 data=null', async () => {
    const store = useAuthStore()
    store.saveLogin({
      token: 'expired-session', tokenType: 'Bearer', expiresAt: '2026-11-25T21:00:00+08:00',
      user: { id: '1', username: '13800138000', nickname: '用户', phone: '13800138000', role: 'CUSTOMER', status: 1 },
    })
    rejectHttp({ code: 40102, msg: '认证失效', data: null }, 401)

    const error = await http.get('/users/me').catch((reason) => reason)

    expect(error).toMatchObject({ code: 40102, message: '认证失效', status: 401, data: null })
    expect(store.isAuthenticated).toBe(false)
    expect(store.token).toBeNull()
    expect(messageSpy).toHaveBeenCalledExactlyOnceWith('认证失效')
  })

  it('无法定位字段的参数错误继续返回 null，不合成 fieldErrors 或虚拟字段', async () => {
    rejectHttp({ code: 40001, msg: '参数错误', data: null }, 400)

    const error = await http.post('/auth/register', {}).catch((reason) => reason)

    expect(error).toMatchObject({ code: 40001, message: '参数错误', status: 400 })
    expect(error.data).toBeNull()
    expect(error).not.toHaveProperty('fieldErrors')
    expect(error).not.toHaveProperty('_global')
    expect(messageSpy).toHaveBeenCalledExactlyOnceWith('参数错误')
  })
})
