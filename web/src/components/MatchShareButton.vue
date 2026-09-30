<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  homeName: string
  awayName: string
  homeScore: number
  awayScore: number
}>()

const text = computed(() => `${props.homeName} ${props.homeScore}:${props.awayScore} ${props.awayName}`)

async function share() {
  const url = window.location.href
  const payload = { title: 'Лига ИТМО по футзалу', text: text.value, url }
  if (typeof navigator.share === 'function') {
    try {
      await navigator.share(payload)
      return
    } catch (error) {
      if ((error as DOMException)?.name === 'AbortError') return
    }
  }
  const href = `https://t.me/share/url?url=${encodeURIComponent(url)}&text=${encodeURIComponent(text.value)}`
  window.open(href, '_blank', 'noopener,noreferrer')
}
</script>

<template>
  <button type="button" class="share" @click="share">Поделиться</button>
</template>

<style scoped>
.share {
  margin-top: 0.35rem;
  border: 1px solid var(--line);
  background: #fff;
  color: var(--navy);
  border-radius: 999px;
  padding: 0.22rem 0.7rem;
  font-size: 0.72rem;
  font-weight: 800;
  cursor: pointer;
}
.share:hover { border-color: var(--ice); }
</style>
