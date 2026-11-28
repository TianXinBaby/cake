// 商品维护接口的本地预校验与提交载荷；规则与后端 PEdit 白名单保持一致。
// fieldErrorsFrom 复用平台管理页面已验证的字段错误提取，只读取 data.fieldErrors。
export { fieldErrorsFrom } from '../admin/validation.js'

const PRICE = /^(0|[1-9][0-9]{0,7})(\.[0-9]{1,2})?$/
const IMAGE = /^(\/|https?:\/\/)\S*$/
const ID = /^[1-9][0-9]{0,19}$/
const MAX_STOCK = 2147483647

function length(value) { return Array.from(value).length }

function text(errors, name, value, max, label, required = true) {
  const current = typeof value === 'string' ? value.trim() : ''
  if (required && current.length === 0) errors[name] = [`${label}不能为空`]
  else if (length(current) > max) errors[name] = [`${label}长度不能超过${max}个字符`]
  return current
}

function integer(value) {
  const text = typeof value === 'string' ? value.trim() : String(value ?? '')
  return /^(0|[1-9][0-9]*)$/.test(text) ? Number(text) : null
}

export function keywordError(value) {
  return length(typeof value === 'string' ? value.trim() : '') > 60 ? '关键字长度不能超过60个字符' : ''
}

export function productInput(form, creating) {
  const errors = {}
  const categoryId = typeof form.categoryId === 'string' ? form.categoryId.trim() : ''
  if (!ID.test(categoryId)) errors.categoryId = ['请选择本店分类']
  const name = text(errors, 'name', form.name, 60, '商品名称')
  const spec = text(errors, 'spec', form.spec, 30, '规格')
  const price = typeof form.price === 'string' ? form.price.trim() : ''
  if (price.length === 0) errors.price = ['单价不能为空']
  else if (!PRICE.test(price) || !(Number(price) > 0)) errors.price = ['单价必须为大于0且最多两位小数的金额']
  const image = typeof form.image === 'string' ? form.image.trim() : ''
  if (image.length > 0 && length(image) > 255) errors.image = ['图片地址长度不能超过255个字符']
  else if (image.length > 0 && !IMAGE.test(image)) errors.image = ['图片地址必须为/开头的站内路径或http(s)链接']
  const description = typeof form.description === 'string' ? form.description.trim() : ''
  if (length(description) > 500) errors.description = ['商品详情长度不能超过500个字符']

  let stock
  if (creating) {
    stock = integer(form.stock)
    if (stock === null || stock > MAX_STOCK) errors.stock = ['库存必须为0到2147483647的整数']
  }

  const payload = { categoryId, name, spec, price, image, description }
  if (creating) payload.stock = stock ?? 0
  return { payload, errors }
}

export function categoryInput(form) {
  const errors = {}
  const name = text(errors, 'name', form.name, 30, '分类名称')
  const sort = integer(form.sort)
  if (sort === null || sort > MAX_STOCK) errors.sort = ['排序必须为非负整数']
  return { payload: { name, sort: sort ?? 0 }, errors }
}

export function stockInput(form) {
  const errors = {}
  const delta = integer(form.delta)
  const expectedStock = integer(form.expectedStock)
  if (delta === null || delta === 0 || delta > MAX_STOCK) errors.delta = ['增减量必须为非零整数']
  if (expectedStock === null || expectedStock > MAX_STOCK) errors.expectedStock = ['原库存必须为非负整数']
  if (!errors.delta && !errors.expectedStock) {
    const adjusted = expectedStock + delta
    if (adjusted < 0) errors.delta = ['调整后库存不能为负数']
    else if (adjusted > MAX_STOCK) errors.delta = ['调整后库存超出上限']
  }
  return { payload: { delta, expectedStock }, errors }
}
