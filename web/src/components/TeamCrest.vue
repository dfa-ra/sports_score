<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { initials } from '../lib/format'

const props = withDefaults(
  defineProps<{
    src?: string | null
    name?: string | null
    size?: number
    enterOnce?: boolean
    /** Stretch to the parent circle. Home lists size the parent, not this box. */
    fill?: boolean
  }>(),
  { size: 28, enterOnce: false, fill: false },
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
const boxStyle = computed(() => {
  if (props.fill) return undefined
  const px = `${props.size}px`
  return { width: px, height: px, minWidth: px, minHeight: px, flex: `0 0 ${px}` }
})
</script>

<template>
  <span
    class="team-crest"
    :class="{ 'crest-enter': enterOnce, 'is-fallback': !showImage, fill }"
    :style="boxStyle"
  >
    <img
      v-if="showImage"
      :src="src!"
      :alt="name || 'Эмблема'"
      :width="size"
      :height="size"
      @error="broken = true"
    />
    <span v-else class="letters" aria-hidden="true">{{ label }}</span>
  </span>
</template>

<style scoped>
.team-crest {
  display: inline-grid;
  place-items: center;
  box-sizing: border-box;
  border-radius: 50%;
  overflow: hidden;
  background: var(--surface, #fff);
  container-type: size;
}
.team-crest.is-fallback {
  background: var(--accent-soft);
  color: var(--navy);
}
.team-crest.fill {
  width: 100%;
  height: 100%;
  min-width: 0;
  min-height: 0;
}
.team-crest.fill.is-fallback {
  background: transparent;
  color: inherit;
}
.team-crest img {
  width: 100%;
  height: 100%;
  max-width: 100%;
  max-height: 100%;
  min-width: 0;
  min-height: 0;
  object-fit: contain;
  object-position: center;
  image-orientation: from-image;
  display: block;
}
.letters {
  font-weight: 800;
  font-style: normal;
  line-height: 1;
  font-size: 38cqmin;
}
</style>
