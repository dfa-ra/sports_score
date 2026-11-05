<script setup lang="ts">
import { crestClass } from '../../lib/homeOverview'
import TeamCrest from '../TeamCrest.vue'

defineProps<{
  src?: string | null
  name?: string | null
  id?: string | null
  large?: boolean
}>()
</script>

<template>
  <span class="crest" :class="[crestClass(id), { lg: large }]">
    <TeamCrest fill :src="src" :name="name" :size="large ? 56 : 28" />
  </span>
</template>

<style scoped>
.crest {
  flex: 0 0 auto;
  width: 28px;
  height: 28px;
  min-width: 0;
  max-width: 28px;
  max-height: 28px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  font-size: 0.75rem;
  font-weight: 800;
  letter-spacing: 0;
  line-height: 1;
}
.crest.lg {
  width: 56px;
  height: 56px;
  max-width: 56px;
  max-height: 56px;
  font-size: 1.05rem;
}
.c0 { background: var(--navy); color: #fff; }
.c1 { background: var(--ice); color: var(--navy); }
.c2 { background: var(--ink); color: var(--ice); }
.c3 { background: #fff; color: var(--navy); box-shadow: inset 0 0 0 2px var(--navy); }
.c4 { background: var(--accent-deep); color: #fff; }
.c5 { background: var(--bg-wash); color: var(--navy); box-shadow: inset 0 0 0 1px var(--line-strong); }
</style>
