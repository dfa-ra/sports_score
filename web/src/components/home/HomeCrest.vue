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
  <span class="crest" :class="[src && !broken ? 'has-photo' : crestClass(id), { lg: large }]">
    <img v-if="src && !broken" :src="src" alt="" @error="broken = true" />
    <template v-else>{{ letters }}</template>
  </span>
</template>

<style scoped>
.crest {
  flex: 0 0 auto;
  box-sizing: border-box;
  width: 28px;
  height: 28px;
  padding: 2px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  background: var(--accent-soft, #eef3f8);
  font-size: 0.75rem;
  font-weight: 800;
  letter-spacing: 0;
  line-height: 1;
}
.crest.lg { width: 36px; height: 36px; font-size: 0.8rem; }
.crest.has-photo { background: var(--accent-soft, #eef3f8); box-shadow: none; }
.crest img {
  width: 100%;
  height: 100%;
  object-fit: contain;
  object-position: center;
  image-orientation: from-image;
  display: block;
  box-shadow: none;
}
@media (max-width: 720px) {
  .crest.lg { width: 32px; height: 32px; font-size: 0.7rem; }
}
.c0 { background: var(--navy); color: #fff; }
.c1 { background: var(--ice); color: var(--navy); }
.c2 { background: var(--ink); color: var(--ice); }
.c3 { background: #fff; color: var(--navy); box-shadow: inset 0 0 0 2px var(--navy); }
.c4 { background: var(--accent-deep); color: #fff; }
.c5 { background: var(--bg-wash); color: var(--navy); box-shadow: inset 0 0 0 1px var(--line-strong); }
</style>
