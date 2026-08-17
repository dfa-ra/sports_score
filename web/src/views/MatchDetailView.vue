<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import api from '../api/client'
import { useAuthStore } from '../stores/auth'
import { eventLabel, formatClock, formatWhen, labelOf } from '../lib/format'
import { useTeamDirectory } from '../lib/useTeamDirectory'
import CopyChip from '../components/CopyChip.vue'
import StatusBadge from '../components/StatusBadge.vue'

const route = useRoute()
const auth = useAuthStore()
const match = ref<any>(null)
const events = ref<any[]>([])
const connected = ref(false)
const teams = useTeamDirectory()
let client: Client | null = null

const timeline = computed(() => [...events.value].reverse())

async function load() {
  const id = route.params.id
  await teams.load()
  const [m, e] = await Promise.all([api.get(`/matches/${id}`), api.get(`/matches/${id}/events`)])
  match.value = m.data
  events.value = e.data
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
      <CopyChip :value="String(match.id)" label="Скопировать id матча" />
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
