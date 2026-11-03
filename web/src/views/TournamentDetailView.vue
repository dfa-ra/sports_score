<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import api from '../api/client'
import { useAuthStore } from '../stores/auth'
import { apiError } from '../lib/errors'
import { formatWhen, isTournamentOpenForApply, tournamentDatesError } from '../lib/format'
import { matchInstant, matchStateLabel, publicScore } from '../lib/match'
import { setPageMeta } from '../lib/pageMeta'
import AdminOnly from '../components/AdminOnly.vue'
import ApplyTeamDialog from '../components/ApplyTeamDialog.vue'
import CreateMatchForm from '../components/CreateMatchForm.vue'
import EmptyState from '../components/EmptyState.vue'
import PlayoffAdmin from '../components/PlayoffAdmin.vue'
import PlayoffBracket from '../components/PlayoffBracket.vue'
import StandingGroups from '../components/StandingGroups.vue'
import StatusBadge from '../components/StatusBadge.vue'
import TeamCrest from '../components/TeamCrest.vue'
import { parseStandings, standingGroupsHaveRows, type StandingGroup } from '../lib/standings'
import { useTeamDirectory } from '../lib/useTeamDirectory'

const route = useRoute()
const auth = useAuthStore()
const names = useTeamDirectory()
const tournament = ref<any>(null)
const teams = ref<any[]>([])
const standings = ref<StandingGroup[]>([])
const tableDraft = ref<{ id?: string; name: string; teamIds: string[] }[]>([])
const matches = ref<any[]>([])
const playoff = ref<any>(null)
const name = ref('')
const description = ref('')
const status = ref('REGISTRATION')
const format = ref('ROUND_ROBIN')
const regulations = ref('')
const seasonYear = ref(2026)
const startDate = ref('')
const endDate = ref('')
const tab = ref<'table' | 'rules'>('table')
const calendarFile = ref<File | null>(null)
const error = ref('')
const ok = ref('')
const pending = ref(false)
const showMatchForm = ref(false)
const applying = ref(false)
const applyNote = ref('')
const missing = ref(false)

const registrationOpen = computed(() => isTournamentOpenForApply(tournament.value?.status))
const playedWhileRecruiting = computed(() =>
  tournament.value?.status === 'REGISTRATION'
  && matches.value.some((match) => match.status === 'LIVE' || match.status === 'PAUSED' || match.status === 'FINISHED'),
)
const showApply = computed(() =>
  registrationOpen.value
  && (auth.hasRole('CAPTAIN') || auth.canManageLeague || !auth.isAuthenticated)
)

onMounted(load)
watch(() => route.params.id, () => {
  load()
})

async function load() {
  const id = route.params.id
  if (!tournament.value || tournament.value.id !== id) {
    tournament.value = null
    missing.value = false
    error.value = ''
  }
  let detail: any
  try {
    await names.load()
    detail = (await api.get(`/tournaments/${id}`)).data
  } catch (e: any) {
    if (route.params.id !== id) return
    const status = e?.response?.status
    if (status === 400 || status === 404) {
      tournament.value = null
      missing.value = true
      setPageMeta({
        title: 'Турнир не найден',
        description: 'Такого турнира нет.',
        path: `/tournaments/${id}`,
      })
      return
    }
    error.value = apiError(e, 'Турнир не загрузился.')
    return
  }
  if (route.params.id !== id) return
  try {
    const [teamRes, standingRes, matchRes, tableRes, playoffRes] = await Promise.all([
      api.get(`/tournaments/${id}/teams`),
      api.get(`/tournaments/${id}/standings`),
      api.get(`/tournaments/${id}/matches`, { params: { size: 50 } }),
      api.get(`/tournaments/${id}/tables`),
      api.get(`/tournaments/${id}/playoff`),
    ])
    if (route.params.id !== id) return
    teams.value = teamRes.data
    standings.value = parseStandings(standingRes.data)
    playoff.value = playoffRes.data
    tableDraft.value = (tableRes.data ?? []).map((row: any) => ({
      id: row.id,
      name: row.name,
      teamIds: (row.teamIds ?? []).map(String),
    }))
    matches.value = matchRes.data.content ?? []
    error.value = ''
  } catch (e: any) {
    if (route.params.id !== id) return
    error.value = apiError(e, 'Турнир не загрузился.')
  }
  if (route.params.id !== id) return
  missing.value = false
  tournament.value = detail
  setPageMeta({
    title: detail?.name || 'Турнир',
    description: `${detail?.name || 'Турнир'} — таблица и матчи Лиги ИТМО.`,
    path: `/tournaments/${id}`,
  })
  name.value = detail.name
  description.value = detail.description || ''
  status.value = detail.status
  format.value = detail.format
  regulations.value = detail.regulations || ''
  seasonYear.value = detail.seasonYear
  startDate.value = detail.startDate || ''
  endDate.value = detail.endDate || ''
}

