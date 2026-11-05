<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { initials } from '../lib/format'

const props = withDefaults(
  defineProps<{
    src?: string | null
    name?: string | null
    /** Pixel edge, or a token: sm 24 (22 on mobile), md 28, lg 36 (32 on mobile). */
    size?: number | 'sm' | 'md' | 'lg'
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
const token = computed(() => (props.size === 'sm' || props.size === 'md' || props.size === 'lg' ? props.size : null))
const box = computed(() => {
  if (token.value) return undefined
  const px = typeof props.size === 'number' ? props.size : 22
  const edge = `${px}px`
  return {
    width: edge,
    height: edge,
    minWidth: edge,
    maxWidth: edge,
    minHeight: edge,
    maxHeight: edge,
    fontSize: `${Math.max(8, Math.round(px * 0.38))}px`,
  }
})
</script>

<template>
  <img
    v-if="showImage"
    class="team-crest"
    :class="[token, { 'crest-enter': enterOnce }]"
    :src="src!"
    :alt="name || 'Эмблема'"
    :style="box"
    @error="broken = true"
  />
  <span
    v-else
    class="team-crest team-crest--fallback"
    :class="[token, { 'crest-enter': enterOnce }]"
    :style="box"
    aria-hidden="true"
  >{{ label }}</span>
</template>

<style scoped>
.team-crest {
  display: block;
  flex: 0 0 auto;
  box-sizing: border-box;
  border-radius: 50%;
  background: var(--accent-soft, #eef3f8);
  box-shadow: none;
  overflow: hidden;
}
img.team-crest {
  object-fit: contain;
  object-position: center;
  image-orientation: from-image;
  padding: 2px;
}
.team-crest.sm {
  width: 24px;
  height: 24px;
  min-width: 24px;
  max-width: 24px;
  min-height: 24px;
  max-height: 24px;
  font-size: 9px;
}
.team-crest.md {
  width: 28px;
  height: 28px;
  min-width: 28px;
  max-width: 28px;
  min-height: 28px;
  max-height: 28px;
  font-size: 11px;
}
.team-crest.lg {
  width: 36px;
  height: 36px;
  min-width: 36px;
  max-width: 36px;
  min-height: 36px;
  max-height: 36px;
  font-size: 13px;
}
.team-crest--fallback {
  display: grid;
  place-items: center;
  color: var(--navy);
  font-weight: 800;
  font-style: normal;
  line-height: 1;
}
@media (max-width: 719px) {
  .team-crest.sm {
    width: 22px;
    height: 22px;
    min-width: 22px;
    max-width: 22px;
    min-height: 22px;
    max-height: 22px;
  }
  .team-crest.lg {
    width: 32px;
    height: 32px;
    min-width: 32px;
    max-width: 32px;
    min-height: 32px;
    max-height: 32px;
  }
}
</style>
