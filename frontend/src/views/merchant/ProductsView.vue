<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  createProduct, deleteProduct, getProduct, listCategories, listProducts, setProductStatus, updateProduct, adjustStock,
} from '../../api/catalog.js'
import { useMerchantAccess, useMerchantProductList } from '../../catalog/useMerchantCatalog.js'
import { fieldErrorsFrom, productInput, stockInput } from '../../catalog/validation.js'
import AdminPagination from '../../components/admin/AdminPagination.vue'
import FieldErrors from '../../components/admin/FieldErrors.vue'
import ConfirmDialog from '../../components/catalog/ConfirmDialog.vue'
import ProductImage from '../../components/catalog/ProductImage.vue'

const { store, handleError } = useMerchantAccess()
const list = useMerchantProductList(listProducts)
const { filters, query, records, total, loading, error, fieldErrors } = list
const categories = ref([])
const categoryError = ref('')
const categoriesById = computed(() => new Map(categories.value.map((category) => [category.id, category])))

const formOpen = ref(false)
const editing = ref(null)
const form = reactive({ categoryId: '', name: '', spec: '', price: '', stock: '0', image: '', description: '' })
const formErrors = ref({})
const submitError = ref('')
const saving = ref(false)

const stockTarget = ref(null)
const stockForm = reactive({ delta: '', expectedStock: 0 })
const stockBusy = ref(false)
const stockError = ref('')
const stockErrors = ref({})

const statusTarget = ref(null)
const statusBusy = ref(false)
const statusError = ref('')
const statusErrors = ref({})

const stopTarget = ref(null)
const stopBusy = ref(false)
const stopError = ref('')
const stopErrors = ref({})

const creating = computed(() => editing.value === null)
const formFields = computed(() => [
  { name: 'categoryId', label: '商品分类', type: 'select' },
  { name: 'name', label: '商品名称', type: 'text', hint: '不同规格可使用相同名称。' },
  { name: 'spec', label: '规格', type: 'text', hint: '如 6寸、8寸；无尺寸时填标准份。' },
  { name: 'price', label: '单价（元）', type: 'text', hint: '最多两位小数，例如 128.50。' },
  ...(creating.value ? [{ name: 'stock', label: '初始库存', type: 'number', hint: '新商品默认下架，库存为0到2147483647。' }] : []),
  { name: 'image', label: '商品图片地址', type: 'text', optional: true, hint: '以 / 开头的站内路径或 http(s) 链接。' },
  { name: 'description', label: '商品详情', type: 'textarea', optional: true },
])

async function loadCategories() {
  try {
    const result = await listCategories()
    categories.value = Array.isArray(result) ? result : []
    categoryError.value = ''
  } catch (reason) {
    categories.value = []
    categoryError.value = reason.message
    await handleError(reason, store.token)
  }
}

const openingEdit = ref(false)

function openCreate() {
  if (saving.value || stockBusy.value || openingEdit.value) return
  editing.value = null
  Object.assign(form, { categoryId: '', name: '', spec: '', price: '', stock: '0', image: '', description: '' })
  formErrors.value = {}
  submitError.value = ''
  formOpen.value = true
  if (!categories.value.length) loadCategories()
}

async function openEdit(product) {
  if (saving.value || stockBusy.value || openingEdit.value) return
  openingEdit.value = true
  try {
    // 编辑前重新读取当前库存与上下架状态，避免使用列表中的旧行。
    let fresh = product
    try { fresh = await getProduct(product.id) } catch { fresh = product }
    editing.value = fresh
    Object.assign(form, {
      categoryId: fresh.categoryId, name: fresh.name, spec: fresh.spec, price: fresh.price,
      stock: String(fresh.stock), image: fresh.image ?? '', description: fresh.description ?? '',
    })
    formErrors.value = {}
    submitError.value = ''
    formOpen.value = true
    if (!categories.value.length) loadCategories()
  } finally {
    openingEdit.value = false
  }
}

function closeForm() {
  if (saving.value) return
  formOpen.value = false
  editing.value = null
}

async function submitForm() {
  if (saving.value) return
  const { payload, errors } = productInput(form, creating.value)
  formErrors.value = errors
  submitError.value = ''
  if (Object.keys(errors).length) return
  saving.value = true
  const token = store.token
  try {
    if (creating.value) await createProduct(payload)
    else await updateProduct(editing.value.id, payload)
    ElMessage.success(creating.value ? '商品已创建，当前为下架状态' : '商品信息已保存')
    formOpen.value = false
    editing.value = null
    await list.load()
  } catch (reason) {
    formErrors.value = fieldErrorsFrom(reason, ['categoryId', 'name', 'spec', 'price', 'stock', 'image', 'description'])
    submitError.value = reason.message
    await handleError(reason, token)
  } finally {
    saving.value = false
  }
}

