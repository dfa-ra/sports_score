<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import api from '../api/client'
const items = ref<any[]>([])
onMounted(async () => {
  const { data } = await api.get('/players', { params: { size: 50 } })
  items.value = data.content
})
</script>
<template>
  <section class="stack">
    <div class="page-title">
      <h1>Игроки</h1>
      <p>Публичные карточки и статистика.</p>
    </div>
    <div v-if="!items.length" class="empty">Игроков пока нет</div>
    <div class="grid cards">
      <RouterLink v-for="p in items" :key="p.id" class="panel card-link" :to="`/players/${p.id}`">
        <h2>{{ p.displayName || `${p.firstName} ${p.lastName}` }}</h2>
        <p>{{ p.position || 'Игрок' }} · №{{ p.jerseyNumber ?? '—' }}</p>
      </RouterLink>
    </div>
  </section>
</template>
<style scoped>
.card-link { text-decoration: none; color: inherit; }
.card-link:hover { text-decoration: none; border-color: rgba(88,166,255,.45); }
h2 { font-size: 1.15rem; margin-bottom: 0.25rem; }
</style>
