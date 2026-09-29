<script setup lang="ts">
import { onMounted, onUnmounted } from 'vue'

const props = defineProps<{
  title: string
  updated: string
}>()

const previousTitle = document.title

onMounted(() => {
  document.title = `${props.title} — Лига ИТМО`
})

onUnmounted(() => {
  document.title = previousTitle
})
</script>

<template>
  <article class="legal">
    <header class="page-title">
      <p class="eyebrow">KRONBARS</p>
      <h1>{{ title }}</h1>
      <p class="dated">{{ updated }}</p>
    </header>
    <div class="sheet prose">
      <slot />
    </div>
  </article>
</template>

<style scoped>
.legal {
  width: min(760px, 100%);
  margin: 0 auto;
  display: grid;
  gap: 1rem;
}
.dated {
  color: var(--muted);
  font-size: 0.92rem;
}
.sheet {
  background: var(--surface);
  border: 1px solid var(--line);
  border-top: 3px solid var(--ice);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow);
  padding: 1.35rem 1.4rem 1.55rem;
}
.prose {
  display: grid;
  gap: 0.85rem;
}
.prose :deep(h2) {
  font-size: 1.02rem;
  color: var(--navy);
  margin-top: 0.45rem;
}
.prose :deep(p),
.prose :deep(li) {
  color: var(--text);
  line-height: 1.6;
}
.prose :deep(ul) {
  margin: 0;
  padding-left: 1.15rem;
  display: grid;
  gap: 0.4rem;
}
.prose :deep(a) {
  color: var(--navy);
  text-decoration: underline;
  text-underline-offset: 0.16em;
}
</style>
