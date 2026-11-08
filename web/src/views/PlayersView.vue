<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import api from '../api/client'
import PlayerAvatar from '../components/PlayerAvatar.vue'
import ScrollFade from '../components/ScrollFade.vue'
import TeamCrest from '../components/TeamCrest.vue'
import { BRAND_MARK_SRC } from '../lib/brand'
import { setPageMeta } from '../lib/pageMeta'
import { isPublicStatus, pageContent } from '../lib/q2Tournaments'
import {
  boardParams,
  goalsAside,
  goalsFromCard,
  highlightParts,
  playerSearchParams,
  playerSearchQuery,
  teamFromCard,
  unitWord,
} from '../lib/q2Players'
import { registeredName } from '../lib/playerTitle'
import { useTeamDirectory } from '../lib/useTeamDirectory'

type Person = {
  playerId?: string
  id?: string
  firstName?: string | null
  lastName?: string | null
  displayName?: string | null
  avatarUrl?: string | null
  goals?: number
  assists?: number
  appearances?: number
  teamId?: string | null
  teamLogoUrl?: string | null
}

type Hit = {
  id: string
  name: string
  firstName?: string | null
  lastName?: string | null
  avatarUrl?: string | null
  team: string
  logo: string
  goals: number | null
}

const teams = useTeamDirectory()
const query = ref('')
const chipId = ref<string | null>(null)
const chips = ref<{ id: string; name: string }[]>([])
const scorers = ref<Person[]>([])
const assists = ref<Person[]>([])
const mode = ref<'goals' | 'assists'>('goals')
const limit = ref(10)
const loading = ref(true)
const error = ref('')
const SEARCH_PAGE_SIZE = 10
const hits = ref<Hit[]>([])
const searchPage = ref(0)
const searchTotal = ref(0)
const searchState = ref<'idle' | 'loading' | 'ready' | 'empty' | 'error'>('idle')
let boardTicket = 0
let searchTicket = 0
let searchTimer: number | undefined

const searching = computed(() => query.value.trim().length >= 2)
const tableRows = computed(() => (mode.value === 'goals' ? scorers.value : assists.value))
const scorer = computed(() => scorers.value[0] || null)
const assist = computed(() => assists.value[0] || null)
const canMore = computed(() => tableRows.value.length >= limit.value && tableRows.value.length > 0)
const searchCanMore = computed(() => hits.value.length > 0 && hits.value.length < searchTotal.value)

function nameOf(person?: Person | null) {
  return registeredName(person) || 'Игрок'
}

function teamName(id?: string | null) {
  if (!id) return ''
  const known = teams.fullName(id, '')
  return known && known !== 'Команда' ? known : ''
}

async function loadChips() {
  try {
    const rows: { id: string; name: string }[] = []
    let page = 0
    for (;;) {
      const { data } = await api.get('/tournaments', { params: { page, size: 50 } })
      const content = pageContent(data)
      for (const row of content) {
        if (!row || typeof row !== 'object') continue
        const item = row as { id?: string; name?: string; status?: string }
        if (!item.id || !isPublicStatus(item.status)) continue
        rows.push({ id: item.id, name: item.name || 'Турнир' })
      }
      const pages = Number(data?.totalPages || 0)
      page += 1
      if (!content.length || content.length < 50 || (pages > 0 && page >= pages) || page > 40) break
    }
    chips.value = rows
  } catch {
    chips.value = []
  }
}

async function loadBoard() {
  const mine = ++boardTicket
  const pinned = chipId.value
  loading.value = true
  error.value = ''
  try {
    const { data } = await api.get('/statistics/board', { params: boardParams(pinned, limit.value) })
    if (mine !== boardTicket || pinned !== chipId.value) return
    scorers.value = Array.isArray(data?.scorers) ? data.scorers : []
    assists.value = Array.isArray(data?.assists) ? data.assists : []
  } catch {
    if (mine !== boardTicket || pinned !== chipId.value) return
    scorers.value = []
    assists.value = []
    error.value = 'Игроки не загрузились'
  } finally {
    if (mine === boardTicket) loading.value = false
  }
}

