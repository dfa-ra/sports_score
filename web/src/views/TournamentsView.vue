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
    error.value = e.response?.data?.message || 'Failed to load tournaments'
  }
})
</script>

<template>
  <section class="stack">
    <h1>Tournaments</h1>
    <p v-if="error" style="color:var(--danger)">{{ error }}</p>
    <div class="grid cards">
      <RouterLink v-for="t in items" :key="t.id" class="panel rise" :to="`/tournaments/${t.id}`">
        <span class="badge">{{ t.status }}</span>
        <h2>{{ t.name }}</h2>
        <p>{{ t.format }} · {{ t.seasonYear }}</p>
      </RouterLink>
    </div>
  </section>
</template>
