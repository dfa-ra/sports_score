<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { initials } from '../lib/format'

const props = withDefaults(
  defineProps<{
    src?: string | null
    name?: string | null
    size?: number
  }>(),
  { size: 56 },
)

const broken = ref(false)
watch(
  () => props.src,
  () => {
    broken.value = false
  },
)

const showImage = computed(() => Boolean(props.src) && !broken.value)
const label = computed(() => initials(props.name || ''))
</script>

<template>
  <img
    v-if="showImage"
    class="player-avatar"
    :src="src!"
    :alt="name || 'Фото игрока'"
    :style="{ width: `${size}px`, height: `${size}px` }"
    @error="broken = true"
  />
  <span
    v-else
    class="player-avatar player-avatar--fallback"
    :style="{ width: `${size}px`, height: `${size}px` }"
    aria-hidden="true"
  >{{ label }}</span>
</template>

<style scoped>
.player-avatar {
  display: block;
  flex: 0 0 auto;
  border-radius: 999px;
  object-fit: cover;
  background: var(--accent-soft);
}
.player-avatar--fallback {
  display: grid;
  place-items: center;
  color: var(--accent);
  font-weight: 800;
  font-size: 0.95em;
}
</style>
