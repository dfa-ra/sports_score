<script setup lang="ts">
import type { StandingGroup } from '../lib/standings'
import StandingTable from './StandingTable.vue'

defineProps<{
  tables: StandingGroup[]
  compact?: boolean
}>()
</script>

<template>
  <div class="groups">
    <section v-for="(table, i) in tables" :key="table.id || i" class="group">
      <h3 v-if="table.name">{{ table.name }}</h3>
      <StandingTable :rows="table.rows" :compact="compact" />
    </section>
  </div>
</template>

<style scoped>
.groups { display: grid; gap: 1.1rem; }
.group h3 {
  margin: 0;
  padding: 0.15rem 0.85rem 0.35rem;
  font-size: 0.92rem;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--navy);
}
</style>
