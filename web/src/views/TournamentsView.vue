<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import api from '../api/client'
import { useAuthStore } from '../stores/auth'
import { labelOfSport } from '../lib/format'
import AdminOnly from '../components/AdminOnly.vue'
import ApplyTeamDialog from '../components/ApplyTeamDialog.vue'
import CreateTournamentForm from '../components/CreateTournamentForm.vue'
import EmptyState from '../components/EmptyState.vue'
import TournamentCard from '../components/TournamentCard.vue'

const auth = useAuthStore()
const items = ref<any[]>([])
const sports = ref<Record<string, { name?: string, code?: string }>>({})
const error = ref('')
const loading = ref(true)
const showForm = ref(false)
const applying = ref<any | null>(null)

const sorted = computed(() => {
  const rank: Record<string, number> = {
    REGISTRATION: 0,
    DRAFT: 1,
    ACTIVE: 2,
    FINISHED: 3,
    CANCELLED: 4,
  }
  return [...items.value].sort((a, b) => (rank[a.status] ?? 9) - (rank[b.status] ?? 9))
})

function sportName(tournament: any) {
  const sport = sports.value[tournament.sportId]
  return sport ? labelOfSport(sport.code, sport.name) : ''
}

async function load() {
  try {
    const [tournaments, sportRes] = await Promise.all([
      api.get('/tournaments', { params: { size: 50 } }),
      api.get('/sports'),
    ])
    items.value = tournaments.data.content ?? []
    const map: Record<string, { name?: string, code?: string }> = {}
    for (const sport of sportRes.data ?? []) {
      map[sport.id] = sport
    }
    sports.value = map
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
      <p>Карточка сезона и кнопка «Заявиться», пока идёт набор. Смотреть можно без аккаунта.</p>
    </div>
    <p v-if="error" class="form-error">{{ error }}</p>
    <div v-if="loading" class="grid cards">
      <div v-for="n in 3" :key="n" class="skeleton" />
    </div>
    <EmptyState v-else-if="!items.length" title="Календарь пуст" text="Админ может завести первый турнир одной кнопкой." />
    <div v-else class="grid cards">
      <TournamentCard
        v-for="t in sorted"
        :key="t.id"
        :tournament="t"
        :sport-name="sportName(t)"
        @apply="applying = t"
      />
    </div>
    <AdminOnly v-if="auth.canManageLeague" title="Для админа">
      <button class="btn" @click="showForm = !showForm">
        {{ showForm ? 'Скрыть форму' : 'Создать турнир' }}
      </button>
      <CreateTournamentForm v-if="showForm" @created="load" />
    </AdminOnly>
    <ApplyTeamDialog
      v-if="applying"
      :tournament="applying"
      @close="applying = null"
      @applied="load"
    />
  </section>
</template>
