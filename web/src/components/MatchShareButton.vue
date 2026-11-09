<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  homeName: string
  awayName: string
  homeScore: number
  awayScore: number
  scoreText?: string
}>()

const text = computed(() => `${props.homeName} ${props.scoreText || `${props.homeScore}:${props.awayScore}`} ${props.awayName}`)

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
  position: relative;
  margin-top: 0.7rem;
  border: 0;
  background: var(--ice);
  color: var(--navy);
  border-radius: 999px;
  padding: 0.4rem 0.95rem;
  font-weight: 800;
  font-size: 0.72rem;
  cursor: pointer;
}
@media (max-width: 720px) {
  .share { margin-top: 0; width: 104px; height: 32px; padding: 0; border-radius: 16px; font-size: 13px; font-weight: 800; }
  .share::before {
    content: "";
    position: absolute;
    left: 50%;
    top: 50%;
    width: 100%;
    height: var(--tap-min);
    transform: translate(-50%, -50%);
  }
}
.share:hover { border-color: var(--ice); }
</style>
