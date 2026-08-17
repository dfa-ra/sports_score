<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import api from '../../api/client'
const matches = ref<any[]>([])
onMounted(async () => {
  const { data } = await api.get('/referee/matches')
  matches.value = data
})
</script>
<template>
  <section class="stack">
    <div class="page-title">
      <h1>Кабинет судьи</h1>
      <p>Только матчи, на которые вас назначили.</p>
    </div>
    <div v-if="!matches.length" class="empty">Нет назначенных матчей</div>
    <div class="grid cards">
      <RouterLink v-for="m in matches" :key="m.id" class="panel card-link" :to="`/referee/matches/${m.id}`">
        <span :class="m.status === 'LIVE' ? 'badge live' : 'badge'">{{ m.status }}</span>
        <div class="score">{{ m.homeScore }} : {{ m.awayScore }}</div>
        <p class="muted">Открыть пульт</p>
      </RouterLink>
    </div>
  </section>
</template>
<style scoped>
.card-link { text-decoration: none; color: inherit; display: grid; gap: 0.45rem; }
.card-link:hover { text-decoration: none; border-color: rgba(88,166,255,.45); }
</style>
