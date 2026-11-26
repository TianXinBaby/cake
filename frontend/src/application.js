import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import App from './App.vue'
import { createAppRouter } from './router/index.js'

export function createApplication({ history } = {}) {
  const app = createApp(App)
  const pinia = createPinia()
  const router = createAppRouter(history)

  app.use(ElementPlus, { locale: zhCn })
  app.use(pinia)
  app.use(router)

  return { app, pinia, router }
}
