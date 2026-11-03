<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import api from '../api/client'
import EmptyState from '../components/EmptyState.vue'
import MatchRow from '../components/MatchRow.vue'
import StandingGroups from '../components/StandingGroups.vue'
import { parseStandings, standingGroupsHaveRows } from '../lib/standings'
import { useTeamDirectory } from '../lib/useTeamDirectory'
import { BRAND_MARK_SRC, SITE_NAME } from '../lib/brand'
import { formatDateRange } from '../lib/format'
import { longKickoff, matchInstant } from '../lib/match'
import { pickNextMatch } from '../lib/nextMatch'
import { setPageMeta } from '../lib/pageMeta'
import { registeredName } from '../lib/playerTitle'

type Slide = {
  id: string
  url?: string
  title?: string
  caption?: string
  linkUrl?: string
  linkLabel?: string
  enabled?: boolean
}

const feed = ref<any>(null)
const matches = ref<any[]>([])
const nextMatch = ref<any>(null)
const loaded = ref(false)
const loadError = ref('')
const slide = ref(0)
const storyRail = ref<HTMLElement | null>(null)
const teams = useTeamDirectory()
let timer: number | undefined

function shownSlides(list: Slide[] | null | undefined): Slide[] {
  return (list ?? []).filter((item) => item?.enabled !== false && !!item?.url)
}

const heroes = computed<Slide[]>(() => shownSlides(feed.value?.heroes))
const stories = computed<Slide[]>(() => shownSlides(feed.value?.stories))
const moments = computed<Slide[]>(() => shownSlides(feed.value?.photos))
const current = computed(() => heroes.value[slide.value] || null)
const storyIndex = ref<number | null>(null)
const openedStory = computed(() =>
  storyIndex.value == null ? null : stories.value[storyIndex.value] || null
)
const tape = computed(() =>
  matches.value
    .slice()
    .sort((a, b) => String(matchInstant(b) || '').localeCompare(String(matchInstant(a) || '')))
    .slice(0, 8)
)
const homeTables = computed(() => parseStandings(feed.value?.standings))
const tournamentDates = computed(() =>
  formatDateRange(feed.value?.tournament?.startDate, feed.value?.tournament?.endDate)
)
const openTournament = computed(() => feed.value?.tournament?.id ? feed.value.tournament : null)

function readNextMatch(response: { status?: number; data?: unknown } | null) {
  if (!response || response.status === 204) return null
  const data = response.data
  if (!data || typeof data !== 'object') return null
  const row = data as { id?: unknown; tournamentId?: unknown }
  return row.id ? data as { id: string; tournamentId?: string } : null
}

async function loadNextMatch(tournamentId: string, rows: any[]) {
  try {
    const response = await api.get('/matches/next', { params: { tournamentId } })
    const row = readNextMatch(response)
    if (row?.id && (!row.tournamentId || row.tournamentId === tournamentId)) return row
  } catch {
    // The list below still applies the same nearest-match rule.
  }
  return pickNextMatch(rows, Date.now(), tournamentId)
}

onMounted(async () => {
  try {
    await teams.load()
    const [{ data }, games] = await Promise.all([
      api.get('/home'),
      api.get('/matches', { params: { size: 40, sort: 'scheduledAt,desc' } }),
    ])
    feed.value = data
    matches.value = games.data.content ?? []
    const openId = data?.tournament?.id as string | undefined
    nextMatch.value = openId ? await loadNextMatch(openId, matches.value) : null
    const name = data?.tournament?.name || SITE_NAME
    setPageMeta({
      title: 'Главная',
      description: `${name}: таблица, ближайшие матчи и бомбардиры.`,
      path: '/',
    })
    start()
  } catch {
    feed.value = null
    matches.value = []
    nextMatch.value = null
    loadError.value = 'Главная не загрузилась.'
  } finally {
    loaded.value = true
  }
})

onUnmounted(() => {
  if (timer) window.clearInterval(timer)
  window.removeEventListener('keydown', onStoryKeys)
})

