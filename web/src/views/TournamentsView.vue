<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import api from '../api/client'

const items = ref<any[]>([])
const error = ref('')

onMounted(async () => {
  try {
    const { data } = await api.get('/tournaments', { params: { size: 50 } })
    items.value = data.content
  } catch (e: any) {
    error.value = e.response?.data?.message || 'Не удалось загрузить турниры'
  }
})
</script>

<template>
  <section class="stack">
    <div class="page-title">
      <h1>Турниры</h1>
      <p>Сезоны, статусы и таблицы.</p>
    </div>
    <p v-if="error" style="color:var(--danger)">{{ error }}</p>
    <div v-if="!items.length && !error" class="empty">Турниров пока нет</div>
    <div class="grid cards">
      <RouterLink v-for="t in items" :key="t.id" class="panel card-link rise" :to="`/tournaments/${t.id}`">
        <span class="badge">{{ t.status }}</span>
        <h2>{{ t.name }}</h2>
        <p>{{ t.format }} · сезон {{ t.seasonYear }}</p>
      </RouterLink>
    </div>
  </section>
</template>

<style scoped>
.card-link { text-decoration: none; color: inherit; display: grid; gap: 0.45rem; }
.card-link:hover { text-decoration: none; border-color: rgba(88,166,255,.45); }
h2 { font-size: 1.15rem; }
</style>
