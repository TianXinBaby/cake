<script setup>
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  createCategory, deleteCategory, listCategories, setCategoryStatus, updateCategory,
} from '../../api/catalog.js'
import { useMerchantAccess, useMerchantCategoryList } from '../../catalog/useMerchantCatalog.js'
import { categoryInput, fieldErrorsFrom } from '../../catalog/validation.js'
import FieldErrors from '../../components/admin/FieldErrors.vue'
import ConfirmDialog from '../../components/catalog/ConfirmDialog.vue'

const { store, handleError } = useMerchantAccess()
const list = useMerchantCategoryList(listCategories)
const { status, records, loading, error, fieldErrors } = list

const formOpen = ref(false)
const editing = ref(null)
const form = ref({ name: '', sort: '0' })
const formErrors = ref({})
const submitError = ref('')
const saving = ref(false)

const statusTarget = ref(null)
const statusBusy = ref(false)
const statusError = ref('')
const statusErrors = ref({})

const deleteTarget = ref(null)
const deleteBusy = ref(false)
const deleteError = ref('')
const deleteErrors = ref({})

const creating = computed(() => editing.value === null)

function formatTime(value) {
  return typeof value === 'string' && value.length >= 16 ? `${value.slice(0, 10)} ${value.slice(11, 16)}` : '—'
}

function openCreate() {
  if (saving.value) return
  editing.value = null
  form.value = { name: '', sort: '0' }
  formErrors.value = {}
  submitError.value = ''
  formOpen.value = true
}

function openEdit(category) {
  if (saving.value) return
  editing.value = category
  form.value = { name: category.name, sort: String(category.sort) }
  formErrors.value = {}
  submitError.value = ''
  formOpen.value = true
}

function closeForm() {
  if (saving.value) return
  formOpen.value = false
  editing.value = null
}

async function submitForm() {
  if (saving.value) return
  const { payload, errors } = categoryInput(form.value)
  formErrors.value = errors
  submitError.value = ''
  if (Object.keys(errors).length) return
  saving.value = true
  const token = store.token
  try {
    if (creating.value) await createCategory(payload)
    else await updateCategory(editing.value.id, payload)
    ElMessage.success(creating.value ? '分类已创建' : '分类已保存')
    formOpen.value = false
    editing.value = null
    await list.load()
  } catch (reason) {
    formErrors.value = fieldErrorsFrom(reason, ['name', 'sort'])
    submitError.value = reason.message
    await handleError(reason, token)
  } finally {
    saving.value = false
  }
}

function openStatus(category) {
  if (statusBusy.value) return
  statusTarget.value = { id: category.id, name: category.name, next: category.status === 1 ? 0 : 1 }
  statusError.value = ''
  statusErrors.value = {}
}

