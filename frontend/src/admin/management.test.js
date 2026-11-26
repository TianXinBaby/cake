import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import { createMemoryHistory } from 'vue-router'
import { ElMessage } from 'element-plus'
import { AxiosError } from 'axios'
import { createApplication } from '../application.js'
import { useAuthStore } from '../stores/auth.js'
import http from '../api/http.js'

const originalAdapter = http.defaults.adapter
const id = '18446744073709551615'
const accountId = '9007199254740993'
const admin = { id: '1', username: '13800138000', nickname: '管理员', phone: '13800138000', role: 'ADMIN', status: 1 }
const merchant = { id, userId: accountId, username: '13800138001', name: '测试店铺', phone: '13800138002', address: '测试地址', description: '店铺简介', status: 1 }
const customer = { id, username: '13800138003', nickname: '测试顾客', phone: '13800138003', role: 'CUSTOMER', status: 1, createTime: '2026-11-26T09:00:00+08:00' }
let app, router, store, requests, currentUser, server, errorMessage

function ok(config, data, status = 200) {
  return { data: JSON.stringify({ code: 200, msg: 'success', data }), status, headers: {}, config }
}
function fail(config, code, status, data = null, msg = '参数错误') {
  throw new AxiosError('private internal detail', 'ERR_BAD_RESPONSE', config, null, {
    data: { code, msg, data }, status, headers: {}, config,
  })
}
function defaultServer(config) {
  if (config.url === '/users/me') return ok(config, currentUser)
  if (config.method === 'get') return ok(config, { records: config.url === '/admin/users' ? [customer] : [merchant], total: 21, page: config.params.page, size: config.params.size })
  return ok(config, config.url.startsWith('/admin/users') ? customer : merchant, config.method === 'post' ? 201 : 200)
}
async function setup({ role = 'ADMIN', authenticated = true, path = '/admin/merchants' } = {}) {
  const application = createApplication({ history: createMemoryHistory() })
  app = application.app
  router = application.router
  store = useAuthStore(application.pinia)
  if (authenticated) store.saveLogin({ token: 'test-admin-session', tokenType: 'Bearer', user: { ...admin, role } })
  await router.isReady()
  await router.push(path)
  document.body.innerHTML = '<div id="app"></div>'
  app.mount('#app')
  await nextTick()
}
function button(text, scope = document) {
  const found = Array.from(scope.querySelectorAll('button')).find((item) => item.textContent.trim() === text)
  expect(found, `button: ${text}`).toBeTruthy()
  return found
}
async function click(text, scope = document) { button(text, scope).click(); await nextTick() }
async function input(selector, value, event = 'input') {
  const field = document.querySelector(selector)
  expect(field, selector).toBeTruthy()
  field.value = value
  field.dispatchEvent(new Event(event, { bubbles: true }))
  await nextTick()
}
async function submit(selector) {
  document.querySelector(selector).dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }))
  await nextTick()
}
async function ready(text = merchant.name) { await vi.waitFor(() => expect(document.body.textContent).toContain(text)) }
async function fillMerchant() {
  for (const [name, value] of Object.entries({ accountPhone: '13800138004', nickname: ' 商家昵称 ', password: 'test-pass', name: ' 新店铺 ', phone: '13800138005', address: ' 新地址 ', description: ' 新简介 ' })) await input(`#merchant-${name}`, value)
}

