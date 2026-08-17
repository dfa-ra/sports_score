<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import api from '../../api/client'
import { labelOf, roleLabel } from '../../lib/format'
import { apiError } from '../../lib/errors'
import { useTeamDirectory } from '../../lib/useTeamDirectory'
import CreateMatchForm from '../../components/CreateMatchForm.vue'
import CreateTournamentForm from '../../components/CreateTournamentForm.vue'
import StatusBadge from '../../components/StatusBadge.vue'

const users = ref<any[]>([])
const tournaments = ref<any[]>([])
const matches = ref<any[]>([])
const teams = ref<any[]>([])
const players = ref<any[]>([])
const sports = ref<any[]>([])
const tab = ref<'users' | 'tournaments' | 'matches' | 'teams' | 'players' | 'referees' | 'statistics'>('users')
const teamStats = ref<any[]>([])
const playerStats = ref<any[]>([])
const error = ref('')
const ok = ref('')
const pending = ref(false)
const names = useTeamDirectory()

const tabs = [
  { id: 'users', label: 'Пользователи' },
  { id: 'tournaments', label: 'Турниры' },
  { id: 'matches', label: 'Матчи' },
  { id: 'teams', label: 'Команды' },
  { id: 'players', label: 'Игроки' },
  { id: 'referees', label: 'Судьи' },
  { id: 'statistics', label: 'Статистика' },
] as const

async function load() {
  const [u, t, m, tm, p, s, ts, ps] = await Promise.all([
    api.get('/admin/users', { params: { size: 100 } }),
    api.get('/tournaments', { params: { size: 50 } }),
    api.get('/matches', { params: { size: 50 } }),
    api.get('/teams', { params: { size: 50, includeDisbanded: true } }),
    api.get('/players', { params: { size: 50 } }),
    api.get('/sports'),
    api.get('/statistics/teams'),
    api.get('/statistics/players'),
  ])
  users.value = u.data.content
  tournaments.value = t.data.content
  matches.value = m.data.content
  teams.value = tm.data.content
  players.value = p.data.content
  sports.value = s.data
  teamStats.value = ts.data
  playerStats.value = ps.data
  await names.load()
}

onMounted(load)

async function updateUser(user: any) {
  error.value = ''
  ok.value = ''
  pending.value = true
  try {
    await api.patch(`/admin/users/${user.id}`, { role: user.role, enabled: user.enabled })
    ok.value = `${user.email} обновлён.`
    await load()
  } catch (e: any) {
    error.value = apiError(e)
  } finally {
    pending.value = false
  }
}

