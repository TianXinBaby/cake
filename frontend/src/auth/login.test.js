import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import { createMemoryHistory } from 'vue-router'
import { ElMessage } from 'element-plus'
import { AxiosError } from 'axios'
import { createApplication } from '../application.js'
import { useAuthStore } from '../stores/auth.js'
import http from '../api/http.js'

const originalAdapter = http.defaults.adapter
const admin = { id: '1', username: '13800138000', nickname: '管理员', phone: '13800138000', role: 'ADMIN', status: 1 }
const merchant = { id: '2', username: '13800138001', nickname: '店主', phone: '13800138001', role: 'MERCHANT', status: 1, merchantId: '5' }
const customer = { id: '3', username: '13800138002', nickname: '顾客', phone: '13800138002', role: 'CUSTOMER', status: 1 }

let app, router, store, requests, server, role, errorMessage

function ok(config, data, status = 200) {
  return { data: JSON.stringify({ code: 200, msg: 'success', data }), status, headers: {}, config }
}
function fail(config, code, status, data = null, msg = '参数错误') {
  throw new AxiosError('private internal detail', 'ERR_BAD_RESPONSE', config, null, {
    data: { code, msg, data }, status, headers: {}, config,
  })
}
function session() {
  return { token: `${role.toLowerCase()}-token`, tokenType: 'Bearer', expiresAt: '2026-11-28T18:00:00.000+08:00', user: role === 'ADMIN' ? admin : role === 'MERCHANT' ? merchant : customer }
}
function defaultServer(config) {
  if (config.url === '/auth/login') return ok(config, session())
  if (config.url === '/users/me') return ok(config, role === 'ADMIN' ? admin : role === 'MERCHANT' ? merchant : customer)
  if (config.url === '/admin/merchants') return ok(config, { records: [], total: 0, page: 1, size: 10 })
  if (config.url === '/merchant/products') return ok(config, { records: [], total: 0, page: 1, size: 10 })
  if (config.url === '/merchant/categories') return ok(config, [])
  return ok(config, null)
}
async function setup(path = '/login', { authenticated = false } = {}) {
  const application = createApplication({ history: createMemoryHistory() })
  app = application.app
  router = application.router
  store = useAuthStore(application.pinia)
  if (authenticated) store.saveLogin(session())
  await router.isReady()
  await router.push(path)
  document.body.innerHTML = '<div id="app"></div>'
  app.mount('#app')
  await nextTick()
}
async function ready(text) { await vi.waitFor(() => expect(document.body.textContent).toContain(text)) }
async function input(selector, value) {
  const field = document.querySelector(selector)
  expect(field, selector).toBeTruthy()
  field.value = value
  field.dispatchEvent(new Event('input', { bubbles: true }))
  await nextTick()
}
async function submit() {
  document.querySelector('.login-form').dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }))
  await nextTick()
}

beforeEach(() => {
  requests = []
  role = 'ADMIN'
  server = defaultServer
  errorMessage = vi.spyOn(ElMessage, 'error').mockImplementation(() => ({ close() {} }))
  vi.spyOn(ElMessage, 'success').mockImplementation(() => ({ close() {} }))
  http.defaults.adapter = async (config) => { requests.push(config); return server(config) }
})
afterEach(() => {
  app?.unmount()
  app = undefined
  document.body.innerHTML = ''
  http.defaults.adapter = originalAdapter
})

