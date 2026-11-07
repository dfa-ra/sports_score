<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import api from '../api/client'
import AdminOnly from '../components/AdminOnly.vue'
import CreateTournamentForm from '../components/CreateTournamentForm.vue'
import ScrollFade from '../components/ScrollFade.vue'
import TeamCrest from '../components/TeamCrest.vue'
import { BRAND_MARK_SRC } from '../lib/brand'
import { progressWidth } from '../lib/homeOverview'
import { setPageMeta } from '../lib/pageMeta'
import {
  assembleTour,
  compareTours,
  countLive,
  filterRows,
  groupRows,
  heroCounts,
  isPublicStatus,
  matchProgress,
  pageContent,
  playoffChampion,
  playoffStageTitle,
  safeRoundTitle,
  tableChampion,
  wantsPlayoff,
  type TourDraft,
  type TourExtra,
  type TourFilter,
  type TourRow,
} from '../lib/q2Tournaments'
import { useAuthStore } from '../stores/auth'
import { useTeamDirectory } from '../lib/useTeamDirectory'

const FILTERS: { id: TourFilter; label: string }[] = [
  { id: 'all', label: 'Все' },
  { id: 'active', label: 'Идут' },
  { id: 'registration', label: 'Регистрация' },
  { id: 'finished', label: 'Завершены' },
]

const auth = useAuthStore()
const teams = useTeamDirectory()
const items = ref<TourRow[]>([])
const filter = ref<TourFilter>('all')
const loading = ref(true)
const error = ref('')
const showForm = ref(false)
let ticket = 0

const counts = computed(() => heroCounts(items.value))
const groups = computed(() => groupRows(filterRows(items.value, filter.value)))

function asDraft(row: unknown): TourDraft | null {
  if (!row || typeof row !== 'object') return null
  const item = row as TourDraft
  if (!item.id || !isPublicStatus(item.status)) return null
  return item
}

async function readPages(path: string, params: Record<string, unknown>, size: number) {
  const rows: unknown[] = []
  let page = 0
  for (;;) {
    const { data } = await api.get(path, { params: { ...params, page, size } })
    const content = pageContent(data)
    rows.push(...content)
    const pages = Number((data as { totalPages?: number } | null)?.totalPages || 0)
    page += 1
    if (!content.length || content.length < size || (pages > 0 && page >= pages) || page > 40) break
  }
  return rows
}

async function pool<T>(list: T[], limit: number, job: (item: T) => Promise<void>) {
  let cursor = 0
  async function worker() {
    for (;;) {
      const index = cursor
      cursor += 1
      if (index >= list.length) return
      await job(list[index])
    }
  }
  const width = Math.min(limit, list.length)
  if (!width) return
  await Promise.all(Array.from({ length: width }, () => worker()))
}

