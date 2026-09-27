<script setup lang="ts">
import { useCopy } from '../lib/useCopy'

const props = defineProps<{ value: string; label?: string }>()
const { copied, copy } = useCopy()
</script>

<template>
  <button
    type="button"
    class="copy-chip"
    :class="{ done: copied }"
    :title="copied ? 'Скопировано' : 'Скопировать'"
    @click="copy(props.value)"
  >
    <span aria-hidden="true">{{ copied ? '✓' : '⎘' }}</span>
    <span>{{ copied ? 'Скопировано' : (label || value) }}</span>
  </button>
</template>
