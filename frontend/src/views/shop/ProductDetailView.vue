<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getShopProduct } from '../../api/catalog.js'
import ProductImage from '../../components/catalog/ProductImage.vue'

const route = useRoute()
const router = useRouter()
const id = computed(() => String(route.params.id ?? ''))
const product = ref(null)
const loading = ref(false)
const error = ref('')
const errorCode = ref(null)

const soldOut = computed(() => product.value?.stock === 0)
const missing = computed(() => errorCode.value === 40401)
const unavailable = computed(() => errorCode.value === 40404)
const errorTitle = computed(() => unavailable.value ? '店铺不存在或已停用'
  : missing.value ? '商品不存在或已下架' : '暂时无法展示商品')

async function load() {
  if (!/^[1-9][0-9]{0,19}$/.test(id.value)) {
    error.value = '商品编号格式不正确，请从店铺商品页重新进入。'
    errorCode.value = 400
    product.value = null
    return
  }
  loading.value = true
  error.value = ''
  errorCode.value = null
  try {
    product.value = await getShopProduct(id.value)
  } catch (reason) {
    product.value = null
    error.value = reason.message
    errorCode.value = reason.code ?? reason.status ?? null
  } finally {
    loading.value = false
  }
}

onMounted(load)
watch(id, load)
</script>

<template>
  <div class="shop-shell">
    <header class="shop-header">
      <div class="shop-brand"><span class="brand-mark" aria-hidden="true">糕</span><strong>蛋糕店订单管理系统</strong></div>
      <span class="shop-tag">商品详情</span>
    </header>

    <main class="shop-main">
      <p v-if="loading && !product" class="shop-panel shop-loading">正在加载商品…</p>

      <section v-else-if="error" class="shop-panel shop-error" role="alert">
        <h1>{{ errorTitle }}</h1>
        <p>{{ error }}</p>
        <div class="shop-actions">
          <button type="button" class="primary-button" @click="router.back()">返回上一页</button>
          <RouterLink v-if="missing || unavailable" class="shop-link-button" to="/">返回系统入口</RouterLink>
        </div>
      </section>

      <template v-else-if="product">
        <nav class="shop-breadcrumb" aria-label="位置">
          <RouterLink :to="`/shop/${encodeURIComponent(product.merchantId)}`">{{ product.merchantName }}</RouterLink>
          <span aria-hidden="true">/</span>
          <span>{{ product.name }}</span>
        </nav>

        <section class="product-detail">
          <ProductImage :src="product.image" :alt="`${product.name} ${product.spec}`" ratio="1 / 1" />
          <div class="product-detail-body">
            <span class="product-category">{{ product.categoryName }}</span>
            <h1>{{ product.name }}</h1>
            <p class="product-detail-spec">规格：{{ product.spec }}</p>
            <p class="product-detail-price">¥{{ product.price }}</p>
            <p :class="['product-stock', { soldout: soldOut }]">
              {{ soldOut ? '已售罄，暂不可下单' : `库存 ${product.stock} 件` }}
            </p>
            <p class="product-detail-shop">
              所属商家：<RouterLink :to="`/shop/${encodeURIComponent(product.merchantId)}`">{{ product.merchantName }}</RouterLink>
            </p>
            <p v-if="product.description" class="product-detail-description">{{ product.description }}</p>
            <p v-else class="product-detail-description muted">该商品暂无详细介绍。</p>
            <div class="product-detail-actions">
              <button type="button" class="primary-button" disabled>
                {{ soldOut ? '已售罄，无法下单' : '去确认订单（订单模块接入后开放）' }}
              </button>
              <RouterLink class="shop-link-button" :to="`/shop/${encodeURIComponent(product.merchantId)}`">返回本店商品</RouterLink>
            </div>
            <p class="shop-note">金额、库存与可下单状态均由服务器返回，页面展示不作为下单依据。</p>
          </div>
        </section>
      </template>
    </main>
  </div>
</template>
