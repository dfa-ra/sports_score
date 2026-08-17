<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import api from '../../api/client'

const route = useRoute()
const match = ref<any>(null)
const teamId = ref('')
const eventType = ref('GOAL')
const message = ref('')

async function reload() {
  const { data } = await api.get(`/matches/${route.params.id}`)
  match.value = data
  if (!teamId.value) teamId.value = data.homeTeamId
}

async function action(path: string) {
  message.value = ''
  await api.post(`/referee/matches/${route.params.id}/${path}`)
  await reload()
}

async function addEvent() {
  message.value = ''
  await api.post(`/referee/matches/${route.params.id}/events`, {
    eventType: eventType.value,
    teamId: teamId.value || null,
  })
  message.value = 'Event recorded'
  await reload()
}

onMounted(reload)
</script>

<template>
  <section v-if="match" class="stack">
    <h1>Live Match Control</h1>
    <div class="panel live-pulse">
      <span class="badge">{{ match.status }}</span>
      <div class="score">{{ match.homeScore }} : {{ match.awayScore }}</div>
    </div>
    <div class="grid" style="grid-template-columns:repeat(auto-fit,minmax(140px,1fr))">
      <button class="btn large" @click="action('start')">Start</button>
      <button class="btn large secondary" @click="action('pause')">Pause</button>
      <button class="btn large secondary" @click="action('resume')">Resume</button>
      <button class="btn large danger" @click="action('finish')">Finish</button>
    </div>
    <div class="panel stack">
      <h2>Add event</h2>
      <label class="field">Type
        <select v-model="eventType">
          <option>GOAL</option><option>ASSIST</option><option>YELLOW_CARD</option>
          <option>RED_CARD</option><option>SUBSTITUTION</option><option>POINT</option><option>OTHER</option>
        </select>
      </label>
      <label class="field">Team ID<input v-model="teamId" /></label>
      <button class="btn large" @click="addEvent">Record event</button>
      <p v-if="message">{{ message }}</p>
    </div>
  </section>
</template>
