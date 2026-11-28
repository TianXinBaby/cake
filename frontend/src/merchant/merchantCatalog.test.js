import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import { createMemoryHistory } from 'vue-router'
import { ElMessage } from 'element-plus'
import { AxiosError } from 'axios'
import { createApplication } from '../application.js'
import { useAuthStore } from '../stores/auth.js'
import http from '../api/http.js'

const originalAdapter = http.defaults.adapter
const merchantUser = {
  id: '2', username: '13800138001', nickname: '店主', phone: '13800138001', role: 'MERCHANT', status: 1,
  merchantId: '5', createTime: '2026-11-28T10:00:00+08:00',
}
const categories = [
  { id: '9', merchantId: '5', name: '生日蛋糕', sort: 0, status: 1, createTime: '2026-11-28T10:00:00+08:00', updateTime: '2026-11-28T10:00:00+08:00' },
  { id: '10', merchantId: '5', name: '节日蛋糕', sort: 1, status: 0, createTime: '2026-11-28T10:00:00+08:00', updateTime: '2026-11-28T10:00:00+08:00' },
]
const offShelf = {
  id: '21', merchantId: '5', merchantName: '甜甜蛋糕店', categoryId: '9', categoryName: '生日蛋糕',
  name: '草莓奶油蛋糕', spec: '6寸', price: '128.00', stock: 3, image: '/images/cake-01.svg',
  description: '当日现做', status: 0, createTime: '2026-11-28T10:00:00.000+08:00', updateTime: '2026-11-28T10:00:00.000+08:00',
}
const listed = {
  ...offShelf, id: '22', name: '巧克力蛋糕', spec: '8寸', price: '168.00', stock: 0, status: 1,
}

let app, router, store, requests, server, currentUser, errorMessage

function ok(config, data, status = 200) {
  return { data: JSON.stringify({ code: 200, msg: 'success', data }), status, headers: {}, config }
}
function fail(config, code, status, data = null, msg = '参数错误') {
  throw new AxiosError('private internal detail', 'ERR_BAD_RESPONSE', config, null, {
    data: { code, msg, data }, status, headers: {}, config,
  })
}
function paged(records) { return { records, total: records.length, page: 1, size: 10 } }
function body(config) { return config.data === undefined ? undefined : JSON.parse(config.data) }
function match(config, pattern) { return typeof config.url === 'string' && pattern.test(config.url) }