async function load() {
  const mine = ++ticket
  loading.value = true
  error.value = ''
  try {
    const [catalog, homeResult, liveResult] = await Promise.all([
      readPages('/tournaments', {}, 50),
      api.get('/home/overview').then((res) => res.data).catch(() => null),
      readPages('/matches', { status: 'LIVE' }, 100).then(
        (rows) => ({ known: true, counts: countLive(rows as { status?: string; tournamentId?: string }[]) }),
        () => ({ known: false, counts: new Map<string, number>() }),
      ),
    ])
    if (mine !== ticket) return
    const homeById = new Map<string, {
      teamsCount?: number
      progress?: number | null
      playoffRoundTitle?: string | null
      competitionStage?: string | null
    }>()
    const homeRows = pageContent(homeResult?.tournaments ? { content: homeResult.tournaments } : homeResult)
    for (const row of Array.isArray(homeResult?.tournaments) ? homeResult.tournaments : homeRows) {
      if (row && typeof row === 'object' && (row as { id?: string }).id) {
        homeById.set((row as { id: string }).id, row as {
          teamsCount?: number
          progress?: number | null
          playoffRoundTitle?: string | null
          competitionStage?: string | null
        })
      }
    }
    const drafts = catalog.map(asDraft).filter((row): row is TourDraft => !!row)
    const extras = new Map<string, TourExtra>()
    for (const draft of drafts) {
      const home = homeById.get(draft.id)
      extras.set(draft.id, {
        teamsCount: typeof home?.teamsCount === 'number' ? home.teamsCount : null,
        stage: home?.competitionStage || draft.competitionStage || null,
        progress: draft.status === 'ACTIVE' && typeof home?.progress === 'number' ? home.progress : null,
        roundTitle: safeRoundTitle(home?.playoffRoundTitle),
        liveCount: liveResult.known ? (liveResult.counts.get(draft.id) || 0) : null,
        liveKnown: liveResult.known,
      })
    }
    await teams.load().catch(() => undefined)
    await pool(drafts, 4, async (draft) => {
      const extra = extras.get(draft.id)
      if (!extra) return
      try {
        const { data } = await api.get(`/tournaments/${draft.id}/overview`)
        if (typeof data?.teamsCount === 'number') extra.teamsCount = data.teamsCount
        if (data?.stage) extra.stage = data.stage
      } catch {
        /* A missing overview omits the team count. The row still renders. */
      }
      const stage = extra.stage || draft.competitionStage
      const playoff = wantsPlayoff(draft.format, stage)
      if (draft.status === 'ACTIVE' && extra.progress == null) {
        try {
          const matches = await readPages('/matches', { tournamentId: draft.id }, 100)
          extra.progress = matchProgress(matches as { status?: string; bracketRound?: number | null }[], playoff)
        } catch {
          extra.progress = null
        }
      }
      if ((draft.status === 'ACTIVE' && playoff) || (draft.status === 'FINISHED' && (playoff || draft.format === 'GROUPS_PLAYOFF'))) {
        try {
          const { data } = await api.get(`/tournaments/${draft.id}/playoff`)
          const title = playoffStageTitle(data)
          if (title) extra.roundTitle = title
          if (draft.status === 'FINISHED') {
            const winner = playoffChampion(data)
            extra.winnerName = winner?.name || ''
            extra.winnerTeamId = winner?.teamId || ''
          }
        } catch {
          extra.winnerName = ''
        }
      } else if (draft.status === 'FINISHED' && draft.format === 'ROUND_ROBIN') {
        try {
          const { data } = await api.get(`/tournaments/${draft.id}/standings`)
          const winner = tableChampion(data)
          extra.winnerName = winner?.name || ''
          extra.winnerTeamId = winner?.teamId || ''
        } catch {
          extra.winnerName = ''
        }
      }
    })
    if (mine !== ticket) return
    items.value = drafts
      .map((draft) => assembleTour(draft, extras.get(draft.id) || { liveKnown: liveResult.known }))
      .filter((row): row is TourRow => !!row)
      .sort(compareTours)
  } catch {
    if (mine !== ticket) return
    items.value = []
    error.value = 'Турниры не загрузились'
  } finally {
    if (mine === ticket) loading.value = false
  }
}

function logoOf(row: TourRow) {
  return teams.logo(row.winnerTeamId) || teams.logoByName(row.winnerName)
}

function barWidth(row: TourRow) {
  return `${progressWidth(row.progress)}%`
}

onMounted(() => {
  setPageMeta({ title: 'Турниры', description: 'Турниры Лиги ИТМО: сезон и формат.' })
  void load()
})
</script>

