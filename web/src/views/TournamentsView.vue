<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import api from '../api/client'
import { useAuthStore } from '../stores/auth'
import CreateTournamentForm from '../components/CreateTournamentForm.vue'
import EmptyState from '../components/EmptyState.vue'
import StatusBadge from '../components/StatusBadge.vue'

const auth = useAuthStore()
const items = ref<any[]>([])
const error = ref('')
const loading = ref(true)
const showForm = ref(false)

async function load() {
  try {
    const { data } = await api.get('/tournaments', { params: { size: 50 } })
    items.value = data.content
  } catch (e: any) {
    error.value = e.response?.data?.message || 'Турниры не загрузились.'
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <section class="stack">
    <div class="page-title">
      <h1>Турниры</h1>
      <p>Сезоны, статусы и таблицы. Смотреть можно без аккаунта.</p>
    </div>
    <button v-if="auth.canManageLeague" class="btn" @click="showForm = !showForm">
      {{ showForm ? 'Скрыть форму' : 'Создать турнир' }}
    </button>
    <div v-if="showForm" class="panel">
      <CreateTournamentForm @created="load" />
    </div>
    <p v-if="error" class="form-error">{{ error }}</p>
    <div v-if="loading" class="grid cards">
      <div v-for="n in 3" :key="n" class="skeleton" />
    </div>
    <EmptyState v-else-if="!items.length" title="Календарь пуст" text="Админ может завести первый турнир одной кнопкой." />
    <div v-else class="grid cards">
      <RouterLink v-for="t in items" :key="t.id" class="panel card-link rise" :to="`/tournaments/${t.id}`">
        <StatusBadge :status="t.status" />
        <h2>{{ t.name }}</h2>
        <p>{{ t.format }} · сезон {{ t.seasonYear }}</p>
      </RouterLink>
    </div>
  </section>
</template>

<style scoped>
.card-link { display: grid; gap: 0.45rem; }
h2 { font-size: 1.25rem; }
</style>
