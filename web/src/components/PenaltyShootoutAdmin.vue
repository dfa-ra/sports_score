<script setup lang="ts">
import { onMounted, ref } from 'vue'
import api from '../api/client'
import { apiError } from '../lib/errors'
import { publicScore } from '../lib/match'

type Kick = { teamId: string; scored: boolean }

const props = defineProps<{
  matchId: string
  homeTeamId: string
  awayTeamId: string
  homeLabel: string
  awayLabel: string
  homeScore: number
  awayScore: number
}>()

const emit = defineEmits<{ saved: [] }>()

const kicks = ref<Kick[]>([])
const pending = ref(false)
const error = ref('')
const saved = ref('')

const preview = () => publicScore({
  homeScore: props.homeScore,
  awayScore: props.awayScore,
  penaltyHome: count(props.homeTeamId),
  penaltyAway: count(props.awayTeamId),
})

function count(teamId: string) {
  if (!kicks.value.length) return null
  return kicks.value.filter((kick) => kick.teamId === teamId && kick.scored).length
}

function add(teamId: string, scored: boolean) {
  kicks.value = [...kicks.value, { teamId, scored }]
  saved.value = ''
}

function undo() {
  kicks.value = kicks.value.slice(0, -1)
  saved.value = ''
}

function mark(kick: Kick) {
  const name = kick.teamId === props.homeTeamId ? props.homeLabel : props.awayLabel
  return `${name}: ${kick.scored ? 'гол' : 'мимо'}`
}

async function load() {
  const { data } = await api.get(`/matches/${props.matchId}/shootout`)
  kicks.value = (data.kicks ?? []).map((kick: Kick) => ({ teamId: kick.teamId, scored: kick.scored }))
}

async function save() {
  error.value = ''
  saved.value = ''
  pending.value = true
  try {
    await api.put(`/admin/matches/${props.matchId}/shootout`, { kicks: kicks.value })
    saved.value = 'Серия сохранена.'
    emit('saved')
    await load()
  } catch (reason) {
    error.value = apiError(reason, 'Серию не удалось сохранить.')
  } finally {
    pending.value = false
  }
}

onMounted(() => {
  load().catch((reason) => {
    error.value = apiError(reason, 'Серию не удалось открыть.')
  })
})
</script>

<template>
  <form class="stack motm-form" @submit.prevent="save">
    <h2>Серия пенальти</h2>
    <p class="muted">
      Удары по порядку: сначала хозяева, потом гости. Первые пять раундов — основа, дальше до первого промаха, который ломает ничью.
      Сейчас: {{ kicks.length ? preview() : 'серии нет' }}.
    </p>
    <p v-if="error" class="form-error">{{ error }}</p>
    <p v-if="saved" class="form-ok">{{ saved }}</p>
    <ol v-if="kicks.length" class="kicks">
      <li v-for="(kick, index) in kicks" :key="index">{{ mark(kick) }}</li>
    </ol>
    <div class="motm-actions">
      <button class="btn secondary" type="button" :disabled="pending" @click="add(homeTeamId, true)">{{ homeLabel }}: гол</button>
      <button class="btn secondary" type="button" :disabled="pending" @click="add(homeTeamId, false)">{{ homeLabel }}: мимо</button>
      <button class="btn secondary" type="button" :disabled="pending" @click="add(awayTeamId, true)">{{ awayLabel }}: гол</button>
      <button class="btn secondary" type="button" :disabled="pending" @click="add(awayTeamId, false)">{{ awayLabel }}: мимо</button>
      <button class="btn secondary" type="button" :disabled="pending || !kicks.length" @click="undo">Убрать последний</button>
      <button class="btn" type="submit" :disabled="pending || !kicks.length">Сохранить серию</button>
    </div>
  </form>
</template>

<style scoped>
.kicks {
  margin: 0;
  padding-left: 1.2rem;
}
.motm-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.4rem;
}
</style>
