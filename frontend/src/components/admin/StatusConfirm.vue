<script setup>
import FieldErrors from './FieldErrors.vue'
defineProps({ title: String, description: String, busy: Boolean, error: String, fieldErrors: { type: Object, default: () => ({}) } })
defineEmits(['confirm', 'cancel'])
</script>

<template>
  <div class="admin-overlay" @keydown.esc="!busy && $emit('cancel')">
    <section class="admin-dialog status-dialog" role="dialog" aria-modal="true" aria-labelledby="status-title" :aria-busy="busy">
      <h2 id="status-title">{{ title }}</h2>
      <p>{{ description }}</p>
      <p v-if="error" class="operation-error" role="alert">{{ error }}</p>
      <FieldErrors id="status-errors" :messages="fieldErrors.status" />
      <div class="dialog-actions">
        <button type="button" :disabled="busy" @click="$emit('cancel')">取消</button>
        <button type="button" class="primary-button" :disabled="busy" @click="$emit('confirm')">{{ busy ? '正在提交…' : '确认' }}</button>
      </div>
    </section>
  </div>
</template>
