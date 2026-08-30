<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import api from '../api/client'
import EmptyState from '../components/EmptyState.vue'
import StandingTable from '../components/StandingTable.vue'

const tournaments = ref<any[]>([])
const tournamentId = ref('')
const standings = ref<any[]>([])
const tournament = ref<any>(null)
const loading = ref(true)

const season = computed(() => {
  const start = tournament.value?.startsOn || tournament.value?.startDate
  if (!start) return ''
  const year = new Date(start).getFullYear()
  return `${year}/${year + 1}`
})

onMounted(async () => {
  const { data } = await api.get('/tournaments', { params: { size: 50 } })
  tournaments.value = data.content ?? []
  try {
    const current = await api.get('/tournaments/current')
    if (current.data?.id) tournamentId.value = current.data.id
  } catch {
    if (tournaments.value[0]) tournamentId.value = tournaments.value[0].id
  }
  if (!tournamentId.value && tournaments.value[0]) tournamentId.value = tournaments.value[0].id
  await loadTable()
})

watch(tournamentId, loadTable)

async function loadTable() {
  if (!tournamentId.value) {
    loading.value = false
    return
  }
  loading.value = true
  try {
    const [t, s] = await Promise.all([
      api.get(`/tournaments/${tournamentId.value}`),
      api.get(`/tournaments/${tournamentId.value}/standings`),
    ])
    tournament.value = t.data
    standings.value = s.data
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <section class="stack page">
    <div class="league">
      <span class="mark">KB</span>
      <div>
        <p class="where">KRONBARS</p>
        <h1>{{ tournament?.name || 'Таблица' }}</h1>
        <label class="field season">
          <select v-model="tournamentId">
            <option v-for="t in tournaments" :key="t.id" :value="t.id">
              {{ t.name }}{{ season && t.id === tournamentId ? ' · ' + season : '' }}
            </option>
          </select>
        </label>
      </div>
    </div>
    <div class="fs-tabs">
      <RouterLink class="on" to="/table">Таблица</RouterLink>
      <RouterLink to="/calendar?tab=played">Результаты</RouterLink>
      <RouterLink to="/statistics">Бомбардиры</RouterLink>
    </div>
    <div class="pills">
      <span class="on">Итого</span>
      <RouterLink to="/statistics">Бомбардиры</RouterLink>
      <RouterLink to="/calendar?tab=played">Результаты</RouterLink>
    </div>
    <div v-if="loading" class="skeleton" />
    <EmptyState v-else-if="!standings.length" title="Таблица пустая" text="Нет утверждённых команд или сыгранных матчей." />
    <div v-else class="sheet">
      <StandingTable :rows="standings" />
    </div>
  </section>
</template>

<style scoped>
.page { gap: 0.45rem; }
.league {
  display: grid;
  grid-template-columns: 56px 1fr;
  gap: 0.75rem;
  align-items: center;
  padding: 0.35rem 0 0.2rem;
}
.mark {
  width: 56px;
  height: 56px;
  border-radius: 12px;
  display: grid;
  place-items: center;
  background: #fff;
  color: var(--navy);
  font-weight: 800;
  box-shadow: var(--shadow);
}
.where {
  margin: 0;
  color: var(--muted);
  font-size: 0.68rem;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  font-weight: 700;
}
.league h1 { font-size: clamp(1.2rem, 3vw, 1.7rem); }
.season { max-width: 360px; margin-top: 0.35rem; }
.sheet {
  background: #fff;
  border: 1px solid var(--line);
  border-radius: 12px;
  overflow: hidden;
}
</style>
