<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { listCustomers, setCustomerStatus } from '../../api/admin.js'
import { useAdminAccess, useAdminList } from '../../admin/useAdminList.js'
import { fieldErrorsFrom } from '../../admin/validation.js'
import AdminPagination from '../../components/admin/AdminPagination.vue'
import FieldErrors from '../../components/admin/FieldErrors.vue'
import StatusConfirm from '../../components/admin/StatusConfirm.vue'

const list = useAdminList(listCustomers)
const { store, handleError } = useAdminAccess()
const { filters, query, records, total, loading, error, fieldErrors } = list
const target = ref(null)
const changing = ref(false)
const statusError = ref('')
const statusErrors = ref({})

function confirmStatus(user) {
  if (changing.value) return
  target.value = { id: user.id, name: user.nickname, status: user.status === 1 ? 0 : 1 }
  statusError.value = ''
  statusErrors.value = {}
}

async function submitStatus() {
  if (changing.value || !target.value) return
  const token = store.token
  changing.value = true
  try {
    await setCustomerStatus(target.value.id, target.value.status)
    ElMessage.success(target.value.status === 1 ? '用户账号已启用，请用户重新登录' : '用户账号已停用')
    target.value = null
    await list.load()
  } catch (reason) {
    statusError.value = reason.message
    statusErrors.value = fieldErrorsFrom(reason, ['status'])
    await handleError(reason, token)
  } finally { changing.value = false }
}
</script>

<template>
  <section class="management-page">
    <div class="page-heading"><div><span class="eyebrow">平台管理 / 用户</span><h1>用户管理</h1><p>查询顾客账号并管理启用状态</p></div></div>
    <section class="management-card">
      <form class="admin-filters" novalidate @submit.prevent="list.search">
        <div class="filter-keyword"><label for="user-keyword">昵称或手机号</label><input id="user-keyword" v-model="filters.keyword" placeholder="搜索用户昵称或手机号" :aria-invalid="Boolean(fieldErrors.keyword)" aria-describedby="user-keyword-errors"><FieldErrors id="user-keyword-errors" :messages="fieldErrors.keyword" /></div>
        <div><label for="user-status">账号状态</label><select id="user-status" v-model="filters.status"><option value="">全部状态</option><option value="1">启用</option><option value="0">停用</option></select><FieldErrors id="user-status-errors" :messages="fieldErrors.status" /></div>
        <button type="submit" class="primary-button" :disabled="loading">{{ loading ? '查询中…' : '查询' }}</button>
      </form>
      <div v-if="error" class="list-error" role="alert"><span>{{ error }}</span><button type="button" :disabled="loading" @click="list.load">重新加载</button></div>
      <FieldErrors id="user-page-errors" :messages="fieldErrors.page" /><FieldErrors id="user-size-errors" :messages="fieldErrors.size" />
      <div class="table-scroll" :aria-busy="loading"><table class="admin-table"><caption class="visually-hidden">顾客账号列表</caption><thead><tr><th scope="col">用户昵称</th><th scope="col">登录账号</th><th scope="col">手机号</th><th scope="col">创建时间</th><th scope="col">账号状态</th><th scope="col">操作</th></tr></thead><tbody>
        <tr v-for="user in records" :key="user.id"><td><strong>{{ user.nickname }}</strong><small>用户 ID：{{ user.id }}</small></td><td>{{ user.username }}</td><td>{{ user.phone }}</td><td>{{ user.createTime }}</td><td><span :class="['status-pill', user.status === 1 ? 'enabled' : 'disabled']">{{ user.status === 1 ? '启用' : '停用' }}</span></td><td><button type="button" :disabled="loading || changing" @click="confirmStatus(user)">{{ user.status === 1 ? '停用' : '启用' }}</button></td></tr>
        <tr v-if="!records.length"><td colspan="6" class="empty-state">{{ loading ? '正在加载用户…' : error ? '用户列表暂时不可用' : '暂无符合条件的用户' }}</td></tr>
      </tbody></table></div>
      <AdminPagination :page="query.page" :size="query.size" :total="total" :disabled="loading" @page="list.changePage" @size="list.changeSize" />
    </section>
    <p class="management-note">仅管理顾客账号。启停后保留历史订单，账号状态变更后原登录凭证失效。</p>
    <StatusConfirm v-if="target" :title="target.status === 1 ? '确认启用用户账号' : '确认停用用户账号'" :description="target.status === 1 ? `启用“${target.name}”后，用户须重新登录。` : `停用“${target.name}”后，用户无法访问需要登录的服务；历史订单保留。`" :busy="changing" :error="statusError" :field-errors="statusErrors" @cancel="target = null" @confirm="submitStatus" />
  </section>
</template>
