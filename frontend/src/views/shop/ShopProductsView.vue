<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { listShopCategories, listShopProducts, getShopMerchant } from '../../api/catalog.js'
import { fieldErrorsFrom, keywordError } from '../../catalog/validation.js'
import ProductImage from '../../components/catalog/ProductImage.vue'
import AdminPagination from '../../components/admin/AdminPagination.vue'
import FieldErrors from '../../components/admin/FieldErrors.vue'

const route = useRoute()
const router = useRouter()
const merchantId = computed(() => String(route.params.merchantId ?? ''))
const categories = ref([])
const records = ref([])
const total = ref(0)
const query = reactive({ page: 1, size: 10, categoryId: undefined, keyword: '' })
const filters = reactive({ keyword: '' })
const loading = ref(false)
const error = ref('')
const errorCode = ref(null)
const fieldErrors = ref({})
const shopName = ref('')
const shop = ref(null)
let request = 0

const unavailable = computed(() => errorCode.value === 40404)
const shopTitle = computed(() => shop.value?.name || shopName.value
  || (merchantId.value ? `店铺 ${merchantId.value}` : '本店商品'))

async function loadShop() {
  try {
    shop.value = await getShopMerchant(merchantId.value)
  } catch {
    // 店铺不可用由商品列表统一提示；此处只影响电话/地址展示。
    shop.value = null
  }
}

async function loadCategories() {
  try {
    const list = await listShopCategories(merchantId.value)
    categories.value = Array.isArray(list) ? list : []
  } catch {
    // 店铺不可用由商品列表统一提示；分类失败不清空已选条件。
    categories.value = []
  }
}

async function load() {
  if (!/^[1-9][0-9]{0,19}$/.test(merchantId.value)) {
    error.value = '店铺编号格式不正确，请从商家列表重新进入。'
    return
  }
  const current = ++request
  loading.value = true
  error.value = ''
  errorCode.value = null
  fieldErrors.value = {}
  try {
    const page = await listShopProducts({
      merchantId: merchantId.value,
      page: query.page,
      size: query.size,
      categoryId: query.categoryId,
      keyword: query.keyword,
    })
    if (current !== request) return
    records.value = Array.isArray(page.records) ? page.records : []
    total.value = page.total ?? records.value.length
    if (records.value[0]?.merchantName) shopName.value = records.value[0].merchantName
  } catch (reason) {
    if (current !== request) return
    records.value = []
    total.value = 0
    error.value = reason.message
    errorCode.value = reason.code
    fieldErrors.value = fieldErrorsFrom(reason, ['merchantId', 'categoryId', 'keyword', 'page', 'size'])
  } finally {
    if (current === request) loading.value = false
  }
}

function chooseCategory(id) {
  if (query.categoryId === id) return
  query.categoryId = id
  query.page = 1
  load()
}

async function search() {
  const message = keywordError(filters.keyword)
  if (message) {
    fieldErrors.value = { keyword: [message] }
    return
  }
  query.keyword = filters.keyword.trim()
  query.page = 1
  await load()
}

function changePage(page) { query.page = page; load() }
function changeSize(size) { query.size = size; query.page = 1; load() }
function productPath(id) { return `/shop/products/${encodeURIComponent(id)}` }

onMounted(async () => {
  await Promise.all([loadCategories(), loadShop(), load()])
})

watch(merchantId, async () => {
  query.page = 1
  query.categoryId = undefined
  query.keyword = ''
  filters.keyword = ''
  shopName.value = ''
  shop.value = null
  categories.value = []
  await Promise.all([loadCategories(), loadShop(), load()])
})
</script>

<template>
  <div class="shop-shell">
    <header class="shop-header">
      <div class="shop-brand"><span class="brand-mark" aria-hidden="true">糕</span><strong>蛋糕店订单管理系统</strong></div>
      <span class="shop-tag">本店商品</span>
    </header>

    <main class="shop-main">
      <section v-if="unavailable" class="shop-panel shop-error" role="alert">
        <h1>店铺不存在或已停用</h1>
        <p>该店铺当前不可浏览，请返回重新选择商家。</p>
        <button type="button" class="primary-button" @click="router.back()">返回上一页</button>
      </section>

      <template v-else>
        <section class="shop-banner">
          <div>
            <p class="shop-eyebrow">正在浏览</p>
            <h1>{{ shopTitle }}</h1>
            <p class="shop-meta">共 {{ total }} 件在售商品<template v-if="categories.length"> · {{ categories.length }} 个分类</template></p>
            <p v-if="shop" class="shop-contact">电话：{{ shop.phone }} · 地址：{{ shop.address }}</p>
            <p v-if="shop?.description" class="shop-intro">{{ shop.description }}</p>
          </div>
          <p class="shop-note">售罄商品仍会展示，但不能下单；切换店铺前请确认清空当前选购。</p>
        </section>

        <section class="shop-toolbar" aria-label="筛选商品">
          <div class="shop-categories" role="tablist" aria-label="商品分类">
            <button type="button" role="tab" :aria-selected="query.categoryId === undefined"
              :class="['shop-chip', { active: query.categoryId === undefined }]" @click="chooseCategory(undefined)">全部</button>
            <button v-for="category in categories" :key="category.id" type="button" role="tab"
              :aria-selected="query.categoryId === category.id"
              :class="['shop-chip', { active: query.categoryId === category.id }]"
              @click="chooseCategory(category.id)">{{ category.name }}</button>
          </div>
          <form class="shop-search" novalidate @submit.prevent="search">
            <label class="visually-hidden" for="shop-keyword">搜索商品名称</label>
            <input id="shop-keyword" v-model="filters.keyword" maxlength="60" placeholder="搜索商品名称"
              :aria-invalid="Boolean(fieldErrors.keyword)" aria-describedby="shop-keyword-errors">
            <button type="submit" class="primary-button" :disabled="loading">{{ loading ? '查询中…' : '搜索' }}</button>
          </form>
          <FieldErrors id="shop-keyword-errors" :messages="fieldErrors.keyword" />
          <FieldErrors id="shop-merchant-errors" :messages="fieldErrors.merchantId" />
          <FieldErrors id="shop-category-errors" :messages="fieldErrors.categoryId" />
        </section>

        <div v-if="error" class="shop-panel shop-error" role="alert">
          <p>{{ error }}</p>
          <button type="button" :disabled="loading" @click="load">重新加载</button>
        </div>

        <section class="product-grid" :aria-busy="loading">
          <RouterLink v-for="product in records" :key="product.id" :to="productPath(product.id)" class="product-card">
            <ProductImage :src="product.image" :alt="`${product.name} ${product.spec}`" />
            <div class="product-card-body">
              <span class="product-category">{{ product.categoryName }}</span>
              <h2 class="product-name">{{ product.name }}</h2>
              <p class="product-spec">{{ product.spec }}</p>
              <p class="product-price">¥{{ product.price }}</p>
              <p :class="['product-stock', { soldout: product.stock === 0 }]">
                {{ product.stock === 0 ? '已售罄' : `库存 ${product.stock} 件` }}
              </p>
            </div>
          </RouterLink>
          <p v-if="!records.length" class="shop-empty">
            {{ loading ? '正在加载商品…' : error ? '商品列表暂时不可用' : '该店铺还没有符合条件的商品' }}
          </p>
        </section>

        <AdminPagination :page="query.page" :size="query.size" :total="total" :disabled="loading"
          @page="changePage" @size="changeSize" />
      </template>
    </main>
  </div>
</template>
