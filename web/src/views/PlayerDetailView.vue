<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import api from '../api/client'
const route = useRoute()
const card = ref<any>(null)
onMounted(async () => {
  const { data } = await api.get(`/players/${route.params.id}/card`)
  card.value = data
})
</script>
<template>
  <section v-if="card" class="stack">
    <h1>{{ card.displayName || `${card.firstName} ${card.lastName}` }}</h1>
    <div class="panel">
      <p>{{ card.position }} · #{{ card.jerseyNumber ?? '-' }}</p>
      <p v-if="card.team">Team: {{ card.team.name }}</p>
      <pre style="white-space:pre-wrap">{{ card.statistics }}</pre>
    </div>
  </section>
</template>
