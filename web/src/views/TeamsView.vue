<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import api from '../api/client'
import { initials } from '../lib/format'
import EmptyState from '../components/EmptyState.vue'

const items = ref<any[]>([])
const loading = ref(true)

onMounted(async () => {
  try {
    const { data } = await api.get('/teams', { params: { size: 50 } })
    items.value = data.content
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <section class="stack">
    <div class="page-title">
      <h1>Команды</h1>
      <p>Составы и капитаны студенческой лиги.</p>
    </div>
    <div v-if="loading" class="grid cards">
      <div v-for="n in 4" :key="n" class="skeleton" />
    </div>
    <EmptyState v-else-if="!items.length" title="Пока без эмблем" text="Первая команда может назвать себя как угодно. Кроме «Без названия»." />
    <div v-else class="grid cards">
      <RouterLink v-for="t in items" :key="t.id" class="panel card-link" :to="`/teams/${t.id}`">
        <span class="crest">{{ initials(t.shortName || t.name) }}</span>
        <h2>{{ t.name }}</h2>
        <p>{{ t.shortName || 'без короткого имени — тоже стиль' }}</p>
      </RouterLink>
    </div>
  </section>
</template>

<style scoped>
.card-link { display: grid; gap: 0.35rem; justify-items: start; }
.crest {
  width: 42px;
  height: 42px;
  display: grid;
  place-items: center;
  border-radius: 14px 11px 13px 10px;
  background: var(--accent-soft);
  color: var(--accent);
  font-weight: 800;
  transform: rotate(-3deg);
}
h2 { font-size: 1.2rem; }
</style>
