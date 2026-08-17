<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import api from '../api/client'
const items = ref<any[]>([])
onMounted(async () => {
  const { data } = await api.get('/teams', { params: { size: 50 } })
  items.value = data.content
})
</script>
<template>
  <section class="stack">
    <div class="page-title">
      <h1>Команды</h1>
      <p>Составы и капитаны студенческой лиги.</p>
    </div>
    <div v-if="!items.length" class="empty">Команд пока нет</div>
    <div class="grid cards">
      <RouterLink v-for="t in items" :key="t.id" class="panel card-link" :to="`/teams/${t.id}`">
        <h2>{{ t.name }}</h2>
        <p>{{ t.shortName || '—' }}</p>
      </RouterLink>
    </div>
  </section>
</template>
<style scoped>
.card-link { text-decoration: none; color: inherit; }
.card-link:hover { text-decoration: none; border-color: rgba(88,166,255,.45); }
h2 { font-size: 1.15rem; margin-bottom: 0.25rem; }
</style>
