<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import { Client } from '@stomp/stompjs'
import api from '../api/client'
import HomeAvatar from '../components/home/HomeAvatar.vue'
import HomeCrest from '../components/home/HomeCrest.vue'
import ScrollFade from '../components/ScrollFade.vue'
import { BRAND_MARK_SRC, SITE_NAME } from '../lib/brand'
import { distinctLabels } from '../lib/shortLabel'
import {
  badgeClass,
  badgeIndex,
  countdown,
  emptyOverview,
  feedRecent,
  feedUpcoming,
  goalsPerMatchLabel,
  heroWhen,
  isFutureScheduled,
  matchTournamentChips,
  leaderLine,
  matchClockLabel,
  matchDayLabel,
  progressWidth,
  registrationEmpty,
  registrationHeadline,
  runningCardMatches,
  stageText,
  statusChip,
  statusText,
  withLiveMinute,
  countLabel,
  orderRunningTournaments,
  tournamentDirectoryCount,
  type HomeMatch,
  type HomeOverview,
  type HomeTournament,
} from '../lib/homeOverview'
import { heroLivePreview, heroLiveTail } from '../lib/heroLive'
import { unitWord } from '../lib/q2Players'
import { setPageMeta } from '../lib/pageMeta'
import { registeredName } from '../lib/playerTitle'
import { scheduleRevision } from '../lib/scheduleRefresh'
import { tableRouteQuery } from '../lib/tableQuery'

const loaded = ref(false)
const failed = ref(false)
const overview = ref<HomeOverview>(emptyOverview())
const directoryCount = ref<number | null>(null)
const chip = ref<string | null>(null)
const scorerTab = ref('')
const nowMs = ref(Date.now())
let clockTimer = 0
let refreshTimer = 0
let client: Client | null = null
let subscribedKey = ''

const now = computed(() => new Date(nowMs.value))
const nextMatch = computed(() => {
  const match = overview.value.next
  if (!isFutureScheduled(match, nowMs.value)) return null
  return withLiveMinute(match as HomeMatch, nowMs.value)
})
const runningTournamentCap = 4
const liveRows = computed(() => overview.value.live.map((match) => withLiveMinute(match, nowMs.value)))
const livePreview = computed(() => heroLivePreview(liveRows.value))
const tournaments = computed(() => overview.value.tournaments)
const orderedTournaments = computed(() => orderRunningTournaments(tournaments.value, overview.value.live))
const visibleTournaments = computed(() => orderedTournaments.value.slice(0, runningTournamentCap))
const matchChips = computed(() => matchTournamentChips(tournaments.value))
const chipLabels = computed(() => {
  const labels = distinctLabels(matchChips.value.map((tournament) => tournament.name || tournament.shortName || ''))
  return new Map(matchChips.value.map((tournament, index) => [tournament.id, labels[index]]))
})
const offseason = computed(() => loaded.value && !failed.value && !nextMatch.value && tournaments.value.length === 0)
const plainHero = computed(() => loaded.value && !failed.value && !nextMatch.value && tournaments.value.length > 0)
const showStats = computed(() => !failed.value && (!loaded.value || !!overview.value.summary))
const showTournaments = computed(() => !loaded.value || failed.value || tournaments.value.length > 0)
const scorerGroups = computed(() => overview.value.scorersByTournament)
const scorerLabels = computed(() => {
  const labels = distinctLabels(scorerGroups.value.map((group) => group.tournamentName || group.shortName || ''))
  return new Map(scorerGroups.value.map((group, index) => [group.tournamentId, labels[index]]))
})
const liveKey = computed(() => overview.value.live.slice(0, 5).map((match) => match.id).join(','))
const upcoming = computed(() => feedUpcoming(overview.value, chip.value, nextMatch.value?.id))
const recent = computed(() => feedRecent(overview.value, chip.value))
const activeScorers = computed(() => scorerGroups.value.find((group) => group.tournamentId === scorerTab.value) || scorerGroups.value[0] || null)
const heroStage = computed(() => {
  if (!nextMatch.value) return ''
  const stage = stageText(nextMatch.value)
  const left = countdown(nextMatch.value.scheduledAt, nowMs.value)
  if (stage && left) return `${stage} · ${left}`
  return stage || left
})

function normalize(data: HomeOverview | null | undefined): HomeOverview {
  return {
    generatedAt: data?.generatedAt,
    next: data?.next ?? null,
    live: data?.live ?? [],
    upcoming: data?.upcoming ?? [],
    recent: data?.recent ?? [],
    tournaments: data?.tournaments ?? [],
    scorersByTournament: data?.scorersByTournament ?? [],
    summary: data?.summary ?? null,
  }
}

async function load(silent = false) {
  try {
    const [overviewResult, directoryResult] = await Promise.all([
      api.get<HomeOverview>('/home/overview'),
      api.get('/tournaments', { params: { size: 50 } }).then(
        (response) => tournamentDirectoryCount(response.data),
        () => null,
      ),
    ])
    overview.value = normalize(overviewResult.data)
    failed.value = false
    if (directoryResult != null) directoryCount.value = directoryResult
    if (chip.value && !matchTournamentChips(overview.value.tournaments).some((item) => item.id === chip.value)) chip.value = null
    if (!scorerTab.value || !overview.value.scorersByTournament.some((item) => item.tournamentId === scorerTab.value)) {
      scorerTab.value = overview.value.scorersByTournament[0]?.tournamentId || ''
    }
  } catch {
    if (!silent || !loaded.value) failed.value = true
  } finally {
    loaded.value = true
  }
}

function badgeOf(tournamentId?: string | null) {
  return badgeClass(badgeIndex(tournaments.value, tournamentId))
}

function scoreText(match: HomeMatch, side: 'home' | 'away') {
  if (match.status === 'SCHEDULED' || match.status === 'CANCELLED') return '–'
  return String(side === 'home' ? match.homeScore : match.awayScore)
}

function teamCount(tournament: HomeTournament) {
  const word = countLabel(tournament.teamsCount, 'команда', 'команды', 'команд')
  return `${tournament.teamsCount} ${word}`
}

function cardMatches(tournament: HomeTournament) {
  return runningCardMatches(tournament, liveRows.value)
}

function statsLink(tournamentId?: string, full = false) {
  return { path: '/table', query: tableRouteQuery({ tournamentId, tab: 'scorers', listAll: full }) }
}

function liveSocketUrl() {
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  return `${protocol}//${window.location.host}/websocket`
}

function connectLive() {
  const ids = liveKey.value ? liveKey.value.split(',') : []
  const key = liveKey.value
  if (key === subscribedKey && client?.active) return
  subscribedKey = key
  client?.deactivate()
  client = null
  if (!ids.length) return
  const next = new Client({
    brokerURL: liveSocketUrl(),
    reconnectDelay: 5000,
    onConnect: () => {
      ids.forEach((id) => {
        next.subscribe(`/topic/matches/${id}`, (message) => {
          const frame = JSON.parse(message.body) as {
            matchId?: string
            status?: string
            homeScore?: number
            awayScore?: number
            gameTimeSeconds?: number | null
            period?: number | null
            periodCount?: number
            periodLengthSeconds?: number
            clockRunningSince?: string | null
          }
          if (!frame.matchId) return
          if (frame.status === 'FINISHED') {
            void load(true)
            return
          }
          const patch = (match: HomeMatch): HomeMatch => match.id === frame.matchId
            ? {
                ...match,
                status: frame.status || match.status,
                homeScore: frame.homeScore ?? match.homeScore,
                awayScore: frame.awayScore ?? match.awayScore,
                gameTimeSeconds: frame.gameTimeSeconds ?? match.gameTimeSeconds,
                period: frame.period ?? match.period,
                periodCount: frame.periodCount ?? match.periodCount,
                periodLengthSeconds: frame.periodLengthSeconds ?? match.periodLengthSeconds,
                clockRunningSince: frame.clockRunningSince ?? match.clockRunningSince,
              }
            : match
          overview.value = {
            ...overview.value,
            next: overview.value.next ? patch(overview.value.next) : null,
            live: overview.value.live.map(patch).filter((match) => match.status === 'LIVE' || match.status === 'PAUSED'),
            upcoming: overview.value.upcoming.map(patch),
            recent: overview.value.recent.map(patch),
          }
        })
      })
    },
  })
  next.activate()
  client = next
}

