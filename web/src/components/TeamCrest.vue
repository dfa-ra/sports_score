<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { initials } from '../lib/format'

const props = withDefaults(
  defineProps<{
    src?: string | null
    name?: string | null
    size?: number
  }>(),
  { size: 22 },
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
const radius = computed(() => (props.size < 28 ? 4 : 8))
</script>

<template>
  <img
    v-if="showImage"
    class="team-crest"
    :src="src!"
    :alt="name || 'Эмблема'"
    :style="{ width: `${size}px`, height: `${size}px`, borderRadius: `${radius}px` }"
    @error="broken = true"
  />
  <span
    v-else
    class="team-crest team-crest--fallback"
    :style="{ width: `${size}px`, height: `${size}px`, borderRadius: `${radius}px`, fontSize: `${Math.max(8, size * 0.38)}px` }"
    aria-hidden="true"
  >{{ label }}</span>
</template>

<style scoped>
.team-crest {
  display: block;
  flex: 0 0 auto;
  object-fit: cover;
  background: var(--accent-soft);
}
.team-crest--fallback {
  display: grid;
  place-items: center;
  color: var(--navy);
  font-weight: 800;
  font-style: normal;
  line-height: 1;
}
</style>
