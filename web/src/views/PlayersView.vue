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
    const { data } = await api.get('/players', { params: { size: 50 } })
    items.value = data.content
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <section class="stack">
    <div class="page-title">
      <h1>Игроки</h1>
      <p>Публичные карточки и статистика. Номера — не для красоты.</p>
    </div>
    <div v-if="loading" class="grid cards">
      <div v-for="n in 4" :key="n" class="skeleton" />
    </div>
    <EmptyState v-else-if="!items.length" title="Состав ещё собирается" text="Как только кто-то зарегистрируется игроком — появится здесь." />
    <div v-else class="grid cards">
      <RouterLink v-for="p in items" :key="p.id" class="panel card-link" :to="`/players/${p.id}`">
        <span class="avatar">{{ initials(p.displayName || `${p.firstName} ${p.lastName}`) }}</span>
        <h2>{{ p.displayName || `${p.firstName} ${p.lastName}` }}</h2>
        <p>{{ p.position || 'Игрок' }} · №{{ p.jerseyNumber ?? '—' }}</p>
      </RouterLink>
    </div>
  </section>
</template>

<style scoped>
.card-link { display: grid; gap: 0.3rem; justify-items: start; }
.avatar {
  width: 42px;
  height: 42px;
  display: grid;
  place-items: center;
  border-radius: 999px;
  background: var(--accent-soft);
  color: var(--accent);
  font-weight: 800;
}
h2 { font-size: 1.2rem; }
</style>