watch(liveKey, () => connectLive())

watch(scheduleRevision, () => {
  if (!loaded.value) return
  void load(true)
})

onMounted(() => {
  setPageMeta({
    title: 'Главная',
    description: `${SITE_NAME}: ближайшие матчи, идущие турниры, бомбардиры и лучшие по передачам.`,
    path: '/',
  })
  void load()
  clockTimer = window.setInterval(() => { nowMs.value = Date.now() }, 1000)
  refreshTimer = window.setInterval(() => {
    if (document.visibilityState === 'visible') void load(true)
  }, 60_000)
})

onUnmounted(() => {
  window.clearInterval(clockTimer)
  window.clearInterval(refreshTimer)
  client?.deactivate()
})
</script>

<template>
  <section class="home-v2">
    <div class="band">
      <div class="container">
        <div v-if="!loaded" class="skeleton sk-hero" aria-hidden="true" />
        <div v-else-if="failed" class="hero-card hero-state" role="alert">
          <p>Не удалось загрузить матчи</p>
          <button class="hv-btn" type="button" @click="load()">Повторить</button>
        </div>
        <div v-else-if="offseason || plainHero" class="hero-card hero-state">
          <img class="wm" :src="BRAND_MARK_SRC" alt="" />
          <div>
            <h1>{{ SITE_NAME }}</h1>
            <div class="acts">
              <RouterLink class="hv-btn" to="/calendar">Игры</RouterLink>
              <RouterLink class="hv-btn ghost" to="/tournaments">Турниры</RouterLink>
            </div>
          </div>
          <div v-if="plainHero && livePreview.length" class="livebar">
            <span class="st live on-dark">Live сейчас</span>
            <RouterLink v-for="match in livePreview" :key="match.id" class="lv" :class="{ paused: match.status === 'PAUSED' }" :to="`/matches/${match.id}`">
              <b>{{ match.homeTeamName }} {{ match.homeScore }}:{{ match.awayScore }} {{ match.awayTeamName }}</b>
              <span>{{ match.tournamentShortName || match.tournamentName }} · <span v-if="match.status === 'PAUSED'" class="badge paused">{{ heroLiveTail(match) }}</span><template v-else>{{ heroLiveTail(match) }}</template></span>
            </RouterLink>
            <RouterLink v-if="liveRows.length > 3" class="all-live" to="/live">Все live →</RouterLink>
          </div>
        </div>
        <div v-else class="hero-card hero-match" :class="{ 'has-live': livePreview.length }">
          <img class="wm" :src="BRAND_MARK_SRC" alt="" />
          <div>
            <p class="eyebrow">Ближайший матч · {{ heroWhen(nextMatch?.scheduledAt, now) }}</p>
            <div class="vs">
              <div class="side">
                <HomeCrest large :id="nextMatch?.homeTeamId" :name="nextMatch?.homeTeamName" :src="nextMatch?.homeLogoUrl" />
                <span class="team-name" lang="ru">{{ nextMatch?.homeTeamName }}</span>
              </div>
              <div class="x">VS</div>
              <div class="side">
                <HomeCrest large :id="nextMatch?.awayTeamId" :name="nextMatch?.awayTeamName" :src="nextMatch?.awayLogoUrl" />
                <span class="team-name" lang="ru">{{ nextMatch?.awayTeamName }}</span>
              </div>
            </div>
            <div class="meta">
              <span class="tb" :class="badgeOf(nextMatch?.tournamentId)">{{ nextMatch?.tournamentShortName || nextMatch?.tournamentName }}</span>
              <span v-if="heroStage" class="stage">{{ heroStage }}</span>
            </div>
            <div class="acts">
              <RouterLink class="hv-btn" :to="`/matches/${nextMatch?.id}`">К матчу</RouterLink>
              <RouterLink class="hv-btn ghost" to="/calendar">Календарь игр</RouterLink>
            </div>
          </div>
          <div v-if="livePreview.length" class="livebar">
            <span class="st live on-dark">Live сейчас</span>
            <RouterLink v-for="match in livePreview" :key="match.id" class="lv" :class="{ paused: match.status === 'PAUSED' }" :to="`/matches/${match.id}`">
              <b>{{ match.homeTeamName }} {{ match.homeScore }}:{{ match.awayScore }} {{ match.awayTeamName }}</b>
              <span>{{ match.tournamentShortName || match.tournamentName }} · <span v-if="match.status === 'PAUSED'" class="badge paused">{{ heroLiveTail(match) }}</span><template v-else>{{ heroLiveTail(match) }}</template></span>
            </RouterLink>
            <RouterLink v-if="liveRows.length > 3" class="all-live" to="/live">Все live →</RouterLink>
          </div>
        </div>
      </div>
    </div>

    <div v-if="showStats" class="container block">
      <div v-if="!loaded" class="skeleton sk-stats" aria-hidden="true" />
      <div v-else-if="overview.summary" class="stats">
        <p class="eyebrow muted">Лига в цифрах</p>
        <div class="sgrid">
          <div class="stat"><b>{{ overview.summary.matchesPlayed }}</b><span><span class="lf">матчей сыграно</span><span class="ls">матчей</span></span></div>
          <div class="stat"><b>{{ overview.summary.goals }}</b><span><span class="lf">голов забито</span><span class="ls">голов</span></span></div>
          <div class="stat"><b>{{ goalsPerMatchLabel(overview.summary.goalsPerMatch) }}</b><span><span class="lf">голов за матч</span><span class="ls">г/матч</span></span></div>
          <div class="stat"><b>{{ overview.summary.teams }}</b><span><span class="lf">команды</span><span class="ls">команд</span></span></div>
          <div class="stat"><b>{{ overview.summary.players }}</b><span><span class="lf">игроков</span><span class="ls">игроков</span></span></div>
        </div>
      </div>
    </div>

    <div class="container block">
      <div class="card matches-card">
        <div class="card-head">
          <div>
            <h2>Матчи</h2>
            <small>Все турниры · ближайшие и последние</small>
          </div>
          <RouterLink class="more-link" to="/calendar">Все игры</RouterLink>
        </div>
        <template v-if="!loaded">
          <div class="filters" aria-hidden="true">
            <div class="chips">
              <span class="chip on"><span class="chip-label">Все турниры</span></span>
            </div>
          </div>
          <div class="feed-list" aria-hidden="true">
            <div class="feed-col">
              <div v-for="n in 3" :key="`mu-${n}`" class="mrow sk-mrow"></div>
            </div>
            <div class="feed-col">
              <div v-for="n in 3" :key="`mr-${n}`" class="mrow sk-mrow"></div>
            </div>
          </div>
          <div class="feed-cards" aria-hidden="true">
            <div class="skeleton sk-matches"></div>
          </div>
          <span class="more">Все игры →</span>
        </template>
        <div v-else-if="failed" class="block-error" role="alert">
          <p>Матчи не загрузились</p>
          <button class="hv-btn dark" type="button" @click="load()">Повторить</button>
        </div>
        <template v-else>
          <div class="filters">
            <ScrollFade arrows fade="#ffffff">
              <div class="chips">
                <button class="chip" :class="{ on: chip == null }" type="button" @click="chip = null">
                  <span class="chip-label">Все турниры</span>
                </button>
                <button
                  v-for="tournament in matchChips"
                  :key="tournament.id"
                  class="chip"
                  :class="{ on: chip === tournament.id }"
                  type="button"
                  :title="tournament.shortName || tournament.name"
                  :aria-label="tournament.shortName || tournament.name"
                  @click="chip = tournament.id"
                >
                  <span class="chip-label" :title="tournament.name || tournament.shortName">{{ chipLabels.get(tournament.id) }}</span>
                </button>
              </div>
            </ScrollFade>
          </div>
          <p v-if="!upcoming.length && !recent.length" class="block-empty">Матчей пока нет</p>
          <div v-else class="feed-list">
            <div class="feed-col">
              <p v-if="!upcoming.length" class="block-empty">Нет ближайших игр</p>
              <RouterLink v-for="match in upcoming" :key="match.id" class="mrow" :to="`/matches/${match.id}`">
                <div class="mtime"><b>{{ matchDayLabel(match.scheduledAt) }}</b><span>{{ matchClockLabel(match.scheduledAt) }}</span></div>
                <div class="mmain">
                  <div class="mmeta">
                    <div class="l">
                      <span class="tb" :class="badgeOf(match.tournamentId)">{{ match.tournamentShortName || match.tournamentName }}</span>
                      <span v-if="stageText(match)" class="stage">{{ stageText(match) }}</span>
                    </div>
                    <span class="st" :class="statusChip(match, now)">{{ statusText(match, now) }}</span>
                  </div>
                  <div class="mteam"><HomeCrest :id="match.homeTeamId" :name="match.homeTeamName" :src="match.homeLogoUrl" /><span class="n">{{ match.homeTeamName }}</span></div>
                  <div class="mteam"><HomeCrest :id="match.awayTeamId" :name="match.awayTeamName" :src="match.awayLogoUrl" /><span class="n">{{ match.awayTeamName }}</span></div>
                </div>
                <div class="mscore"><b class="dim">{{ scoreText(match, 'home') }}</b><b class="dim">{{ scoreText(match, 'away') }}</b></div>
              </RouterLink>
            </div>
            <div class="feed-col">
              <p v-if="!recent.length" class="block-empty">Результатов пока нет</p>
              <RouterLink v-for="match in recent" :key="match.id" class="mrow" :to="`/matches/${match.id}`">
                <div class="mtime"><b>{{ matchDayLabel(match.scheduledAt) }}</b><span>{{ matchClockLabel(match.scheduledAt) }}</span></div>
                <div class="mmain">
                  <div class="mmeta">
                    <div class="l">
                      <span class="tb" :class="badgeOf(match.tournamentId)">{{ match.tournamentShortName || match.tournamentName }}</span>
                      <span v-if="stageText(match)" class="stage">{{ stageText(match) }}</span>
                    </div>
                    <span class="st done">Завершён</span>
                  </div>
                  <div class="mteam"><HomeCrest :id="match.homeTeamId" :name="match.homeTeamName" :src="match.homeLogoUrl" /><span class="n">{{ match.homeTeamName }}</span></div>
                  <div class="mteam"><HomeCrest :id="match.awayTeamId" :name="match.awayTeamName" :src="match.awayLogoUrl" /><span class="n">{{ match.awayTeamName }}</span></div>
                </div>
                <div class="mscore"><b>{{ scoreText(match, 'home') }}</b><b>{{ scoreText(match, 'away') }}</b></div>
              </RouterLink>
            </div>
          </div>
          <ScrollFade v-if="upcoming.length || recent.length" fade="#ffffff">
          <div class="feed-cards">
            <RouterLink v-for="match in upcoming" :key="`u-${match.id}`" class="mc" :to="`/matches/${match.id}`">
              <div class="mc-top"><span class="tb" :class="badgeOf(match.tournamentId)">{{ match.tournamentShortName || match.tournamentName }}</span><span class="st" :class="statusChip(match, now)">{{ statusText(match, now) }}</span></div>
              <div class="mc-t"><HomeCrest :id="match.homeTeamId" :name="match.homeTeamName" :src="match.homeLogoUrl" /><span class="n">{{ match.homeTeamName }}</span><b class="dim">{{ scoreText(match, 'home') }}</b></div>
              <div class="mc-t"><HomeCrest :id="match.awayTeamId" :name="match.awayTeamName" :src="match.awayLogoUrl" /><span class="n">{{ match.awayTeamName }}</span><b class="dim">{{ scoreText(match, 'away') }}</b></div>
              <div class="mc-foot">{{ matchDayLabel(match.scheduledAt) }} · {{ matchClockLabel(match.scheduledAt) }}<template v-if="stageText(match)"> · {{ stageText(match) }}</template></div>
            </RouterLink>
            <RouterLink v-for="match in recent" :key="`r-${match.id}`" class="mc" :to="`/matches/${match.id}`">
              <div class="mc-top"><span class="tb" :class="badgeOf(match.tournamentId)">{{ match.tournamentShortName || match.tournamentName }}</span><span class="st done">Завершён</span></div>
              <div class="mc-t"><HomeCrest :id="match.homeTeamId" :name="match.homeTeamName" :src="match.homeLogoUrl" /><span class="n">{{ match.homeTeamName }}</span><b>{{ scoreText(match, 'home') }}</b></div>
              <div class="mc-t"><HomeCrest :id="match.awayTeamId" :name="match.awayTeamName" :src="match.awayLogoUrl" /><span class="n">{{ match.awayTeamName }}</span><b>{{ scoreText(match, 'away') }}</b></div>
              <div class="mc-foot">{{ matchDayLabel(match.scheduledAt) }} · {{ matchClockLabel(match.scheduledAt) }}<template v-if="stageText(match)"> · {{ stageText(match) }}</template></div>
            </RouterLink>
          </div>
          </ScrollFade>
          <RouterLink class="more" to="/calendar">Все игры →</RouterLink>
        </template>
      </div>
    </div>

    <div v-if="showTournaments" class="container block">
      <div class="section-title">
        <h2>Идут сейчас</h2>
        <RouterLink class="more-link" to="/tournaments">Все турниры</RouterLink>
      </div>
      <div v-if="!loaded" class="tgrid" aria-hidden="true">
        <article v-for="n in 4" :key="n" class="skeleton sk-tcard" :class="'card tcard'">
          <div class="tc-head">
            <div class="tc-top">
              <div>
                <span class="tb">Турнир</span>
                <h3>Турнир</h3>
              </div>
              <span class="st ok">Идёт</span>
            </div>
            <div class="tc-meta">
              <span>Сезон</span>
              <span>Лига</span>
              <span>8 команд</span>
            </div>
            <div class="prog"><i style="width: 40%" /></div>
            <div class="tc-prog">
              <span>Группа</span>
              <span class="hv-btn dark sm">К турниру</span>
            </div>
            <div class="lead">
              <span class="crest sk-crest"></span>
              <div><b>Физтех</b><span>Лидер таблицы · 12 очк. · 4 игры</span></div>
            </div>
          </div>
          <div class="tc-label">Последний и ближайший матчи</div>
          <div class="irow">
            <div class="mtime"><b>5 дек</b><span>17:00</span></div>
            <div class="h"><span class="n">Физтех</span><span class="crest sk-crest"></span></div>
            <div class="sc"><span class="line"><span>1</span><span class="sep">:</span><span>0</span></span><small>пен. 4:3</small></div>
            <div class="a"><span class="crest sk-crest"></span><span class="n">Юристы</span></div>
            <div class="stc"><span class="st done">Завершён</span></div>
          </div>
          <div class="irow">
            <div class="mtime"><b>12 дек</b><span>17:00</span></div>
            <div class="h"><span class="n">Физтех</span><span class="crest sk-crest"></span></div>
            <div class="sc dim"><span class="line"><span>–</span><span class="sep">:</span><span>–</span></span></div>
            <div class="a"><span class="crest sk-crest"></span><span class="n">Юристы</span></div>
            <div class="stc"><span class="st soon">Скоро</span></div>
          </div>
        </article>
      </div>
      <div v-else-if="failed" class="card block-error" role="alert">
        <p>Турниры не загрузились</p>
        <button class="hv-btn dark" type="button" @click="load()">Повторить</button>
      </div>
      <template v-else>
      <div class="tgrid">
        <article v-for="(tournament, index) in visibleTournaments" :key="tournament.id" class="card tcard">
          <div class="tc-head">
            <div class="tc-top">
              <div>
                <span class="tb" :class="badgeClass(index)">{{ tournament.shortName || tournament.name }}</span>
                <h3>{{ tournament.name }}</h3>
              </div>
              <span class="st" :class="tournament.status === 'REGISTRATION' ? 'warn' : 'ok'">{{ tournament.status === 'REGISTRATION' ? 'Регистрация' : 'Идёт' }}</span>
            </div>
            <div class="tc-meta">
              <span>{{ tournament.seasonLabel || 'Сезон' }}</span>
              <span>{{ tournament.formatTitle }}</span>
              <span>{{ teamCount(tournament) }}</span>
            </div>
            <div class="prog"><i v-if="tournament.progress != null" :style="{ width: `${progressWidth(tournament.progress)}%` }" /></div>
            <div class="tc-prog">
              <span>{{ tournament.status === 'REGISTRATION' ? registrationHeadline(tournament) : tournament.stageLabel }}</span>
              <RouterLink class="hv-btn dark sm" :to="`/tournaments/${tournament.id}`">К турниру</RouterLink>
            </div>
            <div v-if="tournament.status === 'REGISTRATION'" class="lead">
              <span class="crest plus">+</span>
              <div><b>Набор открыт</b><span>{{ leaderLine(tournament) }}</span></div>
            </div>
            <div v-else-if="tournament.leader" class="lead">
              <HomeCrest :id="tournament.leader.teamId" :name="tournament.leader.teamName" :src="tournament.leader.logoUrl" />
              <div><b>{{ tournament.leader.teamName }}</b><span>{{ leaderLine(tournament) }}</span></div>
            </div>
            <div v-else-if="leaderLine(tournament)" class="lead">
              <div><b>{{ tournament.playoffRoundTitle || tournament.stageLabel }}</b><span>{{ leaderLine(tournament) }}</span></div>
            </div>
          </div>
          <div class="tc-label">{{ tournament.status === 'REGISTRATION' ? 'Матчи' : 'Последний и ближайший матчи' }}</div>
          <div v-if="!cardMatches(tournament).length" class="empty-note">
            <template v-if="tournament.status === 'REGISTRATION'">
              Матчи появятся после старта турнира.<br />
              <span>{{ registrationEmpty(tournament) }}</span>
            </template>
            <template v-else>Матчей пока нет</template>
          </div>
          <RouterLink
            v-for="match in cardMatches(tournament)"
            :key="match.id"
            class="irow"
            :to="`/matches/${match.id}`"
          >
            <div class="mtime"><b>{{ matchDayLabel(match.scheduledAt) }}</b><span>{{ matchClockLabel(match.scheduledAt) }}</span></div>
            <div class="h"><span class="n">{{ match.homeTeamName }}</span><HomeCrest :id="match.homeTeamId" :name="match.homeTeamName" :src="match.homeLogoUrl" /></div>
            <div class="sc" :class="{ dim: match.status === 'SCHEDULED' }">
              <span class="line"><span>{{ scoreText(match, 'home') }}</span><span class="sep">:</span><span>{{ scoreText(match, 'away') }}</span></span>
              <small v-if="match.status === 'FINISHED' && match.penaltyHome != null">пен. {{ match.penaltyHome }}:{{ match.penaltyAway }}</small>
              <small v-else-if="match.status === 'FINISHED'" class="pen-hold" aria-hidden="true">пен. 0:0</small>
            </div>
            <div class="a"><HomeCrest :id="match.awayTeamId" :name="match.awayTeamName" :src="match.awayLogoUrl" /><span class="n">{{ match.awayTeamName }}</span></div>
            <div class="stc"><span class="st" :class="statusChip(match, now)">{{ statusText(match, now) }}</span></div>
          </RouterLink>
        </article>
      </div>
      <RouterLink
        v-if="orderedTournaments.length > runningTournamentCap && directoryCount != null"
        class="hv-btn dark running-more"
        to="/tournaments"
      >Все турниры ({{ directoryCount }}) →</RouterLink>
      </template>
    </div>

    <div class="container block">
      <div class="section-title">
        <h2>Бомбардиры по турнирам</h2>
        <RouterLink class="more-link" :to="statsLink(activeScorers?.tournamentId)">Вся статистика</RouterLink>
      </div>
      <div v-if="!loaded" class="skeleton sk-scorers" :class="'card'" aria-hidden="true">
        <div class="tabs">
          <button type="button" class="on" tabindex="-1"><span class="tab-label">Лига</span></button>
        </div>
        <div class="scorer-body">
          <table class="stbl">
            <thead>
              <tr><th class="rk"></th><th>Игрок</th><th class="tm">Команда</th><th class="num">Матчи</th><th class="num">Пер.</th><th class="num">Голы</th></tr>
            </thead>
            <tbody>
              <tr v-for="n in 5" :key="`sk-${n}`"><td colspan="6"></td></tr>
            </tbody>
          </table>
          <div class="sl-mob">
            <div v-for="n in 5" :key="`sm-${n}`" class="srow sk-srow"></div>
          </div>
        </div>
        <span class="more">Полный список →</span>
      </div>
      <div v-else-if="failed" class="card block-error" role="alert">
        <p>Статистика не загрузилась</p>
        <button class="hv-btn dark" type="button" @click="load()">Повторить</button>
      </div>
      <div v-else class="card">
        <ScrollFade v-if="scorerGroups.length" arrows solid fade="#ffffff" :watch-key="activeScorers?.tournamentId || ''">
          <div class="tabs">
            <button
              v-for="group in scorerGroups"
              :key="group.tournamentId"
              type="button"
              :class="{ on: activeScorers?.tournamentId === group.tournamentId }"
              :title="group.tournamentName || group.shortName"
              :aria-label="group.tournamentName || group.shortName"
              @click="scorerTab = group.tournamentId"
            >
              <span class="tab-label">{{ scorerLabels.get(group.tournamentId) }}</span>
            </button>
          </div>
        </ScrollFade>
        <div class="scorer-body">
        <p v-if="!activeScorers?.scorers.length" class="block-empty scorer-empty">Голов пока нет</p>
        <template v-else>
          <table class="stbl">
            <thead>
              <tr><th class="rk"></th><th>Игрок</th><th class="tm">Команда</th><th class="num">Матчи</th><th class="num">Пер.</th><th class="num">Голы</th></tr>
            </thead>
            <tbody>
              <tr v-for="(player, index) in activeScorers.scorers" :key="player.playerId">
                <td class="rk">{{ index + 1 }}</td>
                <td>
                  <RouterLink class="pl" :to="`/players/${player.playerId}`">
                    <HomeAvatar :src="player.avatarUrl" :name="registeredName(player)" :first-name="player.firstName" :last-name="player.lastName" />
                    <b>{{ registeredName(player) }}</b>
                  </RouterLink>
                </td>
                <td class="tm">{{ player.teamName }}</td>
                <td class="num">{{ player.appearances }}</td>
                <td class="num">{{ player.assists }}</td>
                <td class="num g">{{ player.goals }}</td>
              </tr>
            </tbody>
          </table>
          <div class="sl-mob">
            <RouterLink v-for="(player, index) in activeScorers.scorers" :key="`m-${player.playerId}`" class="srow" :to="`/players/${player.playerId}`">
              <span class="rk">{{ index + 1 }}</span>
              <HomeAvatar :src="player.avatarUrl" :name="registeredName(player)" :first-name="player.firstName" :last-name="player.lastName" />
              <div><div class="nm">{{ registeredName(player) }}</div><div class="tm">{{ player.teamName }}</div></div>
              <div class="g">{{ player.goals }}<small>{{ player.assists }} {{ unitWord(player.assists, 'assists') }}</small></div>
            </RouterLink>
          </div>
        </template>
        </div>
        <RouterLink v-if="activeScorers" class="more" :to="statsLink(activeScorers.tournamentId, true)">Полный список · {{ activeScorers.shortName || activeScorers.tournamentName }} →</RouterLink>
      </div>
    </div>
  </section>