function defaultServer(config) {
  if (config.url === '/users/me') return ok(config, currentUser)
  if (config.url === '/merchant/categories' && config.method === 'get') return ok(config, categories)
  if (config.url === '/merchant/products' && config.method === 'get') return ok(config, paged([listed, offShelf]))
  if (match(config, /^\/merchant\/products\/\d+$/) && config.method === 'get') {
    return ok(config, config.url.endsWith('22') ? listed : offShelf)
  }
  if (config.method === 'post' && config.url === '/merchant/products') {
    return ok(config, { ...offShelf, id: '30', ...body(config), status: 0 }, 201)
  }
  if (config.method === 'put' && match(config, /^\/merchant\/products\//)) {
    return ok(config, { ...offShelf, ...body(config) })
  }
  if (config.method === 'patch' && config.url.endsWith('/stock')) {
    const input = body(config)
    return ok(config, { id: '21', stock: input.expectedStock + input.delta, updateTime: '2026-11-28T11:00:00.000+08:00' })
  }
  if (config.method === 'patch' && config.url.endsWith('/status')) {
    return ok(config, { ...offShelf, status: body(config).status })
  }
  if (config.method === 'delete' && match(config, /^\/merchant\/products\//)) {
    return ok(config, { id: '21', deleted: true })
  }
  if (config.method === 'post' && config.url === '/merchant/categories') {
    return ok(config, { ...categories[0], id: '31', ...body(config) }, 201)
  }
  if (config.method === 'put' && match(config, /^\/merchant\/categories\//)) {
    return ok(config, { ...categories[0], ...body(config) })
  }
  if (config.method === 'patch' && match(config, /^\/merchant\/categories\/\d+\/status$/)) {
    return ok(config, { ...categories[0], status: body(config).status })
  }
  if (config.method === 'delete' && match(config, /^\/merchant\/categories\//)) {
    return ok(config, { id: '9', deleted: true })
  }
  return ok(config, null)
}

async function setup({ role = 'MERCHANT', serverRole = 'MERCHANT', authenticated = true, path = '/merchant/products' } = {}) {
  const application = createApplication({ history: createMemoryHistory() })
  app = application.app
  router = application.router
  store = useAuthStore(application.pinia)
  if (authenticated) {
    store.saveLogin({ token: 'merchant-session', tokenType: 'Bearer', user: { ...merchantUser, role } })
  }
  currentUser = { ...merchantUser, role: serverRole }
  await router.isReady()
  await router.push(path)
  document.body.innerHTML = '<div id="app"></div>'
  app.mount('#app')
  await nextTick()
}
async function ready(text) { await vi.waitFor(() => expect(document.body.textContent).toContain(text)) }
function button(text) {
  const found = Array.from(document.querySelectorAll('button')).find((item) => item.textContent.trim() === text)
  expect(found, `button: ${text}`).toBeTruthy()
  return found
}
function rowButton(name, text) {
  const row = Array.from(document.querySelectorAll('.admin-table tbody tr'))
    .find((item) => item.textContent.includes(name))
  expect(row, `row: ${name}`).toBeTruthy()
  const found = Array.from(row.querySelectorAll('button')).find((item) => item.textContent.trim() === text)
  expect(found, `row button: ${text}`).toBeTruthy()
  return found
}
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

beforeEach(() => {
  requests = []
  server = defaultServer
  currentUser = merchantUser
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

describe('商家中心路由守卫', () => {
  it('未登录跳转登录页，不发送后台请求', async () => {
    await setup({ authenticated: false })
    expect(router.currentRoute.value.path).toBe('/login')
    expect(document.body.textContent).toContain('账号登录')
    expect(requests).toHaveLength(0)
  })

  it('内存伪造商家角色，服务器当前角色不足仍无权限', async () => {
    await setup({ serverRole: 'CUSTOMER' })
    expect(router.currentRoute.value.path).toBe('/')
    expect(document.body.textContent).toContain('无权限访问商家中心')
    expect(requests.map((item) => item.url)).toEqual(['/users/me'])
  })

  it('真实商家进入并显示两个经营导航', async () => {
    await setup()
    await ready(offShelf.name)
    expect(router.currentRoute.value.path).toBe('/merchant/products')
    expect(Array.from(document.querySelectorAll('aside a')).map((link) => link.getAttribute('href')))
      .toEqual(['/merchant/products', '/merchant/categories'])
  })

  it('鉴权401清理会话并跳转登录页', async () => {
    server = (config) => fail(config, 40102, 401, null, '认证失效')
    await setup()
    expect(store.isAuthenticated).toBe(false)
    expect(router.currentRoute.value.path).toBe('/login')
    expect(document.body.textContent).toContain('账号登录')
  })
})

describe('UI-04 商家商品管理', () => {
  it('显示本店商品真实库存与上下架状态，筛选条件全部发送', async () => {
    await setup()
    await ready(offShelf.name)
    expect(document.body.textContent).toContain('¥128.00')
    expect(document.body.textContent).toContain('3 件')
    expect(document.body.textContent).toContain('已下架')
    expect(document.body.textContent).toContain('已上架')
    expect(document.body.textContent).toContain('本店编号：5')

    await input('#product-keyword', ' 蛋糕 ')
    await input('#product-category-filter', '9', 'change')
    await input('#product-status-filter', '0', 'change')
    button('查询').click()
    await vi.waitFor(() => expect(requests.filter((item) => item.url === '/merchant/products' && item.method === 'get')).toHaveLength(2))
    expect(requests.at(-1).params).toEqual({ page: 1, size: 10, categoryId: '9', status: 0, keyword: '蛋糕' })
  })

  it('新增商品本地校验、白名单提交并在成功后刷新', async () => {
    await setup()
    await ready(offShelf.name)
    button('新增商品').click()
    await vi.waitFor(() => expect(document.querySelector('.product-dialog')).not.toBeNull())
    await submit('.product-dialog form')
    expect(document.body.textContent).toContain('请选择本店分类')
    expect(document.body.textContent).toContain('商品名称不能为空')
    expect(document.body.textContent).toContain('单价不能为空')
    expect(requests.filter((item) => item.method === 'post')).toHaveLength(0)

    await input('#product-categoryId', '9', 'change')
    await input('#product-name', '  新蛋糕  ')
    await input('#product-spec', '标准份')
    await input('#product-price', '88.00')
    await input('#product-stock', '5')
    await input('#product-image', '/images/cake-02.svg')
    await input('#product-description', ' 新品介绍 ')
    await submit('.product-dialog form')
    await vi.waitFor(() => expect(document.querySelector('.product-dialog')).toBeNull())
    const post = requests.find((item) => item.method === 'post')
    expect(post.url).toBe('/merchant/products')
    expect(body(post)).toEqual({
      categoryId: '9', name: '新蛋糕', spec: '标准份', price: '88.00', stock: 5,
      image: '/images/cake-02.svg', description: '新品介绍',
    })
    expect(ElMessage.success).toHaveBeenCalledWith('商品已创建，当前为下架状态')
    expect(requests.filter((item) => item.url === '/merchant/products' && item.method === 'get')).toHaveLength(2)
  })

  it('后端字段错误就地显示并保留输入', async () => {
    await setup()
    await ready(offShelf.name)
    button('新增商品').click()
    await vi.waitFor(() => expect(document.querySelector('.product-dialog')).not.toBeNull())
    await input('#product-categoryId', '9', 'change')
    await input('#product-name', '保留名称')
    await input('#product-spec', '标准份')
    await input('#product-price', '10.00')
    await input('#product-stock', '1')
    server = (config) => config.method === 'post'
      ? fail(config, 40001, 400, { fieldErrors: { name: ['名称重复'], price: ['价格错误'], _global: ['忽略'] } })
      : defaultServer(config)
    await submit('.product-dialog form')
    await ready('名称重复')
    expect(document.body.textContent).toContain('价格错误')
    expect(document.body.textContent).not.toContain('忽略')
    expect(document.querySelector('#product-name').value).toBe('保留名称')
    expect(document.querySelector('.product-dialog')).not.toBeNull()
    expect(ElMessage.success).not.toHaveBeenCalled()
  })

  it('编辑回显不含库存字段，提交不发送库存或状态', async () => {
    await setup()
    await ready(offShelf.name)
    rowButton(offShelf.name, '编辑').click()
    await vi.waitFor(() => expect(document.querySelector('#product-name')?.value).toBe(offShelf.name))
    expect(document.querySelector('#product-stock')).toBeNull()
    await input('#product-name', '改名蛋糕')
    await input('#product-price', '138.00')
    await submit('.product-dialog form')
    await vi.waitFor(() => expect(document.querySelector('.product-dialog')).toBeNull())
    const put = requests.find((item) => item.method === 'put')
    expect(put.url).toBe('/merchant/products/21')
    expect(body(put)).toEqual({
      categoryId: '9', name: '改名蛋糕', spec: '6寸', price: '138.00', image: '/images/cake-01.svg', description: '当日现做',
    })
    expect(body(put)).not.toHaveProperty('stock')
    expect(body(put)).not.toHaveProperty('status')
  })

  it('库存调整使用当前库存作为expectedStock并处理40911刷新', async () => {
    await setup()
    await ready(offShelf.name)
    rowButton(offShelf.name, '库存').click()
    await vi.waitFor(() => expect(document.querySelector('.stock-dialog')).not.toBeNull())
    expect(document.querySelector('#stock-expected').value).toBe('3')
    await input('#stock-delta', '5')
    await submit('.stock-dialog form')
    await vi.waitFor(() => expect(document.querySelector('.stock-dialog')).toBeNull())
    const patch = requests.find((item) => item.url === '/merchant/products/21/stock')
    expect(body(patch)).toEqual({ delta: 5, expectedStock: 3 })
    expect(ElMessage.success).toHaveBeenCalledWith('库存已更新为 8 件')

    server = (config) => {
      if (config.method === 'patch' && config.url.endsWith('/stock')) {
        return fail(config, 40911, 409, null, '库存已变化，请刷新后重试')
      }
      if (config.method === 'get' && config.url === '/merchant/products/21') {
        return ok(config, { ...offShelf, stock: 7 })
      }
      return defaultServer(config)
    }
    rowButton(offShelf.name, '库存').click()
    await vi.waitFor(() => expect(document.querySelector('.stock-dialog')).not.toBeNull())
    await input('#stock-delta', '2')
    await submit('.stock-dialog form')
    await vi.waitFor(() => expect(document.body.textContent).toContain('当前库存 7 件'))
    expect(document.querySelector('#stock-expected').value).toBe('7')
  })

  it('上下架需确认，分类禁用时显示40913且不假报成功', async () => {
    await setup()
    await ready(offShelf.name)
    rowButton(offShelf.name, '上架').click()
    await vi.waitFor(() => expect(document.querySelector('[role="dialog"]')).not.toBeNull())
    expect(document.querySelector('[role="dialog"]').textContent).toContain('分类必须处于启用状态')
    server = (config) => config.method === 'patch' && config.url.endsWith('/status')
      ? fail(config, 40913, 409, null, '请先启用商品分类')
      : defaultServer(config)
    button('确认上架').click()
    await ready('请先启用商品分类')
    expect(requests.filter((item) => item.method === 'patch')).toHaveLength(1)
    expect(document.querySelector('[role="dialog"]')).not.toBeNull()
    expect(ElMessage.success).not.toHaveBeenCalled()
    button('取消').click()
    await vi.waitFor(() => expect(document.querySelector('[role="dialog"]')).toBeNull())
  })

  it('删除有历史引用时显示40910并保留弹窗', async () => {
    await setup()
    await ready(offShelf.name)
    rowButton(offShelf.name, '删除').click()
    await vi.waitFor(() => expect(document.querySelector('[role="dialog"]')).not.toBeNull())
    expect(document.querySelector('[role="dialog"]').textContent).toContain('只能下架')
    server = (config) => config.method === 'delete'
      ? fail(config, 40910, 409, null, '商品已有历史订单，请使用下架')
      : defaultServer(config)
    button('确认删除').click()
    await ready('商品已有历史订单，请使用下架')
    expect(requests.find((item) => item.method === 'delete').url).toBe('/merchant/products/21')
    expect(document.querySelector('[role="dialog"]')).not.toBeNull()
  })
})

describe('UI-11 商家分类管理', () => {
  it('显示本店分类并按状态筛选', async () => {
    await setup({ path: '/merchant/categories' })
    await ready('生日蛋糕')
    expect(document.body.textContent).toContain('节日蛋糕')
    expect(document.body.textContent).toContain('2026-11-28 10:00')
    await input('#category-status-filter', '0', 'change')
    button('查询').click()
    await vi.waitFor(() => expect(requests.filter((item) => item.url === '/merchant/categories')).toHaveLength(2))
    expect(requests.at(-1).params).toEqual({ status: 0 })
  })

  it('新增分类提交名称与排序，重复名称就地提示', async () => {
    await setup({ path: '/merchant/categories' })
    await ready('生日蛋糕')
    button('新增分类').click()
    await vi.waitFor(() => expect(document.querySelector('.category-dialog')).not.toBeNull())
    await submit('.category-dialog form')
    expect(document.body.textContent).toContain('分类名称不能为空')
    await input('#category-name', '新品分类')
    await input('#category-sort', '4')
    await submit('.category-dialog form')
    await vi.waitFor(() => expect(document.querySelector('.category-dialog')).toBeNull())
    const post = requests.find((item) => item.method === 'post')
    expect(body(post)).toEqual({ name: '新品分类', sort: 4 })

    server = (config) => config.method === 'post'
      ? fail(config, 40901, 409, { fieldErrors: { name: ['分类名称已存在'] } })
      : defaultServer(config)
    button('新增分类').click()
    await vi.waitFor(() => expect(document.querySelector('.category-dialog')).not.toBeNull())
    await input('#category-name', '生日蛋糕')
    await submit('.category-dialog form')
    await ready('分类名称已存在')
    expect(document.querySelector('.category-dialog')).not.toBeNull()
  })

  it('编辑分类只发送名称与排序', async () => {
    await setup({ path: '/merchant/categories' })
    await ready('生日蛋糕')
    rowButton('生日蛋糕', '编辑').click()
    await vi.waitFor(() => expect(document.querySelector('#category-name')?.value).toBe('生日蛋糕'))
    await input('#category-name', '改名分类')
    await input('#category-sort', '6')
    await submit('.category-dialog form')
    await vi.waitFor(() => expect(document.querySelector('.category-dialog')).toBeNull())
    const put = requests.find((item) => item.method === 'put')
    expect(put.url).toBe('/merchant/categories/9')
    expect(body(put)).toEqual({ name: '改名分类', sort: 6 })
  })

  it('禁用有上架商品的分类显示40909并可取消', async () => {
    await setup({ path: '/merchant/categories' })
    await ready('生日蛋糕')
    rowButton('生日蛋糕', '禁用').click()
    await vi.waitFor(() => expect(document.querySelector('[role="dialog"]')).not.toBeNull())
    server = (config) => config.method === 'patch'
      ? fail(config, 40909, 409, null, '分类仍有上架商品，请先下架')
      : defaultServer(config)
    button('确认禁用').click()
    await ready('分类仍有上架商品，请先下架')
    expect(document.querySelector('[role="dialog"]')).not.toBeNull()
    button('取消').click()
    await vi.waitFor(() => expect(document.querySelector('[role="dialog"]')).toBeNull())
  })

  it('删除有商品的分类显示40909', async () => {
    await setup({ path: '/merchant/categories' })
    await ready('生日蛋糕')
    rowButton('生日蛋糕', '删除').click()
    await vi.waitFor(() => expect(document.querySelector('[role="dialog"]')).not.toBeNull())
    server = (config) => config.method === 'delete'
      ? fail(config, 40909, 409, null, '分类仍有商品，请先处理所属商品')
      : defaultServer(config)
    button('确认删除').click()
    await ready('分类仍有商品，请先处理所属商品')
    expect(requests.find((item) => item.method === 'delete').url).toBe('/merchant/categories/9')
    expect(document.querySelector('[role="dialog"]')).not.toBeNull()
  })
})
