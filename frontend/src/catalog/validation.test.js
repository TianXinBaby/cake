import { describe, expect, it } from 'vitest'
import { categoryInput, keywordError, productInput, stockInput } from './validation.js'

const base = {
  categoryId: '12', name: '  草莓蛋糕  ', spec: ' 6寸 ', price: ' 128.50 ',
  stock: '4', image: ' /images/cake-01.svg ', description: '  当日现做  ',
}

describe('商品表单本地校验', () => {
  it('去除首尾空白并生成创建载荷，库存转为整数', () => {
    const { payload, errors } = productInput(base, true)
    expect(errors).toEqual({})
    expect(payload).toEqual({
      categoryId: '12', name: '草莓蛋糕', spec: '6寸', price: '128.50', stock: 4,
      image: '/images/cake-01.svg', description: '当日现做',
    })
    expect(payload).not.toHaveProperty('status')
    expect(payload).not.toHaveProperty('merchantId')
  })

  it('编辑载荷不包含库存与状态', () => {
    const { payload, errors } = productInput(base, false)
    expect(errors).toEqual({})
    expect(payload).not.toHaveProperty('stock')
    expect(payload).not.toHaveProperty('status')
  })

  it.each(['0', '0.00', '-1', '1.234', '010.00', '1e2', '.5', '100000000', ''])(
    '拒绝非法单价 %s', (price) => {
      const { errors } = productInput({ ...base, price }, true)
      expect(errors.price?.length).toBeGreaterThan(0)
    })

  it.each(['0.01', '128.5', '99999999.99', '100'])('接受合法单价 %s', (price) => {
    expect(productInput({ ...base, price }, true).errors).toEqual({})
  })

  it('校验名称、规格、分类ID与库存范围', () => {
    const { errors } = productInput({
      categoryId: 'abc', name: '   ', spec: 's'.repeat(31), price: '10.00', stock: '2147483648',
      image: '', description: 'd'.repeat(501),
    }, true)
    expect(Object.keys(errors).sort()).toEqual(['categoryId', 'description', 'name', 'spec', 'stock'])
  })

  it('图片只接受站内路径或 http(s) 链接', () => {
    expect(productInput({ ...base, image: 'ftp://host/a.png' }, true).errors.image).toBeTruthy()
    expect(productInput({ ...base, image: 'images/a.png' }, true).errors.image).toBeTruthy()
    expect(productInput({ ...base, image: 'https://cdn.example.com/a.png' }, false).errors).toEqual({})
    expect(productInput({ ...base, image: '' }, false).payload.image).toBe('')
  })

  it('初始库存缺省不合法，0 合法', () => {
    expect(productInput({ ...base, stock: '' }, true).errors.stock).toBeTruthy()
    expect(productInput({ ...base, stock: '0' }, true).errors).toEqual({})
    expect(productInput({ ...base, stock: '1.5' }, true).errors.stock).toBeTruthy()
  })
})

describe('分类与库存表单本地校验', () => {
  it('分类名称与排序', () => {
    expect(categoryInput({ name: ' 生日蛋糕 ', sort: '3' })).toEqual({
      payload: { name: '生日蛋糕', sort: 3 }, errors: {},
    })
    expect(categoryInput({ name: ' ', sort: '3' }).errors.name).toBeTruthy()
    expect(categoryInput({ name: '分类', sort: '' }).errors.sort).toBeTruthy()
    expect(categoryInput({ name: '分类', sort: '-1' }).errors.sort).toBeTruthy()
  })

  it('库存增减量必须非零且结果在允许范围', () => {
    const ok = stockInput({ delta: '5', expectedStock: '10' })
    expect(ok.errors).toEqual({})
    expect(ok.payload).toEqual({ delta: 5, expectedStock: 10 })

    expect(stockInput({ delta: '0', expectedStock: '10' }).errors.delta).toBeTruthy()
    expect(stockInput({ delta: '-11', expectedStock: '10' }).errors.delta).toBeTruthy()
    expect(stockInput({ delta: '2', expectedStock: '2147483647' }).errors.delta).toBeTruthy()
    expect(stockInput({ delta: '1.5', expectedStock: '10' }).errors.delta).toBeTruthy()
    expect(stockInput({ delta: '5', expectedStock: '-1' }).errors.expectedStock).toBeTruthy()
  })

  it('关键字最长60个字符', () => {
    expect(keywordError('  ')).toBe('')
    expect(keywordError('蛋'.repeat(60))).toBe('')
    expect(keywordError('蛋'.repeat(61))).toContain('60')
  })
})
