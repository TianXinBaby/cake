<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth.js'
import { logout } from '../api/auth.js'

const store = useAuthStore()
const router = useRouter()
const leaving = ref(false)
async function signOut() {
  if (leaving.value) return
  leaving.value = true
  try {
    await logout()
    if (!store.isAuthenticated) await router.replace('/login')
  } catch {
    if (!store.isAuthenticated) await router.replace('/login')
  } finally { leaving.value = false }
}
</script>

<template>
  <div class="admin-shell merchant-shell">
    <header class="admin-header">
      <div><span class="brand-mark" aria-hidden="true">糕</span><strong>蛋糕店订单管理系统</strong><span class="admin-badge">商家中心</span></div>
      <div class="admin-session">
        <span>{{ store.user?.nickname || store.user?.username }}</span>
        <button type="button" :disabled="leaving" @click="signOut">{{ leaving ? '正在退出…' : '退出登录' }}</button>
      </div>
    </header>
    <div class="admin-workspace">
      <aside class="admin-sidebar">
        <span class="nav-caption">本店经营</span>
        <nav aria-label="商家导航">
          <RouterLink to="/merchant/products">商品管理</RouterLink>
          <RouterLink to="/merchant/categories">分类管理</RouterLink>
        </nav>
      </aside>
      <main class="admin-main"><RouterView /></main>
    </div>
  </div>
</template>
