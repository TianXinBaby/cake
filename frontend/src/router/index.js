import { h } from 'vue'
import { createRouter, createWebHistory, useRoute } from 'vue-router'
import { useAuthStore } from '../stores/auth.js'
import { getCurrentUser } from '../api/users.js'
import AdminLayout from '../layouts/AdminLayout.vue'
import MerchantsView from '../views/admin/MerchantsView.vue'
import UsersView from '../views/admin/UsersView.vue'

const notices = {
  'admin-login': '请先使用管理员账号登录后访问后台管理。',
  'admin-forbidden': '无权限访问平台管理，请使用管理员账号。',
  'admin-unavailable': '暂时无法确认账号权限，请稍后重试。',
}

const FoundationOutlet = {
  name: 'FoundationOutlet',
  setup() {
    const route = useRoute()
    return () => notices[route.query.notice] ? h('p', { class: 'access-notice', role: 'alert' }, notices[route.query.notice]) : null
  },
}

export function createAppRouter(history = createWebHistory(import.meta.env.BASE_URL)) {
  const router = createRouter({
    history,
    routes: [
      {
        path: '/',
        // 已授权的管理页面退回此出口；登录页仍由后续页面阶段接入。
        component: FoundationOutlet,
      },
      {
        path: '/admin',
        component: AdminLayout,
        meta: { requiresAdmin: true },
        redirect: '/admin/merchants',
        children: [
          { path: 'merchants', component: MerchantsView },
          { path: 'users', component: UsersView },
        ],
      },
    ],
  })
  router.beforeEach(async (to) => {
    if (!to.meta.requiresAdmin) return true
    const store = useAuthStore()
    const token = store.token
    if (!store.isAuthenticated) return { path: '/', query: { notice: 'admin-login' } }
    try {
      // 每次进入管理路由都读取服务器当前账号，内存中的角色不能代替此校验。
      const user = await getCurrentUser()
      if (store.token !== token || !store.isAuthenticated) return { path: '/', query: { notice: 'admin-login' } }
      if (user.role !== 'ADMIN' || user.status !== 1) return { path: '/', query: { notice: 'admin-forbidden' } }
      return true
    } catch (error) {
      if ((error.status === 401 || error.code === 40102) && store.token === token) store.clearAuthentication()
      return { path: '/', query: { notice: !store.isAuthenticated ? 'admin-login' : error.status === 403 ? 'admin-forbidden' : 'admin-unavailable' } }
    }
  })
  return router
}
