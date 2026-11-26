const PHONE = /^1[3-9][0-9]{9}$/
const INVISIBLE = /[\s\p{Cc}\p{Cf}\p{Cs}\p{Cn}]/u
const CONTROL = /\p{Cc}/u

export function fieldErrorsFrom(error, allowedFields) {
  const source = error?.data?.fieldErrors
  if (!source || typeof source !== 'object' || Array.isArray(source)) return {}
  return Object.fromEntries(allowedFields.flatMap((name) => {
    const messages = source[name]
    return Array.isArray(messages) && messages.every((message) => typeof message === 'string')
      ? [[name, messages]] : []
  }))
}

export function merchantInput(form, creating) {
  const payload = {
    name: form.name.trim(), phone: form.phone.trim(), address: form.address.trim(),
    description: form.description.trim(),
    ...(creating ? { accountPhone: form.accountPhone.trim(), nickname: form.nickname.trim(), password: form.password } : {}),
  }
  const errors = {}
  function text(name, label, limit, required = true) {
    const length = Array.from(payload[name]).length
    if (required && length < 1) errors[name] = [`${label}不能为空`]
    else if (length > limit) errors[name] = [`${label}长度不能超过${limit}个字符`]
  }
  text('name', '店铺名称', 60)
  text('address', '店铺地址', 120)
  text('description', '店铺简介', 500, false)
  if (!PHONE.test(payload.phone)) errors.phone = ['请输入有效的大陆11位手机号']
  if (creating) {
    text('nickname', '账号昵称', 30)
    if (CONTROL.test(payload.nickname)) errors.nickname = ['昵称不能包含控制字符']
    if (!PHONE.test(payload.accountPhone)) errors.accountPhone = ['请输入有效的大陆11位账号手机号']
    if (payload.password.length < 6 || payload.password.length > 20 || INVISIBLE.test(payload.password)) {
      errors.password = ['密码须为6～20位可见字符，不能包含空白']
    }
  }
  return { payload, errors }
}