function start() {
  if (timer) window.clearInterval(timer)
  if (heroes.value.length < 2 || openedStory.value) return
  timer = window.setInterval(() => {
    slide.value = (slide.value + 1) % heroes.value.length
  }, 6500)
}

function openStory(index: number) {
  storyIndex.value = index
  if (timer) window.clearInterval(timer)
  window.addEventListener('keydown', onStoryKeys)
}

function closeStory() {
  storyIndex.value = null
  window.removeEventListener('keydown', onStoryKeys)
  start()
}

function stepStory(dir: number) {
  if (storyIndex.value == null || !stories.value.length) return
  const next = storyIndex.value + dir
  if (next < 0 || next >= stories.value.length) return
  storyIndex.value = next
}

function onStoryKeys(event: KeyboardEvent) {
  if (storyIndex.value == null) return
  if (event.key === 'Escape') closeStory()
  if (event.key === 'ArrowLeft') stepStory(-1)
  if (event.key === 'ArrowRight') stepStory(1)
}

function go(index: number | string) {
  slide.value = Number(index)
  start()
}

function hrefOf(item: Slide | null | undefined, fallback = '/') {
  return item?.linkUrl || fallback
}

function isExternal(url: string) {
  return /^https?:\/\//i.test(url)
}

function nudgeStories(dir: number) {
  storyRail.value?.scrollBy({ left: dir * 140, behavior: 'smooth' })
}

function heroHeading(slide: Slide | null | undefined) {
  const title = slide?.title?.trim()
  if (!title || /живой сезон/i.test(title)) return SITE_NAME
  return title
}

const bannerTitle = computed(() => openTournament.value ? heroHeading(current.value) : SITE_NAME)
</script>

