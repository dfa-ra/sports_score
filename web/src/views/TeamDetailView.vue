<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { bindActiveTab } from '../lib/scrollActive'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import api from '../api/client'
import { useAuthStore } from '../stores/auth'
import { apiError } from '../lib/errors'
import { useTeamDirectory } from '../lib/useTeamDirectory'
import { useFavorites } from '../stores/favorites'
import AdminOnly from '../components/AdminOnly.vue'
import CopyChip from '../components/CopyChip.vue'
import EmptyState from '../components/EmptyState.vue'
import MatchRow from '../components/MatchRow.vue'
import PlayerPicker from '../components/PlayerPicker.vue'
import TeamCardEditor from '../components/TeamCardEditor.vue'
import TeamCrest from '../components/TeamCrest.vue'
import PlayerAvatar from '../components/PlayerAvatar.vue'
import { setPageMeta } from '../lib/pageMeta'
import NotFoundView from './NotFoundView.vue'
import { matchInstant } from '../lib/match'
import { formatMoscowDate } from '../lib/moscowTime'
import { registeredName } from '../lib/playerTitle'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const fav = useFavorites()
const names = useTeamDirectory()
const team = ref<any>(null)
const members = ref<any[]>([])
const stats = ref<any[]>([])
const matches = ref<any[]>([])
type TeamTab = 'results' | 'calendar' | 'squad' | 'stats'
const teamTabs: TeamTab[] = ['results', 'calendar', 'squad', 'stats']
const tabBar = ref<HTMLElement | null>(null)
bindActiveTab(tabBar)
const tab = computed<TeamTab>(() => {
  const raw = String(route.query.tab || '')
  return teamTabs.includes(raw as TeamTab) ? raw as TeamTab : 'results'
})
function setTab(next: TeamTab) {
  const query: Record<string, string> = {}
  for (const [key, value] of Object.entries(route.query)) {
    if (key === 'tab') continue
    if (typeof value === 'string' && value) query[key] = value
  }
  if (next !== 'results') query.tab = next
  if (String(route.query.tab || '') === (query.tab || '')) return
  void router.replace({ query })
}
function rowName(id?: string | null) {
  return names.fullName(id)
}
const played = computed(() => matches.value
  .filter((m) => m.status === 'FINISHED' || m.status === 'CANCELLED')
  .slice()
  .sort((a, b) => String(matchInstant(b) || '').localeCompare(String(matchInstant(a) || ''))))
const upcoming = computed(() => matches.value
  .filter((m) => m.status === 'SCHEDULED' || m.status === 'LIVE' || m.status === 'PAUSED')
  .slice()
  .sort((a, b) => String(matchInstant(a) || '').localeCompare(String(matchInstant(b) || ''))))
const me = ref<any>(null)
const memberIds = computed(() => members.value.map((item) => item.playerId))
const playerId = ref('')
const error = ref('')
const ok = ref('')
const pending = ref(false)
const missing = ref(false)
const starTap = ref(0)

function tapStar() {
  if (!team.value?.id) return
  fav.toggleTeam(team.value.id)
  starTap.value += 1
}

const foundedOnLabel = computed(() => formatMoscowDate(team.value?.foundedOn))
const isCaptain = computed(() => me.value && team.value && me.value.id === team.value.captainId)
const canManage = computed(() => !team.value?.disbanded && (auth.canManageLeague || isCaptain.value))

onMounted(load)
watch(() => route.params.id, () => {
  load()
})
watch(() => route.fullPath, () => {
  const name = team.value?.name
  if (!name || missing.value) return
  const id = String(route.params.id || '')
  void nextTick(() => {
    if (team.value?.name !== name || String(route.params.id || '') !== id) return
    setPageMeta({
      title: name,
      description: `Лига ИТМО: ${name}`,
      path: `/teams/${id}`,
    })
  })
})