</template>

<style scoped>
.home-v2 {
  color: var(--text);
  width: 100%;
  max-width: 100%;
  min-width: 0;
  container-type: inline-size;
  container-name: home;
}
.container { width: min(1120px, calc(100% - 2rem)); margin: 0 auto; min-width: 0; }
.band {
  background:
    radial-gradient(900px 260px at 92% 0%, rgba(76, 180, 229, 0.22), transparent 70%),
    var(--navy);
  padding-top: 0.9rem;
  padding-bottom: 1.5rem;
}
.block { padding-top: 1.5rem; }
.block:last-child { padding-bottom: 0.5rem; }
.hero-card {
  background: var(--ink);
  color: #fff;
  border-radius: 22px;
  position: relative;
  overflow: hidden;
  min-height: 253px;
}
.hero-state, .hero-match { display: grid; gap: 1.1rem; padding: 2rem 2.2rem; align-items: center; }
.hero-match.has-live, .hero-state:has(.livebar) { grid-template-columns: 1.25fr 1fr; }
.hero-match:not(.has-live) { min-height: 0; align-items: start; gap: 0; }
.wm { position: absolute; right: -30px; top: -40px; width: 340px; opacity: 0.08; pointer-events: none; }
.eyebrow { margin: 0; font-size: 0.75rem; font-weight: 800; letter-spacing: 0.08em; text-transform: uppercase; color: var(--ice); }
.eyebrow.muted { color: var(--muted); margin-bottom: 0.6rem; }
h1, h2, h3 { font-family: var(--font-display); font-weight: 700; letter-spacing: 0.01em; text-transform: uppercase; color: var(--ink); margin: 0; }
.hero-card h1 { color: #fff; font-size: 2rem; }
.vs {
  display: flex; align-items: center; justify-content: flex-start; flex-wrap: nowrap;
  gap: 1.5rem; margin: 0.9rem 0 0.8rem; min-width: 0; font-size: 1.6rem;
  container-type: inline-size;
  container-name: hero-vs;
}
.side {
  display: flex; align-items: center; gap: 0.8rem; flex: 1 1 0; min-width: 0;
  font-size: 1em; font-weight: 800; text-transform: uppercase; color: #fff;
  container-type: inline-size;
}
.team-name {
  flex: 1 1 0; min-width: 0; max-width: 100%;
  display: -webkit-box; -webkit-box-orient: vertical;
  -webkit-line-clamp: 2; line-clamp: 2;
  overflow: hidden; white-space: normal;
  word-break: normal; overflow-wrap: normal; hyphens: auto; -webkit-hyphens: auto;
  line-height: 1.15;
  font-size: clamp(0.95rem, 11cqi, 1em);
}
@container hero-vs (max-width: 400px) {
  .side { gap: 0.3rem; }
}
.x { flex: 0 0 auto; color: var(--ice); font-weight: 800; font-size: 1em; letter-spacing: 0.1em; line-height: 1; }
.acts { display: flex; gap: 0.7rem; align-items: center; flex-wrap: wrap; }
.meta {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr);
  align-items: center;
  column-gap: 0.55rem;
  margin-bottom: 1.2rem;
  min-width: 0;
}
.meta .tb { justify-self: start; max-width: 100%; overflow: hidden; text-overflow: ellipsis; }
.meta .stage { grid-column: 2; justify-self: center; text-align: center; }
.stage { font-size: 0.75rem; font-weight: 600; color: rgba(255, 255, 255, 0.7); letter-spacing: 0.03em; }
.hv-btn {
  display: inline-flex; align-items: center; justify-content: center;
  border: 1px solid transparent; border-radius: 13px; padding: 0.7rem 1.15rem;
  background: var(--ice); color: var(--navy); font-family: var(--font-display);
  font-weight: 700; font-size: 0.9375rem; text-decoration: none; cursor: pointer;
}
.hv-btn.ghost { background: transparent; color: #fff; border-color: rgba(255, 255, 255, 0.35); }
.hv-btn.dark { background: #fff; color: var(--navy); border-color: var(--line-strong); }
.hv-btn.sm { padding: 0.45rem 0.85rem; font-size: 0.8125rem; border-radius: 11px; }
.running-more { display: flex; width: fit-content; max-width: 100%; margin: 1rem auto 0; }
.livebar {
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 18px;
  padding: 1rem;
  display: grid;
  gap: 0.6rem;
  position: relative;
}
.lv {
  display: flex; justify-content: space-between; gap: 0.4rem 0.7rem; color: #fff;
  padding: 0.8rem 0.95rem 1.05rem; background: rgba(255, 255, 255, 0.06); border-radius: 14px;
  font-size: 0.9375rem; align-items: center; flex-wrap: wrap; text-decoration: none;
  border-left: 3px solid var(--status-live);
  background-image: linear-gradient(90deg, rgba(227, 107, 91, 0.07), transparent 60%);
}
.lv b { line-height: 1.35; min-width: 0; }
.lv.paused {
  border-left-color: rgba(255, 255, 255, 0.35);
  background-image: none;
}
.lv span, .all-live { font-size: 0.75rem; color: rgba(255, 255, 255, 0.7); font-weight: 600; }
.all-live { justify-self: end; text-decoration: none; font-weight: 800; letter-spacing: 0.04em; text-transform: uppercase; }
.stats { padding: 0.9rem 0.4rem 1.2rem; }
.sgrid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 0;
  align-items: stretch;
}
.stat {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: flex-start;
  text-align: center;
  min-width: 0;
  padding: 0.1rem 0.55rem 0.15rem;
  border-left: 2px solid var(--ice);
}
.stat:first-child { border-left-color: transparent; }
.stat b {
  display: block;
  width: 100%;
  font-size: 2rem;
  line-height: 1.1;
  color: var(--ink);
  font-weight: 800;
  text-align: center;
  font-variant-numeric: tabular-nums;
}
.stat > span {
  display: flex;
  align-items: flex-start;
  justify-content: center;
  width: 100%;
  min-height: 2.4em;
  font-size: 0.8125rem;
  line-height: 1.25;
  color: var(--muted);
  font-weight: 600;
  text-align: center;
}
.ls { display: none; }
.card { background: var(--surface); border-radius: 18px; box-shadow: var(--shadow); overflow: hidden; }
.card-head {
  display: flex; align-items: center; justify-content: space-between; gap: 0.75rem;
  padding: 0.7rem 1rem; background: #eef4f9; border-bottom: 1px solid var(--line);
}
.card-head h2, .section-title h2 { font-size: 0.9375rem; color: var(--navy); }
.section-title h2 { font-size: 1.25rem; }
.card-head small { display: block; margin-top: 1px; font-size: 0.75rem; font-weight: 600; letter-spacing: 0.04em; color: var(--muted); text-transform: none; }
.section-title { display: flex; align-items: baseline; justify-content: space-between; gap: 0.75rem; margin: 0 0 0.8rem; }
.more-link, .more {
  font-size: 0.8125rem; font-weight: 800; letter-spacing: 0.04em; text-transform: uppercase;
  color: var(--navy); white-space: nowrap; text-decoration: none;
}
.more-link::after { content: " →"; }
.more { display: block; text-align: center; padding: 0.9rem; border-top: 1px solid var(--line); }
.filters { padding: 0.65rem 0 0.75rem; border-bottom: 1px solid var(--line); min-width: 0; }
.chips, .tabs {
  display: flex;
  overflow-x: auto;
  overscroll-behavior-x: contain;
  scrollbar-width: none;
  min-width: 0;
  max-width: 100%;
  box-sizing: border-box;
}
.chips::-webkit-scrollbar, .tabs::-webkit-scrollbar { display: none; }
.chips {
  gap: 0.45rem;
  padding: 0.15rem 0 0.25rem;
  scroll-padding-inline: 1.7rem 3.25rem;
}
.chips::before { content: ""; flex: 0 0 1.7rem; }
.chips::after { content: ""; flex: 0 0 3.25rem; }
.chip-label, .tab-label { display: inline-block; max-width: 100%; vertical-align: bottom; }
.chip, .tabs button {
  flex: 0 0 auto; white-space: nowrap; border: 1px solid var(--line); background: #fff; color: var(--navy);
  border-radius: 999px; padding: 0.45rem 0.85rem; font-family: var(--font-display);
  font-size: 0.8125rem; font-weight: 800; letter-spacing: 0.03em; text-transform: uppercase; cursor: pointer;
}
.chip.on { background: var(--ice); border-color: var(--ice); }
.tabs {
  gap: 0;
  border-bottom: 1px solid var(--line);
  padding: 0;
  scroll-padding-inline: 0.75rem;
}
.tabs::before { content: ""; flex: 0 0 1rem; }
.tabs::after { content: ""; flex: 0 0 3.25rem; }
.tabs button {
  border: 0;
  border-radius: 0;
  color: var(--muted);
  padding: 0.75rem 0.9rem 0.65rem;
  background: transparent;
  box-shadow: inset 0 -3px 0 transparent;
}
.tabs button.on { color: var(--navy); box-shadow: inset 0 -3px 0 var(--ice); }
.feed-list { display: grid; grid-template-columns: 1fr 1fr; min-height: calc(3 * 115px); }
.feed-col { min-height: calc(3 * 115px); }
.feed-col + .feed-col { border-left: 1px solid var(--line); }
.feed-cards { display: none; }
.mrow, .irow, .mc, .srow, .pl { color: inherit; text-decoration: none; }
.mrow {
  display: grid; grid-template-columns: 56px 1fr 40px; gap: 0.75rem; align-items: center;
  box-sizing: border-box;
  height: 115px;
  padding: 0.75rem 1rem; border-top: 1px solid var(--line);
}
.feed-col .mrow:first-child { border-top: 0; }
.mtime { display: flex; flex-direction: column; align-items: flex-start; font-size: 0.8125rem; line-height: 1.25; color: var(--muted); font-weight: 600; }
.mtime b { color: var(--navy); font-weight: 800; }
.mmain { display: grid; gap: 0.35rem; min-width: 0; }
.mmeta { display: flex; align-items: center; justify-content: space-between; gap: 0.5rem; flex-wrap: wrap; }
.mmeta .l, .mteam, .mc-t { display: flex; align-items: center; gap: 0.5rem; min-width: 0; }
.mteam, .mc-t { gap: 8px; font-weight: 700; color: var(--ink); }
.n { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.irow .n {
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  flex: 1 1 auto;
  min-width: 0;
  max-width: 100%;
  overflow: hidden;
  white-space: normal;
  text-overflow: ellipsis;
  word-break: normal;
  overflow-wrap: normal;
  hyphens: manual;
  font-size: clamp(0.95rem, 28cqi, 1em);
  line-height: 1.25;
  min-height: calc(2 * 1.25em);
}
.mscore { display: grid; gap: 0.35rem; text-align: right; font-weight: 800; color: var(--ink); font-size: 1.125rem; }
.mscore b { height: 28px; line-height: 28px; }
.mscore b.dim, .mc-t b.dim, .sc.dim { color: var(--muted); font-weight: 600; }
.tb {
  display: inline-flex; align-items: center; border-radius: 7px; padding: 0.12rem 0.5rem;
  font-size: 0.75rem; font-weight: 800; letter-spacing: 0.03em; white-space: nowrap; line-height: 1.5;
}
.tb-0 { background: var(--navy); color: #fff; }
.tb-1 { background: var(--ice); color: var(--navy); }
.tb-2 { background: #dbeaf6; color: #1b4f7a; box-shadow: inset 0 0 0 1px rgba(47, 124, 177, 0.35); }
.tb-3 { background: #fff; color: var(--navy); box-shadow: inset 0 0 0 1.5px var(--navy); }
.card .stage, .mc-foot, .tc-meta, .lead span { color: var(--muted); }
.st {
  display: inline-flex; align-items: center; gap: 0.4rem; border-radius: 999px;
  padding: 0.15rem 0.6rem; font-size: 0.75rem; font-weight: 800; letter-spacing: 0.04em;
  text-transform: uppercase; white-space: nowrap; line-height: 1.5;
}
.st.today, .st.soon { background: var(--status-soon-soft); color: var(--status-soon-ink); }
.st.done { background: var(--status-done-soft); color: var(--status-done-ink); text-transform: none; letter-spacing: 0; }
.st.ok { background: var(--status-ok-soft); color: var(--status-ok); }
.st.warn { background: var(--status-warn-soft); color: var(--status-warn-ink); }
.st.live { background: var(--status-live-soft); color: var(--status-live-ink); }
.st.paused {
  background: var(--status-done-soft);
  color: var(--muted);
  font-weight: 700;
  text-transform: none;
  letter-spacing: 0;
}
.lv .badge.paused {
  background: var(--status-warn-soft);
  color: var(--status-warn-ink);
  border-color: transparent;
  padding: 0.12rem 0.55rem;
  font-size: 0.72rem;
  font-weight: 800;
  letter-spacing: 0;
  text-transform: none;
}
.st.live::before {
  content: ""; width: 8px; height: 8px; border-radius: 50%; background: var(--status-live);
  box-shadow: 0 0 0 0 rgba(227, 107, 91, 0.6); animation: hv-pulse 1.6s infinite;
}
.st.on-dark { background: rgba(227, 107, 91, 0.2); color: #ffd2cb; }
@keyframes hv-pulse { 70% { box-shadow: 0 0 0 7px rgba(227, 107, 91, 0); } 100% { box-shadow: 0 0 0 0 rgba(227, 107, 91, 0); } }
.tgrid { display: grid; grid-template-columns: 1fr 1fr; gap: 1.25rem; align-items: stretch; }
.tcard { display: flex; flex-direction: column; }
.tc-head { padding: 1.35rem 1.2rem 1rem; display: grid; gap: 0.7rem; }
.tc-top { display: flex; justify-content: space-between; gap: 0.6rem; align-items: flex-start; }
.tc-top > div { min-width: 0; }
.tc-top .st { margin-top: 0.15rem; flex: 0 0 auto; }
.tc-top h3 {
  font-size: 1.0625rem;
  margin-top: 0.45rem;
  line-height: 1.25;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  overflow: hidden;
}
.tcard .tb {
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.tc-meta { display: flex; gap: 0.4rem 1rem; flex-wrap: nowrap; min-width: 0; font-size: 0.8125rem; font-weight: 600; }
.tc-meta span { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.tc-meta span + span::before { content: "·"; margin-right: 1rem; color: var(--line-strong); }
.tc-prog { display: flex; justify-content: space-between; align-items: center; gap: 0.5rem; font-size: 0.8125rem; color: var(--muted); font-weight: 600; flex-wrap: wrap; }
.tc-prog > span { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.prog { height: 6px; border-radius: 99px; background: var(--bg-wash); overflow: hidden; }
.prog i { display: block; height: 100%; border-radius: 99px; background: var(--ice); }
.lead { display: flex; align-items: center; gap: 0.7rem; background: #f4f8fb; border: 1px solid var(--line); border-radius: 14px; padding: 0.55rem 0.8rem; }
.lead div { display: flex; flex-direction: column; line-height: 1.3; min-width: 0; }
.lead b {
  color: var(--ink);
  font-size: 0.9375rem;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.lead div span {
  font-size: 0.8125rem;
  font-weight: 600;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  overflow: hidden;
}
.crest.plus, .plus {
  width: 28px; height: 28px; border-radius: 50%; display: inline-flex; align-items: center; justify-content: center;
  background: var(--bg-wash); color: var(--navy); font-weight: 800; box-shadow: inset 0 0 0 1px var(--line-strong);
}
.tc-label { padding: 0.5rem 1.2rem; background: #f6f9fc; border-top: 1px solid var(--line); font-size: 0.75rem; font-weight: 800; letter-spacing: 0.08em; text-transform: uppercase; color: var(--muted); }
.empty-note, .block-empty, .block-error {
  padding: 1.6rem 1.2rem; text-align: center; color: var(--muted); font-size: 0.9375rem;
}
.block-error { display: grid; gap: 0.7rem; justify-items: center; }
.hero-state p { color: rgba(255, 255, 255, 0.86); margin: 0 0 0.8rem; }
.irow {
  display: grid; grid-template-columns: 56px minmax(0, 1fr) 64px minmax(0, 1fr); grid-template-areas: "time h sc a" ". stc stc stc";
  gap: 0.3rem 0.6rem; align-items: center; padding: 0.6rem 1.2rem; border-top: 1px solid var(--line);
}
.irow .mtime { grid-area: time; }
.irow .h, .irow .a { display: flex; align-items: center; gap: 0.5rem; font-weight: 700; color: var(--ink); min-width: 0; container-type: inline-size; }
.irow .h { grid-area: h; justify-content: flex-end; text-align: right; }
.irow .a { grid-area: a; }
.irow .sc { grid-area: sc; text-align: center; font-weight: 800; color: var(--ink); display: flex; flex-direction: column; align-items: center; justify-content: center; min-width: 0; }
.irow .sc .pen-hold { visibility: hidden; }
.irow .sc .line { display: inline-flex; align-items: baseline; justify-content: center; gap: 0.2rem; white-space: nowrap; }
.irow .sc small { font-size: 0.75rem; color: var(--muted); font-weight: 600; white-space: nowrap; }
.irow .stc { grid-area: stc; display: flex; justify-content: center; }
.stbl { width: 100%; border-collapse: collapse; table-layout: fixed; }
.stbl th { font-size: 0.75rem; font-weight: 800; letter-spacing: 0.06em; text-transform: uppercase; color: var(--muted); text-align: left; padding: 0.7rem 0.8rem; border-bottom: 1px solid var(--line); }
.stbl tbody tr { height: 56px; }
.stbl td { box-sizing: border-box; height: 56px; padding: 0 0.8rem; border-top: 1px solid var(--line); font-size: 0.9375rem; }
.stbl th.num,
.stbl td.num {
  width: 5.5rem;
  padding-left: 0.8rem;
  padding-right: 0.8rem;
  text-align: center;
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
}
.stbl .rk { width: 2.75rem; text-align: center; font-weight: 800; color: var(--muted); }
.srow .rk { text-align: center; font-weight: 800; color: var(--muted); }
.stbl th.tm,
.stbl td.tm { width: 11rem; }
.stbl .tm { color: var(--muted); font-weight: 600; }
.stbl td:nth-child(2) { overflow: hidden; }
.stbl th.num:last-child,
.stbl td.num.g {
  margin: 0;
  width: 5.5rem;
  padding: 0 0.8rem;
  text-align: center;
  font-variant-numeric: tabular-nums;
}
.stbl td.num.g {
  font-weight: 800;
  font-size: 1.125rem;
  color: var(--navy);
}
.pl { display: flex; align-items: center; gap: 0.7rem; min-width: 0; }
.pl b { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.pl b, .srow .nm { color: var(--ink); font-weight: 700; }
.sl-mob { display: none; }
.srow { display: grid; grid-template-columns: 24px 36px 1fr auto; gap: 0.7rem; align-items: center; box-sizing: border-box; height: 56px; min-height: 56px; padding: 0 1rem; border-top: 1px solid var(--line); }
.srow .tm { font-size: 0.75rem; color: var(--muted); font-weight: 600; }
.srow .g { font-weight: 800; font-size: 1.25rem; color: var(--navy); text-align: right; line-height: 1; }
.srow .g small { display: block; font-size: 0.75rem; font-weight: 600; color: var(--muted); margin-top: 3px; }
.sk-hero { height: 253px; min-height: 253px; border-radius: 22px; }
.sk-stats { height: 127px; min-height: 127px; }
.sk-matches { height: 118px; min-height: 118px; border-radius: 14px; }
.sk-tcard.skeleton, .sk-scorers.skeleton {
  min-height: 0;
  height: auto;
  background: #fff;
  animation: none;
}
.sk-crest {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: var(--bg-wash);
  box-shadow: inset 0 0 0 1px var(--line-strong);
}
.scorer-body { min-height: calc(41px + 5 * 56px); }
.scorer-empty {
  box-sizing: border-box;
  min-height: calc(41px + 5 * 56px);
  height: auto;
  max-height: none;
  display: flex;
  align-items: center;
  justify-content: center;
}
.sk-srow { height: 56px; min-height: 56px; }
.matches-card .block-empty {
  box-sizing: border-box;
  height: auto;
  max-height: none;
  min-height: calc(3 * 115px);
}
@media (max-width: 720px) {
  .hero-state, .hero-match, .hero-match.has-live, .hero-state:has(.livebar) { grid-template-columns: 1fr; padding: 1.25rem; }
  .hero-card { min-height: 446px; }
  .vs { gap: 0.45rem; flex-wrap: nowrap; font-size: 1.1rem; }
  .side { gap: 0.4rem; font-size: 1em; }
  .wm { width: 220px; right: -50px; }
  .hero-match :deep(.crest.lg), .hero-state :deep(.crest.lg) { width: 44px; height: 44px; }
  .feed-list { display: none; }
  .feed-cards {
    display: flex;
    gap: 0.75rem;
    overflow-x: auto;
    overscroll-behavior-x: contain;
    box-sizing: border-box;
    padding: 0.9rem 1rem 1.15rem;
    scroll-snap-type: x mandatory;
    scroll-padding-inline: 1rem;
    scrollbar-width: none;
    min-width: 0;
    width: 100%;
  }
  .feed-cards::-webkit-scrollbar { display: none; }
  .mc {
    flex: 0 0 auto;
    width: calc(100% - 3.25rem);
    box-sizing: border-box;
    scroll-snap-align: start;
    border: 1px solid var(--line);
    border-radius: 14px;
    padding: 0.85rem 0.85rem 1rem;
    display: grid;
    gap: 0.5rem;
    background: #fff;
  }
  .mc-top { display: flex; align-items: center; justify-content: space-between; gap: 0.4rem; }
  .mc-t b { font-size: 1.125rem; margin-left: auto; }
  .mc-foot { font-size: 0.75rem; color: var(--muted); font-weight: 600; border-top: 1px solid var(--line); padding-top: 0.5rem; }
  .hero-state:not(:has(.livebar)),
  .hero-match:not(.has-live) { min-height: 0; align-items: start; gap: 0; }
  .irow { grid-template-columns: 48px minmax(0, 1fr) auto; grid-template-areas: "time h sc" "time a sc" ". stc stc"; padding: 0.7rem 1rem; }
  .irow .h { flex-direction: row-reverse; justify-content: flex-end; text-align: left; }
  .irow .sc { align-items: flex-end; }
  .irow .sc .line { white-space: nowrap; }
  .irow .stc { justify-content: flex-start; }
  .tb { white-space: normal; }
  .tcard .tb { white-space: nowrap; }
  .tc-top h3 { min-height: calc(2 * 1.25 * 1.0625rem); }
  .lead div span { min-height: calc(2 * 1.3 * 0.8125rem); }
  .tc-meta span + span::before { display: none; }
  .stats { padding: 0.8rem 0.35rem 1rem; }
  .stat { padding-inline: 0.28rem; }
  .stat b { font-size: 1.25rem; line-height: 1.15; }
  .stat > span { font-size: 0.6875rem; line-height: 1.2; min-height: 2.2em; }
  .meta { display: flex; flex-wrap: wrap; gap: 0.35rem 0.55rem; }
  .meta .stage { flex: 1 0 100%; text-align: center; }
  .stbl { display: none; }
  .sl-mob { display: block; }
  .hero-card.has-live, .hero-card:has(.livebar) { min-height: 446px; }
  .sk-hero { height: 446px; min-height: 446px; }
  .sk-stats { height: 136px; min-height: 136px; }
}
@container home (max-width: 720px) {
  .hero-state, .hero-match, .hero-match.has-live, .hero-state:has(.livebar) { grid-template-columns: 1fr; }
  .hero-state:not(:has(.livebar)),
  .hero-match:not(.has-live) { min-height: 0; align-items: start; gap: 0; }
  .vs { gap: 0.45rem; flex-wrap: nowrap; font-size: 1.1rem; }
  .side { gap: 0.4rem; }
  .meta { display: flex; flex-wrap: wrap; gap: 0.35rem 0.55rem; }
  .meta .stage { flex: 1 0 100%; text-align: center; }
  .irow { grid-template-columns: 48px minmax(0, 1fr) auto; }
  .irow .sc .line { white-space: nowrap; }
}
@media (max-width: 960px) {
  .lf { display: none; }
  .ls { display: inline; }
}
@media (max-width: 960px) {
  .tgrid { grid-template-columns: 1fr; }
}
@container home (max-width: 960px) {
  .tgrid { grid-template-columns: 1fr; }
}
@media (max-width: 480px) {
  .vs { gap: 0.35rem; }
  .side { gap: 0.35rem; }
  .x { letter-spacing: 0; }
}
@container home (max-width: 480px) {
  .vs { gap: 0.35rem; }
  .side { gap: 0.35rem; }
  .x { letter-spacing: 0; }
}
@media (max-width: 720px) {
  .container { width: min(1120px, calc(100% - 2 * var(--page-gutter))); }
  .band { background: #001433; padding: 10px 0; }
  .block { padding-top: 12px; }
  .hero-card {
    background: #0a2149;
    border-radius: 16px;
    min-height: 0 !important;
    margin-inline: 0;
  }
  .hero-state, .hero-match, .hero-match.has-live, .hero-state:has(.livebar) {
    padding: 12px;
    min-height: 0;
  }
  .hero-card.has-live, .hero-card:has(.livebar) { min-height: 0; }
  .wm { width: 140px; opacity: 0.15; right: -8px; top: -18px; }
  .eyebrow { font-size: 11px; font-weight: 700; line-height: 14px; letter-spacing: 0.06em; }
  .vs { height: 36px; margin: 10px 0; gap: 8px; font-size: 14px; align-items: center; }
  .side { gap: 8px; }
  .team-name { font-size: 14px; line-height: 16px; font-weight: 800; }
  .x { font-size: 15px; font-weight: 800; letter-spacing: 0.04em; }
  .hero-match :deep(.crest.lg), .hero-state :deep(.crest.lg) { width: 28px; height: 28px; font-size: 10px; }
  .meta {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 4px 8px;
    min-height: 22px;
    height: auto;
    margin: 0 0 10px;
  }
  .meta .stage {
    flex: 1 1 120px;
    min-width: 0;
    max-width: 100%;
    text-align: left;
    font-size: 12px;
    line-height: 16px;
    font-weight: 500;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }
  .meta .tb {
    display: block;
    flex: 0 1 auto;
    max-width: 100%;
    min-width: 0;
    height: 22px;
    box-sizing: border-box;
    border-radius: 999px;
    padding: 0 8px;
    font-size: 11px;
    font-weight: 700;
    line-height: 22px;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }
  .acts { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; width: 100%; }
  .hv-btn { height: 44px; width: 100%; border-radius: 12px; padding: 0 12px; font-size: 14px; position: relative; }
  .stats {
    box-sizing: border-box;
    height: 58px;
    padding: 6px 0 4px;
    background: #fff;
    border-radius: 12px;
    box-shadow: var(--shadow);
  }
  .stats .eyebrow { font-size: 10px; line-height: 12px; font-weight: 700; letter-spacing: 0.06em; margin: 0 0 2px 10px; }
  .stat { border-left: 0; padding: 0 2px; position: relative; }
  .stat:not(:first-child)::before {
    content: "";
    position: absolute;
    left: 0;
    top: 50%;
    width: 1px;
    height: 30px;
    transform: translateY(-50%);
    background: #dce5ee;
  }
  .stat b { font-size: 17px; line-height: 17px; font-weight: 800; }
  .stat > span { min-height: 0; font-size: 10.5px; line-height: 12px; font-weight: 500; }
  .card { border-radius: 12px; }
  .card-head { height: 44px; padding: 0 12px; background: #fff; border-bottom: 0; }
  .card-head small { display: none; }
  .card-head h2 { font-size: 16px; line-height: 20px; font-weight: 800; }
  .more-link { font-size: 12px; font-weight: 800; line-height: 16px; }
  .filters { height: 44px; padding: 0; border-bottom: 0; display: flex; align-items: center; }
  .chips { gap: 8px; padding: 0; scroll-padding-inline: 12px; }
  .chips::before { flex-basis: 12px; }
  .chips::after { flex-basis: 28px; }
  .chip {
    position: relative;
    height: 32px;
    padding: 0 14px;
    font-size: 12px;
    font-weight: 800;
    line-height: 16px;
  }
  .chip::before {
    content: "";
    position: absolute;
    left: 0;
    right: 0;
    top: 50%;
    height: var(--tap-min);
    transform: translateY(-50%);
  }
  .feed-cards { gap: 10px; padding: 4px 0 12px 12px; scroll-padding-inline: 0; }
  .mc {
    flex: 0 0 calc(100% - 66px);
    width: calc(100% - 66px);
    height: 118px;
    padding: 8px 10px;
    gap: 0;
    border-radius: 14px;
    grid-template-rows: 22px 26px 26px auto;
    align-content: start;
  }
  .mc-top { height: 22px; }
  .mc .tb, .mc .st { height: 22px; font-size: 11px; line-height: 22px; padding: 0 8px; }
  .mc-t { height: 26px; font-size: 14px; font-weight: 700; }
  .mc-t :deep(.crest) { width: 22px !important; height: 22px !important; font-size: 10px; }
  .mc-t b { font-size: 14px; }
  .mc-foot { font-size: 11.5px; line-height: 14px; font-weight: 500; padding-top: 4px; }
  .tabs { height: 44px; }
  .tabs button { height: 44px; padding: 0 12px; font-size: 12px; font-weight: 700; }
  .sk-hero { height: 170px; min-height: 170px; border-radius: 16px; }
  .sk-stats { height: 58px; min-height: 58px; border-radius: 12px; }
  .sk-matches { height: 118px; min-height: 118px; }
  .feed-cards { min-height: calc(118px + 16px); }
  .scorer-body, .scorer-empty { min-height: calc(5 * 56px); }
  .matches-card .block-empty { min-height: calc(118px + 16px); }
  .block-empty, .block-error {
    box-sizing: border-box;
    min-height: 60px;
    max-height: 68px;
    height: 64px;
    padding: 0 12px;
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
    text-align: left;
    font-size: 13.5px;
    font-weight: 700;
  }
  .block-error .hv-btn { width: auto; min-width: 108px; }
  .matches-card .block-empty,
  .scorer-empty {
    height: auto;
    max-height: none;
    justify-content: center;
    text-align: center;
  }
  .matches-card .block-empty { min-height: calc(118px + 16px); }
  .scorer-body,
  .scorer-empty { min-height: calc(5 * 56px); }
}
@media (prefers-reduced-motion: reduce) {
  .st.live::before { animation: none; }
}
</style>
