<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import api from '../api/client'

const items = ref<any[]>([])
const error = ref('')
const loading = ref(true)

onMounted(async () => {
  try {
    const { data } = await api.get('/matches', { params: { size: 50, sort: 'scheduledAt,desc' } })
    items.value = data.content
  } catch (e: any) {
    error.value = e.response?.data?.message || 'Не удалось загрузить матчи'
  } finally {
    loading.value = false
  }
})

function badgeClass(status: string) {
  return status === 'LIVE' || status === 'PAUSED' ? 'badge live' : 'badge'
}
</script>

<template>
  <section class="stack">
    <div class="page-title">
      <h1>Матчи</h1>
      <p>Live и расписание. Откройте карточку для ленты событий.</p>
    </div>
    <p v-if="error" style="color:var(--danger)">{{ error }}</p>
    <div v-if="loading" class="empty">Загрузка…</div>
    <div v-else-if="!items.length" class="empty">Матчей пока нет</div>
    <div v-else class="grid cards">
      <RouterLink
        v-for="m in items"
        :key="m.id"
        class="panel match-card"
        :class="{ 'live-pulse': m.status === 'LIVE' }"
        :to="`/matches/${m.id}`"
      >
        <span :class="badgeClass(m.status)">{{ m.status }}</span>
        <div class="score">{{ m.homeScore }} : {{ m.awayScore }}</div>
        <p class="muted">{{ new Date(m.scheduledAt).toLocaleString('ru-RU') }}</p>
      </RouterLink>
    </div>
  </section>
</template>

<style scoped>
.match-card { text-decoration: none; color: inherit; display: grid; gap: 0.55rem; }
.match-card:hover { text-decoration: none; border-color: rgba(88, 166, 255, 0.45); }
</style>
