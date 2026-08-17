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
    <h1>Referee Dashboard</h1>
    <div class="grid cards">
      <RouterLink v-for="m in matches" :key="m.id" class="panel" :to="`/referee/matches/${m.id}`">
        <span class="badge">{{ m.status }}</span>
        <div class="score">{{ m.homeScore }} : {{ m.awayScore }}</div>
      </RouterLink>
    </div>
  </section>
</template>
