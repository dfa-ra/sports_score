<script setup lang="ts">
import { ref, watch } from 'vue'

const props = defineProps<{
  value?: string | number | null
}>()

const popping = ref(false)
let frame = 0

watch(
  () => (props.value == null ? '' : String(props.value)),
  (next, prev) => {
    if (prev === undefined || next === prev) return
    popping.value = false
    cancelAnimationFrame(frame)
    frame = requestAnimationFrame(() => {
      popping.value = true
    })
  },
)

function settle(event: AnimationEvent) {
  if (event.target !== event.currentTarget) return
  popping.value = false
}
</script>

<template>
  <span class="score-pop" :class="{ 'is-on': popping }" @animationend="settle">{{ value }}</span>
</template>
