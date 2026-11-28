import { afterEach, describe, expect, it } from 'vitest'
import { nextTick } from 'vue'
import { createMemoryHistory } from 'vue-router'
import { defineStore } from 'pinia'
import { ElButton } from 'element-plus'
import { createApplication } from './application.js'

let mountedApp

afterEach(() => {
  mountedApp?.unmount()
  mountedApp = undefined
  document.body.innerHTML = ''
})

describe('Vue 基础初始化', () => {
  it('挂载真实应用和空路由出口，不生成业务页面', async () => {
    const { app, router } = createApplication({ history: createMemoryHistory() })
    document.body.innerHTML = '<div id="app"></div>'
    await router.isReady()
    app.mount('#app')
    mountedApp = app
    await nextTick()

    expect(document.querySelector('#app').hasAttribute('data-v-app')).toBe(true)
    expect(document.querySelector('#app').textContent).toBe('')
  })

  it('注册真实 Element Plus 组件与消息入口', () => {
    const { app } = createApplication({ history: createMemoryHistory() })

    expect(app.component('ElButton')).toBe(ElButton)
    expect(typeof app.config.globalProperties.$message.error).toBe('function')
  })

  it('通过已注册的 Pinia 创建可读写状态', () => {
    const { app, pinia } = createApplication({ history: createMemoryHistory() })
    const useProbe = defineStore('initialization-probe', {
      state: () => ({ count: 0 }),
      actions: { increment() { this.count += 1 } },
    })
    const store = useProbe(pinia)

    store.increment()

    expect(app.config.globalProperties.$pinia).toBe(pinia)
    expect(store.count).toBe(1)
    expect(pinia.state.value['initialization-probe'].count).toBe(1)
  })

  it('注册真实 Router 并支持技术入口导航', async () => {
    const { app, router } = createApplication({ history: createMemoryHistory() })
    await router.isReady()
    await router.push('/?phase=1')

    expect(app.config.globalProperties.$router).toBe(router)
    expect(router.currentRoute.value.path).toBe('/')
    expect(router.currentRoute.value.query.phase).toBe('1')
    expect(router.getRoutes().map((route) => route.path).sort())
      .toEqual(['/', '/admin', '/admin/merchants', '/admin/users', '/login', '/merchant', '/merchant/categories',
        '/merchant/products', '/shop/:merchantId', '/shop/products/:id'])
  })

  it('不同应用实例的 Pinia 状态互相隔离', () => {
    const first = createApplication({ history: createMemoryHistory() })
    const second = createApplication({ history: createMemoryHistory() })
    const useProbe = defineStore('isolated-probe', { state: () => ({ count: 0 }) })

    useProbe(first.pinia).count = 1

    expect(useProbe(second.pinia).count).toBe(0)
  })
})