async function load() {
  const id = route.params.id
  missing.value = false
  error.value = ''
  try {
    await names.load()
    const [t, m, games, squadStats] = await Promise.all([
      api.get(`/teams/${id}`),
      api.get(`/teams/${id}/members`),
      api.get('/matches', { params: { size: 100, sort: 'scheduledAt,desc' } }),
      api.get(`/teams/${id}/player-stats`),
    ])
    team.value = t.data
    const title = t.data?.name || 'Команда'
    const publish = () => setPageMeta({
      title,
      description: `Лига ИТМО: ${title}`,
      path: `/teams/${id}`,
    })
    publish()
    void nextTick(publish)
    members.value = m.data
    stats.value = squadStats.data ?? []
    matches.value = (games.data.content ?? []).filter((row: any) =>
      row.homeTeamId === t.data.id || row.awayTeamId === t.data.id
    )
    if (auth.isAuthenticated) {
      try {
        const { data } = await api.get('/players/me')
        me.value = data
      } catch {
        me.value = null
      }
    }
  } catch (e: any) {
    const status = e?.response?.status
    if (status === 400 || status === 404) {
      team.value = null
      missing.value = true
      return
    }
    error.value = apiError(e, 'Команда не загрузилась.')
  }
}

async function addMember() {
  error.value = ''
  ok.value = ''
  pending.value = true
  try {
    await api.post(`/teams/${team.value.id}/members`, { playerId: playerId.value })
    ok.value = 'Игрок в составе.'
    await load()
  } catch (e: any) {
    error.value = apiError(e)
  } finally {
    pending.value = false
  }
}