async function submitStatus() {
  if (statusBusy.value || !statusTarget.value) return
  statusBusy.value = true
  const token = store.token
  try {
    await setCategoryStatus(statusTarget.value.id, statusTarget.value.next)
    ElMessage.success(statusTarget.value.next === 1 ? '分类已启用' : '分类已禁用')
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

function openDelete(category) {
  if (deleteBusy.value) return
  deleteTarget.value = { id: category.id, name: category.name }
  deleteError.value = ''
  deleteErrors.value = {}
}

async function submitDelete() {
  if (deleteBusy.value || !deleteTarget.value) return
  deleteBusy.value = true
  const token = store.token
  try {
    await deleteCategory(deleteTarget.value.id)
    ElMessage.success('分类已删除')
    deleteTarget.value = null
    await list.load()
  } catch (reason) {
    deleteError.value = reason.message
    await handleError(reason, token)
  } finally {
    deleteBusy.value = false
  }
}

async function changeStatusFilter() {
  await list.load()
}
</script>

<template>
  <section class="management-page">
    <div class="page-heading">
      <div>
        <span class="eyebrow">商家中心 / 分类</span>
        <h1>分类管理</h1>
        <p>仅维护本店分类；同店名称唯一，不同店铺可以使用相同名称。</p>
      </div>
      <button class="primary-button" type="button" :disabled="saving" @click="openCreate">新增分类</button>
    </div>

    <section class="management-card">
      <form class="admin-filters" novalidate @submit.prevent="changeStatusFilter">
        <div>
          <label for="category-status-filter">分类状态</label>
          <select id="category-status-filter" v-model="status" :aria-invalid="Boolean(fieldErrors.status)"
            aria-describedby="category-status-errors">
            <option value="">全部状态</option>
            <option value="1">启用</option>
            <option value="0">禁用</option>
          </select>
          <FieldErrors id="category-status-errors" :messages="fieldErrors.status" />
        </div>
        <button class="primary-button" type="submit" :disabled="loading">{{ loading ? '查询中…' : '查询' }}</button>
      </form>

      <div v-if="error" class="list-error" role="alert">
        <span>{{ error }}</span>
        <button type="button" :disabled="loading" @click="list.load">重新加载</button>
      </div>

      <div class="table-scroll" :aria-busy="loading">
        <table class="admin-table">
          <caption class="visually-hidden">本店分类列表</caption>
          <thead>
            <tr><th scope="col">分类名称</th><th scope="col">排序</th><th scope="col">状态</th><th scope="col">最后修改</th><th scope="col">操作</th></tr>
          </thead>
          <tbody>
            <tr v-for="category in records" :key="category.id">
              <td><strong>{{ category.name }}</strong><small>分类 ID：{{ category.id }}</small></td>
              <td>{{ category.sort }}</td>
              <td><span :class="['status-pill', category.status === 1 ? 'enabled' : 'disabled']">{{ category.status === 1 ? '启用' : '禁用' }}</span></td>
              <td>{{ formatTime(category.updateTime) }}</td>
              <td>
                <div class="row-actions">
                  <button type="button" :disabled="loading || saving || statusBusy || deleteBusy" @click="openEdit(category)">编辑</button>
                  <button type="button" :disabled="loading || saving || statusBusy || deleteBusy" @click="openStatus(category)">{{ category.status === 1 ? '禁用' : '启用' }}</button>
                  <button type="button" class="danger-button" :disabled="loading || saving || statusBusy || deleteBusy" @click="openDelete(category)">删除</button>
                </div>
              </td>
            </tr>
            <tr v-if="!records.length">
              <td colspan="5" class="empty-state">{{ loading ? '正在加载分类…' : error ? '分类列表暂时不可用' : '暂无符合条件的分类' }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <p class="management-note">禁用分类前须先下架该分类的全部商品；有商品或上架商品的分类不能删除。历史订单保存的是下单时的商品快照。</p>
    </section>

    <div v-if="formOpen" class="admin-overlay" @keydown.esc="closeForm">
      <section class="admin-dialog category-dialog" role="dialog" aria-modal="true" aria-labelledby="category-form-title" :aria-busy="saving">
        <div class="dialog-heading">
          <h2 id="category-form-title">{{ creating ? '新增分类' : '编辑分类' }}</h2>
          <button type="button" :disabled="saving" aria-label="关闭表单" @click="closeForm">关闭</button>
        </div>
        <form novalidate autocomplete="off" @submit.prevent="submitForm">
          <div class="merchant-form-grid">
            <div class="form-field full-width">
              <label for="category-name">分类名称<span class="required-mark" aria-hidden="true"> *</span></label>
              <input id="category-name" v-model="form.name" maxlength="30" :disabled="saving"
                :aria-invalid="Boolean(formErrors.name)" aria-describedby="category-name-errors">
              <small class="field-hint">1～30 个字符；保存时自动去除首尾空白。</small>
              <FieldErrors id="category-name-errors" :messages="formErrors.name" />
            </div>
            <div class="form-field full-width">
              <label for="category-sort">排序<span class="required-mark" aria-hidden="true"> *</span></label>
              <input id="category-sort" v-model="form.sort" type="number" step="1" min="0" :disabled="saving"
                :aria-invalid="Boolean(formErrors.sort)" aria-describedby="category-sort-errors">
              <small class="field-hint">非负整数；数值小的分类排在前面，相同值按 ID 升序。</small>
              <FieldErrors id="category-sort-errors" :messages="formErrors.sort" />
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

    <ConfirmDialog v-if="statusTarget"
      :title="statusTarget.next === 1 ? '确认启用分类' : '确认禁用分类'"
      :description="statusTarget.next === 1
        ? `启用“${statusTarget.name}”后，该分类下的已上架商品继续展示。`
        : `禁用“${statusTarget.name}”前必须下架该分类下的全部商品，否则操作会被拒绝。`"
      :confirm-label="statusTarget.next === 1 ? '确认启用' : '确认禁用'"
      :busy="statusBusy" :error="statusError" :field-errors="statusErrors"
      @cancel="statusTarget = null" @confirm="submitStatus" />

    <ConfirmDialog v-if="deleteTarget" title="删除分类"
      :description="`仅当“${deleteTarget.name}”下没有任何商品时可删除；已有商品时请先转移或删除商品。`"
      confirm-label="确认删除" :busy="deleteBusy" :error="deleteError" :field-errors="deleteErrors"
      @cancel="deleteTarget = null" @confirm="submitDelete" />
  </section>
</template>
