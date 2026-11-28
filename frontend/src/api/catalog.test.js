import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useAuthStore } from '../stores/auth.js'
import http from './http.js'
import {
  adjustStock, createCategory, createProduct, deleteCategory, deleteProduct, getProduct, getShopProduct,
  listCategories, listProducts, listShopCategories, listShopProducts, setCategoryStatus, setProductStatus,
  updateCategory, updateProduct,
} from './catalog.js'

const originalAdapter = http.defaults.adapter
const id = '18446744073709551615'
let adapter

beforeEach(() => {
  setActivePinia(createPinia())
  adapter = vi.fn(async (config) => ({
    data: `{"code":200,"msg":"success","data":{"id":"${id}","merchantId":"5","categoryId":"9","stock":0,"price":"10.00"}}`,
    status: config.method === 'post' ? 201 : 200,
    headers: {},
    config,
  }))
  http.defaults.adapter = adapter
})
afterEach(() => { http.defaults.adapter = originalAdapter })

function config() { return adapter.mock.calls[0][0] }

describe('公开商品与分类接口（API-02、15、16）', () => {
  it('匿名分页查询携带所选店铺、分类与关键字，且不添加 Bearer', async () => {
    await listShopProducts({ merchantId: '5', page: 2, size: 20, categoryId: '9', keyword: '草莓' })
    expect(config()).toMatchObject({ method: 'get', url: '/products', baseURL: '/api' })
    expect(config().params).toEqual({ merchantId: '5', page: 2, size: 20, categoryId: '9', keyword: '草莓' })
    expect(config().headers.Authorization).toBeUndefined()
  })

  it('公开分类仅发送 merchantId，详情路径使用原始字符串 ID', async () => {
    await listShopCategories('5')
    expect(config().params).toEqual({ merchantId: '5' })
    adapter.mockClear()
    const data = await getShopProduct(id)
    expect(config().url).toBe(`/products/${id}`)
    expect(data.id).toBe(id)
    expect(data.merchantId).toBe('5')
  })

  it('空筛选值不进入查询参数', async () => {
    await listShopProducts({ merchantId: '5', page: 1, size: 10, categoryId: '', keyword: '' })
    expect(config().params).toEqual({ merchantId: '5', page: 1, size: 10 })
  })
})

describe('商家商品与分类接口', () => {
  beforeEach(() => {
    useAuthStore().saveLogin({ token: 'merchant-test-token', tokenType: 'Bearer', user: { id: '2', role: 'MERCHANT', status: 1, merchantId: '5' } })
  })

  it('后台商品查询保留状态0，忽略空值与客户端 merchantId', async () => {
    await listProducts({ page: 1, size: 10, categoryId: '', keyword: '', status: 0, merchantId: '99' })
    expect(config().url).toBe('/merchant/products')
    expect(config().params).toEqual({ page: 1, size: 10, status: 0 })
    expect(config().headers.Authorization).toBe('Bearer merchant-test-token')
    adapter.mockClear()
    await listProducts({})
    expect(config().params).toEqual({})
  })

  it('创建商品发送库存，编辑商品不发送库存与状态', async () => {
    await createProduct({ categoryId: '9', name: '蛋糕', spec: '6寸', price: '128.50', stock: 4,
      image: '/images/cake-01.svg', description: '详情', status: 1, merchantId: '99' })
    expect(JSON.parse(config().data)).toEqual({ categoryId: '9', name: '蛋糕', spec: '6寸', price: '128.50',
      stock: 4, image: '/images/cake-01.svg', description: '详情' })
    adapter.mockClear()
    await updateProduct(id, { categoryId: '9', name: '改名', spec: '8寸', price: '168.00',
      image: null, description: null, stock: 1, status: 0, merchantId: '99' })
    expect(config().url).toBe(`/merchant/products/${id}`)
    expect(JSON.parse(config().data)).toEqual({ categoryId: '9', name: '改名', spec: '8寸', price: '168.00',
      image: null, description: null })
  })

  it('库存与上下架使用 PATCH 且只发送规定字段', async () => {
    await adjustStock(id, { delta: -3, expectedStock: 10 })
    expect(config()).toMatchObject({ method: 'patch', url: `/merchant/products/${id}/stock` })
    expect(JSON.parse(config().data)).toEqual({ delta: -3, expectedStock: 10 })
    adapter.mockClear()
    await setProductStatus(id, 1)
    expect(config()).toMatchObject({ method: 'patch', url: `/merchant/products/${id}/status` })
    expect(JSON.parse(config().data)).toEqual({ status: 1 })
  })

  it('分类新增、编辑、启停与删除只发送白名单字段', async () => {
    await createCategory({ name: '生日蛋糕', sort: 2, status: 0, merchantId: '99' })
    expect(config().url).toBe('/merchant/categories')
    expect(JSON.parse(config().data)).toEqual({ name: '生日蛋糕', sort: 2 })
    adapter.mockClear()
    await updateCategory(id, { name: '改', sort: 3, status: 0 })
    expect(JSON.parse(config().data)).toEqual({ name: '改', sort: 3 })
    adapter.mockClear()
    await setCategoryStatus(id, 0)
    expect(config().url).toBe(`/merchant/categories/${id}/status`)
    expect(JSON.parse(config().data)).toEqual({ status: 0 })
    adapter.mockClear()
    await deleteCategory(id)
    expect(config()).toMatchObject({ method: 'delete', url: `/merchant/categories/${id}` })
    expect(config().data).toBeUndefined()
  })

  it('分类查询状态0不丢失，空状态不发送', async () => {
    await listCategories({ status: 0 })
    expect(config().params).toEqual({ status: 0 })
    adapter.mockClear()
    await listCategories({ status: '' })
    expect(config().params).toEqual({})
  })

  it('商品详情与删除使用十进制字符串 ID', async () => {
    await getProduct(id)
    expect(config().url).toBe(`/merchant/products/${id}`)
    adapter.mockClear()
    await deleteProduct(id)
    expect(config()).toMatchObject({ method: 'delete', url: `/merchant/products/${id}` })
  })
})
