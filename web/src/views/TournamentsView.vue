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
const sports = ref<Record<string, { name?: string; code?: string }>>({})
const error = ref('')
const loading = ref(true)
const showForm = ref(false)
const applying = ref<any | null>(null)
const applyNote = ref('')

const sorted = computed(() => {
  return [...items.value].sort((a, b) => {
    const ad = a.startDate || a.endDate || '9999-99-99'
    const bd = b.startDate || b.endDate || '9999-99-99'
    if (ad !== bd) return ad < bd ? -1 : 1
    return String(a.name || '').localeCompare(String(b.name || ''), 'ru')
  })
})

function sportName(tournament: { sportId?: string }) {
  const sport = tournament.sportId ? sports.value[tournament.sportId] : undefined
  return sport ? labelOfSport(sport.code, sport.name) : ''
}

async function load() {
  try {
    const [tournaments, sportRes] = await Promise.all([
      api.get('/tournaments', { params: { size: 50 } }),
      api.get('/sports').catch(() => ({ data: [] })),
    ])
    items.value = tournaments.data.content ?? []
    const map: Record<string, { name?: string; code?: string }> = {}
    for (const sport of sportRes.data ?? []) {
      if (sport?.id) map[sport.id] = sport
    }
    sports.value = map
  } catch (e: any) {
    error.value = e.response?.data?.message || 'Турниры не загрузились.'
  } finally {
    loading.value = false
  }
}

function onApplied(message: string) {
  applyNote.value = message
  applying.value = null
  load()
}

onMounted(load)
</script>

<template>
  <section class="stack">
    <div class="page-title">
      <h1>Турниры</h1>
    </div>
    <p v-if="error" class="form-error">{{ error }}</p>
    <p v-if="applyNote" class="form-ok">{{ applyNote }}</p>
    <div v-if="loading" class="grid cards">
      <div v-for="n in 3" :key="n" class="skeleton" />
    </div>
    <EmptyState v-else-if="!items.length" title="Турниров нет" />
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
      @applied="onApplied"
    />
  </section>
</template>
