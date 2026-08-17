<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import api from '../../api/client'
import { useTeamDirectory } from '../../lib/useTeamDirectory'
import StatusBadge from '../../components/StatusBadge.vue'

const route = useRoute()
const match = ref<any>(null)
const teamId = ref('')
const eventType = ref('GOAL')
const message = ref('')
const error = ref('')
const pending = ref(false)
const teams = useTeamDirectory()

const homeLabel = computed(() => teams.fullName(match.value?.homeTeamId, 'Хозяева'))
const awayLabel = computed(() => teams.fullName(match.value?.awayTeamId, 'Гости'))

async function reload() {
  await teams.load()
  const { data } = await api.get(`/matches/${route.params.id}`)
  match.value = data
  if (!teamId.value) teamId.value = data.homeTeamId
}

async function action(path: string) {
  message.value = ''
  error.value = ''
  pending.value = true
  try {
    await api.post(`/referee/matches/${route.params.id}/${path}`)
    await reload()
    message.value = path === 'finish' ? 'Финиш. Можно выдохнуть.' : 'Готово. Свисток услышан.'
  } catch (e: any) {
    error.value = e.response?.data?.message || 'Действие не прошло. Протокол не любит спешку.'
  } finally {
    pending.value = false
  }
}

async function addEvent() {
  message.value = ''
  error.value = ''
  pending.value = true
  try {
    await api.post(`/referee/matches/${route.params.id}/events`, {
      eventType: eventType.value,
      teamId: teamId.value || null,
    })
    message.value = 'Событие в протоколе. Трибуна уже спорит.'
    await reload()
  } catch (e: any) {
    error.value = e.response?.data?.message || 'Не удалось записать событие'
  } finally {
    pending.value = false
  }
}

onMounted(reload)
</script>

<template>
  <section v-if="match" class="stack">
    <div class="page-title">
      <h1>Пульт судьи</h1>
      <p>Крупные кнопки. Нажимайте уверенно — интерфейс пружинит, протокол нет.</p>
    </div>

    <div class="panel scoreboard" :class="{ 'live-pulse': match.status === 'LIVE' }">
      <StatusBadge :status="match.status" />
      <div class="sides">
        <strong>{{ homeLabel }}</strong>
        <div class="score">{{ match.homeScore }} : {{ match.awayScore }}</div>
        <strong>{{ awayLabel }}</strong>
      </div>
    </div>

    <div class="grid controls">
      <button class="btn large success" :disabled="pending" @click="action('start')">Старт</button>
      <button class="btn large secondary" :disabled="pending" @click="action('pause')">Пауза</button>
      <button class="btn large secondary" :disabled="pending" @click="action('resume')">Продолжить</button>
      <button class="btn large danger" :disabled="pending" @click="action('finish')">Финиш</button>
    </div>

    <div class="panel stack">
      <h2>Добавить событие</h2>
      <label class="field">Тип
        <select v-model="eventType">
          <option value="GOAL">Гол</option>
          <option value="ASSIST">Пас</option>
          <option value="YELLOW_CARD">Жёлтая</option>
          <option value="RED_CARD">Красная</option>
          <option value="SUBSTITUTION">Замена</option>
          <option value="POINT">Очко</option>
          <option value="OTHER">Другое</option>
        </select>
      </label>
      <label class="field">Команда
        <select v-model="teamId">
          <option :value="match.homeTeamId">{{ homeLabel }}</option>
          <option :value="match.awayTeamId">{{ awayLabel }}</option>
        </select>
      </label>
      <button class="btn large" :disabled="pending" @click="addEvent">Записать событие</button>
      <p v-if="message" class="form-ok">{{ message }}</p>
      <p v-if="error" class="form-error">{{ error }}</p>
    </div>
  </section>
</template>

<style scoped>
.scoreboard { display: grid; gap: 0.55rem; border-radius: 26px 18px 22px 16px; }
.sides {
  width: 100%;
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  gap: 1rem;
  align-items: center;
}
.sides strong { font-family: var(--font-display); }
.sides strong:last-child { text-align: right; }
.controls { grid-template-columns: repeat(auto-fit, minmax(140px, 1fr)); }
h2 { font-size: 1.15rem; }
</style>