function onApplied(message: string) {
  applyNote.value = message
  load()
}

async function save(event?: Event) {
  error.value = ''
  const form = event?.currentTarget as HTMLFormElement | undefined
  const dates = form ? [...form.querySelectorAll('input[type="date"]')] as HTMLInputElement[] : []
  const start = dates[0]?.value || startDate.value
  const end = dates[1]?.value || endDate.value
  startDate.value = start
  endDate.value = end
  const dateError = tournamentDatesError(start, end)
  if (dateError) {
    error.value = dateError
    return
  }
  pending.value = true
  try {
    await api.put(`/tournaments/${tournament.value.id}`, {
      name: name.value,
      description: description.value || undefined,
      status: status.value,
      format: format.value,
      regulations: regulations.value || undefined,
      seasonYear: Number(seasonYear.value),
      startDate: start || undefined,
      endDate: end || undefined,
    })
    ok.value = 'Турнир обновлён.'
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

const LETTERS = 'ABCDEFGHIJKLMNOP'

function setTableCount(count: number) {
  const next = Math.max(0, Math.min(16, Number(count) || 0))
  const draft = tableDraft.value.slice(0, next)
  while (draft.length < next) {
    draft.push({ name: `Группа ${LETTERS[draft.length] || draft.length + 1}`, teamIds: [] })
  }
  tableDraft.value = draft
}

function toggleTeam(tableIndex: number, teamId: string) {
  tableDraft.value = tableDraft.value.map((table, i) => {
    const has = table.teamIds.includes(teamId)
    if (i === tableIndex) {
      return { ...table, teamIds: has ? table.teamIds.filter((id) => id !== teamId) : [...table.teamIds, teamId] }
    }
    return has ? { ...table, teamIds: table.teamIds.filter((id) => id !== teamId) } : table
  })
}

function teamInTable(teamId: string) {
  return tableDraft.value.findIndex((table) => table.teamIds.includes(teamId))
}

async function saveTables() {
  error.value = ''
  pending.value = true
  try {
    await api.put(`/tournaments/${tournament.value.id}/tables`, {
      tables: tableDraft.value.map((table) => ({
        id: table.id,
        name: table.name.trim(),
        teamIds: table.teamIds,
      })),
    })
    ok.value = 'Таблицы турнира сохранены.'
    await load()
  } catch (e: any) {
    error.value = apiError(e)
  } finally {
    pending.value = false
  }
}

const savedTables = computed(() =>
  tableDraft.value
    .filter((table) => table.id)
    .map((table) => ({ id: table.id as string, name: table.name, teamIds: table.teamIds }))
)
const assignableTeams = computed(() => teams.value.filter((team) => team.status !== 'WITHDRAWN'))
const listedTeams = computed(() => teams.value.filter((team) => team.status !== 'WITHDRAWN'))

async function exclude(team: { teamId: string; teamName?: string }) {
  const name = team.teamName || 'команду'
  if (!confirm(`Убрать «${name}» из турнира? Заявка пропадёт из списка и из таблиц. Несыгранные матчи этой команды в турнире удалятся. Сыгранные останутся в календаре, но из зачёта таблицы уйдут.`)) return
  error.value = ''
  pending.value = true
  try {
    await api.delete(`/tournaments/${tournament.value.id}/teams/${team.teamId}`)
    ok.value = `${name} убрана из турнира.`
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
      <StatusBadge :status="tournament.status" scope="tournament" />
      <h1>{{ tournament.name }}</h1>
      <p v-if="tournament.description">{{ tournament.description }}</p>
    </div>

    <div v-if="showApply" class="panel stack">
      <h2>Заявить команду</h2>
      <p>Пока идёт набор, капитан и админ могут подать заявку. В сетку команду всё равно допускает админ.</p>
      <p v-if="applyNote" class="form-ok">{{ applyNote }}</p>
      <button v-if="auth.isAuthenticated" class="btn" type="button" @click="applying = true">Заявиться</button>
      <RouterLink v-else class="btn" :to="{ name: 'login', query: { redirect: route.fullPath } }">
        Войти и заявиться
      </RouterLink>
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
      <h2>{{ standings.length > 1 ? 'Таблицы' : 'Таблица' }}</h2>
      <EmptyState v-if="!standingGroupsHaveRows(standings)" title="Результатов пока нет" />
      <StandingGroups v-else :tables="standings" :marked-places="playoff?.advancingPlaces ?? []" />
    </div>

    <div class="panel stack">
      <h2>Команды</h2>
      <EmptyState v-if="!listedTeams.length" title="Заявок нет" />
      <div v-for="team in listedTeams" :key="team.id" class="row">
        <RouterLink class="club" :to="`/teams/${team.teamId}`">
          <TeamCrest :src="names.logo(team.teamId)" :name="team.teamName" :size="22" />
          {{ team.teamName }}
        </RouterLink>
        <StatusBadge :status="team.status" />
      </div>
    </div>

    <div v-if="playoff?.rounds?.length" class="panel stack">
      <h2>Сетка плей-офф</h2>
      <PlayoffBracket
        :tournament-id="tournament.id"
        :rounds="playoff.rounds"
        :editable="auth.canManageLeague && playoff.competitionStage === 'PLAYOFF'"
        @saved="load"
      />
    </div>

    <div class="panel stack">
      <h2>Матчи</h2>
      <EmptyState v-if="!matches.length" title="Матчей нет" />
      <RouterLink v-for="m in matches" :key="m.id" class="row match-row" :to="`/matches/${m.id}`">
        <span class="match-side">
          <TeamCrest :src="names.logo(m.homeTeamId)" :name="names.fullName(m.homeTeamId)" :size="22" />
          <span>{{ names.fullName(m.homeTeamId) }}</span>
        </span>
        <span class="match-score">{{ publicScore(m) }}</span>
        <span class="match-side away">
          <span>{{ names.fullName(m.awayTeamId) }}</span>
          <TeamCrest :src="names.logo(m.awayTeamId)" :name="names.fullName(m.awayTeamId)" :size="22" />
        </span>
        <span class="match-meta">
          <span>{{ formatWhen(matchInstant(m)) }}</span>
          <span v-if="m.status !== 'FINISHED'">{{ matchStateLabel(m.status) }}</span>
        </span>
      </RouterLink>
    </div>
    <p v-if="error && !auth.canManageLeague" class="form-error">{{ error }}</p>
    <p v-if="ok && !auth.canManageLeague" class="form-ok">{{ ok }}</p>

    <ApplyTeamDialog
      v-if="applying && tournament"
      :tournament="tournament"
      @close="applying = false"
      @applied="onApplied"
    />

    <AdminOnly v-if="auth.canManageLeague" title="Для админа" open>
      <p v-if="error" class="form-error">{{ error }}</p>
      <p v-if="ok" class="form-ok">{{ ok }}</p>
      <h2>Настройки турнира</h2>
      <form class="stack" novalidate @submit.prevent="save">
        <label class="field">Название<input v-model="name" /></label>
        <label class="field">Описание<textarea v-model="description" rows="2" /></label>
        <label class="field">Сезон<input v-model.number="seasonYear" type="number" /></label>
        <label class="field">Старт<input v-model="startDate" type="date" /></label>
        <label class="field">Финиш<input v-model="endDate" type="date" /></label>
        <p v-if="error" class="form-error">{{ error }}</p>
        <label class="field">Статус
          <select v-model="status">
            <option value="DRAFT">Черновик</option>
            <option value="REGISTRATION">Набор</option>
            <option value="ACTIVE">Идёт</option>
            <option value="FINISHED">Завершён</option>
            <option value="CANCELLED">Отменён</option>
          </select>
        </label>
        <p v-if="playedWhileRecruiting" class="form-error">
          Матчи уже сыграны или идут, а статус всё ещё «Набор». Поставьте «Идёт» и сохраните. Завершённый турнир это само не переписывает.
        </p>
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

      <h2>Таблицы и группы</h2>
      <p class="muted">Сколько групп показать и какие команды в какой. Матчи между разными группами в зачёт не идут.</p>
      <label class="field">Количество таблиц
        <input
          type="number"
          min="0"
          max="16"
          :value="tableDraft.length"
          @input="setTableCount(Number(($event.target as HTMLInputElement).value))"
        />
      </label>
      <div v-for="(table, i) in tableDraft" :key="i" class="group-edit">
        <label class="field">Название
          <input v-model="table.name" maxlength="200" />
        </label>
        <div class="team-picks">
          <label v-for="team in assignableTeams" :key="team.teamId" class="pick">
            <input
              type="checkbox"
              :checked="table.teamIds.includes(team.teamId)"
              @change="toggleTeam(i, team.teamId)"
            />
            <TeamCrest :src="names.logo(team.teamId)" :name="team.teamName" :size="18" />
            <span>{{ team.teamName }}</span>
          </label>
        </div>
      </div>
      <p v-if="assignableTeams.some((team) => teamInTable(team.teamId) < 0) && tableDraft.length" class="muted">
        Без таблицы:
        {{ assignableTeams.filter((team) => teamInTable(team.teamId) < 0).map((team) => team.teamName).join(', ') }}
      </p>
      <button class="btn secondary" type="button" :disabled="pending" @click="saveTables">Сохранить таблицы</button>

      <PlayoffAdmin
        :tournament-id="tournament.id"
        :tables="savedTables"
        :playoff="playoff"
        @changed="load"
      />

      <h2>Заявки</h2>
      <div v-for="team in teams" :key="`admin-${team.id}`" class="row">
        <RouterLink class="club" :to="`/teams/${team.teamId}`">
          <TeamCrest :src="names.logo(team.teamId)" :name="team.teamName" :size="22" />
          {{ team.teamName }}
        </RouterLink>
        <StatusBadge :status="team.status" />
        <div class="actions">
          <button v-if="team.status !== 'APPROVED'" class="btn" :disabled="pending" @click="approve(team.teamId)">Допустить</button>
          <button class="btn danger" :disabled="pending" @click="exclude(team)">Убрать из турнира</button>
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
  <section v-else-if="missing" class="stack">
    <h1>Турнир не найден</h1>
  </section>
  <p v-else-if="error" class="form-error">{{ error }}</p>
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
.row.match-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr) auto;
  align-items: center;
}
.match-side {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  min-width: 0;
}
.match-side.away { justify-content: flex-end; }
.match-score {
  font-variant-numeric: tabular-nums;
  font-weight: 800;
  white-space: nowrap;
}
.match-meta {
  display: grid;
  justify-items: end;
  color: var(--muted);
  font-size: 0.82rem;
  white-space: nowrap;
}
@media (max-width: 720px) {
  .row.match-row {
    grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr);
  }
  .match-meta {
    grid-column: 1 / -1;
    justify-items: start;
    white-space: normal;
  }
}
.actions { display: flex; gap: 0.45rem; }
.club {
  display: inline-flex;
  align-items: center;
  gap: 0.45rem;
}
.group-edit {
  display: grid;
  gap: 0.65rem;
  padding: 0.85rem 0;
  border-bottom: 1px solid var(--line);
}
.team-picks {
  display: grid;
  gap: 0.35rem;
}
.pick {
  display: grid;
  grid-template-columns: auto 18px 1fr;
  gap: 0.45rem;
  align-items: center;
  color: var(--text-strong);
}
</style>
