<script setup>
import { ref, watch } from 'vue'

const props = defineProps({
  src: { type: String, default: '' },
  alt: { type: String, default: '商品图片' },
  ratio: { type: String, default: '4 / 3' },
})
const failed = ref(false)
const placeholder = '/images/cake-placeholder.svg'
watch(() => props.src, () => { failed.value = false })
</script>

<template>
  <div class="product-image" :style="{ aspectRatio: ratio }">
    <img v-if="src && !failed" :src="src" :alt="alt" loading="lazy" @error="failed = true">
    <img v-else :src="placeholder" :alt="alt + '（暂无图片）'" class="product-image-placeholder">
  </div>
</template>
