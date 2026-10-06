<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
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
const error = ref('')
const ok = ref('')
const pending = ref(false)

const bothQuery = ref('')
const homeQuery = ref('')
const awayQuery = ref('')
const bothHits = ref<Person[]>([])
const homeHits = ref<Person[]>([])
const awayHits = ref<Person[]>([])
const bothPick = ref<Person | null>(null)
const homePick = ref<Person | null>(null)
const awayPick = ref<Person | null>(null)

const timers: Record<string, number> = {}

function title(person: Person | null | undefined) {
  if (!person) return ''
  const name = [person.lastName, person.firstName].filter(Boolean).join(' ')
  return name ? `${name} · ${person.email}` : person.email
}

function applyAssignment(data: Assignment) {
  assignment.value = data
  if (data.mode === 'SPLIT' || data.mode === 'BOTH') mode.value = data.mode
  bothPick.value = data.analyst
  homePick.value = data.homeAnalyst
  awayPick.value = data.awayAnalyst
}

async function load() {
  const { data } = await api.get(`/matches/${props.matchId}/analyst-assignment`)
  applyAssignment(data)
}

function search(which: 'both' | 'home' | 'away', query: string) {
  window.clearTimeout(timers[which])
  timers[which] = window.setTimeout(async () => {
    const q = query.trim()
    const target = which === 'both' ? bothHits : which === 'home' ? homeHits : awayHits
    if (!q) {
      target.value = []
      return
    }
    try {
      const { data } = await api.get('/referee/analysts', { params: { q } })
      target.value = data
    } catch (e: any) {
      error.value = apiError(e, 'Поиск не удался')
    }
  }, 250)
}

watch(bothQuery, (q) => search('both', q))
watch(homeQuery, (q) => search('home', q))
watch(awayQuery, (q) => search('away', q))

function pick(which: 'both' | 'home' | 'away', person: Person) {
  if (which === 'both') {
    bothPick.value = person
    bothQuery.value = ''
    bothHits.value = []
  } else if (which === 'home') {
    homePick.value = person
    homeQuery.value = ''
    homeHits.value = []
  } else {
    awayPick.value = person
    awayQuery.value = ''
    awayHits.value = []
  }
}

async function save() {
  error.value = ''
  ok.value = ''
  pending.value = true
  try {
    const body = mode.value === 'BOTH'
      ? { mode: 'BOTH', userId: bothPick.value?.id ?? null }
      : { mode: 'SPLIT', homeUserId: homePick.value?.id ?? null, awayUserId: awayPick.value?.id ?? null }
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
    bothPick.value = null
    homePick.value = null
    awayPick.value = null
    ok.value = 'Назначение снято'
  } catch (e: any) {
    error.value = apiError(e, 'Не удалось снять назначение')
  } finally {
    pending.value = false
  }
}

onMounted(() => {
  load().catch((e) => {
    error.value = apiError(e, 'Назначение не открылось')
  })
})
</script>

<template>
  <section class="panel stack analysts">
    <h2>Аналитики</h2>
    <p v-if="assignment?.mode === 'BOTH' && assignment.analyst" class="current">
      Сейчас: {{ title(assignment.analyst) }} на обе команды
    </p>
    <p v-else-if="assignment?.mode === 'SPLIT'" class="current">
      Сейчас: {{ homeLabel }} — {{ title(assignment.homeAnalyst) || 'не выбран' }};
      {{ awayLabel }} — {{ title(assignment.awayAnalyst) || 'не выбран' }}
    </p>
    <p v-else class="muted">Сейчас никто не назначен.</p>

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

    <div v-if="mode === 'BOTH'" class="stack">
      <label class="field">Аналитик
        <input v-model="bothQuery" type="search" placeholder="Фамилия, имя или почта" autocomplete="off" />
      </label>
      <div v-if="bothHits.length" class="hits">
        <button v-for="person in bothHits" :key="person.id" type="button" class="hit" @click="pick('both', person)">
          {{ title(person) }}
        </button>
      </div>
      <p v-if="bothPick" class="picked">Выбран: {{ title(bothPick) }}</p>
    </div>

    <div v-else class="stack">
      <label class="field">{{ homeLabel }}
        <input v-model="homeQuery" type="search" placeholder="Фамилия, имя или почта" autocomplete="off" />
      </label>
      <div v-if="homeHits.length" class="hits">
        <button v-for="person in homeHits" :key="person.id" type="button" class="hit" @click="pick('home', person)">
          {{ title(person) }}
        </button>
      </div>
      <p v-if="homePick" class="picked">Выбран: {{ title(homePick) }}</p>

      <label class="field">{{ awayLabel }}
        <input v-model="awayQuery" type="search" placeholder="Фамилия, имя или почта" autocomplete="off" />
      </label>
      <div v-if="awayHits.length" class="hits">
        <button v-for="person in awayHits" :key="person.id" type="button" class="hit" @click="pick('away', person)">
          {{ title(person) }}
        </button>
      </div>
      <p v-if="awayPick" class="picked">Выбран: {{ title(awayPick) }}</p>
    </div>

    <div class="actions">
      <button class="btn" type="button" :disabled="pending" @click="save">Назначить</button>
      <button v-if="assignment?.mode" class="btn secondary" type="button" :disabled="pending" @click="clear">
        Снять назначение
      </button>
    </div>
    <p v-if="ok" class="form-ok">{{ ok }}</p>
    <p v-if="error" class="form-error">{{ error }}</p>
  </section>
</template>

<style scoped>
.analysts h2 { margin: 0; font-family: var(--font-display); }
.current { margin: 0; font-weight: 700; color: var(--navy); }
.modes { display: grid; gap: 0.35rem; }
.choice { display: flex; align-items: center; gap: 0.45rem; font-weight: 700; }
.hits { display: grid; gap: 0.3rem; }
.hit {
  text-align: left;
  border: 1px solid var(--line-strong);
  background: #fff;
  border-radius: 12px;
  padding: 0.45rem 0.7rem;
  color: var(--navy);
  font-weight: 700;
  cursor: pointer;
}
.picked { margin: 0; color: var(--navy); font-weight: 700; }
.actions { display: flex; flex-wrap: wrap; gap: 0.5rem; }
</style>