async function removeMember(id: string) {
  error.value = ''
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

async function makeCaptain(id: string) {
  if (!team.value || id === team.value.captainId) return
  error.value = ''
  const previous = team.value.captainId
  const teamId = team.value.id
  team.value = { ...team.value, captainId: id }
  pending.value = true
  try {
    await api.put(`/teams/${teamId}/captain`, { playerId: id })
  } catch (e: any) {
    if (team.value) team.value = { ...team.value, captainId: previous }
    error.value = apiError(e)
    return
  } finally {
    pending.value = false
  }
  try {
    await auth.refreshMe()
    await load()
  } catch {
    // The captain already moved. A later refresh hiccup should not restore the old badge.
  }
}

async function disbandTeam() {
  if (!confirm(`Расформировать «${team.value.name}»? Состав снимут, заявки на турниры снимут.`)) return
  error.value = ''
  pending.value = true
  try {
    await api.delete(`/teams/${team.value.id}`)
    ok.value = 'Команда расформирована.'
    await load()
  } catch (e: any) {
    error.value = apiError(e)
  } finally {
    pending.value = false
  }
}

async function deleteTeam() {
  if (!confirm(`Удалить «${team.value.name}»? Это можно, только если у команды нет матчей.`)) return
  error.value = ''
  pending.value = true
  try {
    await api.delete(`/teams/${team.value.id}`, { params: { purge: true } })
    await router.push('/teams')
  } catch (e: any) {
    error.value = apiError(e)
  } finally {
    pending.value = false
  }
}
</script>

<template>
  <section v-if="team" class="stack">
    <div class="page-title team-head">
      <TeamCrest :src="team.logoUrl" :name="team.shortName || team.name" :size="48" />
      <div>
        <h1>{{ team.name }}</h1>
        <p v-if="team.disbanded">Команда расформирована. История матчей остаётся.</p>
        <p v-else-if="team.shortName || team.foundedOn">{{ team.shortName }}{{ team.shortName && foundedOnLabel ? ' · ' : '' }}{{ foundedOnLabel ? 'осн. ' + foundedOnLabel : '' }}</p>
      </div>
      <button
        class="star"
        type="button"
        :class="{ on: fav.hasTeam(team.id) }"
        @click="tapStar"
      ><span class="star-glyph" :key="starTap" :class="{ 'star-tap': starTap }">★</span></button>
    </div>

    <div ref="tabBar" class="fs-tabs">
      <button type="button" :class="{ on: tab === 'results' }" @click="setTab('results')">Результаты</button>
      <button type="button" :class="{ on: tab === 'calendar' }" @click="setTab('calendar')">Календарь</button>
      <button type="button" :class="{ on: tab === 'squad' }" @click="setTab('squad')">Состав</button>
      <button type="button" :class="{ on: tab === 'stats' }" @click="setTab('stats')">Статистика</button>
    </div>

    <div v-if="canManage && isCaptain" class="panel stack">
      <h2>Карточка</h2>
      <TeamCardEditor :team="team" @saved="load" />
    </div>

    <div v-if="tab === 'results'" class="sheet">
      <EmptyState v-if="!played.length" title="Сыгранных матчей пока нет" />
      <MatchRow
        v-for="m in played"
        :key="m.id"
        :match="m"
        :home-name="rowName(m.homeTeamId)"
        :away-name="rowName(m.awayTeamId)"
        :highlight-team-id="team.id"
      />
    </div>

    <div v-else-if="tab === 'calendar'" class="sheet">
      <EmptyState v-if="!upcoming.length" title="Ближайших матчей нет" />
      <MatchRow
        v-for="m in upcoming"
        :key="m.id"
        :match="m"
        :home-name="rowName(m.homeTeamId)"
        :away-name="rowName(m.awayTeamId)"
        :highlight-team-id="team.id"
      />
    </div>

    <div v-else-if="tab === 'squad'" class="panel stack">
      <h2>Состав</h2>
      <EmptyState v-if="!members.length" title="В составе никого нет" />
      <div v-for="m in members" :key="m.id" class="member">
        <div class="who">
          <RouterLink :to="`/players/${m.playerId}`">
            <strong>{{ registeredName(m) }}</strong>
          </RouterLink>
          <span v-if="m.playerId === team.captainId" class="captain-badge">Капитан</span>
        </div>
        <span class="muted jersey">№{{ m.jerseyNumber ?? '—' }}</span>
        <div v-if="canManage && isCaptain" class="actions">
          <button
            v-if="m.playerId !== team.captainId"
            type="button"
            class="make-captain"
            :disabled="pending"
            @click="makeCaptain(m.playerId)"
          >Сделать капитаном</button>
          <button class="btn ghost" :disabled="pending || m.playerId === team.captainId" @click="removeMember(m.playerId)">Убрать</button>
        </div>
      </div>
      <form v-if="canManage && isCaptain" class="stack" @submit.prevent="addMember">
        <label class="field">Добавить игрока
          <PlayerPicker
            v-model="playerId"
            :exclude-ids="memberIds"
            hint="Добавить можно только уже зарегистрированного игрока"
            empty-text="Игрок не найден, он должен сначала зарегистрироваться"
          />
        </label>
        <button class="btn" type="submit" :disabled="pending || !playerId">Добавить в состав</button>
      </form>
      <p v-if="error && !auth.canManageLeague" class="form-error">{{ error }}</p>
      <p v-if="ok && !auth.canManageLeague" class="form-ok">{{ ok }}</p>
    </div>

    <div v-else class="panel stack stats-card">
      <h2>Статистика</h2>
      <p v-if="!stats.length" class="empty-line">В составе никого нет</p>
      <div v-else class="table-wrap">
        <table class="stat-table">
          <thead>
            <tr>
              <th class="who-h" scope="col">Фамилия Имя</th>
              <th scope="col">Голы</th>
              <th scope="col"><span class="assists-short">Пер.</span><span class="assists-wide">Передачи</span></th>
              <th scope="col">Игры</th>
              <th scope="col">ЖК</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in stats" :key="row.playerId">
              <td>
                <div class="who">
                  <PlayerAvatar :src="row.photoUrl" :name="registeredName(row)" :first-name="row.firstName" :last-name="row.lastName" :size="40" />
                  <RouterLink :to="`/players/${row.playerId}`">{{ registeredName(row) }}</RouterLink>
                  <span v-if="row.playerId === team.captainId" class="captain-badge">Капитан</span>
                </div>
              </td>
              <td class="num">{{ row.goals }}</td>
              <td class="num">{{ row.assists }}</td>
              <td class="num">{{ row.appearances }}</td>
              <td class="num">{{ row.yellowCards }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <AdminOnly v-if="auth.canManageLeague" title="Для админа">
      <p class="muted">Служебные действия. На публичной карточке их нет.</p>
      <CopyChip :value="String(team.id)" label="Скопировать id команды" />
      <TeamCardEditor v-if="!team.disbanded" :team="team" @saved="load" />
      <form v-if="!team.disbanded" class="stack" @submit.prevent="addMember">
        <label class="field">Добавить игрока
          <PlayerPicker
            v-model="playerId"
            :exclude-ids="memberIds"
            hint="Добавить можно только уже зарегистрированного игрока"
            empty-text="Игрок не найден, он должен сначала зарегистрироваться"
          />
        </label>
        <button class="btn" type="submit" :disabled="pending || !playerId">Добавить в состав</button>
      </form>
      <div v-if="!team.disbanded && members.length" class="stack">
        <div v-for="m in members" :key="`admin-${m.id}`" class="member">
          <div class="who">
            <span>{{ registeredName(m) }}</span>
            <span v-if="m.playerId === team.captainId" class="captain-badge">Капитан</span>
          </div>
          <div class="actions">
            <button
              v-if="m.playerId !== team.captainId"
              type="button"
              class="make-captain"
              :disabled="pending"
              @click="makeCaptain(m.playerId)"
            >Сделать капитаном</button>
            <button class="btn ghost" :disabled="pending || m.playerId === team.captainId" @click="removeMember(m.playerId)">Убрать</button>
          </div>
        </div>
      </div>
      <button v-if="!team.disbanded" class="btn danger" :disabled="pending" @click="disbandTeam">Расформировать команду</button>
      <button v-else class="btn danger" :disabled="pending" @click="deleteTeam">Удалить команду</button>
      <p v-if="error" class="form-error">{{ error }}</p>
      <p v-if="ok" class="form-ok">{{ ok }}</p>
    </AdminOnly>
  </section>
  <NotFoundView v-else-if="missing" heading="Команда не найдена" />
  <section v-else-if="error" class="stack">
    <strong>{{ error }}</strong>
    <button class="btn secondary" type="button" @click="load">Повторить</button>
  </section>
</template>

<style scoped>
.team-head {
  display: grid;
  grid-template-columns: auto 1fr auto;
  align-items: center;
  gap: 0.8rem;
}
.star {
  border: 0;
  background: transparent;
  color: #c5ced8;
  font-size: 1.5rem;
  cursor: pointer;
}
.star.on { color: var(--ice); }
.sheet {
  background: #fff;
  border: 1px solid var(--line);
  border-radius: 12px;
  overflow: hidden;
}
h2 { font-size: 1.2rem; }
.member {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.4rem 0.75rem;
  padding: 0.75rem 0.1rem;
  border-bottom: 1px solid var(--line);
}
.who {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  flex: 1 1 9rem;
  min-width: 0;
}
.member a, .stat-table a { color: var(--text-strong); text-decoration: none; font-weight: 700; }
.member a:hover, .stat-table a:hover { color: var(--accent); }
.empty-line { color: var(--navy); font-weight: 700; margin: 0.2rem 0 0.4rem; }
.stats-card { gap: 0.35rem; }
.stat-table { width: 100%; border-collapse: collapse; }
.stat-table th {
  font-family: inherit;
  font-size: 0.68rem;
  font-weight: 700;
  letter-spacing: 0.04em;
  text-transform: none;
  text-align: right;
  color: var(--muted);
  padding: 0.15rem 0.35rem 0.45rem;
  white-space: nowrap;
}
.stat-table th.who-h { text-align: left; }
.assists-wide { display: none; }
@media (min-width: 400px) {
  .assists-short { display: none; }
  .assists-wide { display: inline; }
}
.stat-table td {
  padding: 0.65rem 0.35rem;
  border-top: 1px solid var(--line);
  vertical-align: middle;
}
.stat-table .who { flex-wrap: nowrap; }
.stat-table .who a {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.stat-table .num {
  width: 3.1rem;
  text-align: right;
  font-variant-numeric: tabular-nums;
  font-weight: 700;
  color: var(--text-strong);
  white-space: nowrap;
}
.jersey { font-variant-numeric: tabular-nums; }
.captain-badge {
  flex: 0 0 auto;
  padding: 0.08rem 0.42rem;
  border-radius: 999px;
  background: color-mix(in srgb, var(--ice) 18%, #fff);
  color: var(--navy);
  font-size: 0.68rem;
  font-weight: 700;
  line-height: 1.45;
  letter-spacing: 0.01em;
}
.actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: flex-end;
  gap: 0.35rem 0.8rem;
  margin-left: auto;
}
.make-captain {
  border: 0;
  background: none;
  margin: 0;
  padding: 0;
  font: inherit;
  font-size: 0.78rem;
  font-weight: 600;
  letter-spacing: 0.01em;
  color: color-mix(in srgb, var(--muted) 58%, #fff);
  cursor: pointer;
  white-space: nowrap;
}
.make-captain:hover,
.make-captain:focus-visible { color: var(--navy); }
.make-captain:disabled { cursor: wait; opacity: 0.45; }
@media (max-width: 399px) {
  .stats-card { padding-left: 0.7rem; padding-right: 0.7rem; }
  .stats-card .table-wrap { overflow-x: clip; }
  .stats-card .stat-table {
    table-layout: fixed;
    width: 100%;
  }
  .stats-card .stat-table th:not(.who-h),
  .stats-card .stat-table td.num {
    padding-left: 0.08rem;
    padding-right: 0.08rem;
  }
  .stats-card .stat-table th:nth-child(2),
  .stats-card .stat-table td:nth-child(2),
  .stats-card .stat-table th:nth-child(4),
  .stats-card .stat-table td:nth-child(4) { width: 2.35rem; }
  .stats-card .stat-table th:nth-child(3),
  .stats-card .stat-table td:nth-child(3) { width: 1.95rem; }
  .stats-card .stat-table th:nth-child(5),
  .stats-card .stat-table td:nth-child(5) { width: 1.55rem; }
  .stats-card .stat-table td:first-child { padding-left: 0.1rem; padding-right: 0.15rem; }
  .stats-card .stat-table th.who-h { white-space: normal; }
  .stats-card .stat-table .who {
    display: grid;
    grid-template-columns: 40px minmax(0, 1fr);
    column-gap: 0.4rem;
    row-gap: 0.15rem;
    align-items: center;
  }
  .stats-card .stat-table .who a {
    grid-column: 2;
    min-width: 0;
    white-space: normal;
    overflow: visible;
    text-overflow: unset;
    overflow-wrap: break-word;
  }
  .stats-card .stat-table .captain-badge {
    grid-column: 2;
    justify-self: start;
    width: fit-content;
  }
}
@media (max-width: 720px) {
  .stack { gap: 12px; margin-top: -5px; }
  .team-head { gap: 0; min-height: 44px; margin-bottom: 0; align-items: center; }
  .team-head :deep(.team-crest) { width: 44px !important; height: 44px !important; font-size: 14px !important; }
  .team-head h1 { font-size: 20px; line-height: 24px; font-weight: 800; }
  .team-head p { margin: 0; font-size: 12px; line-height: 16px; font-weight: 500; }
  .star {
    position: relative;
    width: 44px;
    height: 44px;
    font-size: 18px;
    display: grid;
    place-items: center;
  }
  .star::before {
    content: "";
    position: absolute;
    inset: 0;
  }
  .fs-tabs button { padding: 0 9px; font-size: 12px; }
  .sheet { border-radius: 12px; }
}
@media (hover: hover) and (pointer: fine) and (min-width: 721px) {
  .make-captain {
    max-width: 0;
    overflow: hidden;
    opacity: 0;
  }
  .member:hover .make-captain,
  .member:focus-within .make-captain {
    max-width: 11rem;
    opacity: 1;
  }
}
</style>