beforeEach(() => {
  requests = []
  currentUser = admin
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

describe('平台管理员路由守卫', () => {
  it('未登录回技术根提示，不发送后台请求，不提供令牌或认证页面', async () => {
    await setup({ authenticated: false })
    expect(router.currentRoute.value.path).toBe('/')
    expect(document.body.textContent).toContain('请先使用管理员账号登录')
    expect(requests).toHaveLength(0)
    expect(document.querySelector('input')).toBeNull()
  })

  it.each(['MERCHANT', 'CUSTOMER'])('内存伪造 ADMIN，服务器当前 %s 仍无权限访问', async (role) => {
    currentUser = { ...customer, role }
    await setup()
    expect(router.currentRoute.value.path).toBe('/')
    expect(document.body.textContent).toContain('无权限访问平台管理')
    expect(requests.map((config) => config.url)).toEqual(['/users/me'])
    expect(store.user.role).toBe(role)
  })

  it('内存旧角色不替代服务器当前角色，管理员可访问且导航重新鉴权', async () => {
    await setup({ role: 'CUSTOMER' })
    await ready()
    expect(router.currentRoute.value.path).toBe('/admin/merchants')
    await router.push('/admin/users')
    await ready(customer.nickname)
    expect(requests.filter((config) => config.url === '/users/me')).toHaveLength(2)
    expect(Array.from(document.querySelectorAll('aside a')).map((link) => link.getAttribute('href'))).toEqual(['/admin/merchants', '/admin/users'])
  })

  it('服务器禁用 ADMIN 不进入后台', async () => {
    currentUser = { ...admin, status: 0 }
    await setup()
    expect(router.currentRoute.value.path).toBe('/')
    expect(requests).toHaveLength(1)
  })

  it('鉴权401清理当前会话并回根提示，403显示权限提示', async () => {
    server = (config) => fail(config, 40102, 401, null, '认证失效')
    await setup()
    expect(store.isAuthenticated).toBe(false)
    expect(document.body.textContent).toContain('请先使用管理员账号登录')
    expect(errorMessage).toHaveBeenCalledExactlyOnceWith('认证失效')
  })

  it('鉴权403保留会话并显示无权限，未请求管理数据', async () => {
    server = (config) => fail(config, 40302, 403, null, '无权限')
    await setup()
    expect(store.isAuthenticated).toBe(true)
    expect(document.body.textContent).toContain('无权限访问平台管理')
    expect(requests).toHaveLength(1)
  })
})

describe('UI-14 商家管理', () => {
  it('加载状态、空状态及错误重试，绑定账号与店铺电话区分，输入按纯文本展示', async () => {
    let resolvePage
    server = (config) => config.url === '/users/me' ? defaultServer(config) : new Promise((resolve) => { resolvePage = (records) => resolve(ok(config, { records, total: records.length, page: 1, size: 10 })) })
    await setup()
    await vi.waitFor(() => expect(resolvePage).toBeTypeOf('function'))
    expect(document.body.textContent).toContain('正在加载店铺')
    expect(button('查询中…').disabled).toBe(true)
    resolvePage([{ ...merchant, name: '<img src=x onerror=alert(1)>', description: '<script>alert(1)</script>' }])
    await ready('<img src=x onerror=alert(1)>')
    expect(document.querySelector('.admin-table img, .admin-table script')).toBeNull()
    expect(document.body.textContent).toContain(merchant.username)
    expect(document.body.textContent).toContain(merchant.phone)
    expect(document.body.textContent).toContain(id)
    expect(document.body.textContent).toContain(accountId)
    server = (config) => fail(config, 50001, 500, null, 'private SQL detail')
    await submit('.admin-filters')
    await ready('操作失败，请稍后重试')
    expect(document.body.textContent).not.toContain('private SQL detail')
    server = (config) => ok(config, { records: [], total: 0, page: 1, size: 10 })
    await click('重新加载')
    await ready('暂无符合条件的店铺')
  })

  it('必填校验阻止创建请求；后端fieldErrors全部消息就地映射，失败保留输入可重试', async () => {
    await setup(); await ready(); await click('创建商家'); await submit('.merchant-dialog form')
    expect(document.body.textContent).toContain('店铺名称不能为空')
    expect(requests.filter((config) => config.method === 'post')).toHaveLength(0)
    await fillMerchant()
    server = (config) => config.method === 'post'
      ? fail(config, 40001, 400, { fieldErrors: { accountPhone: ['账号手机号重复', '请使用其他号码'], name: ['店名错误'], _global: ['不显示虚拟字段'] } }) : defaultServer(config)
    await submit('.merchant-dialog form')
    await ready('账号手机号重复')
    expect(document.body.textContent).toContain('请使用其他号码')
    expect(document.body.textContent).toContain('店名错误')
    expect(document.body.textContent).not.toContain('不显示虚拟字段')
    expect(document.querySelector('#merchant-password').value).toBe('test-pass')
    server = (config) => config.method === 'post' ? fail(config, 50001, 500) : defaultServer(config)
    await submit('.merchant-dialog form')
    await ready('操作失败，请稍后重试')
    expect(document.querySelector('#merchant-name').value).toBe(' 新店铺 ')
    server = defaultServer
    await submit('.merchant-dialog form')
    await vi.waitFor(() => expect(document.querySelector('.merchant-dialog')).toBeNull())
    expect(ElMessage.success).toHaveBeenCalledWith('商家账号和店铺创建成功')
    const body = JSON.parse(requests.find((config) => config.method === 'post').data)
    expect(body).toEqual({ accountPhone: '13800138004', nickname: '商家昵称', password: 'test-pass', name: '新店铺', phone: '13800138005', address: '新地址', description: '新简介' })
  })

  it('创建进行中禁止重复提交，取消后重新打开不保留初始密码', async () => {
    await setup(); await ready(); await click('创建商家'); await fillMerchant()
    let finish
    server = (config) => config.method === 'post' ? new Promise((resolve) => { finish = () => resolve(ok(config, merchant, 201)) }) : defaultServer(config)
    await submit('.merchant-dialog form')
    await vi.waitFor(() => expect(finish).toBeTypeOf('function'))
    await submit('.merchant-dialog form')
    expect(requests.filter((config) => config.method === 'post')).toHaveLength(1)
    expect(button('正在保存…').disabled).toBe(true)
    finish()
    await vi.waitFor(() => expect(document.querySelector('.merchant-dialog')).toBeNull())
    await click('创建商家'); await input('#merchant-password', 'new-secret'); await click('取消'); await click('创建商家')
    expect(document.querySelector('#merchant-password').value).toBe('')
  })

  it('编辑回显仅店铺字段，保留绑定及大ID路径；保存后重新查询', async () => {
    await setup(); await ready(); await click('编辑')
    expect(document.querySelector('#merchant-name').value).toBe(merchant.name)
    expect(document.querySelector('#merchant-accountPhone')).toBeNull()
    expect(document.querySelector('#merchant-password')).toBeNull()
    await input('#merchant-name', '新店名'); await input('#merchant-description', '第一行简介\n第二行简介'); await submit('.merchant-dialog form')
    await vi.waitFor(() => expect(document.querySelector('.merchant-dialog')).toBeNull())
    const request = requests.find((config) => config.method === 'put')
    expect(request.url).toBe(`/admin/merchants/${id}`)
    expect(JSON.parse(request.data)).toEqual({ name: '新店名', phone: merchant.phone, address: merchant.address, description: '第一行简介\n第二行简介' })
    expect(requests.filter((config) => config.url === '/admin/merchants' && config.method === 'get')).toHaveLength(2)
  })

  it('店铺启停需要确认，取消不写；提交中不可重复，成功才刷新', async () => {
    await setup(); await ready(); await click('停用')
    expect(document.querySelector('[role="dialog"]').textContent).toContain('历史数据保留')
    await click('取消')
    expect(requests.filter((config) => config.method === 'patch')).toHaveLength(0)
    await click('停用')
    let finish
    server = (config) => config.method === 'patch' ? new Promise((resolve) => { finish = () => resolve(ok(config, { ...merchant, status: 0 })) }) : defaultServer(config)
    await click('确认')
    await vi.waitFor(() => expect(finish).toBeTypeOf('function'))
    expect(button('正在提交…').disabled).toBe(true)
    expect(requests.filter((config) => config.method === 'patch')).toHaveLength(1)
    expect(JSON.parse(requests.find((config) => config.method === 'patch').data)).toEqual({ status: 0 })
    finish()
    await vi.waitFor(() => expect(document.querySelector('[role="dialog"]')).toBeNull())
    expect(ElMessage.success).toHaveBeenCalledWith('店铺已停用')
  })
})

describe('UI-15 用户管理', () => {
  it('昵称/手机号和状态筛选，分页及每页条数，未知角色参数不会发送', async () => {
    await setup({ path: '/admin/users' }); await ready(customer.nickname)
    await input('#user-keyword', '  %_<b>  '); await input('#user-status', '0', 'change'); await submit('.admin-filters')
    await vi.waitFor(() => expect(requests.filter((config) => config.url === '/admin/users')).toHaveLength(2))
    expect(requests.at(-1).params).toEqual({ page: 1, size: 10, keyword: '%_<b>', status: 0 })
    await vi.waitFor(() => expect(button('下一页').disabled).toBe(false)); await click('下一页')
    await vi.waitFor(() => expect(requests.at(-1).params.page).toBe(2))
    await vi.waitFor(() => expect(document.querySelector('[aria-label="每页条数"]').disabled).toBe(false)); await input('[aria-label="每页条数"]', '20', 'change')
    await vi.waitFor(() => expect(requests.at(-1).params).toEqual({ page: 1, size: 20, keyword: '%_<b>', status: 0 }))
    expect(document.body.textContent).not.toContain('密码')
    expect(document.querySelector('select option[value="ADMIN"]')).toBeNull()
  })

  it('过长关键字本地阻止请求，后端筛选错误就地显示', async () => {
    await setup({ path: '/admin/users' }); await ready(customer.nickname)
    await input('#user-keyword', '测'.repeat(61)); await submit('.admin-filters')
    expect(document.body.textContent).toContain('关键字长度不能超过60个字符')
    expect(requests.filter((config) => config.url === '/admin/users')).toHaveLength(1)
    server = (config) => fail(config, 40001, 400, { fieldErrors: { keyword: ['后端关键字错误'], status: ['后端状态错误'] } })
    await input('#user-keyword', '用户'); await submit('.admin-filters'); await ready('后端关键字错误')
    expect(document.body.textContent).toContain('后端状态错误')
  })

  it('启停失败显示status字段错误和403，保留确认弹窗，不假报成功', async () => {
    await setup({ path: '/admin/users' }); await ready(customer.nickname); await click('停用')
    server = (config) => fail(config, 40001, 400, { fieldErrors: { status: ['状态格式不正确', '仅允许0或1'] } })
    await click('确认'); await ready('状态格式不正确')
    expect(document.body.textContent).toContain('仅允许0或1')
    expect(document.querySelector('[role="dialog"]')).not.toBeNull()
    expect(ElMessage.success).not.toHaveBeenCalled()
    server = (config) => fail(config, 40302, 403, null, '无权限操作')
    await click('确认'); await ready('无权限操作')
    expect(store.isAuthenticated).toBe(true)
    expect(router.currentRoute.value.path).toBe('/admin/users')
    expect(JSON.parse(requests.find((config) => config.method === 'patch').data)).toEqual({ status: 0 })
  })

  it('用户启停成功保持大ID并刷新列表，历史订单及角色无操作入口', async () => {
    await setup({ path: '/admin/users' }); await ready(customer.nickname); await click('停用'); await click('确认')
    await vi.waitFor(() => expect(document.querySelector('[role="dialog"]')).toBeNull())
    expect(requests.find((config) => config.method === 'patch').url).toBe(`/admin/users/${id}/status`)
    expect(requests.filter((config) => config.url === '/admin/users')).toHaveLength(2)
    expect(document.body.textContent).toContain('保留历史订单')
    expect(Array.from(document.querySelectorAll('.admin-table button')).map((item) => item.textContent.trim())).toEqual(['停用'])
  })

  it('列表请求401使用原有统一失效处理，退出到提示', async () => {
    await setup({ path: '/admin/users' }); await ready(customer.nickname)
    server = (config) => fail(config, 40102, 401, null, '认证失效')
    await submit('.admin-filters')
    await vi.waitFor(() => expect(router.currentRoute.value.path).toBe('/'))
    expect(store.isAuthenticated).toBe(false)
    expect(document.body.textContent).toContain('请先使用管理员账号登录')
  })

  it('公共退出调用现有API，成功清理会话并回根提示', async () => {
    await setup({ path: '/admin/users' }); await ready(customer.nickname); await click('退出登录')
    await vi.waitFor(() => expect(router.currentRoute.value.path).toBe('/'))
    expect(requests.find((config) => config.url === '/auth/logout').method).toBe('post')
    expect(store.isAuthenticated).toBe(false)
  })

  it('公共退出网络失败保留会话和当前页面，继续使用统一错误反馈', async () => {
    await setup({ path: '/admin/users' }); await ready(customer.nickname)
    server = () => { throw new AxiosError('private detail', 'ERR_NETWORK') }
    await click('退出登录')
    await vi.waitFor(() => expect(errorMessage).toHaveBeenCalledWith('网络连接异常，请稍后重试'))
    expect(store.isAuthenticated).toBe(true)
    expect(router.currentRoute.value.path).toBe('/admin/users')
  })

  it('较晚返回的旧查询不会覆盖新筛选结果', async () => {
    await setup({ path: '/admin/users' }); await ready(customer.nickname)
    let finishOld
    server = (config) => config.params.keyword === '旧'
      ? new Promise((resolve) => { finishOld = () => resolve(ok(config, { records: [{ ...customer, nickname: '旧结果' }], total: 1, page: 1, size: 10 })) })
      : ok(config, { records: [{ ...customer, nickname: '新结果' }], total: 1, page: 1, size: 10 })
    await input('#user-keyword', '旧'); await submit('.admin-filters')
    await vi.waitFor(() => expect(finishOld).toBeTypeOf('function'))
    await input('#user-keyword', '新'); await submit('.admin-filters'); await ready('新结果')
    finishOld(); await new Promise((resolve) => setTimeout(resolve, 10)); await nextTick()
    expect(document.body.textContent).toContain('新结果')
    expect(document.body.textContent).not.toContain('旧结果')
  })
})
