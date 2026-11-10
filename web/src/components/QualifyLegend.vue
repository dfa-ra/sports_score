<script setup lang="ts">
import { computed } from 'vue'
import { legendItems, type QualifyMark } from '../lib/qualifyStages'

const props = defineProps<{ marks?: QualifyMark[] | null }>()
const items = computed(() => legendItems(props.marks || []))
</script>

<template>
  <p v-if="items.length" class="legend">
    <span v-for="item in items" :key="item.id">
      <i :data-qualify="item.id"></i>{{ item.label }}
    </span>
  </p>
</template>

<style scoped>
.legend {
  display: flex;
  flex-wrap: wrap;
  gap: 0.45rem 0.9rem;
  margin: 0.7rem 0 0;
  color: var(--muted);
  font-size: 0.8rem;
}
span { display: inline-flex; align-items: center; gap: 0.35rem; }
i {
  width: 0.7rem;
  height: 0.7rem;
  border-radius: 50%;
  background: var(--q-dot);
}
</style>