<template>
  <section class="home">
    <div class="stage">
      <div class="container">
        <div v-if="stories.length" class="stories-wrap">
          <button class="nudge" type="button" aria-label="Сюжеты назад" @click="nudgeStories(-1)">‹</button>
          <div ref="storyRail" class="stories">
            <button
              v-for="(item, index) in stories"
              :key="item.id"
              type="button"
              class="story"
              @click="openStory(Number(index))"
            >
              <img :src="item.url" width="72" height="72" :alt="item.title || item.caption || 'Сюжет'" />
              <span>{{ item.title || item.caption || 'Сюжет' }}</span>
            </button>
          </div>
          <button class="nudge" type="button" aria-label="Сюжеты вперёд" @click="nudgeStories(1)">›</button>
        </div>

        <div v-if="!loaded" class="hero hero-pending" />
        <div v-else class="hero">
          <div class="hero-copy">
            <p v-if="openTournament" class="eyebrow">
              <RouterLink :to="`/tournaments/${openTournament.id}`">{{ openTournament.name || SITE_NAME }}</RouterLink>
            </p>
            <h1>{{ bannerTitle }}</h1>
            <p v-if="tournamentDates">{{ tournamentDates }}</p>
            <p v-if="openTournament && current?.caption">{{ current.caption }}</p>
            <p v-if="openTournament && nextMatch?.id">
              Ближайший матч: {{ teams.fullName(nextMatch.homeTeamId) }} — {{ teams.fullName(nextMatch.awayTeamId) }}
              · {{ longKickoff(matchInstant(nextMatch)) }}
            </p>
            <div class="hero-actions">
              <template v-if="openTournament">
                <a
                  v-if="isExternal(hrefOf(current, '/calendar'))"
                  class="btn hero-cta"
                  :href="hrefOf(current, '/calendar')"
                  target="_blank"
                  rel="noreferrer"
                >
                  {{ current?.linkLabel || 'Смотреть календарь' }}
                </a>
                <RouterLink v-else class="btn hero-cta" :to="hrefOf(current, '/calendar')">
                  {{ current?.linkLabel || 'Смотреть календарь' }}
                </RouterLink>
              </template>
              <template v-else>
                <RouterLink class="btn hero-cta" to="/calendar">Игры</RouterLink>
                <RouterLink class="btn hero-cta secondary" to="/tournaments">Турниры</RouterLink>
              </template>
            </div>
            <a
              v-if="feed?.vkAlbumUrl && !feed?.photos?.length"
              class="album"
              :href="feed.vkAlbumUrl"
              target="_blank"
              rel="noreferrer"
            >Кадры во ВКонтакте</a>
          </div>
          <div class="league-badge">
            <img class="league-photo" :src="BRAND_MARK_SRC" width="512" height="512" alt="" />
          </div>
          <div v-if="heroes.length > 1" class="dots">
            <button
              v-for="(item, index) in heroes"
              :key="item.id"
              type="button"
              :class="{ on: Number(index) === slide }"
              :aria-label="item.title || `Слайд ${Number(index) + 1}`"
              @click="go(index)"
            />
          </div>
        </div>
      </div>
    </div>

    <div class="container blocks">
      <p v-if="loadError" class="form-error">{{ loadError }}</p>
      <div v-if="!loaded" class="sheet skeleton home-skel" />
      <div v-else-if="tape.length" class="sheet">
        <div class="league-head">
          <div>
            <RouterLink v-if="feed?.tournament?.id" :to="`/tournaments/${feed.tournament.id}`">{{ feed.tournament.name || 'Матчи' }}</RouterLink>
            <template v-else>{{ feed?.tournament?.name || 'Матчи' }}</template>
            <small>Последние и ближайшие</small>
          </div>
          <RouterLink class="more" to="/calendar">Все игры</RouterLink>
        </div>
        <MatchRow
          v-for="m in tape"
          :key="m.id"
          :match="m"
          :home-name="teams.fullName(m.homeTeamId)"
          :away-name="teams.fullName(m.awayTeamId)"
        />
      </div>

      <div class="hero-grid">
        <div class="panel table-block">
          <div class="page-title">
            <p class="eyebrow">Таблица</p>
            <h2>
              <RouterLink v-if="feed?.tournament?.id" :to="`/tournaments/${feed.tournament.id}`">{{ feed.tournament.name }}</RouterLink>
              <template v-else>Турнир ещё не открыт</template>
            </h2>
          </div>
          <div v-if="!loaded" class="skeleton home-skel" />
          <EmptyState v-else-if="!standingGroupsHaveRows(homeTables)" title="Таблицы пока нет" />
          <StandingGroups v-else :tables="homeTables" compact />
        </div>

        <aside class="panel leaders" aria-label="Бомбардиры и ассистенты">
          <section class="leaders-section">
            <h2>Бомбардиры</h2>
            <div v-if="!loaded" class="skeleton home-skel" />
            <EmptyState v-else-if="!feed?.scorers?.length" title="Голов пока нет" />
            <ol v-else class="leaders-list">
              <li v-for="(p, index) in feed?.scorers" :key="p.playerId">
                <RouterLink class="leader" :to="`/players/${p.playerId}`">
                  <span class="place">{{ Number(index) + 1 }}</span>
                  <span class="who">{{ registeredName(p) || 'Игрок' }}</span>
                  <span class="tally">{{ p.goals }}</span>
                </RouterLink>
              </li>
            </ol>
          </section>
          <section class="leaders-section">
            <h2>Ассистенты</h2>
            <div v-if="!loaded" class="skeleton" />
            <ol v-else-if="feed?.assists?.length" class="leaders-list">
              <li v-for="(p, index) in feed.assists" :key="'a-' + p.playerId">
                <RouterLink class="leader" :to="`/players/${p.playerId}`">
                  <span class="place">{{ Number(index) + 1 }}</span>
                  <span class="who">{{ registeredName(p) || 'Игрок' }}</span>
                  <span class="tally">{{ p.assists }}</span>
                </RouterLink>
              </li>
            </ol>
          </section>
        </aside>
      </div>

      <div v-if="feed?.photos?.length" class="panel stack moments">
        <div class="moments-head">
          <h2>Моменты</h2>
          <a v-if="feed?.vkAlbumUrl" :href="feed.vkAlbumUrl" target="_blank" rel="noreferrer">Все кадры →</a>
        </div>
        <EmptyState v-if="loaded && !moments.length" title="Фото пока нет" />
        <div v-else class="photos">
          <figure v-for="photo in moments" :key="photo.id">
            <img :src="photo.url" :alt="photo.caption || photo.title || 'Момент'" />
            <figcaption v-if="photo.title || photo.caption">{{ photo.title || photo.caption }}</figcaption>
          </figure>
        </div>
      </div>
    </div>

    <Teleport to="body">
      <div
        v-if="openedStory"
        class="story-overlay"
        role="dialog"
        aria-modal="true"
        :aria-label="openedStory.title || openedStory.caption || 'Сюжет'"
        @click.self="closeStory"
      >
        <button class="story-close" type="button" aria-label="Закрыть" @click="closeStory">×</button>
        <button
          v-if="stories.length > 1 && storyIndex"
          class="story-step"
          type="button"
          aria-label="Предыдущий сюжет"
          @click="stepStory(-1)"
        >‹</button>
        <figure class="story-card">
          <img :src="openedStory.url" :alt="openedStory.title || openedStory.caption || 'Сюжет'" />
          <figcaption v-if="openedStory.title || openedStory.caption">
            <strong v-if="openedStory.title">{{ openedStory.title }}</strong>
            <span v-if="openedStory.caption">{{ openedStory.caption }}</span>
          </figcaption>
          <a
            v-if="openedStory.linkUrl && isExternal(openedStory.linkUrl)"
            class="btn story-link"
            :href="openedStory.linkUrl"
            target="_blank"
            rel="noreferrer"
          >{{ openedStory.linkLabel || 'Открыть' }}</a>
          <RouterLink
            v-else-if="openedStory.linkUrl"
            class="btn story-link"
            :to="openedStory.linkUrl"
            @click="closeStory"
          >{{ openedStory.linkLabel || 'Открыть' }}</RouterLink>
        </figure>
        <button
          v-if="stories.length > 1 && storyIndex != null && storyIndex < stories.length - 1"
          class="story-step next"
          type="button"
          aria-label="Следующий сюжет"
          @click="stepStory(1)"
        >›</button>
      </div>
    </Teleport>
  </section>
