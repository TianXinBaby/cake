import axios from 'axios'
import { ElMessage } from 'element-plus'
import { getActivePinia } from 'pinia'
import { useAuthStore } from '../stores/auth.js'

const SYSTEM_MESSAGE = '操作失败，请稍后重试'
const NETWORK_MESSAGE = '网络连接异常，请稍后重试'
const TIMEOUT_MESSAGE = '请求超时，请稍后重试'

// 这是前端本地错误类型，不增加后端响应字段或改变 data 内部结构。
export class RequestError extends Error {
  constructor(message, { code = null, data = null, status = null } = {}) {
    super(message)
    this.name = 'RequestError'
    this.code = code
    this.data = data
    this.status = status
  }
}

function isEnvelope(body) {
  return body !== null
    && typeof body === 'object'
    && Number.isInteger(body.code)
    && typeof body.msg === 'string'
    && Object.hasOwn(body, 'data')
}

function responseError(body, status) {
  if (!isEnvelope(body) || body.code === 200) {
    return new RequestError(SYSTEM_MESSAGE, { status })
  }

  // 系统错误始终显示需求规定的安全提示，不显示内部异常文本。
  const message = body.code === 50001 ? SYSTEM_MESSAGE : body.msg || SYSTEM_MESSAGE
  return new RequestError(message, { code: body.code, data: body.data, status })
}

// 将会话快照保存在内存中，避免旧请求的延迟响应清除后来登录的新会话。
const requestSessions = new WeakMap()

function currentAuthStore() {
  const pinia = getActivePinia()
  return pinia ? useAuthStore(pinia) : null
}

function rejectWithMessage(error, config) {
  const session = config && requestSessions.get(config)
  const shouldClear = error.code === 40102
    || (error.status === 401 && config?.url === '/auth/logout')
  if (shouldClear && session?.store && session.store.token === session.token) {
    session.store.clearAuthentication()
  }
  ElMessage.error(error.message)
  return Promise.reject(error)
}

// 所有 API 模块复用此实例，后端地址仅由 Vite 开发代理管理。
const http = axios.create({
  baseURL: '/api',
  timeout: 10000,
})

http.interceptors.request.use((config) => {
  const store = currentAuthStore()
  requestSessions.set(config, { store, token: store?.token })
  if (!config.skipAuthentication && store?.token) {
    config.headers.set('Authorization', `Bearer ${store.token}`)
  }
  return config
})

http.interceptors.response.use(
  (response) => {
    const body = response.data
    if (isEnvelope(body) && body.code === 200) {
      // HTTP 200/201 等 2xx 均按业务 code 判定；调用方直接获得原始 data。
      return body.data
    }
    return rejectWithMessage(responseError(body, response.status), response.config)
  },
  (error) => {
    if (error.response) {
      return rejectWithMessage(responseError(error.response.data, error.response.status), error.config)
    }
    const isTimeout = error.code === 'ECONNABORTED' || error.code === 'ETIMEDOUT'
    const message = isTimeout
      ? TIMEOUT_MESSAGE
      : error.code === 'ERR_NETWORK' ? NETWORK_MESSAGE : SYSTEM_MESSAGE
    return rejectWithMessage(new RequestError(message))
  },
)

export default http
