<script setup lang="ts">
import { useMatchAlerts } from '../lib/useMatchAlerts'

defineProps<{ headless?: boolean }>()

const { enabled, requestAlerts } = useMatchAlerts()
</script>

<template>
  <button
    v-if="!headless"
    type="button"
    class="btn secondary goal-alerts"
    :class="{ on: enabled }"
    :aria-pressed="enabled"
    @click="requestAlerts"
  >Сообщать о голах</button>
</template>

<style scoped>
.goal-alerts {
  min-height: 36px;
  padding: 0.4rem 0.8rem;
  font-size: 0.85rem;
  border-radius: 10px;
  white-space: nowrap;
}
.goal-alerts.on {
  border-color: var(--ice);
  background: color-mix(in srgb, var(--ice) 16%, var(--white));
}
</style>
