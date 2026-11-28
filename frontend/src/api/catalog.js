import http from './http.js'

// 只发送原文允许的查询字段；merchantId 由页面显式传入，商家端接口从不发送 merchantId。
function value(params, name) {
  const current = params?.[name]
  return current === undefined || current === null || current === '' ? undefined : current
}

function query(params, names) {
  return Object.fromEntries(names.flatMap((name) => {
    const current = value(params, name)
    return current === undefined ? [] : [[name, current]]
  }))
}

export function listShopProducts({ merchantId, page, size, categoryId, keyword } = {}) {
  return http.get('/products', { params: query({ merchantId, page, size, categoryId, keyword },
    ['merchantId', 'page', 'size', 'categoryId', 'keyword']) })
}

export function getShopProduct(id) {
  return http.get(`/products/${encodeURIComponent(id)}`)
}

export function listShopCategories(merchantId) {
  return http.get('/categories', { params: { merchantId } })
}

export function getShopMerchant(id) {
  return http.get(`/merchants/${encodeURIComponent(id)}`)
}

export function listProducts(params) {
  return http.get('/merchant/products', { params: query(params, ['page', 'size', 'categoryId', 'keyword', 'status']) })
}

export function getProduct(id) {
  return http.get(`/merchant/products/${encodeURIComponent(id)}`)
}

export function createProduct({ categoryId, name, spec, price, stock, image, description }) {
  return http.post('/merchant/products', { categoryId, name, spec, price, stock, image, description })
}

export function updateProduct(id, { categoryId, name, spec, price, image, description }) {
  return http.put(`/merchant/products/${encodeURIComponent(id)}`,
    { categoryId, name, spec, price, image, description })
}

export function deleteProduct(id) {
  return http.delete(`/merchant/products/${encodeURIComponent(id)}`)
}

export function adjustStock(id, { delta, expectedStock }) {
  return http.patch(`/merchant/products/${encodeURIComponent(id)}/stock`, { delta, expectedStock })
}

export function setProductStatus(id, status) {
  return http.patch(`/merchant/products/${encodeURIComponent(id)}/status`, { status })
}

export function listCategories(params = {}) {
  return http.get('/merchant/categories', { params: query(params, ['status']) })
}

export function createCategory({ name, sort }) {
  return http.post('/merchant/categories', { name, sort })
}

export function updateCategory(id, { name, sort }) {
  return http.put(`/merchant/categories/${encodeURIComponent(id)}`, { name, sort })
}

export function deleteCategory(id) {
  return http.delete(`/merchant/categories/${encodeURIComponent(id)}`)
}

export function setCategoryStatus(id, status) {
  return http.patch(`/merchant/categories/${encodeURIComponent(id)}/status`, { status })
}
