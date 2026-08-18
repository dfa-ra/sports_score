<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import api from '../api/client'
import { fromLocalInput } from '../lib/datetime'
import { apiError } from '../lib/errors'

const props = defineProps<{ tournamentId?: string }>()
const emit = defineEmits<{ created: [match: any] }>()
const router = useRouter()
const tournaments = ref<any[]>([])
const teams = ref<any[]>([])
const tournamentId = ref(props.tournamentId || '')
const homeTeamId = ref('')
const awayTeamId = ref('')
const scheduledAt = ref('')
const preset = ref('campus')
const periodCount = ref(2)
const periodLengthMinutes = ref(20)
const pending = ref(false)
const error = ref('')

const presets = [
  { id: 'campus', label: 'Студенческий: 2 тайма по 20 мин', count: 2, minutes: 20 },
  { id: 'classic', label: 'Классика: 2 тайма по 45 мин', count: 2, minutes: 45 },
  { id: 'quarters', label: 'Четверти: 4 по 10 мин', count: 4, minutes: 10 },
  { id: 'hockey', label: 'Три периода по 20 мин', count: 3, minutes: 20 },
  { id: 'custom', label: 'Свой формат', count: 2, minutes: 20 },
] as const

function applyPreset(id: string) {
  preset.value = id
  const found = presets.find((p) => p.id === id)
  if (found && id !== 'custom') {
    periodCount.value = found.count
    periodLengthMinutes.value = found.minutes
  }
}

const approved = computed(() => teams.value.filter((t) => !t.status || t.status === 'APPROVED'))

onMounted(async () => {
  if (!props.tournamentId) {
    const { data } = await api.get('/tournaments', { params: { size: 100 } })
    tournaments.value = data.content ?? []
    if (!tournamentId.value && tournaments.value[0]) tournamentId.value = tournaments.value[0].id
  }
  await loadTeams()
})

watch(tournamentId, loadTeams)

async function loadTeams() {
  if (!tournamentId.value) {
    teams.value = []
    return
  }
  const { data } = await api.get(`/tournaments/${tournamentId.value}/teams`)
  teams.value = data
  homeTeamId.value = approved.value[0]?.teamId || ''
  awayTeamId.value = approved.value[1]?.teamId || ''
}

async function submit() {
  error.value = ''
  if (homeTeamId.value === awayTeamId.value) {
    error.value = 'Две одинаковые команды — это тренировка, не матч.'
    return
  }
  pending.value = true
  try {
    const { data } = await api.post('/matches', {
      tournamentId: tournamentId.value,
      homeTeamId: homeTeamId.value,
      awayTeamId: awayTeamId.value,
      scheduledAt: fromLocalInput(scheduledAt.value),
      periodCount: Number(periodCount.value),
      periodLengthMinutes: Number(periodLengthMinutes.value),
    })
    emit('created', data)
    router.push(`/matches/${data.id}`)
  } catch (e: any) {
    error.value = apiError(e, 'Матч не создался. Команды должны быть допущены в турнир.')
  } finally {
    pending.value = false
  }
}
</script>

<template>
  <form class="stack" @submit.prevent="submit">
    <label v-if="!props.tournamentId" class="field">Турнир
      <select v-model="tournamentId" required>
        <option v-for="t in tournaments" :key="t.id" :value="t.id">{{ t.name }}</option>
      </select>
    </label>
    <label class="field">Хозяева
      <select v-model="homeTeamId" required>
        <option v-for="t in approved" :key="t.teamId" :value="t.teamId">{{ t.teamName }}</option>
      </select>
    </label>
    <label class="field">Гости
      <select v-model="awayTeamId" required>
        <option v-for="t in approved" :key="t.teamId" :value="t.teamId">{{ t.teamName }}</option>
      </select>
    </label>
    <label class="field">Когда
      <input v-model="scheduledAt" type="datetime-local" required />
    </label>
    <label class="field">Формат времени
      <select :value="preset" @change="applyPreset(($event.target as HTMLSelectElement).value)">
        <option v-for="p in presets" :key="p.id" :value="p.id">{{ p.label }}</option>
      </select>
    </label>
    <div v-if="preset === 'custom'" class="grid two">
      <label class="field">Сколько таймов
        <input v-model.number="periodCount" type="number" min="1" max="8" required />
      </label>
      <label class="field">Минут в одном тайме
        <input v-model.number="periodLengthMinutes" type="number" min="1" max="90" required />
      </label>
    </div>
    <p class="muted">По умолчанию — два тайма по 20 минут, как на студенческом поле после пар.</p>
    <p v-if="!approved.length" class="muted">Сначала допустите хотя бы две команды в турнир.</p>
    <p v-if="error" class="form-error">{{ error }}</p>
    <button class="btn" type="submit" :disabled="pending || approved.length < 2">
      {{ pending ? 'Ставим в сетку…' : 'Назначить матч' }}
    </button>
  </form>
</template>

<style scoped>
.grid.two { display: grid; grid-template-columns: 1fr 1fr; gap: 0.75rem; }
@media (max-width: 640px) {
  .grid.two { grid-template-columns: 1fr; }
}
</style>
