<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import api from '../api/client'
import { useAuthStore } from '../stores/auth'

const route = useRoute()
const auth = useAuthStore()
const match = ref<any>(null)
const events = ref<any[]>([])
const connected = ref(false)
let client: Client | null = null

async function load() {
  const id = route.params.id
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
      <h1>Карточка матча</h1>
      <p>
        {{ connected ? 'Live-канал подключён' : 'Подключение к live…' }}
      </p>
    </div>

    <div class="panel scoreboard" :class="{ 'live-pulse': match.status === 'LIVE' }">
      <span :class="match.status === 'LIVE' || match.status === 'PAUSED' ? 'badge live' : 'badge'">
        {{ match.status }}
      </span>
      <div class="score">{{ match.homeScore }} : {{ match.awayScore }}</div>
      <p class="muted">
        Период {{ match.period ?? '—' }} · {{ match.gameTimeSeconds ?? 0 }}с игрового времени
      </p>
    </div>

    <div class="panel">
      <h2>Лента событий</h2>
      <div v-if="!events.length" class="empty" style="margin-top:0.75rem">Событий пока нет</div>
      <ul v-else class="timeline">
        <li v-for="ev in [...events].reverse()" :key="ev.id" :class="{ voided: ev.voided }">
          <span class="t">t={{ ev.gameTime ?? '—' }}</span>
          <strong>{{ ev.eventType }}</strong>
          <span class="muted">{{ ev.voided ? 'отменено' : 'активно' }}</span>
        </li>
      </ul>
    </div>
  </section>
</template>

<style scoped>
.scoreboard { display: grid; gap: 0.5rem; justify-items: start; }
h2 { font-size: 1.1rem; margin-bottom: 0.35rem; }
.timeline { list-style: none; margin: 0.75rem 0 0; padding: 0; display: grid; gap: 0.45rem; }
.timeline li {
  display: grid;
  grid-template-columns: 72px 1fr auto;
  gap: 0.75rem;
  align-items: center;
  padding: 0.55rem 0.65rem;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: var(--bg);
}
.timeline li.voided { opacity: 0.55; }
.t { color: var(--accent); font-variant-numeric: tabular-nums; font-size: 0.85rem; }
</style>
