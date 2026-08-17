<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import api from '../api/client'

const items = ref<any[]>([])
onMounted(async () => {
  const { data } = await api.get('/matches', { params: { size: 50, sort: 'scheduledAt,desc' } })
  items.value = data.content
})
</script>

<template>
  <section class="stack">
    <h1>Matches</h1>
    <div class="grid cards">
      <RouterLink v-for="m in items" :key="m.id" class="panel" :class="{ 'live-pulse': m.status === 'LIVE' }" :to="`/matches/${m.id}`">
        <span class="badge">{{ m.status }}</span>
        <div class="score">{{ m.homeScore }} : {{ m.awayScore }}</div>
        <p class="muted">{{ new Date(m.scheduledAt).toLocaleString() }}</p>
      </RouterLink>
    </div>
  </section>
</template>