async function disbandTeam(team: any) {
  if (team.disbanded) return
  if (!confirm(`Расформировать «${team.name}»? Состав снимут, заявки на турниры снимут.`)) return
  error.value = ''
  ok.value = ''
  pending.value = true
  try {
    await api.delete(`/teams/${team.id}`)
    ok.value = `${team.name} расформирована.`
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
    <div class="page-title">
      <p class="eyebrow">Служебный вход</p>
      <h1>Админ-панель</h1>
      <p>Всё управление лигой — здесь. Без curl, без Swagger, только кнопки.</p>
    </div>
    <div class="tabs">
      <button
        v-for="t in tabs"
        :key="t.id"
        class="btn secondary"
        :class="{ on: tab === t.id }"
        @click="tab = t.id"
      >{{ t.label }}</button>
    </div>
    <p v-if="error" class="form-error">{{ error }}</p>
    <p v-if="ok" class="form-ok">{{ ok }}</p>

    <div v-if="tab === 'users'" class="panel">
      <h2>Пользователи</h2>
      <p class="muted">Админа через форму не назначают — он из .env. Остальным роли ставятся здесь.</p>
      <table class="table">
        <thead><tr><th>Email</th><th>Роль</th><th>Активен</th><th></th></tr></thead>
        <tbody>
          <tr v-for="u in users" :key="u.id">
            <td>{{ u.email }}</td>
            <td>
              <select v-if="u.role !== 'ADMIN'" v-model="u.role">
                <option value="FAN">Зритель</option>
                <option value="PLAYER">Игрок</option>
                <option value="CAPTAIN">Капитан</option>
                <option value="REFEREE">Судья</option>
              </select>
              <span v-else>{{ labelOf(roleLabel, u.role) }}</span>
            </td>
            <td>
              <label class="check">
                <input v-model="u.enabled" type="checkbox" :disabled="u.role === 'ADMIN'" />
                {{ u.enabled ? 'да' : 'нет' }}
              </label>
            </td>
            <td>
              <button v-if="u.role !== 'ADMIN'" class="btn" :disabled="pending" @click="updateUser(u)">Сохранить</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <div v-else-if="tab === 'tournaments'" class="grid two">
      <div class="panel stack">
        <h2>Новый турнир</h2>
        <CreateTournamentForm @created="load" />
      </div>
      <div class="panel">
        <h2>Уже идут</h2>
        <div v-for="t in tournaments" :key="t.id" class="row">
          <RouterLink :to="`/tournaments/${t.id}`">{{ t.name }}</RouterLink>
          <StatusBadge :status="t.status" />
        </div>
      </div>
    </div>

    <div v-else-if="tab === 'matches'" class="grid two">
      <div class="panel stack">
        <h2>Назначить матч</h2>
        <CreateMatchForm @created="load" />
      </div>
      <div class="panel">
        <h2>Сетка</h2>
        <div v-for="m in matches" :key="m.id" class="row">
          <RouterLink :to="`/matches/${m.id}`">
            {{ names.name(m.homeTeamId) }} {{ m.homeScore }}:{{ m.awayScore }} {{ names.name(m.awayTeamId) }}
          </RouterLink>
          <StatusBadge :status="m.status" />
        </div>
      </div>
    </div>

    <div v-else-if="tab === 'teams'" class="panel stack">
      <h2>Клубы</h2>
      <p class="muted">Админ команды не создаёт — только правит карточку и может расформировать.</p>
      <div v-for="t in teams" :key="t.id" class="row">
        <RouterLink :to="`/teams/${t.id}`">{{ t.name }}</RouterLink>
        <span v-if="t.disbanded" class="muted">расформирована</span>
        <button v-else class="btn danger" :disabled="pending" @click="disbandTeam(t)">Расформировать</button>
      </div>
    </div>

    <div v-else-if="tab === 'players'" class="panel">
      <h2>Игроки</h2>
      <div v-for="p in players" :key="p.id" class="row">
        <RouterLink :to="`/players/${p.id}`">{{ p.displayName || `${p.firstName} ${p.lastName}` }}</RouterLink>
      </div>
    </div>

    <div v-else-if="tab === 'referees'" class="panel stack">
      <h2>Судьи</h2>
      <p class="muted">Роль ставится во вкладке «Пользователи». Потом судью назначают в карточке матча.</p>
      <div v-for="u in users.filter(x => x.role === 'REFEREE')" :key="u.id" class="row">{{ u.email }}</div>
      <p class="muted">Виды спорта: {{ sports.map(s => s.code).join(', ') || 'пока не заданы' }}</p>
    </div>

    <div v-else class="panel stack">
      <h2>Статистика</h2>
      <p>Топ игроков по голам:</p>
      <div v-for="p in playerStats.slice(0, 10)" :key="p.playerId" class="row">
        {{ p.displayName }} · G{{ p.goals }} A{{ p.assists }}
      </div>
      <p>Команды:</p>
      <div v-for="t in teamStats.slice(0, 10)" :key="t.teamId" class="row">
        {{ t.teamName }} · {{ t.points }} очков
      </div>
    </div>
  </section>
</template>

<style scoped>
.tabs { display: flex; flex-wrap: wrap; gap: 0.5rem; }
.btn.on { background: var(--accent); color: #22180b; border-color: transparent; }
h2 { font-size: 1.15rem; margin-bottom: 0.55rem; }
.row {
  display: flex;
  justify-content: space-between;
  gap: 1rem;
  padding: 0.65rem 0;
  border-bottom: 1px solid var(--line);
}
.grid.two { display: grid; grid-template-columns: 1fr 1fr; gap: 1rem; }
.check { display: inline-flex; align-items: center; gap: 0.4rem; }
@media (max-width: 860px) {
  .grid.two { grid-template-columns: 1fr; }
}
</style>
