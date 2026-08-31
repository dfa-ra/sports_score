<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import api from '../api/client'
import { useAuthStore } from '../stores/auth'
import { apiError } from '../lib/errors'
import AdminOnly from '../components/AdminOnly.vue'
import CreateMatchForm from '../components/CreateMatchForm.vue'
import EmptyState from '../components/EmptyState.vue'
import StandingTable from '../components/StandingTable.vue'
import StatusBadge from '../components/StatusBadge.vue'
import TeamCrest from '../components/TeamCrest.vue'
import { useTeamDirectory } from '../lib/useTeamDirectory'

const route = useRoute()
const auth = useAuthStore()
const names = useTeamDirectory()
const tournament = ref<any>(null)
const teams = ref<any[]>([])
const standings = ref<any[]>([])
const matches = ref<any[]>([])
const myTeams = ref<any[]>([])
const teamId = ref('')
const name = ref('')
const description = ref('')
const status = ref('REGISTRATION')
const format = ref('ROUND_ROBIN')
const regulations = ref('')
const seasonYear = ref(2026)
const tab = ref<'table' | 'rules'>('table')
const calendarFile = ref<File | null>(null)
const error = ref('')
const ok = ref('')
const pending = ref(false)
const showMatchForm = ref(false)

const canRegister = computed(() => auth.role === 'CAPTAIN')

onMounted(load)

async function load() {
  const id = route.params.id
  await names.load()
  const [t, teamRes, standingRes, matchRes] = await Promise.all([
    api.get(`/tournaments/${id}`),
    api.get(`/tournaments/${id}/teams`),
    api.get(`/tournaments/${id}/standings`),
    api.get(`/tournaments/${id}/matches`, { params: { size: 50 } }),
  ])
  tournament.value = t.data
  teams.value = teamRes.data
  standings.value = standingRes.data
  matches.value = matchRes.data.content ?? []
  name.value = t.data.name
  description.value = t.data.description || ''
  status.value = t.data.status
  format.value = t.data.format
  regulations.value = t.data.regulations || ''
  seasonYear.value = t.data.seasonYear
  if (auth.isAuthenticated) {
    const { data } = await api.get('/teams', { params: { size: 100 } })
    let mine = (data.content ?? []).filter((x: any) => !x.disbanded)
    if (auth.role === 'CAPTAIN') {
      try {
        const me = await api.get('/players/me')
        mine = mine.filter((x: any) => x.captainId === me.data.id)
      } catch {
        mine = []
      }
    }
    myTeams.value = mine
    if (!teamId.value && myTeams.value[0]) teamId.value = myTeams.value[0].id
  }
}

async function save() {
  error.value = ''
  pending.value = true
  try {
    await api.put(`/tournaments/${tournament.value.id}`, {
      name: name.value,
      description: description.value || undefined,
      status: status.value,
      format: format.value,
      regulations: regulations.value || undefined,
      seasonYear: Number(seasonYear.value),
    })
    ok.value = 'Турнир обновлён.'
    await load()
  } catch (e: any) {
    error.value = apiError(e)
  } finally {
    pending.value = false
  }
}

async function registerTeam() {
  error.value = ''
  pending.value = true
  try {
    await api.post(`/tournaments/${tournament.value.id}/teams`, { teamId: teamId.value })
    ok.value = 'Заявка ушла. Ждём допуск админа.'
    await load()
  } catch (e: any) {
    error.value = apiError(e)
  } finally {
    pending.value = false
  }
}

async function approve(id: string) {
  pending.value = true
  try {
    await api.post(`/tournaments/${tournament.value.id}/teams/${id}/approve`)
    await load()
  } catch (e: any) {
    error.value = apiError(e)
  } finally {
    pending.value = false
  }
}

async function importCalendar() {
  if (!calendarFile.value) return
  error.value = ''
  pending.value = true
  try {
    const form = new FormData()
    form.append('file', calendarFile.value)
    const { data } = await api.post(`/tournaments/${tournament.value.id}/calendar/import`, form)
    ok.value = `Календарь: создано ${data.created}, пропущено ${data.skipped}.`
    await load()
  } catch (e: any) {
    error.value = apiError(e)
  } finally {
    pending.value = false
  }
}

async function exclude(id: string) {
  pending.value = true
  try {
    await api.delete(`/tournaments/${tournament.value.id}/teams/${id}`)
    await load()
  } catch (e: any) {
    error.value = apiError(e)
  } finally {
    pending.value = false
  }
}
</script>