function pickChip(id: string | null) {
  if (chipId.value === id) return
  chipId.value = id
  limit.value = 10
  void loadBoard()
}

function showMore() {
  limit.value += 10
  void loadBoard()
}

function knownHit(person: Person): { team: string; logo: string; goals: number | null } {
  const team = teamName(person.teamId)
  return {
    team,
    logo: person.teamLogoUrl || teams.logo(person.teamId) || '',
    goals: typeof person.goals === 'number' ? person.goals : null,
  }
}

async function enrich(list: Hit[], mine: number, q: string) {
  await pool(list, 4, async (hit) => {
    if (hit.goals != null && hit.team) return
    try {
      const { data } = await api.get(`/players/${hit.id}/card`)
      if (mine !== searchTicket || playerSearchQuery(query.value) !== q) return
      const goals = goalsFromCard(data)
      const team = teamFromCard(data)
      hit.goals = goals
      if (team?.name) hit.team = team.name
      if (team?.logo) hit.logo = team.logo
    } catch {
      /* Missing card leaves the dash. The name is already on screen. */
    }
  })
}

async function pool<T>(list: T[], limitSize: number, job: (item: T) => Promise<void>) {
  let cursor = 0
  async function worker() {
    for (;;) {
      const index = cursor
      cursor += 1
      if (index >= list.length) return
      await job(list[index])
    }
  }
  const width = Math.min(limitSize, list.length)
  if (!width) return
  await Promise.all(Array.from({ length: width }, () => worker()))
}

function hitsFrom(content: Person[], kept: Map<string, Hit>): Hit[] {
  const board = new Map<string, Person>()
  for (const row of [...scorers.value, ...assists.value]) {
    if (row.playerId) board.set(row.playerId, row)
  }
  const seen = new Set<string>()
  const next: Hit[] = []
  for (const person of content) {
    const id = String(person.id || person.playerId || '')
    if (!id || seen.has(id)) continue
    seen.add(id)
    const prior = board.get(id)
    const remembered = kept.get(id)
    const extra = prior
      ? knownHit(prior)
      : { team: remembered?.team || '', logo: remembered?.logo || '', goals: remembered?.goals ?? null }
    next.push({
      id,
      name: nameOf(person),
      firstName: person.firstName,
      lastName: person.lastName,
      avatarUrl: person.avatarUrl,
      team: extra.team,
      logo: extra.logo,
      goals: extra.goals,
    })
  }
  return next
}

async function search(more = false) {
  const q = playerSearchQuery(query.value)
  const pinned = chipId.value
  if (q.length < 2) {
    searchState.value = 'idle'
    hits.value = []
    searchTotal.value = 0
    searchPage.value = 0
    return
  }
  const page = more ? searchPage.value + 1 : 0
  const mine = ++searchTicket
  if (!more) searchState.value = 'loading'
  try {
    const { data } = await api.get('/players', {
      params: playerSearchParams(q, page, pinned, SEARCH_PAGE_SIZE),
    })
    if (mine !== searchTicket || playerSearchQuery(query.value) !== q || chipId.value !== pinned) return
    const content = pageContent(data) as Person[]
    const reported = Number((data as { totalElements?: unknown } | null)?.totalElements)
    const kept = new Map(hits.value.map((hit) => [hit.id, hit]))
    const incoming = hitsFrom(content, kept)
    if (more) {
      const seen = new Set(hits.value.map((hit) => hit.id))
      hits.value = [...hits.value, ...incoming.filter((hit) => !seen.has(hit.id))]
    } else {
      hits.value = incoming
    }
    searchPage.value = page
    if (Number.isFinite(reported)) searchTotal.value = reported
    else if (!more) searchTotal.value = hits.value.length
    searchState.value = hits.value.length ? 'ready' : 'empty'
    void enrich(hits.value, mine, q)
  } catch {
    if (mine !== searchTicket || playerSearchQuery(query.value) !== q || chipId.value !== pinned) return
    if (more) return
    hits.value = []
    searchTotal.value = 0
    searchPage.value = 0
    searchState.value = 'error'
  }
}

