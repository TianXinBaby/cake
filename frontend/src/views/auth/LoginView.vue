<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { login } from '../../api/auth.js'
import { useAuthStore } from '../../stores/auth.js'
import { fieldErrorsFrom } from '../../admin/validation.js'
import FieldErrors from '../../components/admin/FieldErrors.vue'

const router = useRouter()
const route = useRoute()
const store = useAuthStore()
const form = reactive({ username: '', password: '' })
const fieldErrors = ref({})
const error = ref('')
const submitting = ref(false)
const PHONE = /^1[3-9][0-9]{9}$/

const fields = computed(() => [
  { name: 'username', label: '手机号', type: 'tel', autocomplete: 'username' },
  { name: 'password', label: '密码', type: 'password', autocomplete: 'current-password' },
])

function homeFor(role) {
  if (role === 'ADMIN') return '/admin/merchants'
  // UI-05 商家订单页尚未交付，先进入已完成的商品管理；不伪造订单入口。
  if (role === 'MERCHANT') return '/merchant/products'
  return '/'
}

function safeRedirect(value) {
  if (typeof value !== 'string' || !value.startsWith('/') || value.startsWith('//')) return null
  if (value === '/login' || value.startsWith('/login?')) return null
  return value
}

function destination() {
  return safeRedirect(route.query.redirect) || homeFor(store.user?.role)
}

async function submit() {
  if (submitting.value) return
  const errors = {}
  const username = form.username.trim()
  if (!PHONE.test(username)) errors.username = ['请输入注册手机号']
  if (!form.password) errors.password = ['请输入密码']
  fieldErrors.value = errors
  error.value = ''
  if (Object.keys(errors).length) return
  submitting.value = true
  try {
    await login({ username, password: form.password })
    form.password = ''
    const target = destination()
    if (!store.user?.role) {
      error.value = '无法识别账号角色，请联系管理员。'
      return
    }
    if (target === '/') {
      await router.replace({ path: '/', query: { notice: 'customer-home' } })
    } else {
      await router.replace(target)
    }
    ElMessage.success('登录成功')
  } catch (reason) {
    error.value = reason.message
    fieldErrors.value = fieldErrorsFrom(reason, ['username', 'password'])
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  if (store.isAuthenticated && store.user?.role) {
    const target = safeRedirect(route.query.redirect) || homeFor(store.user.role)
    if (target === '/') {
      router.replace({ path: '/', query: { notice: 'customer-home' } })
    } else {
      router.replace(target)
    }
  }
})
</script>

<template>
  <div class="login-shell">
    <section class="login-card" aria-labelledby="login-title">
      <header class="login-heading">
        <span class="brand-mark" aria-hidden="true">糕</span>
        <div>
          <p class="login-eyebrow">蛋糕店订单管理系统</p>
          <h1 id="login-title">账号登录</h1>
        </div>
      </header>
      <p class="login-intro">三类角色共用此入口，登录后由账号角色决定可访问的页面。</p>

      <form class="login-form" novalidate autocomplete="on" @submit.prevent="submit">
        <div v-for="field in fields" :key="field.name" class="login-field">
          <label :for="`login-${field.name}`">{{ field.label }}<span class="required-mark" aria-hidden="true"> *</span></label>
          <input :id="`login-${field.name}`" v-model="form[field.name]" :type="field.type"
            :required="true" :autocomplete="field.autocomplete" :disabled="submitting"
            :aria-invalid="Boolean(fieldErrors[field.name])" :aria-describedby="`login-${field.name}-errors`">
          <FieldErrors :id="`login-${field.name}-errors`" :messages="fieldErrors[field.name]" />
        </div>

        <p v-if="error" class="login-error" role="alert">{{ error }}</p>

        <button class="login-submit" type="submit" :disabled="submitting">
          {{ submitting ? '正在登录…' : '登录' }}
        </button>
      </form>

      <p class="login-note">商家账号由平台创建；公开注册的用户账号固定为顾客角色。登录状态仅保存在当前页面内存，刷新后需要重新登录。</p>
    </section>
  </div>
</template>
