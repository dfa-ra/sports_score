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
    <h1>Players</h1>
    <div class="grid cards">
      <RouterLink v-for="p in items" :key="p.id" class="panel" :to="`/players/${p.id}`">
        <h2>{{ p.displayName || `${p.firstName} ${p.lastName}` }}</h2>
        <p>{{ p.position || 'Player' }} · #{{ p.jerseyNumber ?? '-' }}</p>
      </RouterLink>
    </div>
  </section>
</template>
