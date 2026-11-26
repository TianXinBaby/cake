<script setup>
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { createMerchant, editMerchant, listMerchants, setMerchantStatus } from '../../api/admin.js'
import { useAdminAccess, useAdminList } from '../../admin/useAdminList.js'
import { fieldErrorsFrom, merchantInput } from '../../admin/validation.js'
import AdminPagination from '../../components/admin/AdminPagination.vue'
import FieldErrors from '../../components/admin/FieldErrors.vue'
import StatusConfirm from '../../components/admin/StatusConfirm.vue'

const list = useAdminList(listMerchants)
const { store, handleError } = useAdminAccess()
const { filters, query, records, total, loading, error, fieldErrors } = list
const formOpen = ref(false)
const selectedId = ref(null)
const creating = computed(() => selectedId.value === null)
const form = reactive({ accountPhone: '', nickname: '', password: '', name: '', phone: '', address: '', description: '' })
const formErrors = ref({})
const submitError = ref('')
const saving = ref(false)
const statusTarget = ref(null)
const statusBusy = ref(false)
const statusError = ref('')
const statusErrors = ref({})
const fields = computed(() => [
  ...(creating.value ? [
    { name: 'accountPhone', label: '账号手机号', type: 'tel', hint: '创建后作为商家登录账号，不能在此修改。' },
    { name: 'nickname', label: '账号昵称', type: 'text' },
    { name: 'password', label: '初始密码', type: 'password', hint: '6～20位可见字符，不能包含空白。' },
  ] : []),
  { name: 'name', label: '店铺名称', type: 'text' },
  { name: 'phone', label: '店铺联系电话', type: 'tel', hint: '店铺业务联系电话，与登录账号分别维护。' },
  { name: 'address', label: '店铺地址', type: 'text' },
  { name: 'description', label: '店铺简介', type: 'textarea', optional: true },
])

function openForm(merchant = null) {
  if (saving.value || statusBusy.value) return
  selectedId.value = merchant?.id ?? null
  Object.assign(form, {
    accountPhone: '', nickname: '', password: '', name: merchant?.name ?? '',
    phone: merchant?.phone ?? '', address: merchant?.address ?? '', description: merchant?.description ?? '',
  })
  formErrors.value = {}
  submitError.value = ''
  formOpen.value = true
}

function closeForm() {
  if (saving.value) return
  formOpen.value = false
  form.password = ''
}

async function submitForm() {
  if (saving.value) return
  const { payload, errors } = merchantInput(form, creating.value)
  formErrors.value = errors
  submitError.value = ''
  if (Object.keys(errors).length) return
  const token = store.token
  saving.value = true
  try {
    if (creating.value) await createMerchant(payload)
    else await editMerchant(selectedId.value, payload)
    ElMessage.success(creating.value ? '商家账号和店铺创建成功' : '店铺信息已保存')
    formOpen.value = false
    form.password = ''
    await list.load()
  } catch (reason) {
    formErrors.value = fieldErrorsFrom(reason, fields.value.map((field) => field.name))
    submitError.value = reason.message
    await handleError(reason, token)
  } finally { saving.value = false }
}

function confirmStatus(merchant) {
  if (statusBusy.value || saving.value) return
  statusTarget.value = { id: merchant.id, name: merchant.name, status: merchant.status === 1 ? 0 : 1 }
  statusError.value = ''
  statusErrors.value = {}
}

async function submitStatus() {
  if (statusBusy.value || !statusTarget.value) return
  const token = store.token
  statusBusy.value = true
  try {
    await setMerchantStatus(statusTarget.value.id, statusTarget.value.status)
    ElMessage.success(statusTarget.value.status === 1 ? '店铺已启用' : '店铺已停用')
    statusTarget.value = null
    await list.load()
  } catch (reason) {
    statusError.value = reason.message
    statusErrors.value = fieldErrorsFrom(reason, ['status'])
    await handleError(reason, token)
  } finally { statusBusy.value = false }
}
</script>

