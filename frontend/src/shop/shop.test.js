import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import { createMemoryHistory } from 'vue-router'
import { ElMessage } from 'element-plus'
import { AxiosError } from 'axios'
import { createApplication } from '../application.js'
import http from '../api/http.js'

const originalAdapter = http.defaults.adapter
const shop = {
  id: '5', merchantId: '5', name: '甜甜蛋糕店', phone: '13800000002', address: '测试地址', description: '店铺简介', status: 1,
}
const categories = [
  { id: '9', merchantId: '5', name: '生日蛋糕', sort: 0, status: 1 },
  { id: '10', merchantId: '5', name: '节日蛋糕', sort: 1, status: 1 },
]
const product = {
  id: '21', merchantId: '5', merchantName: shop.name, categoryId: '9', categoryName: '生日蛋糕',
  name: '草莓奶油蛋糕', spec: '6寸', price: '128.00', stock: 3, image: '/images/cake-01.svg',
  description: '当日现做', status: 1,
}
const soldOut = { ...product, id: '20', name: '巧克力蛋糕', spec: '8寸', price: '168.00', stock: 0 }

let app, router, requests, server, errorMessage

function ok(config, data, status = 200) {
  return { data: JSON.stringify({ code: 200, msg: 'success', data }), status, headers: {}, config }
}
function fail(config, code, status, data = null, msg = '参数错误') {
  throw new AxiosError('private internal detail', 'ERR_BAD_RESPONSE', config, null, {
    data: { code, msg, data }, status, headers: {}, config,
  })
}
function page(records, total = records.length) { return { records, total, page: 1, size: 10 } }
function defaultServer(config) {
  if (config.url === '/categories') return ok(config, categories)
  if (config.url === '/merchants/5') {
    return ok(config, { id: '5', name: shop.name, phone: shop.phone, address: shop.address,
      description: shop.description, status: 1 })
  }
  if (config.url === '/products') return ok(config, page([soldOut, product]))
  const match = config.url.match(/^\/products\/(\d+)$/)
  if (match) return ok(config, match[1] === soldOut.id ? soldOut : { ...product, id: match[1] })
  return ok(config, null)
}
async function setup(path = '/shop/5') {
  const application = createApplication({ history: createMemoryHistory() })
  app = application.app
  router = application.router
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
async function input(selector, value, event = 'input') {
  const field = document.querySelector(selector)
  expect(field, selector).toBeTruthy()
  field.value = value
  field.dispatchEvent(new Event(event, { bubbles: true }))
  await nextTick()
}

beforeEach(() => {
  requests = []
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

describe('UI-02 本店商品页', () => {
  it('匿名显示店铺名称、分类与商品，售罄仍展示且携带本店链接', async () => {
    await setup()
    await ready(product.name)
    expect(document.body.textContent).toContain(shop.name)
    expect(document.body.textContent).toContain(shop.phone)
    expect(document.body.textContent).toContain(shop.address)
    expect(document.body.textContent).toContain('生日蛋糕')
    expect(document.body.textContent).toContain('节日蛋糕')
    expect(document.body.textContent).toContain('¥128.00')
    expect(document.body.textContent).toContain('¥168.00')
    expect(document.body.textContent).toContain('已售罄')
    expect(document.body.textContent).toContain('库存 3 件')
    const links = Array.from(document.querySelectorAll('.product-card')).map((card) => card.getAttribute('href'))
    expect(links).toEqual(['/shop/products/20', '/shop/products/21'])
    expect(requests.map((item) => item.url).sort()).toEqual(['/categories', '/merchants/5', '/products'])
    expect(requests[0].headers.Authorization).toBeUndefined()
    expect(document.body.textContent).not.toContain('加入购物车')
  })

  it('切换分类与关键字会重新查询，过长关键字本地阻止', async () => {
    await setup()
    await ready(product.name)
    button('节日蛋糕').click()
    await vi.waitFor(() => expect(requests.filter((item) => item.url === '/products')).toHaveLength(2))
    expect(requests.at(-1).params).toEqual({ merchantId: '5', page: 1, size: 10, categoryId: '10' })

    await input('#shop-keyword', '  草莓  ')
    button('搜索').click()
    await vi.waitFor(() => expect(requests.filter((item) => item.url === '/products')).toHaveLength(3))
    expect(requests.at(-1).params).toEqual({ merchantId: '5', page: 1, size: 10, categoryId: '10', keyword: '草莓' })

    await input('#shop-keyword', '蛋'.repeat(61))
    button('搜索').click()
    await vi.waitFor(() => expect(document.body.textContent).toContain('关键字长度不能超过60个字符'))
    expect(requests.filter((item) => item.url === '/products')).toHaveLength(3)
  })

  it('店铺停用显示友好提示，不展示商品列表', async () => {
    server = (config) => fail(config, 40404, 404, null, '商家不存在或不可用')
    await setup()
    await ready('店铺不存在或已停用')
    expect(document.querySelector('.product-grid')).toBeNull()
    expect(errorMessage).toHaveBeenCalledWith('商家不存在或不可用')
  })

  it('点击商品卡进入商品详情页', async () => {
    await setup()
    await ready(product.name)
    document.querySelector('.product-card').click()
    await vi.waitFor(() => expect(router.currentRoute.value.path).toBe('/shop/products/20'))
    await ready('巧克力蛋糕')
  })

  it('商品名称与描述按纯文本渲染', async () => {
    const injected = '<img src=x onerror=alert(1)>'
    server = (config) => config.url === '/categories' ? ok(config, categories)
      : ok(config, page([{ ...product, name: injected, description: '<script>alert(1)</script>' }]))
    await setup()
    await ready(injected)
    expect(document.querySelector('.product-card img[src="x"], .product-card script')).toBeNull()
    expect(document.body.textContent).toContain(injected)
  })
})

describe('UI-10 商品详情页', () => {
  it('展示规格、价格、库存与详情，并提供返回本店商品', async () => {
    await setup('/shop/products/21')
    await ready(product.name)
    expect(document.body.textContent).toContain(shop.name)
    expect(document.body.textContent).toContain('规格：6寸')
    expect(document.body.textContent).toContain('¥128.00')
    expect(document.body.textContent).toContain('库存 3 件')
    expect(document.body.textContent).toContain('当日现做')
    const back = Array.from(document.querySelectorAll('a')).map((link) => link.getAttribute('href'))
    expect(back).toContain('/shop/5')
    expect(button('去确认订单（订单模块接入后开放）').disabled).toBe(true)
  })

  it('售罄商品禁止下单', async () => {
    await setup('/shop/products/20')
    await ready('巧克力蛋糕')
    expect(document.body.textContent).toContain('已售罄，暂不可下单')
    expect(button('已售罄，无法下单').disabled).toBe(true)
  })

  it('下架或不存在时显示40401友好提示', async () => {
    server = (config) => fail(config, 40401, 404, null, '商品不存在或已下架')
    await setup('/shop/products/99')
    await ready('商品不存在或已下架')
    expect(document.querySelector('.product-detail')).toBeNull()
  })

  it('店铺不可用与系统错误分别提示', async () => {
    server = (config) => fail(config, 40404, 404, null, '商家不存在或不可用')
    await setup('/shop/products/21')
    await ready('店铺不存在或已停用')
    app.unmount()
    document.body.innerHTML = ''
    server = (config) => fail(config, 50001, 500, null, 'private SQL detail')
    await setup('/shop/products/21')
    await ready('操作失败，请稍后重试')
    expect(document.body.textContent).not.toContain('private SQL detail')
  })
})
