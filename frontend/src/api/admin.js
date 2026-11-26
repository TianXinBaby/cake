import http from './http.js'

// 管理接口只发送原文允许的字段；ID 始终作为十进制字符串传递。
function query({ page, size, keyword, status } = {}) {
  return { page, size, keyword, status }
}

export function listMerchants(params) {
  return http.get('/admin/merchants', { params: query(params) })
}

export function createMerchant({ accountPhone, nickname, password, name, phone, address, description }) {
  return http.post('/admin/merchants', { accountPhone, nickname, password, name, phone, address, description })
}

export function editMerchant(id, { name, phone, address, description }) {
  return http.put(`/admin/merchants/${encodeURIComponent(id)}`, { name, phone, address, description })
}

export function setMerchantStatus(id, status) {
  return http.patch(`/admin/merchants/${encodeURIComponent(id)}/status`, { status })
}

export function listCustomers(params) {
  return http.get('/admin/users', { params: query(params) })
}

export function setCustomerStatus(id, status) {
  return http.patch(`/admin/users/${encodeURIComponent(id)}/status`, { status })
}
