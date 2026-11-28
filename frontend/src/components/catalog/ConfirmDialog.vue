<script setup>
import { computed } from 'vue'
import FieldErrors from '../admin/FieldErrors.vue'

const props = defineProps({
  title: { type: String, required: true },
  description: { type: String, default: '' },
  confirmLabel: { type: String, default: '确认' },
  busyLabel: { type: String, default: '正在提交…' },
  busy: Boolean,
  error: { type: String, default: '' },
  fieldErrors: { type: Object, default: () => ({}) },
})
defineEmits(['confirm', 'cancel'])
const messages = computed(() => Object.values(props.fieldErrors).flat())
</script>

<template>
  <div class="admin-overlay" @keydown.esc="!busy && $emit('cancel')">
    <section class="admin-dialog status-dialog" role="dialog" aria-modal="true" aria-labelledby="catalog-confirm-title" :aria-busy="busy">
      <h2 id="catalog-confirm-title">{{ title }}</h2>
      <p v-if="description">{{ description }}</p>
      <p v-if="error" class="operation-error" role="alert">{{ error }}</p>
      <FieldErrors id="catalog-confirm-errors" :messages="messages" />
      <div class="dialog-actions">
        <button type="button" :disabled="busy" @click="$emit('cancel')">取消</button>
        <button type="button" class="primary-button" :disabled="busy" @click="$emit('confirm')">{{ busy ? busyLabel : confirmLabel }}</button>
      </div>
    </section>
  </div>
</template>
