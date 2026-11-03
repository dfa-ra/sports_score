<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { initials } from '../../lib/format'

const props = defineProps<{ src?: string | null; name?: string | null }>()
const broken = ref(false)
watch(() => props.src, () => { broken.value = false })
const letters = computed(() => initials(props.name || ''))
</script>

<template>
  <span class="ava">
    <img v-if="src && !broken" :src="src" alt="" @error="broken = true" />
    <template v-else>{{ letters }}</template>
  </span>
</template>

<style scoped>
.ava {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: var(--bg-wash);
  color: var(--navy);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  font-size: 0.75rem;
  font-weight: 800;
  box-shadow: inset 0 0 0 1px var(--line-strong);
  flex: 0 0 auto;
}
.ava img { width: 100%; height: 100%; object-fit: cover; display: block; }
</style>
