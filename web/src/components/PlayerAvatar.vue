<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { initials } from '../lib/format'

const props = withDefaults(
  defineProps<{
    src?: string | null
    name?: string | null
    size?: number
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
/** Circle: inset to the inscribed square so border-radius does not slice corners. Tile: clear the 12px radius. */
const inset = computed(() => (props.tile ? 4 : Math.ceil(props.size * 0.1465)))
</script>

<template>
  <img
    v-if="showImage"
    class="player-avatar"
    :class="{ tile }"
    :src="src!"
    :alt="name || 'Фото игрока'"
    :style="{ width: `${size}px`, height: `${size}px`, padding: `${inset}px` }"
    @error="broken = true"
  />
  <span
    v-else
    class="player-avatar player-avatar--fallback"
    :class="{ tile }"
    :style="{ width: `${size}px`, height: `${size}px` }"
    aria-hidden="true"
  >{{ label }}</span>
</template>

<style scoped>
.player-avatar {
  display: block;
  flex: 0 0 auto;
  box-sizing: border-box;
  border-radius: 999px;
  object-fit: contain;
  object-position: center;
  background: var(--surface, #fff);
}
.player-avatar.tile {
  border-radius: 12px;
}
.player-avatar--fallback {
  display: grid;
  place-items: center;
  background: var(--accent-soft);
  color: var(--accent);
  font-weight: 800;
  font-size: 0.95em;
}
</style>