function showMoreSearch() {
  void search(true)
}

function scheduleSearch() {
  if (searchTimer) window.clearTimeout(searchTimer)
  const q = playerSearchQuery(query.value)
  if (q.length < 2) {
    searchTicket += 1
    hits.value = []
    searchTotal.value = 0
    searchPage.value = 0
    searchState.value = 'idle'
    return
  }
  searchPage.value = 0
  searchState.value = 'loading'
  searchTimer = window.setTimeout(() => { void search() }, 200)
}

watch(query, scheduleSearch)

function clearQuery() {
  query.value = ''
}

onMounted(() => {
  setPageMeta({ title: 'Игроки', description: 'Игроки Лиги ИТМО.' })
  void teams.load()
  void loadChips()
  void loadBoard()
})
</script>

<template>
  <section class="players">
    <header class="hero">
      <img class="mark" :src="BRAND_MARK_SRC" alt="" />
      <h1>Игроки</h1>
      <label class="search">
        <input
          v-model="query"
          type="text"
          placeholder="Поиск игрока"
          aria-label="Поиск игрока"
          autocomplete="off"
        />
        <button v-if="query" type="button" aria-label="Очистить" @click="clearQuery">×</button>
      </label>
    </header>

    <template v-if="searching">
      <div v-if="searchState === 'loading'" class="sheet" aria-hidden="true">
        <div v-for="n in 4" :key="n" class="skel">
          <span class="skeleton dot" />
          <span class="skeleton bars" />
          <span class="skeleton num" />
        </div>
      </div>
      <div v-else-if="searchState === 'error'" class="sheet note">
        <p>Игроки не загрузились</p>
        <button class="btn" type="button" @click="search()">Повторить</button>
      </div>
      <div v-else-if="searchState === 'empty'" class="sheet note">
        <p>Игроков с таким именем нет</p>
        <p class="quiet">Проверьте написание</p>
      </div>
      <div v-else class="sheet">
        <p class="found">Найдено · {{ searchTotal }}</p>
        <RouterLink v-for="hit in hits" :key="hit.id" class="hit" :to="`/players/${hit.id}`">
          <PlayerAvatar :src="hit.avatarUrl" :first-name="hit.firstName" :last-name="hit.lastName" :name="hit.name" :size="36" />
          <span class="who name-team">
            <span class="player-name">
              <template v-for="(part, index) in highlightParts(hit.name, query)" :key="index">
                <mark v-if="part.hit">{{ part.text }}</mark>
                <template v-else>{{ part.text }}</template>
              </template>
            </span>
            <span v-if="hit.team" class="club">
              <TeamCrest :name="hit.team" :src="hit.logo" :size="16" />
              <span class="club-name">{{ hit.team }}</span>
            </span>
          </span>
          <span class="goals">{{ goalsAside(hit.goals) }}</span>
        </RouterLink>
        <button v-if="searchCanMore" class="more" type="button" @click="showMoreSearch">Показать ещё</button>
      </div>
    </template>

    <template v-else-if="error">
      <div class="sheet note">
        <p>{{ error }}</p>
        <button class="btn" type="button" @click="loadBoard">Повторить</button>
      </div>
    </template>

    <template v-else>
      <div v-if="loading" class="leaders" aria-hidden="true">
        <span class="skeleton card" />
        <span class="skeleton card" />
      </div>
      <div v-else class="leaders">
        <article class="leader">
          <p>Бомбардир</p>
          <div v-if="scorer" class="leader-row">
            <PlayerAvatar :src="scorer.avatarUrl" :first-name="scorer.firstName" :last-name="scorer.lastName" :name="nameOf(scorer)" :size="40" />
            <span class="who name-team">
              <RouterLink :to="`/players/${scorer.playerId}`">{{ nameOf(scorer) }}</RouterLink>
              <span v-if="teamName(scorer.teamId)" class="club">
                <TeamCrest :name="teamName(scorer.teamId)" :src="scorer.teamLogoUrl || teams.logo(scorer.teamId)" :size="16" />
                <span class="club-name">{{ teamName(scorer.teamId) }}</span>
              </span>
            </span>
            <span class="leader-metric"><b>{{ scorer.goals ?? 0 }}</b><small>{{ unitWord(scorer.goals || 0, 'goals') }}</small></span>
          </div>
        </article>
        <article class="leader">
          <p>Ассистент</p>
          <div v-if="assist" class="leader-row">
            <PlayerAvatar :src="assist.avatarUrl" :first-name="assist.firstName" :last-name="assist.lastName" :name="nameOf(assist)" :size="40" />
            <span class="who name-team">
              <RouterLink :to="`/players/${assist.playerId}`">{{ nameOf(assist) }}</RouterLink>
              <span v-if="teamName(assist.teamId)" class="club">
                <TeamCrest :name="teamName(assist.teamId)" :src="assist.teamLogoUrl || teams.logo(assist.teamId)" :size="16" />
                <span class="club-name">{{ teamName(assist.teamId) }}</span>
              </span>
            </span>
            <span class="leader-metric"><b>{{ assist.assists ?? 0 }}</b><small>{{ unitWord(assist.assists || 0, 'assists') }}</small></span>
          </div>
        </article>
      </div>

      <div class="chips-sticky">
        <ScrollFade solid arrows fade="#e8eef4" :watch-key="chipId || 'all'">
          <div class="chips" role="group" aria-label="Турнир статистики">
            <button type="button" class="chip" :class="{ on: !chipId }" :aria-pressed="!chipId" @click="pickChip(null)">Все турниры</button>
            <button
              v-for="chip in chips"
              :key="chip.id"
              type="button"
              class="chip"
              :class="{ on: chipId === chip.id }"
              :aria-pressed="chipId === chip.id"
              @click="pickChip(chip.id)"
            >{{ chip.name }}</button>
          </div>
        </ScrollFade>
      </div>

      <div class="sheet">
        <div class="board-head">
          <h2>Бомбардиры</h2>
          <div class="switch" role="group" aria-label="Показатель">
            <button type="button" :class="{ on: mode === 'goals' }" :aria-pressed="mode === 'goals'" @click="mode = 'goals'">Голы</button>
            <button type="button" :class="{ on: mode === 'assists' }" :aria-pressed="mode === 'assists'" @click="mode = 'assists'">Пасы</button>
          </div>
        </div>
        <div v-if="loading" aria-hidden="true">
          <div v-for="n in 6" :key="n" class="skel">
            <span class="skeleton dot" />
            <span class="skeleton bars" />
            <span class="skeleton num" />
          </div>
        </div>
        <template v-else>
          <div class="cols head">
            <span>#</span>
            <span>Игрок</span>
            <span class="stat stat-m">М</span>
            <span class="stat" :class="mode === 'goals' ? 'stat-side' : 'stat-main'">П</span>
            <span class="stat" :class="mode === 'assists' ? 'stat-side' : 'stat-main'">Г</span>
          </div>
          <RouterLink v-for="(row, index) in tableRows" :key="row.playerId || index" class="cols line" :to="`/players/${row.playerId}`">
            <span class="place">{{ index + 1 }}</span>
            <span class="who row-who">
              <PlayerAvatar :src="row.avatarUrl" :first-name="row.firstName" :last-name="row.lastName" :name="nameOf(row)" :size="32" />
              <span class="name-team">
                <span class="player-name">{{ nameOf(row) }}</span>
                <span v-if="teamName(row.teamId) || row.teamLogoUrl" class="club">
                  <TeamCrest :name="teamName(row.teamId) || 'Команда'" :src="row.teamLogoUrl || teams.logo(row.teamId)" :size="16" />
                  <span class="club-name">{{ teamName(row.teamId) }}</span>
                </span>
              </span>
            </span>
            <span class="stat stat-m">{{ row.appearances ?? 0 }}</span>
            <span class="stat" :class="mode === 'goals' ? 'stat-side' : 'stat-main'">{{ row.assists ?? 0 }}</span>
            <span class="stat" :class="mode === 'assists' ? 'stat-side' : 'stat-main'">{{ row.goals ?? 0 }}</span>
          </RouterLink>
          <p v-if="!tableRows.length" class="note quiet">Игроков пока нет</p>
          <button v-if="canMore" class="more" type="button" @click="showMore">Показать ещё</button>
          <p class="legend">М матчи · П пасы · Г голы</p>
        </template>
      </div>
    </template>
  </section>