<template>
  <section class="tours">
    <header class="hero">
      <img class="mark" :src="BRAND_MARK_SRC" alt="" />
      <div class="hero-top">
        <h1>Турниры</h1>
        <div v-if="loading" class="figures" aria-hidden="true">
          <span class="skeleton fig" />
          <span class="skeleton fig" />
          <span class="skeleton fig" />
        </div>
        <div v-else-if="!error" class="figures">
          <p class="figure"><b>{{ counts.active }}</b><span>идут</span></p>
          <p class="figure"><b>{{ counts.registration }}</b><span>регистрация</span></p>
          <p class="figure"><b>{{ counts.total }}</b><span>всего</span></p>
        </div>
      </div>
      <ScrollFade v-if="!error" solid arrows fade="#001433" :watch-key="filter">
        <div class="chips" role="group" aria-label="Фильтр турниров">
          <button
            v-for="chip in FILTERS"
            :key="chip.id"
            type="button"
            class="chip"
            :class="{ on: filter === chip.id }"
            :aria-pressed="filter === chip.id"
            @click="filter = chip.id"
          >{{ chip.label }}</button>
        </div>
      </ScrollFade>
    </header>

    <div v-if="loading" class="sheet" aria-hidden="true">
      <div v-for="n in 4" :key="n" class="skeleton line" />
    </div>
    <div v-else-if="error" class="sheet note">
      <p>{{ error }}</p>
      <button class="btn" type="button" @click="load">Повторить</button>
    </div>
    <div v-else-if="!items.length" class="sheet note">
      <p>Турниров пока нет</p>
    </div>
    <div v-else-if="!groups.length" class="sheet note">
      <p>Турниров пока нет</p>
      <p class="quiet">В этой категории ничего нет</p>
      <button class="btn" type="button" @click="filter = 'all'">Показать все</button>
    </div>
    <div v-else class="sheet">
      <section v-for="group in groups" :key="group.status" class="group">
        <h2>{{ group.title }}</h2>
        <RouterLink
          v-for="row in group.rows"
          :key="row.id"
          class="row"
          :class="{ muted: row.muted }"
          :to="`/tournaments/${row.id}`"
        >
          <span class="tour-badge" :class="row.status.toLowerCase()">{{ row.badge }}</span>
          <span class="name-block">
            <span class="name">{{ row.name }}</span>
            <span v-if="row.meta" class="meta">{{ row.meta }}</span>
          </span>
          <span class="aside">
            <span v-if="row.liveText" class="live">{{ row.liveText }}</span>
            <span v-if="row.applied" class="applied">{{ row.applied }}</span>
            <span v-if="row.winnerName" class="winner">
              <TeamCrest :name="row.winnerName" :src="logoOf(row)" :size="18" />
              Победитель · {{ row.winnerName }}
            </span>
            <span v-if="row.stage" class="stage">{{ row.stage }}</span>
            <span v-if="row.progress != null" class="bar" aria-hidden="true"><i :style="{ width: barWidth(row) }" /></span>
          </span>
          <span class="chev" aria-hidden="true">›</span>
        </RouterLink>
      </section>
    </div>

    <AdminOnly v-if="auth.canManageLeague" title="Для админа">
      <button class="btn" type="button" @click="showForm = !showForm">
        {{ showForm ? 'Скрыть форму' : 'Создать турнир' }}
      </button>
      <CreateTournamentForm v-if="showForm" @created="load" />
    </AdminOnly>
  </section>
</template>

