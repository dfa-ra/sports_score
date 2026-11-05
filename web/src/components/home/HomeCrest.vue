<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { initials } from '../../lib/format'

const props = defineProps<{
  src?: string | null
  name?: string | null
  id?: string | null
  large?: boolean
}>()

const broken = ref(false)
watch(() => props.src, () => { broken.value = false })
const letters = computed(() => initials(props.name || ''))
const showImage = computed(() => Boolean(props.src) && !broken.value)
void props.id
</script>

<template>
  <span class="crest" :class="{ lg: large }">
    <img v-if="showImage" :src="src!" alt="" @error="broken = true" />
    <template v-else>{{ letters }}</template>
  </span>
</template>

<style scoped>
.crest {
  flex: 0 0 28px;
  width: 28px;
  height: 28px;
  min-width: 28px;
  max-width: 28px;
  min-height: 28px;
  max-height: 28px;
  box-sizing: border-box;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  background: var(--accent-soft, #eef3f8);
  box-shadow: none;
  color: var(--navy);
  font-size: 0.75rem;
  font-weight: 800;
  letter-spacing: 0;
  line-height: 1;
}
.crest.lg {
  flex-basis: 56px;
  width: 56px;
  height: 56px;
  min-width: 56px;
  max-width: 56px;
  min-height: 56px;
  max-height: 56px;
  font-size: 1.05rem;
}
.crest img {
  width: 100%;
  height: 100%;
  box-sizing: border-box;
  padding: 2px;
  object-fit: contain;
  object-position: center;
  image-orientation: from-image;
  display: block;
}
</style>
