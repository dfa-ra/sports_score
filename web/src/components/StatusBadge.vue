<script setup lang="ts">
import { computed } from 'vue'
import { labelOf, statusLabel } from '../lib/format'

const props = defineProps<{ status?: string | null; scope?: 'tournament' | 'match'; at?: string | null }>()
const text = computed(() => {
  if (props.scope === 'tournament' && props.status === 'FINISHED') return 'Завершён'
  if (props.status === 'SCHEDULED' && props.at === null) return 'Дата уточняется'
  return labelOf(statusLabel, props.status)
})
const klass = computed(() => {
  if (props.status === 'LIVE' || props.status === 'PAUSED' || props.status === 'ACTIVE') return 'badge live'
  if (props.status === 'FINISHED') return 'badge done'
  if (props.status === 'CANCELLED') return 'badge warn'
  return 'badge'
})
</script>

<template>
  <span :class="klass">{{ text }}</span>
</template>
