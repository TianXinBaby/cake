<script setup>
import { computed } from 'vue'
const props = defineProps({ page: Number, size: Number, total: Number, disabled: Boolean })
defineEmits(['page', 'size'])
const pages = computed(() => Math.max(1, Math.ceil(props.total / props.size)))
</script>

<template>
  <nav class="admin-pagination" aria-label="列表分页">
    <span>共 {{ total }} 条，第 {{ page }} 页<span v-if="page <= pages"> / {{ pages }} 页</span></span>
    <label>每页
      <select :value="size" :disabled="disabled" @change="$emit('size', Number($event.target.value))" aria-label="每页条数">
        <option v-for="value in [10, 20, 50, 100]" :key="value" :value="value">{{ value }} 条</option>
      </select>
    </label>
    <button type="button" :disabled="disabled || page <= 1" @click="$emit('page', page - 1)">上一页</button>
    <button type="button" :disabled="disabled || page >= pages" @click="$emit('page', page + 1)">下一页</button>
  </nav>
</template>
