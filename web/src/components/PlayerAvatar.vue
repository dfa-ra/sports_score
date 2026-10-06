<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { initials } from '../lib/format'

const props = withDefaults(
  defineProps<{
    src?: string | null
    name?: string | null
    size?: number
    /** Accepted so older call sites compile. The photo is always a plain circle. */
    tile?: boolean
  }>(),
  { size: 56, tile: false },
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
// Older screens pass `tile` for a rounded square. Player photos stay a circle.
void props.tile
</script>

<template>
  <span
    class="player-avatar"
    :class="{ 'player-avatar--fallback': !showImage }"
    :style="{ width: `${size}px`, height: `${size}px` }"
  >
    <img
      v-if="showImage"
      :src="src!"
      :alt="name || 'Фото игрока'"
      @error="broken = true"
    />
    <span v-else aria-hidden="true">{{ label }}</span>
  </span>
</template>

<style scoped>
.player-avatar {
  display: grid;
  place-items: center;
  flex: 0 0 auto;
  box-sizing: border-box;
  padding: 0;
  border: 0;
  border-radius: 50%;
  overflow: hidden;
  background: transparent;
  box-shadow: none;
  line-height: 1;
}
.player-avatar img {
  width: 0;
  height: 0;
  min-width: 100%;
  min-height: 100%;
  object-fit: cover;
  object-position: center;
  display: block;
  border: 0;
}
.player-avatar--fallback {
  background: var(--accent-soft);
  color: var(--accent);
  font-weight: 800;
  font-size: 0.95em;
}
</style>
