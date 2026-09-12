<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import api from '../api/client'
import { useAuthStore } from '../stores/auth'
import { formatWhen } from '../lib/format'
import { apiError } from '../lib/errors'
import EmptyState from '../components/EmptyState.vue'
import StatusBadge from '../components/StatusBadge.vue'
import PlayerPicker from '../components/PlayerPicker.vue'
import TeamCrest from '../components/TeamCrest.vue'

const auth = useAuthStore()
const team = ref<any>(null)
const members = ref<any[]>([])
const matches = ref<any[]>([])
const stats = ref<any>(null)
const playerId = ref('')
const memberIds = computed(() => members.value.map((item) => item.playerId))
const error = ref('')
const ok = ref('')
const pending = ref(false)

const canEditRoster = computed(() => auth.hasRole('CAPTAIN') || auth.hasRole('ADMIN'))

onMounted(load)

async function load() {
  try {
    const { data } = await api.get('/teams/mine')
    team.value = data
    const [m, cal, st] = await Promise.all([
      api.get(`/teams/${data.id}/members`),
      api.get(`/teams/${data.id}/matches`, { params: { size: 30 } }),
      api.get('/statistics/teams', { params: { teamId: data.id } }),
    ])
    members.value = m.data
    matches.value = cal.data.content ?? []
    stats.value = (st.data ?? [])[0] || null
  } catch (e: any) {
    error.value = apiError(e, 'Команда ещё не назначена.')
  }
}

async function addMember() {
  pending.value = true
  error.value = ''
  try {
    await api.post(`/teams/${team.value.id}/members`, { playerId: playerId.value })
    ok.value = 'Игрок добавлен в состав.'
    await load()
  } catch (e: any) {
    error.value = apiError(e)
  } finally {
    pending.value = false
  }
}

async function removeMember(id: string) {
  pending.value = true
  try {
    await api.delete(`/teams/${team.value.id}/members/${id}`)
    await load()
  } catch (e: any) {
    error.value = apiError(e)
  } finally {
    pending.value = false
  }
}
</script>

<template>
  <section class="stack">
    <div class="page-title team-head">
      <TeamCrest v-if="team" :src="team.logoUrl" :name="team.name" :size="42" />
      <div>
        <h1>Моя команда</h1>
        <p v-if="team">{{ team.name }} · основана {{ team.foundedOn || '—' }}</p>
      </div>
    </div>
    <p v-if="error" class="form-error">{{ error }}</p>
    <p v-if="ok" class="form-ok">{{ ok }}</p>
    <EmptyState v-if="!team && !error" title="Команды нет" text="Админ создаёт команду и назначает капитана." />
    <template v-if="team">
      <div class="panel" v-if="stats">
        <h2>Статистика</h2>
        <p>В {{ stats.wins }} · Н {{ stats.draws }} · П {{ stats.losses }} · {{ stats.points }} очков</p>
      </div>
      <div class="panel">
        <h2>Состав</h2>
        <ul class="stack">
          <li v-for="m in members" :key="m.id">
            <RouterLink :to="`/players/${m.playerId}`">{{ m.displayName || `${m.firstName} ${m.lastName}` }}</RouterLink>
            <button v-if="canEditRoster && m.playerId !== team.captainId" class="btn ghost" @click="removeMember(m.playerId)">Убрать</button>
          </li>
        </ul>
        <form v-if="canEditRoster" class="toolbar" @submit.prevent="addMember">
          <PlayerPicker v-model="playerId" :exclude-ids="memberIds" />
          <button class="btn" :disabled="pending || !playerId">Добавить игрока</button>
        </form>
      </div>
      <div class="panel">
        <h2>Календарь команды</h2>
        <EmptyState v-if="!matches.length" title="Матчей нет" />
        <RouterLink v-for="m in matches" :key="m.id" class="row" :to="`/matches/${m.id}`">
          <StatusBadge :status="m.status" />
          <span>{{ formatWhen(m.scheduledAt) }} · {{ m.homeScore }}:{{ m.awayScore }}</span>
        </RouterLink>
      </div>
    </template>
  </section>
</template>

<style scoped>
.team-head { display: flex; align-items: center; gap: 0.8rem; }
.toolbar { display: flex; gap: 0.6rem; flex-wrap: wrap; align-items: end; margin-top: 0.8rem; }
.row { display: flex; gap: 0.7rem; align-items: center; padding: 0.45rem 0; }
</style>