</template>

<style scoped>
.players { min-width: 0; max-width: 100%; display: grid; gap: 0.8rem; }
.hero {
  position: relative;
  overflow: hidden;
  background: #001433;
  color: #fff;
  border-radius: 22px;
  padding: 1.1rem 1.15rem 1rem;
  display: grid;
  gap: 0.75rem;
}
.mark {
  position: absolute;
  right: -6px;
  top: -16px;
  width: 120px;
  height: 120px;
  object-fit: contain;
  opacity: 0.16;
  pointer-events: none;
}
.hero h1 { margin: 0; font-size: 2rem; letter-spacing: 0.04em; color: #fff; }
.search { position: relative; display: block; }
.search input {
  width: 100%;
  border: 0;
  border-radius: 12px;
  padding: 0.75rem 2.4rem 0.75rem 0.9rem;
  background: #fff;
  color: var(--ink);
}
.search input::-webkit-search-cancel-button { display: none; }
.search button {
  position: absolute;
  top: 50%;
  right: 0.45rem;
  transform: translateY(-50%);
  width: 1.8rem;
  height: 1.8rem;
  border: 0;
  border-radius: 50%;
  background: transparent;
  color: #5d6b7c;
  cursor: pointer;
  font-size: 1.2rem;
}
.chips-sticky { position: sticky; top: 64px; z-index: 4; min-width: 0; max-width: 100%; background: #e8eef4; padding: 0.15rem 0; }
.chips { display: flex; gap: 0.4rem; width: 100%; min-width: 0; overflow-x: auto; scrollbar-width: none; }
.chips::-webkit-scrollbar { display: none; }
.chip {
  border: 1px solid #d5e2ee;
  background: #fff;
  color: var(--navy);
  border-radius: 999px;
  padding: 0.4rem 0.8rem;
  font-weight: 700;
  white-space: nowrap;
  cursor: pointer;
}
.chip.on { background: #4cb4e5; color: #04203f; border-color: transparent; }
.leaders { display: grid; grid-template-columns: 1fr 1fr; gap: 0.7rem; align-items: start; }
.leader, .sheet { background: #fff; border-radius: 16px; min-width: 0; }
.leader { padding: 20px; border-radius: 18px; }
.leader p { margin: 0 0 8px; font-size: 12px; line-height: 1.2; letter-spacing: 0.08em; text-transform: uppercase; color: #8b97a8; }
.leader-row, .hit { display: flex; align-items: center; gap: 0.55rem; }
.leader-row { align-items: center; min-width: 0; }
.leader-row :deep(.player-avatar) { flex: 0 0 auto; }
.who, .name-team {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 0.35rem;
  flex: 1;
}
.leader-row .name-team {
  flex-direction: column;
  align-items: stretch;
  justify-content: center;
  gap: 2px;
}
.player-name, .who a { font-weight: 700; color: var(--navy); line-height: 1.2; min-width: 0; overflow-wrap: break-word; }
.leader-row .who a { display: block; max-width: 100%; font-size: 16px; line-height: 1.2; white-space: normal; }
.club {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  margin: 0;
  min-width: 0;
  color: #6d7b8c;
  font-size: 0.75rem;
  line-height: 1.2;
}
.leader-row .club {
  display: flex;
  align-items: flex-start;
  max-width: 100%;
  font-size: 12px;
  line-height: 1.2;
  white-space: normal;
}
.leader-row .club :deep(.team-crest) { flex: 0 0 auto; }
.club :deep(.team-crest),
.club-name { line-height: 1.2; }
.club-name { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.leader-row .club-name {
  white-space: normal;
  overflow: visible;
  text-overflow: unset;
  overflow-wrap: break-word;
}
.leader-metric {
  flex: 0 0 auto;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  align-self: center;
  gap: 4px;
  min-width: 72px;
  margin-left: auto;
  text-align: center;
}
.leader-metric b,
.leader-metric small {
  display: block;
  margin: 0;
  padding: 0;
  white-space: nowrap;
  line-height: 1;
}
.leader-metric b {
  font-family: var(--font-display);
  font-size: 38px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  color: var(--navy);
}
.leader-metric small { color: #8b97a8; font-size: 12px; font-weight: 600; }
.leaders .card { min-height: 116px; border-radius: 18px; display: block; }
.sheet { overflow: hidden; }
.board-head { display: flex; justify-content: space-between; align-items: center; gap: 0.6rem; padding: 0.75rem 0.85rem 0.3rem; }
.board-head h2 { margin: 0; font-size: 1rem; letter-spacing: 0.04em; }
.switch { display: inline-flex; background: #eef3f8; border-radius: 999px; padding: 0.15rem; }
.switch button { border: 0; background: transparent; border-radius: 999px; padding: 0.28rem 0.7rem; font-weight: 700; color: #5d6b7c; cursor: pointer; }
.switch button.on { background: #4cb4e5; color: #04203f; }
.cols {
  display: grid;
  grid-template-columns: 1.6rem minmax(0, 1fr) 2rem 2rem 2rem;
  gap: 0.35rem;
  align-items: center;
  padding: 0.45rem 0.85rem;
}
.head { color: #8b97a8; font-size: 0.72rem; letter-spacing: 0.04em; text-transform: uppercase; }
.line { color: inherit; text-decoration: none; border-top: 1px solid #eef3f8; }
.line:hover { background: #f7fbfe; }
.stat { text-align: right; font-variant-numeric: tabular-nums; font-weight: 700; }
.row-who { display: flex; align-items: center; gap: 0.45rem; }
.place { color: #8b97a8; }
.more {
  display: block;
  margin: 0.7rem auto 0.2rem;
  border: 1px solid #d5e2ee;
  background: #fff;
  color: var(--navy);
  border-radius: 999px;
  padding: 0.45rem 1rem;
  font-weight: 700;
  cursor: pointer;
}
.legend { margin: 0.35rem 0 0.7rem; text-align: center; color: #8b97a8; font-size: 0.72rem; }
.found { margin: 0; padding: 0.7rem 0.85rem 0.2rem; color: #8b97a8; font-size: 0.78rem; }
.hit { padding: 0.55rem 0.85rem; color: inherit; text-decoration: none; border-top: 1px solid #eef3f8; }
.goals { font-weight: 700; color: var(--navy); white-space: nowrap; }
mark { background: #d7f0fb; color: inherit; border-radius: 3px; }
.note { padding: 1.2rem 0.8rem; text-align: center; }
.note p { margin: 0; font-weight: 700; }
.quiet { font-weight: 500; color: var(--muted); }
.skel { display: grid; grid-template-columns: 36px minmax(0, 1fr) 28px; gap: 0.55rem; align-items: center; padding: 0.55rem 0.85rem; }
.dot { width: 36px; height: 36px; min-height: 0; border-radius: 50%; }
.bars { height: 28px; min-height: 0; }
.num { height: 18px; min-height: 0; }
@media (max-width: 720px) {
  .hero { border-radius: 16px; }
  .hero h1 { font-size: 1.55rem; }
  .chips-sticky { top: 56px; }
  .leaders { grid-template-columns: 1fr; }
  .cols { grid-template-columns: 1.5rem minmax(0, 1fr) 2rem; }
  .stat-m, .stat-side { display: none; }
}
</style>
