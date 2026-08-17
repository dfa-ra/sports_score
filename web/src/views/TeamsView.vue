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
    <h1>Teams</h1>
    <div class="grid cards">
      <RouterLink v-for="t in items" :key="t.id" class="panel" :to="`/teams/${t.id}`">
        <h2>{{ t.name }}</h2>
        <p>{{ t.shortName }}</p>
      </RouterLink>
    </div>
  </section>
</template>
