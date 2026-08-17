<script setup lang="ts">
import { computed } from 'vue'
import { labelOf, statusLabel } from '../lib/format'

const props = defineProps<{ status?: string | null }>()
const klass = computed(() => {
  if (props.status === 'LIVE' || props.status === 'PAUSED' || props.status === 'ACTIVE') return 'badge live'
  if (props.status === 'FINISHED') return 'badge done'
  if (props.status === 'CANCELLED') return 'badge warn'
  return 'badge'
})
</script>

<template>
  <span :class="klass">{{ labelOf(statusLabel, status) }}</span>
</template>
