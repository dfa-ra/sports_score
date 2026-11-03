<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { initials } from '../../lib/format'
import { crestClass } from '../../lib/homeOverview'

const props = defineProps<{
  src?: string | null
  name?: string | null
  id?: string | null
  large?: boolean
}>()

const broken = ref(false)
watch(() => props.src, () => { broken.value = false })
const letters = computed(() => initials(props.name || ''))
</script>

<template>
  <span class="crest" :class="[crestClass(id), { lg: large }]">
    <img v-if="src && !broken" :src="src" alt="" @error="broken = true" />
    <template v-else>{{ letters }}</template>
  </span>
</template>

<style scoped>
.crest {
  flex: 0 0 auto;
  width: 28px;
  height: 28px;
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
.crest.lg { width: 56px; height: 56px; font-size: 1.05rem; }
.crest img { width: 100%; height: 100%; object-fit: cover; display: block; }
.c0 { background: var(--navy); color: #fff; }
.c1 { background: var(--ice); color: var(--navy); }
.c2 { background: var(--ink); color: var(--ice); }
.c3 { background: #fff; color: var(--navy); box-shadow: inset 0 0 0 2px var(--navy); }
.c4 { background: var(--accent-deep); color: #fff; }
.c5 { background: var(--bg-wash); color: var(--navy); box-shadow: inset 0 0 0 1px var(--line-strong); }
</style>
