<script setup lang="ts">
import { isForfeit } from '../lib/forfeit'

const props = defineProps<{
  match?: { forfeit?: string | null } | null
}>()
</script>

<template>
  <span v-if="isForfeit(props.match)" class="tp-badge" aria-label="Техническое поражение">ТП</span>
</template>

<style scoped>
.tp-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  box-sizing: border-box;
  width: 27px;
  height: 18px;
  border-radius: 7px;
  background: var(--danger);
  color: #fff;
  font-family: var(--font-display);
  font-weight: 700;
  font-size: 11px;
  line-height: 1;
  letter-spacing: 0.02em;
  flex: 0 0 auto;
}
@media (max-width: 720px) {
  .tp-badge {
    width: 23px;
    height: 18px;
    border-radius: 6px;
    font-size: 10px;
  }
}
</style>
