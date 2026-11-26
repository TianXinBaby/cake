import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useAuthStore } from '../stores/auth.js'
import http from './http.js'
import { createMerchant, editMerchant, listCustomers, listMerchants, setCustomerStatus, setMerchantStatus } from './admin.js'

const originalAdapter = http.defaults.adapter
const id = '18446744073709551615'
const userId = '9007199254740993'
let adapter
beforeEach(() => {
  setActivePinia(createPinia())
  useAuthStore().saveLogin({ token: 'admin-test-token', tokenType: 'Bearer', user: { id: '1', role: 'ADMIN', status: 1 } })
  // 经 Axios 的真实 JSON 解析后仍须保留 unsigned BIGINT 原始十进制字符串。
  adapter = vi.fn(async (config) => ({
    data: `{"code":200,"msg":"success","data":{"id":"${id}","userId":"${userId}"}}`,
    status: config.method === 'post' ? 201 : 200, headers: {}, config,
  }))
  http.defaults.adapter = adapter
})
afterEach(() => { http.defaults.adapter = originalAdapter })

describe('API-35～40 原文请求与字符串 ID', () => {
  it.each([
    ['商家查询', () => listMerchants({ page: 2, size: 20, keyword: '%_<b>', status: 0, role: 'ADMIN', merchantId: '8' }), 'get', '/admin/merchants', undefined],
    ['用户查询', () => listCustomers({ page: 2, size: 20, keyword: '%_<b>', status: 0, role: 'ADMIN', userId: '8' }), 'get', '/admin/users', undefined],
    ['商家创建', () => createMerchant({ accountPhone: '13800138001', nickname: '商家', password: 'test-pass', name: '店铺', phone: '13800138002', address: '地址', description: '', role: 'ADMIN', userId: '7', status: 0 }), 'post', '/admin/merchants', { accountPhone: '13800138001', nickname: '商家', password: 'test-pass', name: '店铺', phone: '13800138002', address: '地址', description: '' }],
    ['店铺编辑', () => editMerchant(id, { name: '新店名', phone: '13800138002', address: '新地址', description: null, accountPhone: '13800138003', role: 'ADMIN', userId: '7', status: 0 }), 'put', `/admin/merchants/${id}`, { name: '新店名', phone: '13800138002', address: '新地址', description: null }],
    ['店铺状态同值设置', () => setMerchantStatus(id, 1), 'patch', `/admin/merchants/${id}/status`, { status: 1 }],
    ['顾客状态同值设置', () => setCustomerStatus(id, 0), 'patch', `/admin/users/${id}/status`, { status: 0 }],
  ])('%s 仅发送白名单字段，复用单实例 Bearer 并保留原始返回 ID', async (label, invoke, method, url, body) => {
    await expect(invoke()).resolves.toEqual({ id, userId })
    const [config] = adapter.mock.calls[0]
    expect(config).toMatchObject({ baseURL: '/api', method, url })
    expect(config.headers.Authorization).toBe('Bearer admin-test-token')
    expect(config.data === undefined ? undefined : JSON.parse(config.data)).toEqual(body)
    if (method === 'get') expect(config.params).toEqual({ page: 2, size: 20, keyword: '%_<b>', status: 0 })
    expect(adapter).toHaveBeenCalledTimes(1)
  })
})
