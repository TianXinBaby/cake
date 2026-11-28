import { h } from 'vue'
import { createRouter, createWebHistory, useRoute } from 'vue-router'
import { useAuthStore } from '../stores/auth.js'
import { getCurrentUser } from '../api/users.js'
import AdminLayout from '../layouts/AdminLayout.vue'
import MerchantLayout from '../layouts/MerchantLayout.vue'
import LoginView from '../views/auth/LoginView.vue'
import MerchantsView from '../views/admin/MerchantsView.vue'
import UsersView from '../views/admin/UsersView.vue'
import ShopProductsView from '../views/shop/ShopProductsView.vue'
import ProductDetailView from '../views/shop/ProductDetailView.vue'
import MerchantProductsView from '../views/merchant/ProductsView.vue'
import MerchantCategoriesView from '../views/merchant/CategoriesView.vue'

const notices = {
  'admin-login': '请先使用管理员账号登录后访问后台管理。',
  'admin-forbidden': '无权限访问平台管理，请使用管理员账号。',
  'admin-unavailable': '暂时无法确认账号权限，请稍后重试。',
  'merchant-login': '请先使用商家账号登录后访问商家中心。',
  'merchant-forbidden': '无权限访问商家中心，请使用商家账号。',
  'merchant-unavailable': '暂时无法确认账号权限，请稍后重试。',
  'customer-home': '顾客选店与下单页面将在后续阶段开放，当前可浏览公开店铺商品。',
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
        path: '/login',
        component: LoginView,
      },
      {
        path: '/',
        // 已授权的管理页面退回此出口；登录页为 UI-01。
        component: FoundationOutlet,
      },
      {
        path: '/shop/:merchantId',
        component: ShopProductsView,
      },
      {
        path: '/shop/products/:id',
        component: ProductDetailView,
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
      {
        path: '/merchant',
        component: MerchantLayout,
        meta: { requiresMerchant: true },
        redirect: '/merchant/products',
        children: [
          { path: 'products', component: MerchantProductsView },
          { path: 'categories', component: MerchantCategoriesView },
        ],
      },
    ],
  })
  router.beforeEach(async (to) => {
    const role = to.meta.requiresAdmin ? 'ADMIN' : to.meta.requiresMerchant ? 'MERCHANT' : null
    if (!role) return true
    const prefix = role === 'ADMIN' ? 'admin' : 'merchant'
    const store = useAuthStore()
    const token = store.token
    const login = () => to.fullPath && to.fullPath !== '/'
      ? { path: '/login', query: { redirect: to.fullPath } } : { path: '/login' }
    if (!store.isAuthenticated) return login()
    try {
      // 每次进入保护路由都读取服务器当前账号，内存中的角色不能代替此校验。
      const user = await getCurrentUser()
      if (store.token !== token || !store.isAuthenticated) return login()
      if (user.role !== role || user.status !== 1) return { path: '/', query: { notice: `${prefix}-forbidden` } }
      return true
    } catch (error) {
      if ((error.status === 401 || error.code === 40102) && store.token === token) store.clearAuthentication()
      if (!store.isAuthenticated) return login()
      const notice = error.status === 403 ? `${prefix}-forbidden` : `${prefix}-unavailable`
      return { path: '/', query: { notice } }
    }
  })
  return router
}
