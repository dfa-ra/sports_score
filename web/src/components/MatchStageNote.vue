<script setup lang="ts">
import { computed } from 'vue'
import { matchCardStage, protocolStage } from '../lib/playoffLabel'

const props = defineProps<{
  match?: { thirdPlace?: boolean | null; stageLabel?: string | null } | null
  variant?: 'card' | 'protocol'
}>()

const text = computed(() =>
  props.variant === 'protocol' ? protocolStage(props.match) : matchCardStage(props.match),
)
</script>

<template>
  <p v-if="text" class="match-stage-note">{{ text }}</p>
</template>

<style scoped>
.match-stage-note {
  margin: 0;
  font-size: 0.75rem;
  font-weight: 800;
  letter-spacing: 0.04em;
  text-transform: none;
  color: #8a5a2b;
}
</style>
