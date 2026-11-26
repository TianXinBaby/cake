import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth.js'
import { fieldErrorsFrom } from './validation.js'

export function useAdminAccess() {
  const router = useRouter()
  const store = useAuthStore()
  return {
    store,
    async handleError(error, token) {
      if ((error.status === 401 || error.code === 40102) && store.token === token) {
        store.clearAuthentication()
        await router.replace({ path: '/', query: { notice: 'admin-login' } })
      } else if (error.code === 40102 && !store.isAuthenticated) {
        await router.replace({ path: '/', query: { notice: 'admin-login' } })
      }
    },
  }
}

export function useAdminList(fetchPage) {
  const { store, handleError } = useAdminAccess()
  const filters = reactive({ keyword: '', status: '' })
  const query = reactive({ page: 1, size: 10, keyword: '', status: undefined })
  const records = ref([])
  const total = ref(0)
  const loading = ref(false)
  const error = ref('')
  const fieldErrors = ref({})
  let request = 0

  async function load() {
    const currentRequest = ++request
    const token = store.token
    loading.value = true
    error.value = ''
    fieldErrors.value = {}
    try {
      const page = await fetchPage({ ...query })
      if (request === currentRequest && store.token === token) {
        records.value = page.records
        total.value = page.total
      }
    } catch (reason) {
      if (request === currentRequest && (store.token === token || !store.isAuthenticated)) {
        records.value = []
        total.value = 0
        error.value = reason.message
        fieldErrors.value = fieldErrorsFrom(reason, ['keyword', 'status', 'page', 'size'])
        await handleError(reason, token)
      }
    } finally {
      if (request === currentRequest) loading.value = false
    }
  }

  async function search() {
    const keyword = filters.keyword.trim()
    if (Array.from(keyword).length > 60) {
      fieldErrors.value = { keyword: ['关键字长度不能超过60个字符'] }
      return
    }
    Object.assign(query, { page: 1, keyword, status: filters.status === '' ? undefined : Number(filters.status) })
    await load()
  }

  async function changePage(page) {
    query.page = page
    await load()
  }

  async function changeSize(size) {
    query.size = size
    query.page = 1
    await load()
  }

  onMounted(load)
  return { filters, query, records, total, loading, error, fieldErrors, load, search, changePage, changeSize }
}