<template>
  <section v-if="tournament" class="stack">
    <div class="page-title">
      <StatusBadge :status="tournament.status" />
      <h1>{{ tournament.name }}</h1>
      <p>{{ tournament.description || 'Таблица, заявки и характер сезона — всё на одной странице.' }}</p>
    </div>

    <div v-if="canRegister" class="panel stack">
      <h2>Заявить команду</h2>
      <form class="stack" @submit.prevent="registerTeam">
        <label class="field">Команда
          <select v-model="teamId" required>
            <option v-for="t in myTeams" :key="t.id" :value="t.id">{{ t.name }}</option>
          </select>
        </label>
        <button class="btn" type="submit" :disabled="pending || !myTeams.length">Подать заявку</button>
      </form>
    </div>

    <div class="filters">
      <button class="btn secondary" :class="{ on: tab === 'table' }" @click="tab = 'table'">Таблица</button>
      <button class="btn secondary" :class="{ on: tab === 'rules' }" @click="tab = 'rules'">Регламент</button>
    </div>
    <div v-if="tab === 'rules'" class="panel">
      <h2>Регламент</h2>
      <p style="white-space: pre-wrap">{{ tournament.regulations || 'Регламент ещё не опубликован.' }}</p>
    </div>
    <div v-if="tab === 'table'" class="panel">
      <h2>Таблица</h2>
      <EmptyState v-if="!standings.length" title="Ещё рано считать" text="Очки появятся после первых свистков." />
      <StandingTable v-else :rows="standings" />
    </div>

    <div class="panel stack">
      <h2>Команды</h2>
      <EmptyState v-if="!teams.length" title="Заявок нет" text="Капитаны ещё выбирают цвет формы." />
      <div v-for="team in teams" :key="team.id" class="row">
        <RouterLink class="club" :to="`/teams/${team.teamId}`">
          <TeamCrest :src="names.logo(team.teamId)" :name="team.teamName" :size="22" />
          {{ team.teamName }}
        </RouterLink>
        <StatusBadge :status="team.status" />
      </div>
    </div>

    <div class="panel stack">
      <h2>Матчи</h2>
      <EmptyState v-if="!matches.length" title="Сетки ещё нет" />
      <RouterLink v-for="m in matches" :key="m.id" class="row" :to="`/matches/${m.id}`">
        <span>{{ m.homeScore }}:{{ m.awayScore }}</span>
        <StatusBadge :status="m.status" />
      </RouterLink>
    </div>
    <p v-if="error && !auth.canManageLeague" class="form-error">{{ error }}</p>
    <p v-if="ok && !auth.canManageLeague" class="form-ok">{{ ok }}</p>

    <AdminOnly v-if="auth.canManageLeague" title="Для админа">
      <h2>Заявить команду</h2>
      <form class="stack" @submit.prevent="registerTeam">
        <label class="field">Команда
          <select v-model="teamId" required>
            <option v-for="t in myTeams" :key="t.id" :value="t.id">{{ t.name }}</option>
          </select>
        </label>
        <button class="btn" type="submit" :disabled="pending || !myTeams.length">Подать заявку</button>
      </form>

      <h2>Настройки турнира</h2>
      <form class="stack" @submit.prevent="save">
        <label class="field">Название<input v-model="name" required /></label>
        <label class="field">Описание<textarea v-model="description" rows="2" /></label>
        <label class="field">Сезон<input v-model.number="seasonYear" type="number" /></label>
        <label class="field">Статус
          <select v-model="status">
            <option value="DRAFT">Черновик</option>
            <option value="REGISTRATION">Набор</option>
            <option value="ACTIVE">Идёт</option>
            <option value="FINISHED">Финал</option>
            <option value="CANCELLED">Отменён</option>
          </select>
        </label>
        <label class="field">Формат
          <select v-model="format">
            <option value="ROUND_ROBIN">Круговой турнир</option>
            <option value="CUP">Кубок / плей-офф</option>
            <option value="GROUPS_PLAYOFF">Группы + плей-офф</option>
            <option value="SWISS">Швейцарская система</option>
            <option value="DOUBLE_ELIMINATION">Double elimination</option>
          </select>
        </label>
        <label class="field">Регламент
          <textarea v-model="regulations" rows="5" />
        </label>
        <button class="btn secondary" type="submit" :disabled="pending">Сохранить</button>
      </form>

      <h2>Заявки</h2>
      <div v-for="team in teams" :key="`admin-${team.id}`" class="row">
        <span class="club">
          <TeamCrest :src="names.logo(team.teamId)" :name="team.teamName" :size="22" />
          {{ team.teamName }}
        </span>
        <StatusBadge :status="team.status" />
        <div class="actions">
          <button v-if="team.status !== 'APPROVED'" class="btn" :disabled="pending" @click="approve(team.teamId)">Допустить</button>
          <button class="btn secondary" :disabled="pending" @click="exclude(team.teamId)">Убрать</button>
        </div>
      </div>

      <h2>Календарь из Excel / CSV</h2>
      <p class="muted">Колонки: date, time, home, away. Команды подхватываются по названию и сразу попадают в турнир.</p>
      <form class="stack" @submit.prevent="importCalendar">
        <input type="file" accept=".csv,.xlsx,.xls" @change="calendarFile = ($event.target as HTMLInputElement).files?.[0] || null" />
        <button class="btn secondary" type="submit" :disabled="pending || !calendarFile">Загрузить календарь</button>
      </form>

      <div class="head">
        <h2>Назначить матч</h2>
        <button class="btn" @click="showMatchForm = !showMatchForm">{{ showMatchForm ? 'Скрыть' : 'Форма матча' }}</button>
      </div>
      <CreateMatchForm v-if="showMatchForm" :tournament-id="tournament.id" @created="load" />
      <p v-if="error" class="form-error">{{ error }}</p>
      <p v-if="ok" class="form-ok">{{ ok }}</p>
    </AdminOnly>
  </section>
</template>

<style scoped>
h2 { font-size: 1.2rem; margin-bottom: 0.45rem; }
.head { display: flex; justify-content: space-between; gap: 1rem; align-items: center; flex-wrap: wrap; }
.row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 1rem;
  padding: 0.7rem 0.15rem;
  border-bottom: 1px solid var(--line);
  color: var(--text-strong);
  text-decoration: none;
}
.row:hover { color: var(--accent); }
.actions { display: flex; gap: 0.45rem; }
.club {
  display: inline-flex;
  align-items: center;
  gap: 0.45rem;
}
</style>
