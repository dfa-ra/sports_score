<script setup lang="ts">
import ForfeitBadge from './ForfeitBadge.vue'
import { isForfeit } from '../lib/forfeit'

const props = defineProps<{
  match?: { forfeit?: string | null } | null
}>()
</script>

<template>
  <span class="tp-score" :class="{ on: isForfeit(props.match) }">
    <span class="tp-score-main"><slot /></span>
    <ForfeitBadge :match="match" />
  </span>
</template>

<style scoped>
.tp-score {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  max-width: 100%;
  vertical-align: middle;
}
.tp-score-main {
  min-width: 0;
}
@media (max-width: 720px) {
  .tp-score.on {
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 2px;
  }
}
</style>
