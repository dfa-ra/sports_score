<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import api from '../../api/client'

const route = useRoute()
const match = ref<any>(null)
const teamId = ref('')
const eventType = ref('GOAL')
const message = ref('')
const error = ref('')

async function reload() {
  const { data } = await api.get(`/matches/${route.params.id}`)
  match.value = data
  if (!teamId.value) teamId.value = data.homeTeamId
}

async function action(path: string) {
  message.value = ''
  error.value = ''
  try {
    await api.post(`/referee/matches/${route.params.id}/${path}`)
    await reload()
    message.value = 'Готово'
  } catch (e: any) {
    error.value = e.response?.data?.message || 'Ошибка действия'
  }
}

async function addEvent() {
  message.value = ''
  error.value = ''
  try {
    await api.post(`/referee/matches/${route.params.id}/events`, {
      eventType: eventType.value,
      teamId: teamId.value || null,
    })
    message.value = 'Событие записано'
    await reload()
  } catch (e: any) {
    error.value = e.response?.data?.message || 'Не удалось записать событие'
  }
}

onMounted(reload)
</script>

<template>
  <section v-if="match" class="stack">
    <div class="page-title">
      <h1>Пульт судьи</h1>
      <p>Крупные кнопки для быстрого ввода во время матча.</p>
    </div>

    <div class="panel scoreboard" :class="{ 'live-pulse': match.status === 'LIVE' }">
      <span :class="match.status === 'LIVE' || match.status === 'PAUSED' ? 'badge live' : 'badge'">
        {{ match.status }}
      </span>
      <div class="score">{{ match.homeScore }} : {{ match.awayScore }}</div>
    </div>

    <div class="grid controls">
      <button class="btn large success" @click="action('start')">Старт</button>
      <button class="btn large secondary" @click="action('pause')">Пауза</button>
      <button class="btn large secondary" @click="action('resume')">Продолжить</button>
      <button class="btn large danger" @click="action('finish')">Финиш</button>
    </div>

    <div class="panel stack">
      <h2>Добавить событие</h2>
      <label class="field">Тип
        <select v-model="eventType">
          <option>GOAL</option>
          <option>ASSIST</option>
          <option>YELLOW_CARD</option>
          <option>RED_CARD</option>
          <option>SUBSTITUTION</option>
          <option>POINT</option>
          <option>OTHER</option>
        </select>
      </label>
      <label class="field">Team ID<input v-model="teamId" /></label>
      <button class="btn large" @click="addEvent">Записать событие</button>
      <p v-if="message" style="color:var(--success)">{{ message }}</p>
      <p v-if="error" style="color:var(--danger)">{{ error }}</p>
    </div>
  </section>
</template>

<style scoped>
.scoreboard { display: grid; gap: 0.45rem; }
.controls { grid-template-columns: repeat(auto-fit, minmax(140px, 1fr)); }
h2 { font-size: 1.1rem; }
</style>
