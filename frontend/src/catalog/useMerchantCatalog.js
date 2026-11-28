import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth.js'
import { fieldErrorsFrom, keywordError } from './validation.js'

export function useMerchantAccess() {
  const router = useRouter()
  const store = useAuthStore()
  return {
    store,
    async handleError(error, token) {
      if ((error.status === 401 || error.code === 40102) && store.token === token) {
        store.clearAuthentication()
        await router.replace({ path: '/login', query: { redirect: router.currentRoute.value.fullPath } })
      } else if (error.code === 40102 && !store.isAuthenticated) {
        await router.replace('/login')
      }
    },
  }
}

export function useMerchantProductList(fetchPage) {
  const { store, handleError } = useMerchantAccess()
  const filters = reactive({ keyword: '', categoryId: '', status: '' })
  const query = reactive({ page: 1, size: 10, keyword: '', categoryId: undefined, status: undefined })
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
        fieldErrors.value = fieldErrorsFrom(reason, ['keyword', 'status', 'categoryId', 'page', 'size'])
        await handleError(reason, token)
      }
    } finally {
      if (request === currentRequest) loading.value = false
    }
  }

  async function search() {
    const message = keywordError(filters.keyword)
    if (message) {
      fieldErrors.value = { keyword: [message] }
      return
    }
    Object.assign(query, {
      page: 1,
      keyword: filters.keyword.trim(),
      status: filters.status === '' ? undefined : Number(filters.status),
      categoryId: filters.categoryId === '' ? undefined : filters.categoryId,
    })
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

export function useMerchantCategoryList(fetchCategories) {
  const { store, handleError } = useMerchantAccess()
  const status = ref('')
  const records = ref([])
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
      const categories = await fetchCategories(status.value === '' ? {} : { status: Number(status.value) })
      if (request === currentRequest && store.token === token) records.value = categories
    } catch (reason) {
      if (request === currentRequest && (store.token === token || !store.isAuthenticated)) {
        records.value = []
        error.value = reason.message
        fieldErrors.value = fieldErrorsFrom(reason, ['status'])
        await handleError(reason, token)
      }
    } finally {
      if (request === currentRequest) loading.value = false
    }
  }

  onMounted(load)
  return { status, records, loading, error, fieldErrors, load }
}