describe('UI-01 登录页', () => {
  it('显示手机号、密码与登录按钮，不提供角色选择', async () => {
    await setup()
    expect(document.querySelector('#login-username').getAttribute('type')).toBe('tel')
    expect(document.querySelector('#login-password').getAttribute('type')).toBe('password')
    expect(document.querySelector('select')).toBeNull()
    expect(document.body.textContent).toContain('登录后由账号角色决定可访问的页面')
    expect(document.querySelector('.login-submit').disabled).toBe(false)
  })

  it('空表单本地阻止请求并就地提示', async () => {
    await setup()
    await submit()
    expect(document.body.textContent).toContain('请输入注册手机号')
    expect(document.body.textContent).toContain('请输入密码')
    expect(requests.filter((config) => config.url === '/auth/login')).toHaveLength(0)
  })

  it('管理员登录后进入平台商家管理，密码不进入状态', async () => {
    await setup()
    await input('#login-username', '13800138000')
    await input('#login-password', 'test-pass')
    await submit()
    await vi.waitFor(() => expect(router.currentRoute.value.path).toBe('/admin/merchants'))
    const request = requests.find((config) => config.url === '/auth/login')
    expect(JSON.parse(request.data)).toEqual({ username: '13800138000', password: 'test-pass' })
    expect(store.isAuthenticated).toBe(true)
    expect(store.user.role).toBe('ADMIN')
    expect(JSON.stringify(store.user)).not.toContain('test-pass')
    expect(ElMessage.success).toHaveBeenCalledWith('登录成功')
  })

  it('商家登录后进入本店商品管理', async () => {
    role = 'MERCHANT'
    await setup()
    await input('#login-username', '13800138001')
    await input('#login-password', 'test-pass')
    await submit()
    await vi.waitFor(() => expect(router.currentRoute.value.path).toBe('/merchant/products'))
    expect(store.user.role).toBe('MERCHANT')
  })

  it('顾客登录后回公开入口并提示后续页面', async () => {
    role = 'CUSTOMER'
    await setup()
    await input('#login-username', '13800138002')
    await input('#login-password', 'test-pass')
    await submit()
    await vi.waitFor(() => expect(router.currentRoute.value.path).toBe('/'))
    expect(router.currentRoute.value.query.notice).toBe('customer-home')
    expect(document.body.textContent).toContain('顾客选店与下单页面将在后续阶段开放')
    expect(store.user.role).toBe('CUSTOMER')
  })

  it('账号密码错误时保留登录页并显示统一提示', async () => {
    server = (config) => config.url === '/auth/login'
      ? fail(config, 40101, 401, null, '账号或密码错误') : defaultServer(config)
    await setup()
    await input('#login-username', '13800138000')
    await input('#login-password', 'wrong-pass')
    await submit()
    await ready('账号或密码错误')
    expect(router.currentRoute.value.path).toBe('/login')
    expect(store.isAuthenticated).toBe(false)
    expect(document.querySelector('#login-password').value).toBe('wrong-pass')
  })

  it.each([
    [40301, 403, '账号已被禁用，请联系管理员'],
    [42301, 423, '账号已被临时锁定，请稍后重试'],
  ])('登录错误码 %s 显示服务器提示', async (code, status, text) => {
    server = (config) => config.url === '/auth/login'
      ? fail(config, code, status, null, text) : defaultServer(config)
    await setup()
    await input('#login-username', '13800138000')
    await input('#login-password', 'test-pass')
    await submit()
    await ready(text)
    expect(store.isAuthenticated).toBe(false)
  })

  it('提交中禁用按钮并只发送一次请求', async () => {
    let finish
    server = (config) => config.url === '/auth/login'
      ? new Promise((resolve) => { finish = () => resolve(ok(config, session())) })
      : defaultServer(config)
    await setup()
    await input('#login-username', '13800138000')
    await input('#login-password', 'test-pass')
    await submit()
    await vi.waitFor(() => expect(finish).toBeTypeOf('function'))
    expect(document.querySelector('.login-submit').disabled).toBe(true)
    await submit()
    expect(requests.filter((config) => config.url === '/auth/login')).toHaveLength(1)
    finish()
    await vi.waitFor(() => expect(router.currentRoute.value.path).toBe('/admin/merchants'))
  })

  it('已登录访问登录页按角色跳转，不重复登录', async () => {
    await setup('/login', { authenticated: true })
    await vi.waitFor(() => expect(router.currentRoute.value.path).toBe('/admin/merchants'))
    expect(requests.filter((config) => config.url === '/auth/login')).toHaveLength(0)
  })

  it('登录后回到被拦截的商家分类页，非法外部重定向被忽略', async () => {
    role = 'MERCHANT'
    await setup('/login?redirect=/merchant/categories')
    await input('#login-username', '13800138001')
    await input('#login-password', 'test-pass')
    await submit()
    await vi.waitFor(() => expect(router.currentRoute.value.path).toBe('/merchant/categories'))

    app.unmount()
    document.body.innerHTML = ''
    role = 'MERCHANT'
    await setup('/login?redirect=//evil.example')
    await input('#login-username', '13800138001')
    await input('#login-password', 'test-pass')
    await submit()
    await vi.waitFor(() => expect(router.currentRoute.value.path).toBe('/merchant/products'))
  })
})
