<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import api from '../api/client'
import { registeredName } from '../lib/playerTitle'
import PlayerAvatar from './PlayerAvatar.vue'

const query = ref('')
const items = ref<any[]>([])
const leaders = ref<any[]>([])
const pending = ref(false)
const searched = ref(false)
const searchError = ref('')
const leadersError = ref('')
const leadersLoading = ref(true)
let timer: number | undefined

function labelOf(player: any) {
  return registeredName(player) || 'Игрок'
}

async function loadLeaders() {
  leadersLoading.value = true
  leadersError.value = ''
  try {
    const current = await api.get('/tournaments/current')
    const id = current.data?.id
    if (!id) {
      leaders.value = []
      return
    }
    const { data } = await api.get('/statistics/board', { params: { tournamentId: id, limit: 12 } })
    const seen = new Set<string>()
    const rows: any[] = []
    for (const player of [...(data.scorers ?? []), ...(data.assists ?? [])]) {
      if (!player?.playerId || seen.has(player.playerId)) continue
      seen.add(player.playerId)
      rows.push(player)
      if (rows.length >= 12) break
    }
    leaders.value = rows
  } catch {
    leaders.value = []
    leadersError.value = 'Список игроков не загрузился.'
  } finally {
    leadersLoading.value = false
  }
}

async function search() {
  const q = query.value.trim()
  if (q.length < 2) {
    items.value = []
    searched.value = false
    pending.value = false
    searchError.value = ''
    return
  }
  pending.value = true
  searchError.value = ''
  try {
    const { data } = await api.get('/players', { params: { q, size: 8, sort: 'lastName,asc' } })
    if (query.value.trim() !== q) return
    items.value = data.content ?? []
    searched.value = true
  } catch {
    if (query.value.trim() !== q) return
    items.value = []
    searched.value = true
    searchError.value = 'Поиск не выполнился.'
  } finally {
    if (query.value.trim() === q) pending.value = false
  }
}

function schedule() {
  if (timer) window.clearTimeout(timer)
  const q = query.value.trim()
  if (q.length < 2) {
    items.value = []
    searched.value = false
    pending.value = false
    searchError.value = ''
    return
  }
  pending.value = true
  searchError.value = ''
  timer = window.setTimeout(() => { void search() }, 250)
}

onMounted(loadLeaders)
onUnmounted(() => {
  if (timer) window.clearTimeout(timer)
})
</script>

<template>
  <div class="search">
    <label class="field">
      <input
        v-model="query"
        type="search"
        placeholder="Поиск игрока"
        aria-label="Поиск игрока"
        autocomplete="off"
        @input="schedule"
      />
    </label>
    <p v-if="pending" class="pending">
      <span class="spin" aria-hidden="true" />
      Ищем…
    </p>
    <p v-else-if="searchError" class="form-error">{{ searchError }}</p>
    <p v-else-if="searched && !items.length" class="muted">Ничего не найдено</p>
    <div v-else-if="items.length" class="hints">
      <RouterLink v-for="player in items" :key="player.id" class="hint" :to="`/players/${player.id}`">
        <PlayerAvatar :src="player.avatarUrl" :name="labelOf(player)" :first-name="player.firstName" :last-name="player.lastName" :size="32" />
        <span>
          <strong>{{ labelOf(player) }}</strong>
          <small>{{ player.position || 'Игрок' }} · №{{ player.jerseyNumber ?? '—' }}</small>
        </span>
      </RouterLink>
    </div>

    <template v-if="query.trim().length < 2">
      <p v-if="leadersLoading" class="pending"><span class="spin" aria-hidden="true" /> Загружаем игроков…</p>
      <p v-else-if="leadersError" class="form-error">{{ leadersError }}</p>
      <div v-else-if="leaders.length" class="leaders">
        <p class="muted">Бомбардиры текущего турнира</p>
        <div class="hints">
          <RouterLink v-for="player in leaders" :key="player.playerId" class="hint" :to="`/players/${player.playerId}`">
            <PlayerAvatar :src="player.avatarUrl" :name="labelOf(player)" :first-name="player.firstName" :last-name="player.lastName" :size="32" />
            <span>
              <strong>{{ labelOf(player) }}</strong>
              <small>{{ player.goals ? `${player.goals} гол.` : '' }}<template v-if="player.goals && player.assists"> · </template>{{ player.assists ? `${player.assists} пас.` : '' }}</small>
            </span>
          </RouterLink>
        </div>
      </div>
      <p v-else class="muted">Игроков пока нет.</p>
    </template>
  </div>
</template>

<style scoped>
.search { display: grid; gap: 0.6rem; }
.pending {
  display: flex;
  align-items: center;
  gap: 0.45rem;
  color: var(--muted);
  margin: 0;
}
.spin {
  width: 0.95rem;
  height: 0.95rem;
  border: 2px solid var(--line-strong);
  border-top-color: var(--navy);
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }
.hints { display: grid; gap: 0.35rem; }
.leaders { display: grid; gap: 0.45rem; }
.hint {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 0.7rem;
  align-items: center;
  padding: 0.55rem 0.7rem;
  border: 1px solid var(--line);
  border-radius: 14px;
  background: #fff;
  color: inherit;
  text-decoration: none;
}
.hint strong { display: block; }
.hint small { color: var(--muted); }
</style>
