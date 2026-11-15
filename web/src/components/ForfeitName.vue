<script setup lang="ts">
import { computed } from 'vue'
import { forfeitReasonCaption, type ForfeitMatch } from '../lib/forfeit'

const props = defineProps<{
  match?: ForfeitMatch | null
  side: 'home' | 'away'
  name: string
  align?: 'start' | 'end'
}>()

const caption = computed(() => forfeitReasonCaption(props.match, props.side))
</script>

<template>
  <span class="name-stack" :class="align || 'start'">
    <span class="name-text">{{ name }}</span>
    <small v-if="caption" class="tp-reason">{{ caption }}</small>
  </span>
</template>

<style scoped>
.name-stack {
  display: flex;
  flex-direction: column;
  min-width: 0;
  max-width: 100%;
  flex: 1 1 auto;
  line-height: 1.2;
}
.name-stack.end { align-items: flex-end; text-align: right; }
.name-text {
  min-width: 0;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.tp-reason {
  color: var(--danger);
  font-size: 12px;
  font-weight: 600;
  line-height: 1.2;
}
@media (max-width: 720px) {
  .tp-reason { font-size: 11px; }
}
</style>