function openStock(product) {
  if (stockBusy.value || saving.value) return
  stockTarget.value = { ...product }
  stockForm.delta = ''
  stockForm.expectedStock = product.stock
  stockError.value = ''
  stockErrors.value = {}
}

async function submitStock() {
  if (stockBusy.value || !stockTarget.value) return
  const { payload, errors } = stockInput(stockForm)
  stockErrors.value = errors
  stockError.value = ''
  if (Object.keys(errors).length) return
  stockBusy.value = true
  const token = store.token
  try {
    const result = await adjustStock(stockTarget.value.id, payload)
    ElMessage.success(`库存已更新为 ${result.stock} 件`)
    stockTarget.value = null
    await list.load()
  } catch (reason) {
    stockError.value = reason.message
    stockErrors.value = fieldErrorsFrom(reason, ['delta', 'expectedStock'])
    if (reason.code === 40911 && stockTarget.value) {
      try {
        const fresh = await getProduct(stockTarget.value.id)
        stockForm.expectedStock = fresh.stock
        stockTarget.value = { ...fresh }
        stockError.value = `${reason.message}（当前库存 ${fresh.stock} 件，已刷新，请确认后重试）`
      } catch { /* 刷新失败时保留后端提示 */ }
    }
    await handleError(reason, token)
  } finally {
    stockBusy.value = false
  }
}

function openStatus(product) {
  if (statusBusy.value) return
  statusTarget.value = { id: product.id, name: product.name, status: product.status,
    next: product.status === 1 ? 0 : 1, categoryId: product.categoryId }
  statusError.value = ''
  statusErrors.value = {}
}

async function submitStatus() {
  if (statusBusy.value || !statusTarget.value) return
  statusBusy.value = true
  const token = store.token
  try {
    await setProductStatus(statusTarget.value.id, statusTarget.value.next)
    ElMessage.success(statusTarget.value.next === 1 ? '商品已上架' : '商品已下架')
    statusTarget.value = null
    await list.load()
  } catch (reason) {
    statusError.value = reason.message
    statusErrors.value = fieldErrorsFrom(reason, ['status'])
    await handleError(reason, token)
  } finally {
    statusBusy.value = false
  }
}

function openDelete(product) {
  if (stopBusy.value) return
  stopTarget.value = { id: product.id, name: product.name }
  stopError.value = ''
  stopErrors.value = {}
}

async function submitDelete() {
  if (stopBusy.value || !stopTarget.value) return
  stopBusy.value = true
  const token = store.token
  try {
    await deleteProduct(stopTarget.value.id)
    ElMessage.success('商品已删除')
    stopTarget.value = null
    await list.load()
  } catch (reason) {
    stopError.value = reason.message
    stopErrors.value = fieldErrorsFrom(reason, [])
    await handleError(reason, token)
  } finally {
    stopBusy.value = false
  }
}

function categoryLabel(id) {
  const category = categoriesById.value.get(id)
  return category ? `${category.name}${category.status === 1 ? '' : '（已禁用）'}` : id
}
function statusActionLabel(product) { return product.status === 1 ? '下架' : '上架' }

onMounted(loadCategories)
</script>