</template>

<style scoped>
.stage {
  background:
    radial-gradient(720px 280px at 80% 0%, rgba(76, 180, 229, 0.22), transparent 60%),
    var(--navy);
  padding: 0.85rem 0 1.15rem;
}
.stories-wrap {
  display: grid;
  grid-template-columns: auto 1fr auto;
  align-items: center;
  gap: 0.4rem;
}
.nudge {
  width: 36px;
  height: 36px;
  border: 0;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.14);
  color: #fff;
  cursor: pointer;
  font-size: 1.4rem;
  line-height: 1;
}
.nudge:hover { background: rgba(255, 255, 255, 0.24); }
.stories {
  display: flex;
  gap: 1rem;
  overflow-x: auto;
  padding: 0.4rem 0 1.1rem;
  scrollbar-width: none;
}
.stories::-webkit-scrollbar { display: none; }
.story {
  width: 86px;
  flex: 0 0 auto;
  display: grid;
  justify-items: center;
  gap: 0.4rem;
  color: #fff;
  text-decoration: none;
  font: inherit;
  font-size: 0.72rem;
  text-align: center;
  border: 0;
  background: transparent;
  padding: 0;
  cursor: pointer;
}
.story:hover { color: #fff; text-decoration: none; }
.story img {
  width: 72px;
  height: 72px;
  border-radius: 50%;
  object-fit: cover;
  border: 2px solid var(--ice);
  box-shadow: 0 0 0 3px rgba(76, 180, 229, 0.22);
}
.story span {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  min-height: 2.1em;
}
.hero {
  position: relative;
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: 1rem;
  border-radius: 18px;
  overflow: hidden;
  min-height: 280px;
  padding: 1.25rem 1.35rem 1.35rem 1.5rem;
  background:
    radial-gradient(280px 280px at 100% 50%, rgba(255, 255, 255, 0.14), transparent 68%),
    #001433;
}
.hero-pending { min-height: 280px; display: block; }
.album { color: #fff; font-weight: 700; width: fit-content; }
.album:hover { color: #fff; }
.home-skel { min-height: 220px; }
.league-badge {
  width: min(220px, 34vw);
  aspect-ratio: 1;
  border-radius: 50%;
  background: #fff;
  display: grid;
  place-items: center;
  padding: 0.4rem;
  box-shadow: 0 12px 28px rgba(0, 0, 0, 0.28);
}
.league-badge img {
  width: 100%;
  height: 100%;
  object-fit: contain;
  display: block;
}
.hero-copy {
  position: relative;
  z-index: 1;
  display: grid;
  gap: 0.55rem;
  align-content: center;
  min-width: 0;
  color: #fff;
}
.hero-copy h1,
.hero-copy .eyebrow,
.hero-copy p { color: #fff; }
.hero-copy h1 { font-size: clamp(1.45rem, 3.2vw, 2.1rem); }
.hero-copy p { color: rgba(255,255,255,0.86); }
.hero-copy a,
.hero-copy a:hover { color: #fff; text-decoration: none; }
.hero-actions { display: flex; flex-wrap: wrap; gap: 0.5rem; }
.table-block h2 a,
.league-head a { color: inherit; text-decoration: none; }
.hero-cta,
.hero-cta:hover {
  width: fit-content;
  border-radius: 999px;
  padding: 0.58rem 1.15rem;
  color: #fff;
}
a.hero-cta.secondary,
a.hero-cta.secondary:hover {
  background: #fff;
  color: var(--navy);
}
.dots {
  position: absolute;
  left: 50%;
  bottom: 1rem;
  transform: translateX(-50%);
  display: flex;
  gap: 0.4rem;
}
.dots button {
  width: 28px;
  height: 4px;
  border: 0;
  border-radius: 99px;
  background: rgba(255,255,255,0.35);
  cursor: pointer;
  padding: 0;
}
.dots button.on { width: 46px; background: #fff; }
.blocks { display: grid; gap: 1.2rem; padding-top: 1.4rem; }
.sheet {
  background: #fff;
  border: 1px solid var(--line);
  border-radius: 12px;
  overflow: hidden;
}
.league-head { justify-content: space-between; }
.more { color: var(--navy); font-size: 0.75rem; font-weight: 800; }
.hero-grid { display: grid; grid-template-columns: 1.5fr 0.8fr; gap: 1rem; align-items: start; }
.hero-grid > * { min-width: 0; }
.table-block { padding: 1.4rem 1.5rem; }
.leaders {
  display: grid;
  gap: 1.25rem;
  align-content: start;
  background: #fff;
  color: var(--text);
  padding: 1.15rem 1.05rem 0.8rem;
}
.leaders-section { display: grid; min-width: 0; }
.leaders h2 {
  position: relative;
  width: fit-content;
  margin: 0 0 0.55rem;
  padding: 0 0 0.42rem;
  font-size: 0.92rem;
  font-weight: 700;
  letter-spacing: 0.06em;
  line-height: 1.2;
  color: var(--navy);
}
.leaders h2::after {
  content: "";
  position: absolute;
  left: 0;
  bottom: 0;
  width: 1.65rem;
  height: 2px;
  border-radius: 99px;
  background: var(--ice);
}
.leaders-list {
  list-style: none;
  margin: 0.1rem -0.45rem 0;
  padding: 0;
  min-width: 0;
}
.leader {
  display: grid;
  grid-template-columns: 1.35rem minmax(0, 1fr) auto;
  column-gap: 0.55rem;
  align-items: center;
  min-height: 2.85rem;
  padding: 0.48rem 0.45rem;
  border-bottom: 1px solid var(--line);
  color: var(--navy);
  text-decoration: none;
}
.leaders-list li:last-child .leader { border-bottom: 0; }
.leader:hover {
  background: rgba(76, 180, 229, 0.08);
  text-decoration: none;
}
.leader:focus-visible {
  outline: 2px solid var(--ice);
  outline-offset: -2px;
}
.place {
  color: var(--muted);
  font-size: 0.75rem;
  font-weight: 700;
  line-height: 1;
  text-align: center;
  font-variant-numeric: tabular-nums;
}
.who {
  min-width: 0;
  font-size: 0.98rem;
  font-weight: 700;
  line-height: 1.35;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  overflow-wrap: break-word;
}
.tally {
  font-size: 0.98rem;
  font-weight: 800;
  line-height: 1;
  font-variant-numeric: tabular-nums;
  padding-left: 0.35rem;
}
.moments-head {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  gap: 1rem;
}
.moments-head a { font-weight: 700; }
.photos { display: grid; grid-template-columns: repeat(auto-fill, minmax(168px, 1fr)); gap: 0.8rem; }
.photos figure { position: relative; margin: 0; }
.photos img { width: 100%; height: 240px; object-fit: cover; border-radius: 16px; display: block; }
.photos figcaption {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 1.4rem 0.75rem 0.7rem;
  color: #fff;
  font-size: 0.82rem;
  font-weight: 700;
  background: linear-gradient(transparent, rgba(0, 20, 51, 0.86));
  border-radius: 0 0 16px 16px;
}
.story-overlay {
  position: fixed;
  inset: 0;
  z-index: 40;
  display: grid;
  place-items: center;
  background: rgba(0, 20, 51, 0.88);
  padding: 1.2rem;
}
.story-card {
  margin: 0;
  width: min(520px, 100%);
  display: grid;
  gap: 0.75rem;
  justify-items: center;
}
.story-card img {
  width: 100%;
  max-height: 72vh;
  object-fit: contain;
  border-radius: 16px;
  background: #001433;
}
.story-card figcaption {
  display: grid;
  gap: 0.25rem;
  color: #fff;
  text-align: center;
}
.story-card strong { font-size: 1.05rem; }
.story-card span { color: rgba(255, 255, 255, 0.82); font-size: 0.9rem; }
.story-link { width: fit-content; }
.story-close,
.story-step {
  position: absolute;
  border: 0;
  background: rgba(255, 255, 255, 0.16);
  color: #fff;
  cursor: pointer;
  line-height: 1;
}
.story-close {
  top: 1rem;
  right: 1rem;
  width: 40px;
  height: 40px;
  border-radius: 50%;
  font-size: 1.6rem;
}
.story-step {
  left: 0.8rem;
  top: 50%;
  transform: translateY(-50%);
  width: 40px;
  height: 40px;
  border-radius: 50%;
  font-size: 1.6rem;
}
.story-step.next { left: auto; right: 0.8rem; }
.story-close:hover,
.story-step:hover { background: rgba(255, 255, 255, 0.28); }
@media (max-width: 1099px) and (min-width: 720px) {
  .blocks {
    grid-template-columns: 1.15fr 0.85fr;
    align-items: start;
  }
  .sheet { grid-column: 1; grid-row: 1 / span 2; }
  .hero-grid { grid-column: 2; grid-template-columns: 1fr; }
  .moments { grid-column: 1 / -1; }
}
@media (max-width: 719px) {
  .hero-grid { grid-template-columns: 1fr; }
  .hero { grid-template-columns: minmax(0, 1fr) 96px; min-height: 220px; padding: 1rem; }
  .hero-copy a,
  .hero-copy a:hover { color: #fff; }
  a.hero-cta.secondary,
  a.hero-cta.secondary:hover { color: var(--navy); }
  .stories-wrap { gap: 0.2rem; }
  .nudge { width: 28px; height: 28px; }
  .story { width: 72px; }
  .story img { width: 60px; height: 60px; }
  .hero-pending { min-height: 220px; }
  .league-badge { width: 96px; }
  .hero-copy h1 { font-size: 1.25rem; }
  .blocks { padding-top: 0.75rem; gap: 0.7rem; }
  .table-block { padding: 0.85rem; }
  .leaders { padding: 0.95rem 0.8rem 0.7rem; gap: 1.05rem; }
  .who, .tally { font-size: 1rem; }
  .leader { min-height: 2.95rem; }
}
</style>
