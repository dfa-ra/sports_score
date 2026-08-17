<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import api from '../api/client'
const route = useRoute()
const team = ref<any>(null)
const members = ref<any[]>([])
onMounted(async () => {
  const id = route.params.id
  const [t, m] = await Promise.all([api.get(`/teams/${id}`), api.get(`/teams/${id}/members`)])
  team.value = t.data
  members.value = m.data
})
</script>
<template>
  <section v-if="team" class="stack">
    <h1>{{ team.name }}</h1>
    <div class="panel">
      <h2>Roster</h2>
      <div v-for="m in members" :key="m.id">{{ m.displayName || `${m.playerFirstName} ${m.playerLastName}` }} · #{{ m.jerseyNumber ?? '-' }}</div>
    </div>
  </section>
</template>
