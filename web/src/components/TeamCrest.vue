<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { initials } from '../lib/format'

const props = withDefaults(
  defineProps<{
    src?: string | null
    name?: string | null
    size?: number
    enterOnce?: boolean
  }>(),
  { size: 22, enterOnce: false },
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
    class="team-crest"
    :class="{ 'crest-enter': enterOnce }"
    :src="src!"
    :alt="name || 'Эмблема'"
    :width="size"
    :height="size"
    :style="{ width: `${size}px`, height: `${size}px` }"
    @error="broken = true"
  />
  <span
    v-else
    class="team-crest team-crest--fallback"
    :class="{ 'crest-enter': enterOnce }"
    :style="{ width: `${size}px`, height: `${size}px`, fontSize: `${Math.max(8, size * 0.38)}px` }"
    aria-hidden="true"
  >{{ label }}</span>
</template>

<style scoped>
.team-crest {
  display: block;
  flex: 0 0 auto;
  box-sizing: border-box;
  object-fit: contain;
  object-position: center;
  image-orientation: from-image;
  background: var(--accent-soft, #eef3f8);
  border-radius: 50%;
  padding: 2px;
}
.team-crest--fallback {
  display: grid;
  place-items: center;
  background: var(--accent-soft, #eef3f8);
  color: var(--navy);
  font-weight: 800;
  font-style: normal;
  line-height: 1;
}
</style>
