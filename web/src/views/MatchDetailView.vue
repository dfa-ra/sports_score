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
const live = ref<any>(null)
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
      client?.subscribe(`/topic/matches/${route.params.id}`, (message) => {
        live.value = JSON.parse(message.body)
        if (live.value) {
          match.value = {
            ...match.value,
            status: live.value.status,
            homeScore: live.value.homeScore,
            awayScore: live.value.awayScore,
            gameTimeSeconds: live.value.gameTimeSeconds,
            period: live.value.period,
          }
          if (live.value.lastEvent) {
            events.value = [...events.value.filter((x) => x.id !== live.value.lastEvent.id), live.value.lastEvent]
          }
        }
      })
    },
  })
  client.activate()
})

onUnmounted(() => client?.deactivate())
</script>

<template>
  <section v-if="match" class="stack">
    <div class="panel live-pulse" v-if="match.status === 'LIVE'">
      <span class="badge">LIVE</span>
      <div class="score">{{ match.homeScore }} : {{ match.awayScore }}</div>
      <p>Period {{ match.period ?? '-' }} · {{ match.gameTimeSeconds ?? 0 }}s</p>
    </div>
    <div class="panel" v-else>
      <span class="badge">{{ match.status }}</span>
      <div class="score">{{ match.homeScore }} : {{ match.awayScore }}</div>
    </div>
    <div class="panel">
      <h2>Events</h2>
      <div v-for="ev in events" :key="ev.id" class="muted">
        {{ ev.eventType }} · {{ ev.voided ? 'voided' : 'active' }} · t={{ ev.gameTime ?? '-' }}
      </div>
    </div>
  </section>
</template>