<template>
  <section class="management-page">
    <div class="page-heading">
      <div>
        <span class="eyebrow">商家中心 / 商品</span>
        <h1>商品管理</h1>
        <p>仅维护本店商品；新商品默认下架，库存使用增减量调整。</p>
      </div>
      <button class="primary-button" type="button" :disabled="saving || stockBusy" @click="openCreate">新增商品</button>
    </div>

    <section class="management-card">
      <form class="admin-filters" novalidate @submit.prevent="list.search">
        <div class="filter-keyword">
          <label for="product-keyword">商品名称</label>
          <input id="product-keyword" v-model="filters.keyword" maxlength="60" placeholder="搜索商品名称"
            :aria-invalid="Boolean(fieldErrors.keyword)" aria-describedby="product-keyword-errors">
          <FieldErrors id="product-keyword-errors" :messages="fieldErrors.keyword" />
        </div>
        <div>
          <label for="product-category-filter">商品分类</label>
          <select id="product-category-filter" v-model="filters.categoryId"
            :aria-invalid="Boolean(fieldErrors.categoryId)" aria-describedby="product-category-filter-errors">
            <option value="">全部分类</option>
            <option v-for="category in categories" :key="category.id" :value="category.id">{{ categoryLabel(category.id) }}</option>
          </select>
          <FieldErrors id="product-category-filter-errors" :messages="fieldErrors.categoryId" />
        </div>
        <div>
          <label for="product-status-filter">上下架状态</label>
          <select id="product-status-filter" v-model="filters.status" :aria-invalid="Boolean(fieldErrors.status)"
            aria-describedby="product-status-filter-errors">
            <option value="">全部状态</option>
            <option value="1">已上架</option>
            <option value="0">已下架</option>
          </select>
          <FieldErrors id="product-status-filter-errors" :messages="fieldErrors.status" />
        </div>
        <button class="primary-button" type="submit" :disabled="loading">{{ loading ? '查询中…' : '查询' }}</button>
      </form>

      <p v-if="categoryError" class="list-error" role="alert">
        <span>分类加载失败：{{ categoryError }}，暂不能新增商品。</span>
        <button type="button" @click="loadCategories">重新加载</button>
      </p>
      <div v-if="error" class="list-error" role="alert">
        <span>{{ error }}</span>
        <button type="button" :disabled="loading" @click="list.load">重新加载</button>
      </div>
      <FieldErrors id="product-page-errors" :messages="fieldErrors.page" />
      <FieldErrors id="product-size-errors" :messages="fieldErrors.size" />

      <div class="table-scroll" :aria-busy="loading">
        <table class="admin-table">
          <caption class="visually-hidden">本店商品列表</caption>
          <thead>
            <tr>
              <th scope="col">商品</th><th scope="col">分类</th><th scope="col">单价</th>
              <th scope="col">库存</th><th scope="col">状态</th><th scope="col">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="product in records" :key="product.id">
              <td>
                <div class="product-cell">
                  <ProductImage :src="product.image" :alt="product.name" ratio="1 / 1" />
                  <div><strong>{{ product.name }}</strong><small>{{ product.spec }} · 商品 ID：{{ product.id }}</small></div>
                </div>
              </td>
              <td>{{ categoryLabel(product.categoryId) }}</td>
              <td>¥{{ product.price }}</td>
              <td :class="{ 'stock-low': product.stock === 0 }">{{ product.stock }} 件</td>
              <td><span :class="['status-pill', product.status === 1 ? 'enabled' : 'disabled']">{{ product.status === 1 ? '已上架' : '已下架' }}</span></td>
              <td>
                <div class="row-actions">
                  <button type="button" :disabled="loading || saving || stockBusy || statusBusy || stopBusy" @click="openEdit(product)">编辑</button>
                  <button type="button" :disabled="loading || saving || stockBusy || statusBusy || stopBusy" @click="openStock(product)">库存</button>
                  <button type="button" :disabled="loading || saving || stockBusy || statusBusy || stopBusy" @click="openStatus(product)">{{ statusActionLabel(product) }}</button>
                  <button type="button" class="danger-button" :disabled="loading || saving || stockBusy || statusBusy || stopBusy" @click="openDelete(product)">删除</button>
                </div>
              </td>
            </tr>
            <tr v-if="!records.length">
              <td colspan="6" class="empty-state">{{ loading ? '正在加载商品…' : error ? '商品列表暂时不可用' : '暂无符合条件的商品' }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <AdminPagination :page="query.page" :size="query.size" :total="total" :disabled="loading"
        @page="list.changePage" @size="list.changeSize" />
    </section>
    <p class="management-note">本店编号：{{ store.user?.merchantId }}（只读）。普通编辑不会修改库存与上下架状态；分类禁用后不能上架。</p>

    <div v-if="formOpen" class="admin-overlay" @keydown.esc="closeForm">
      <section class="admin-dialog product-dialog" role="dialog" aria-modal="true" aria-labelledby="product-form-title" :aria-busy="saving">
        <div class="dialog-heading">
          <h2 id="product-form-title">{{ creating ? '新增商品' : '编辑商品' }}</h2>
          <button type="button" :disabled="saving" aria-label="关闭表单" @click="closeForm">关闭</button>
        </div>
        <form novalidate autocomplete="off" @submit.prevent="submitForm">
          <div class="merchant-form-grid">
            <div v-for="field in formFields" :key="field.name" :class="['form-field', { 'full-width': field.type === 'textarea' }]">
              <label :for="`product-${field.name}`">{{ field.label }}<span v-if="!field.optional" class="required-mark" aria-hidden="true"> *</span><span v-else class="optional-mark">（选填）</span></label>
              <select v-if="field.type === 'select'" :id="`product-${field.name}`" v-model="form[field.name]" :disabled="saving"
                :aria-invalid="Boolean(formErrors[field.name])" :aria-describedby="`product-${field.name}-errors`">
                <option value="">请选择本店分类</option>
                <option v-for="category in categories" :key="category.id" :value="category.id">{{ categoryLabel(category.id) }}</option>
              </select>
              <textarea v-else-if="field.type === 'textarea'" :id="`product-${field.name}`" v-model="form[field.name]"
                :disabled="saving" :aria-invalid="Boolean(formErrors[field.name])"
                :aria-describedby="`product-${field.name}-errors`" rows="3" />
              <input v-else :id="`product-${field.name}`" v-model="form[field.name]"
                :type="field.type === 'number' ? 'number' : 'text'" :step="field.type === 'number' ? '1' : undefined"
                :disabled="saving" :aria-invalid="Boolean(formErrors[field.name])"
                :aria-describedby="`product-${field.name}-errors`">
              <small v-if="field.hint" class="field-hint">{{ field.hint }}</small>
              <FieldErrors :id="`product-${field.name}-errors`" :messages="formErrors[field.name]" />
            </div>
          </div>
          <p v-if="submitError" class="operation-error" role="alert">{{ submitError }}，可保留当前内容后重试。</p>
          <div class="dialog-actions">
            <button type="button" :disabled="saving" @click="closeForm">取消</button>
            <button class="primary-button" type="submit" :disabled="saving">{{ saving ? '正在保存…' : creating ? '创建' : '保存' }}</button>
          </div>
        </form>
      </section>
    </div>

    <div v-if="stockTarget" class="admin-overlay" @keydown.esc="!stockBusy && (stockTarget = null)">
      <section class="admin-dialog stock-dialog" role="dialog" aria-modal="true" aria-labelledby="stock-title" :aria-busy="stockBusy">
        <h2 id="stock-title">调整库存</h2>
        <p class="stock-summary">“{{ stockTarget.name }}” {{ stockTarget.spec }}，当前库存 <strong>{{ stockTarget.stock }}</strong> 件。</p>
        <form novalidate @submit.prevent="submitStock">
          <div class="merchant-form-grid">
            <div class="form-field">
              <label for="stock-delta">增减量<span class="required-mark" aria-hidden="true"> *</span></label>
              <input id="stock-delta" v-model="stockForm.delta" type="number" step="1" :disabled="stockBusy"
                :aria-invalid="Boolean(stockErrors.delta)" aria-describedby="stock-delta-errors">
              <small class="field-hint">正数增加、负数减少，不能为 0。</small>
              <FieldErrors id="stock-delta-errors" :messages="stockErrors.delta" />
            </div>
            <div class="form-field">
              <label for="stock-expected">提交时原库存</label>
              <input id="stock-expected" :value="stockForm.expectedStock" type="number" readonly disabled>
              <small class="field-hint">用于防止覆盖其他人或订单造成的库存变化。</small>
              <FieldErrors id="stock-expected-errors" :messages="stockErrors.expectedStock" />
            </div>
          </div>
          <p v-if="stockError" class="operation-error" role="alert">{{ stockError }}</p>
          <div class="dialog-actions">
            <button type="button" :disabled="stockBusy" @click="stockTarget = null">取消</button>
            <button class="primary-button" type="submit" :disabled="stockBusy">{{ stockBusy ? '正在提交…' : '确认调整' }}</button>
          </div>
        </form>
      </section>
    </div>

    <ConfirmDialog v-if="statusTarget"
      :title="statusTarget.next === 1 ? '确认上架商品' : '确认下架商品'"
      :description="statusTarget.next === 1
        ? `上架“${statusTarget.name}”后顾客可浏览并下单；分类必须处于启用状态。`
        : `下架“${statusTarget.name}”后顾客不可见，已有订单不受影响。`"
      :confirm-label="statusTarget.next === 1 ? '确认上架' : '确认下架'"
      :busy="statusBusy" :error="statusError" :field-errors="statusErrors"
      @cancel="statusTarget = null" @confirm="submitStatus" />

    <ConfirmDialog v-if="stopTarget" title="删除商品"
      :description="`删除“${stopTarget.name}”前会检查所有订单明细（含已取消订单）。已有历史引用的商品只能下架，不能删除。`"
      confirm-label="确认删除" :busy="stopBusy" :error="stopError" :field-errors="stopErrors"
      @cancel="stopTarget = null" @confirm="submitDelete" />
  </section>
</template>
