<script setup lang="ts">
import { BRAND_MARK_SRC } from '../lib/brand'

withDefaults(
  defineProps<{
    size?: number
    rounded?: 'circle' | 'tile'
  }>(),
  { size: 36, rounded: 'circle' },
)
</script>

<template>
  <img
    class="brand-mark"
    :class="rounded"
    :src="BRAND_MARK_SRC"
    alt=""
    :width="size"
    :height="size"
    :style="{ width: `${size}px`, height: `${size}px` }"
  />
</template>

<style scoped>
.brand-mark {
  display: block;
  object-fit: contain;
  background: transparent;
  flex-shrink: 0;
}
.brand-mark.circle { border-radius: 50%; }
.brand-mark.tile { border-radius: 12px; }
</style>
