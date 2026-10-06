<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import api from '../api/client'
import { apiError } from '../lib/errors'

type Person = {
  id: string
  firstName?: string | null
  lastName?: string | null
  email: string
}

type Assignment = {
  matchId: string
  mode: 'BOTH' | 'SPLIT' | null
  analyst: Person | null
  homeAnalyst: Person | null
  awayAnalyst: Person | null
}

const props = defineProps<{
  matchId: string
  homeLabel: string
  awayLabel: string
}>()

const mode = ref<'BOTH' | 'SPLIT'>('BOTH')
const assignment = ref<Assignment | null>(null)
const analysts = ref<Person[]>([])
const bothId = ref('')
const homeId = ref('')
const awayId = ref('')
const error = ref('')
const ok = ref('')
const pending = ref(false)
const directoryReady = ref(false)

function title(person: Person | null | undefined) {
  if (!person) return ''
  const name = [person.lastName, person.firstName].filter(Boolean).join(' ')
  return name ? `${name} · ${person.email}` : person.email
}

function nowLine(person: Person | null | undefined) {
  const label = title(person)
  return label ? `Сейчас: ${label}` : 'Сейчас никто не назначен.'
}

function slotLine(active: boolean, person: Person | null | undefined) {
  return nowLine(active ? person : null)
}

function withAssigned(selectedId: string, assigned: Person | null | undefined) {
  const list = [...analysts.value]
  if (assigned && !list.some((person) => person.id === assigned.id)) list.unshift(assigned)
  if (selectedId && !list.some((person) => person.id === selectedId)) {
    const known = [assignment.value?.analyst, assignment.value?.homeAnalyst, assignment.value?.awayAnalyst]
      .find((person) => person?.id === selectedId)
    if (known) list.unshift(known)
  }
  return list
}

const bothOptions = computed(() => withAssigned(bothId.value, assignment.value?.analyst))
const homeOptions = computed(() => withAssigned(homeId.value, assignment.value?.homeAnalyst))
const awayOptions = computed(() => withAssigned(awayId.value, assignment.value?.awayAnalyst))

function applyAssignment(data: Assignment) {
  assignment.value = data
  if (data.mode === 'SPLIT' || data.mode === 'BOTH') mode.value = data.mode
  bothId.value = data.analyst?.id || analysts.value[0]?.id || ''
  homeId.value = data.homeAnalyst?.id || ''
  awayId.value = data.awayAnalyst?.id || ''
}

async function loadAnalysts() {
  const { data } = await api.get('/referee/analysts')
  analysts.value = Array.isArray(data) ? data : []
}

async function load() {
  const { data } = await api.get(`/matches/${props.matchId}/analyst-assignment`)
  applyAssignment(data)
}

async function save() {
  error.value = ''
  ok.value = ''
  pending.value = true
  try {
    const body = mode.value === 'BOTH'
      ? { mode: 'BOTH', userId: bothId.value || null }
      : { mode: 'SPLIT', homeUserId: homeId.value || null, awayUserId: awayId.value || null }
    const { data } = await api.put(`/referee/matches/${props.matchId}/analysts`, body)
    applyAssignment(data)
    ok.value = 'Аналитики назначены'
  } catch (e: any) {
    error.value = apiError(e, 'Не удалось назначить')
  } finally {
    pending.value = false
  }
}

async function clear() {
  error.value = ''
  ok.value = ''
  pending.value = true
  try {
    await api.delete(`/referee/matches/${props.matchId}/analysts`)
    applyAssignment({
      matchId: props.matchId,
      mode: null,
      analyst: null,
      homeAnalyst: null,
      awayAnalyst: null,
    })
    ok.value = 'Назначение снято'
  } catch (e: any) {
    error.value = apiError(e, 'Не удалось снять назначение')
  } finally {
    pending.value = false
  }
}

onMounted(() => {
  Promise.all([loadAnalysts(), load()])
    .then(() => {
      if (mode.value === 'BOTH' && !bothId.value) bothId.value = analysts.value[0]?.id || ''
    })
    .catch((e) => {
      error.value = apiError(e, 'Назначение не открылось')
    })
    .finally(() => {
      directoryReady.value = true
    })
})
</script>

<template>
  <section class="panel stack analysts">
    <h2>Аналитики</h2>

    <div class="modes">
      <label class="choice">
        <input v-model="mode" type="radio" value="BOTH" />
        Один на обе команды
      </label>
      <label class="choice">
        <input v-model="mode" type="radio" value="SPLIT" />
        По команде
      </label>
    </div>

    <form v-if="mode === 'BOTH'" class="stack" @submit.prevent="save">
      <p class="muted">{{ slotLine(assignment?.mode === 'BOTH', assignment?.analyst) }}</p>
      <label class="field">Аналитик
        <select v-model="bothId" required>
          <option v-for="person in bothOptions" :key="person.id" :value="person.id">{{ title(person) }}</option>
        </select>
      </label>
      <button class="btn assign-btn" type="submit" :disabled="pending || !bothId">Назначить</button>
    </form>

    <template v-else>
      <form class="stack" @submit.prevent="save">
        <p class="muted">{{ slotLine(assignment?.mode === 'SPLIT', assignment?.homeAnalyst) }}</p>
        <label class="field">{{ homeLabel }}
          <select v-model="homeId" required>
            <option value="">Не выбран</option>
            <option v-for="person in homeOptions" :key="person.id" :value="person.id">{{ title(person) }}</option>
          </select>
        </label>
        <button class="btn assign-btn" type="submit" :disabled="pending || !homeId">Назначить</button>
      </form>
      <form class="stack" @submit.prevent="save">
        <p class="muted">{{ slotLine(assignment?.mode === 'SPLIT', assignment?.awayAnalyst) }}</p>
        <label class="field">{{ awayLabel }}
          <select v-model="awayId" required>
            <option value="">Не выбран</option>
            <option v-for="person in awayOptions" :key="person.id" :value="person.id">{{ title(person) }}</option>
          </select>
        </label>
        <button class="btn assign-btn" type="submit" :disabled="pending || !awayId">Назначить</button>
      </form>
    </template>

    <p v-if="directoryReady && !analysts.length" class="muted">Сначала поставьте кому-то роль аналитика в админке.</p>
    <button
      v-if="assignment?.mode"
      class="btn secondary assign-btn"
      type="button"
      :disabled="pending"
      @click="clear"
    >Снять назначение</button>
    <p v-if="ok" class="form-ok">{{ ok }}</p>
    <p v-if="error" class="form-error">{{ error }}</p>
  </section>
</template>

<style scoped>
.analysts h2 { margin: 0; font-size: 1.5rem; }
.modes { display: grid; gap: 0.35rem; }
.choice { display: flex; align-items: center; gap: 0.45rem; font-weight: 700; }
.assign-btn { width: 100%; }
</style>