<template>
  <section class="management-page">
    <div class="page-heading"><div><span class="eyebrow">平台管理 / 商家</span><h1>商家管理</h1><p>管理商家账号绑定与店铺信息</p></div><button class="primary-button" type="button" :disabled="saving || statusBusy" @click="openForm()">创建商家</button></div>
    <section class="management-card">
      <form class="admin-filters" novalidate @submit.prevent="list.search">
        <div class="filter-keyword"><label for="merchant-keyword">店铺名称</label><input id="merchant-keyword" v-model="filters.keyword" placeholder="搜索店铺名称" :aria-invalid="Boolean(fieldErrors.keyword)" aria-describedby="merchant-keyword-errors"><FieldErrors id="merchant-keyword-errors" :messages="fieldErrors.keyword" /></div>
        <div><label for="merchant-status">店铺状态</label><select id="merchant-status" v-model="filters.status"><option value="">全部状态</option><option value="1">启用</option><option value="0">停用</option></select><FieldErrors id="merchant-status-errors" :messages="fieldErrors.status" /></div>
        <button class="primary-button" type="submit" :disabled="loading">{{ loading ? '查询中…' : '查询' }}</button>
      </form>
      <div v-if="error" class="list-error" role="alert"><span>{{ error }}</span><button type="button" :disabled="loading" @click="list.load">重新加载</button></div>
      <FieldErrors id="merchant-page-errors" :messages="fieldErrors.page" /><FieldErrors id="merchant-size-errors" :messages="fieldErrors.size" />
      <div class="table-scroll" :aria-busy="loading">
        <table class="admin-table"><caption class="visually-hidden">店铺及绑定商家账号列表</caption><thead><tr><th scope="col">店铺名称</th><th scope="col">绑定账号</th><th scope="col">店铺联系电话</th><th scope="col">地址与简介</th><th scope="col">店铺状态</th><th scope="col">操作</th></tr></thead><tbody>
          <tr v-for="merchant in records" :key="merchant.id"><td><strong>{{ merchant.name }}</strong><small>店铺 ID：{{ merchant.id }}</small></td><td>{{ merchant.username }}<small>账号 ID：{{ merchant.userId }}</small></td><td>{{ merchant.phone }}</td><td class="merchant-description"><span>{{ merchant.address }}</span><small>{{ merchant.description || '暂无简介' }}</small></td><td><span :class="['status-pill', merchant.status === 1 ? 'enabled' : 'disabled']">{{ merchant.status === 1 ? '启用' : '停用' }}</span></td><td><div class="row-actions"><button type="button" :disabled="loading || saving || statusBusy" @click="openForm(merchant)">编辑</button><button type="button" :disabled="loading || saving || statusBusy" @click="confirmStatus(merchant)">{{ merchant.status === 1 ? '停用' : '启用' }}</button></div></td></tr>
          <tr v-if="!records.length"><td colspan="6" class="empty-state">{{ loading ? '正在加载店铺…' : error ? '店铺列表暂时不可用' : '暂无符合条件的店铺' }}</td></tr>
        </tbody></table>
      </div>
      <AdminPagination :page="query.page" :size="query.size" :total="total" :disabled="loading" @page="list.changePage" @size="list.changeSize" />
    </section>
    <p class="management-note">停用店铺后停止新订单，已支付的历史订单仍由所属商家履约。</p>

    <div v-if="formOpen" class="admin-overlay" @keydown.esc="closeForm">
      <section class="admin-dialog merchant-dialog" role="dialog" aria-modal="true" aria-labelledby="merchant-form-title" :aria-busy="saving">
        <div class="dialog-heading"><h2 id="merchant-form-title">{{ creating ? '创建商家账号与店铺' : '编辑店铺信息' }}</h2><button type="button" :disabled="saving" aria-label="关闭表单" @click="closeForm">关闭</button></div>
        <form novalidate autocomplete="off" @submit.prevent="submitForm">
          <div class="merchant-form-grid"><div v-for="field in fields" :key="field.name" :class="['form-field', { 'full-width': field.name === 'description' }]">
            <label :for="`merchant-${field.name}`">{{ field.label }}<span v-if="!field.optional" class="required-mark" aria-hidden="true"> *</span><span v-else class="optional-mark">（选填）</span></label>
            <textarea v-if="field.type === 'textarea'" :id="`merchant-${field.name}`" v-model="form[field.name]" :disabled="saving" :aria-invalid="Boolean(formErrors[field.name])" :aria-describedby="`merchant-${field.name}-errors`" rows="3" />
            <input v-else :id="`merchant-${field.name}`" v-model="form[field.name]" :type="field.type" :required="!field.optional" :disabled="saving" :autocomplete="field.type === 'password' ? 'new-password' : 'off'" :aria-invalid="Boolean(formErrors[field.name])" :aria-describedby="`merchant-${field.name}-errors`">
            <small v-if="field.hint" class="field-hint">{{ field.hint }}</small><FieldErrors :id="`merchant-${field.name}-errors`" :messages="formErrors[field.name]" />
          </div></div>
          <p v-if="submitError" class="operation-error" role="alert">{{ submitError }}，可保留当前内容后重试。</p>
          <div class="dialog-actions"><button type="button" :disabled="saving" @click="closeForm">取消</button><button class="primary-button" type="submit" :disabled="saving">{{ saving ? '正在保存…' : creating ? '创建' : '保存' }}</button></div>
        </form>
      </section>
    </div>
    <StatusConfirm v-if="statusTarget" :title="statusTarget.status === 1 ? '确认启用店铺' : '确认停用店铺'" :description="statusTarget.status === 1 ? `启用“${statusTarget.name}”后恢复店铺可用状态。` : `停用“${statusTarget.name}”后将阻止新订单；历史数据保留，已支付订单仍可履约。`" :busy="statusBusy" :error="statusError" :field-errors="statusErrors" @cancel="statusTarget = null" @confirm="submitStatus" />
  </section>
</template>
