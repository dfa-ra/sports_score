<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import api from '../api/client'
import { useAuthStore } from '../stores/auth'
import { eventLabel, formatClock, formatWhen, labelOf } from '../lib/format'
import { apiError } from '../lib/errors'
import { useTeamDirectory } from '../lib/useTeamDirectory'
import AdminOnly from '../components/AdminOnly.vue'
import CopyChip from '../components/CopyChip.vue'
import StatusBadge from '../components/StatusBadge.vue'

const route = useRoute()
const auth = useAuthStore()
const match = ref<any>(null)
const events = ref<any[]>([])
const referees = ref<any[]>([])
const users = ref<any[]>([])
const refereeId = ref('')
const connected = ref(false)
const error = ref('')
const ok = ref('')
const pending = ref(false)
const teams = useTeamDirectory()
let client: Client | null = null

const timeline = computed(() => [...events.value].reverse())

async function load() {
  const id = route.params.id
  await teams.load()
  const [m, e, r] = await Promise.all([
    api.get(`/matches/${id}`),
    api.get(`/matches/${id}/events`),
    api.get(`/matches/${id}/referees`),
  ])
  match.value = m.data
  events.value = e.data
  referees.value = r.data
  if (auth.canManageLeague) {
    const { data } = await api.get('/admin/users', { params: { size: 100 } })
    users.value = (data.content ?? []).filter((u: any) => u.role === 'REFEREE')
    if (!refereeId.value && users.value[0]) refereeId.value = users.value[0].id
  }
}

async function assignReferee() {
  error.value = ''
  ok.value = ''
  pending.value = true
  try {
    await api.post(`/matches/${match.value.id}/referees`, { refereeId: refereeId.value })
    ok.value = 'Судья назначен. Пульт уже ждёт.'
    await load()
  } catch (e: any) {
    error.value = apiError(e)
  } finally {
    pending.value = false
  }
}

onMounted(async () => {
  await load()
  client = new Client({
    webSocketFactory: () => new SockJS('/ws') as any,
    connectHeaders: auth.accessToken ? { Authorization: `Bearer ${auth.accessToken}` } : {},
    onConnect: () => {
      connected.value = true
      client?.subscribe(`/topic/matches/${route.params.id}`, (message) => {
        const live = JSON.parse(message.body)
        match.value = {
          ...match.value,
          status: live.status,
          homeScore: live.homeScore,
          awayScore: live.awayScore,
          gameTimeSeconds: live.gameTimeSeconds,
          period: live.period,
        }
        if (live.lastEvent) {
          events.value = [...events.value.filter((x: any) => x.id !== live.lastEvent.id), live.lastEvent]
        }
      })
    },
    onDisconnect: () => { connected.value = false },
  })
  client.activate()
})

onUnmounted(() => client?.deactivate())
</script>

<template>
  <section v-if="match" class="stack">
    <div class="page-title">
      <h1>{{ teams.fullName(match.homeTeamId) }} — {{ teams.fullName(match.awayTeamId) }}</h1>
      <p>{{ connected ? 'Live-канал дышит' : 'Подключаемся к трансляции…' }} · {{ formatWhen(match.scheduledAt) }}</p>
    </div>

    <div class="panel scoreboard" :class="{ 'live-pulse': match.status === 'LIVE' }">
      <StatusBadge :status="match.status" />
      <div class="sides">
        <strong>{{ teams.name(match.homeTeamId) }}</strong>
        <div class="score">{{ match.homeScore }} : {{ match.awayScore }}</div>
        <strong>{{ teams.name(match.awayTeamId) }}</strong>
      </div>
      <p class="muted">
        Период {{ match.period ?? '—' }} · {{ formatClock(match.gameTimeSeconds) }} игрового времени
      </p>
    </div>

    <div v-if="referees.length || auth.canOfficiate" class="panel stack">
      <h2>Судьи</h2>
      <p v-for="r in referees" :key="r.id" class="muted">{{ r.refereeEmail || r.refereeId }}</p>
      <p v-if="!referees.length" class="muted">Судья ещё не назначен.</p>
      <RouterLink v-if="auth.canOfficiate" class="btn secondary" :to="`/referee/matches/${match.id}`">Открыть пульт</RouterLink>
    </div>

    <div class="panel">
      <h2>Лента событий</h2>
      <div v-if="!events.length" class="empty" style="margin-top:0.75rem">Пока ни гола, ни карточки. Тишина перед взрывом.</div>
      <ul v-else class="timeline">
        <li v-for="ev in timeline" :key="ev.id" :class="{ voided: ev.voided }">
          <span class="t">{{ formatClock(ev.gameTime) }}</span>
          <strong>{{ labelOf(eventLabel, ev.eventType) }}</strong>
          <span class="muted">{{ ev.voided ? 'отменено' : 'в протоколе' }}</span>
        </li>
      </ul>
    </div>

    <AdminOnly v-if="auth.canManageLeague" title="Для админа">
      <CopyChip :value="String(match.id)" label="Скопировать id матча" />
      <form class="stack" @submit.prevent="assignReferee">
        <label class="field">Назначить судью
          <select v-model="refereeId" required>
            <option v-for="u in users" :key="u.id" :value="u.id">{{ u.email }}</option>
          </select>
        </label>
        <button class="btn" type="submit" :disabled="pending || !users.length">Назначить</button>
      </form>
      <p v-if="!users.length" class="muted">Сначала поставьте кому-то роль судьи в админке.</p>
      <p v-if="error" class="form-error">{{ error }}</p>
      <p v-if="ok" class="form-ok">{{ ok }}</p>
    </AdminOnly>
  </section>
</template>

<style scoped>
.scoreboard { display: grid; gap: 0.7rem; justify-items: start; border-radius: 26px 18px 22px 16px; }
.sides {
  width: 100%;
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  gap: 1rem;
  align-items: center;
}
.sides strong { font-family: var(--font-display); font-size: 1.2rem; }
.sides strong:last-child { text-align: right; }
h2 { font-size: 1.2rem; margin-bottom: 0.35rem; }
.timeline { list-style: none; margin: 0.75rem 0 0; padding: 0; display: grid; gap: 0.5rem; }
.timeline li {
  display: grid;
  grid-template-columns: 72px 1fr auto;
  gap: 0.75rem;
  align-items: center;
  padding: 0.65rem 0.75rem;
  border: 1px solid var(--line);
  border-radius: 13px 11px 14px 10px;
  background: rgba(10, 13, 8, 0.35);
}
.timeline li.voided { opacity: 0.5; }
.t { color: var(--accent); font-variant-numeric: tabular-nums; font-size: 0.85rem; }
</style>
