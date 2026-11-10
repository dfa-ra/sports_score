<script setup lang="ts">
import { nextTick, onMounted, onUnmounted, ref, watch } from 'vue'

const props = withDefaults(defineProps<{
  full: string
  short: string
  /** Below this width the short form is used even when the full name would fit. */
  forceBelow?: number
}>(), {
  forceBelow: 0,
})

const el = ref<HTMLElement | null>(null)
const text = ref(props.full)
let phoneQuery: MediaQueryList | null = null
let forceQuery: MediaQueryList | null = null
let observer: ResizeObserver | null = null

function phone() {
  return !!phoneQuery?.matches
}

async function measure() {
  const node = el.value
  if (!node || !phone()) {
    text.value = props.full
    return
  }
  if (props.forceBelow > 0 && forceQuery?.matches) {
    text.value = props.short || props.full
    return
  }
  text.value = props.full
  await nextTick()
  const box = el.value
  if (!box || box.clientWidth < 1) return
  if (box.scrollWidth > box.clientWidth + 1) text.value = props.short || props.full
}

onMounted(() => {
  phoneQuery = window.matchMedia('(max-width: 720px)')
  if (props.forceBelow > 0) forceQuery = window.matchMedia(`(max-width: ${props.forceBelow}px)`)
  phoneQuery.addEventListener('change', measure)
  forceQuery?.addEventListener('change', measure)
  observer = new ResizeObserver(() => { void measure() })
  if (el.value) observer.observe(el.value)
  void measure()
})

onUnmounted(() => {
  phoneQuery?.removeEventListener('change', measure)
  forceQuery?.removeEventListener('change', measure)
  observer?.disconnect()
})

watch(() => [props.full, props.short, props.forceBelow], () => { void measure() })
</script>

<template>
  <span ref="el" class="fit-name">{{ text }}</span>
</template>

<style scoped>
.fit-name {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