<style scoped>
.tours { min-width: 0; display: grid; gap: 0.9rem; }
.hero {
  position: relative;
  overflow: hidden;
  background: #001433;
  color: #fff;
  border-radius: 22px;
  padding: 1.15rem 1.2rem 1rem;
  display: grid;
  gap: 0.9rem;
}
.mark {
  position: absolute;
  right: 8px;
  top: 10px;
  z-index: 0;
  width: 112px;
  height: 112px;
  object-fit: contain;
  opacity: 0.14;
  pointer-events: none;
}
.hero-top { position: relative; z-index: 1; display: flex; justify-content: space-between; align-items: flex-end; gap: 1rem; padding-right: 4.5rem; }
.hero h1 { margin: 0; font-size: 2rem; letter-spacing: 0.04em; color: #fff; }
.figures { display: flex; gap: 1.15rem; }
.figure { margin: 0; text-align: center; }
.figure b { display: block; font-family: var(--font-display); font-size: 1.7rem; line-height: 1; }
.figure span { display: block; margin-top: 0.15rem; font-size: 0.72rem; color: #c5d4ea; }
.fig { width: 3.2rem; height: 2.4rem; min-height: 0; border-radius: 10px; display: block; }
.chips {
  position: relative;
  z-index: 1;
  display: flex;
  gap: 0.4rem;
  overflow-x: auto;
  scrollbar-width: none;
}
.chips::-webkit-scrollbar { display: none; }
.chip {
  flex: 1 1 auto;
  border: 0;
  border-radius: 999px;
  padding: 0.42rem 0.75rem;
  background: rgba(255, 255, 255, 0.08);
  color: #d5e4f5;
  font-weight: 700;
  white-space: nowrap;
  cursor: pointer;
}
.chip.on { background: #4cb4e5; color: #04203f; }
.sheet { background: #fff; border-radius: 18px; overflow: hidden; min-width: 0; }
.line { min-height: 64px; margin: 0.55rem; }
.note { padding: 1.4rem 1rem; text-align: center; display: grid; gap: 0.45rem; justify-items: center; }
.note p { margin: 0; font-weight: 700; }
.quiet { font-weight: 500; color: var(--muted); }
.group + .group { border-top: 8px solid #e8eef4; }
.group h2 {
  margin: 0;
  padding: 0.7rem 0.95rem 0.2rem;
  font-size: 0.72rem;
  letter-spacing: 0.08em;
  color: #8b97a8;
}
.row {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) minmax(140px, 190px) auto;
  gap: 0.75rem;
  align-items: center;
  min-height: 60px;
  padding: 0.6rem 0.9rem;
  color: inherit;
  text-decoration: none;
  border-top: 1px solid #eef3f8;
}
.row:hover { background: #f7fbfe; }
.name-block { min-width: 0; display: grid; gap: 0.12rem; }
.name {
  font-weight: 700;
  color: var(--navy);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.muted .name { color: #8b97a8; }
.meta { color: #8b97a8; font-size: 0.75rem; }
.tour-badge {
  justify-self: start;
  border-radius: 999px;
  padding: 0.18rem 0.5rem;
  font-size: 0.72rem;
  font-weight: 700;
  letter-spacing: 0;
  text-transform: none;
  white-space: nowrap;
}
.tour-badge.active { background: #e5f6ea; color: #1d7a3a; }
.tour-badge.registration { background: #fff4d6; color: #8a5a00; }
.tour-badge.finished { background: #eef1f4; color: #6b7280; }
.aside { text-align: right; min-width: 0; display: grid; justify-items: end; gap: 0.15rem; }
.live { color: #ff5a5f; font-weight: 700; font-size: 0.78rem; }
.live::before { content: '● '; }
.applied { color: #b5811a; font-size: 0.78rem; font-weight: 700; }
.stage { color: #5d6b7c; font-size: 0.78rem; }
.winner { display: inline-flex; align-items: center; gap: 0.35rem; font-size: 0.78rem; color: var(--navy); }
.bar { width: 100%; height: 3px; background: #e6eef5; border-radius: 99px; overflow: hidden; }
.bar i { display: block; height: 100%; background: #4cb4e5; }
.chev { color: #b7c3d1; font-size: 1.3rem; line-height: 1; }
@media (max-width: 720px) {
  .hero { border-radius: 16px; padding: 0.9rem 0.7rem 0.8rem; }
  .hero h1 { font-size: 1.55rem; }
  .hero-top { display: grid; justify-content: stretch; padding-right: 2.5rem; }
  .mark { width: 84px; height: 84px; }
  .chips { display: flex; overflow: visible; }
  .chip { flex: 0 0 auto; padding: 0.36rem 0.42rem; font-size: 0.7rem; }
  .sheet { border-radius: 10px; }
  .row {
    grid-template-columns: minmax(0, 1fr) auto;
    grid-template-areas: "name badge" "aside aside";
    align-items: start;
  }
  .name-block { grid-area: name; }
  .tour-badge { grid-area: badge; justify-self: end; }
  .aside { grid-area: aside; justify-items: start; text-align: left; }
  .bar { height: 4px; }
  .chev { display: none; }
}
</style>
