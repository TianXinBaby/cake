<script setup>
defineProps({ messages: { type: Array, default: () => [] }, id: { type: String, required: true } })
</script>

<template>
  <ul v-if="messages.length" :id="id" class="field-errors" role="alert">
    <li v-for="(message, index) in messages" :key="index">{{ message }}</li>
  </ul>
</template>
