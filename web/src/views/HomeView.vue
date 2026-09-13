<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import api from '../api/client'
import EmptyState from '../components/EmptyState.vue'
import MatchRow from '../components/MatchRow.vue'
import StandingTable from '../components/StandingTable.vue'
import { useTeamDirectory } from '../lib/useTeamDirectory'

type Slide = {
  id: string
  url?: string
  title?: string
  caption?: string
  linkUrl?: string
  linkLabel?: string
}

const feed = ref<any>(null)
const matches = ref<any[]>([])
const loaded = ref(false)
const slide = ref(0)
const storyRail = ref<HTMLElement | null>(null)
const teams = useTeamDirectory()
let timer: number | undefined

const heroes = computed<Slide[]>(() => feed.value?.heroes ?? [])
const stories = computed<Slide[]>(() => feed.value?.stories ?? [])
const current = computed(() => heroes.value[slide.value] || null)
const storyIndex = ref<number | null>(null)
const openedStory = computed(() =>
  storyIndex.value == null ? null : stories.value[storyIndex.value] || null
)
const tape = computed(() =>
  matches.value
    .slice()
    .sort((a, b) => String(b.scheduledAt).localeCompare(String(a.scheduledAt)))
    .slice(0, 8)
)

onMounted(async () => {
  try {
    await teams.load()
    const [{ data }, games] = await Promise.all([
      api.get('/home'),
      api.get('/matches', { params: { size: 40, sort: 'scheduledAt,desc' } }),
    ])
    feed.value = data
    matches.value = games.data.content ?? []
    start()
  } catch {
    feed.value = null
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
  if (!title || /живой сезон/i.test(title)) return 'Лига ИТМО по футзалу'
  return title
}
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
              <img :src="item.url" :alt="item.title || item.caption || 'Сюжет'" />
              <span>{{ item.title || item.caption || 'Сюжет' }}</span>
            </button>
          </div>
          <button class="nudge" type="button" aria-label="Сюжеты вперёд" @click="nudgeStories(1)">›</button>
        </div>

        <div class="hero" :class="{ photo: !!current?.url }">
          <img v-if="current?.url" :src="current.url" :alt="current.title || 'Главный кадр'" />
          <div v-if="current?.url" class="hero-shade" />
          <div class="hero-copy">
            <p class="eyebrow">{{ feed?.tournament?.name || 'Студенческая лига' }}</p>
            <h1>{{ heroHeading(current) }}</h1>
            <p v-if="current?.caption">{{ current.caption }}</p>
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
      <div v-if="tape.length" class="sheet">
        <div class="league-head">
          <div>
            {{ feed?.tournament?.name || 'Матчи' }}
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
            <h2>{{ feed?.tournament?.name || 'Турнир ещё не открыт' }}</h2>
          </div>
          <EmptyState v-if="loaded && !feed?.standings?.length" title="Нет строк" text="Когда админ запустит турнир — таблица появится здесь." />
          <StandingTable v-else-if="feed?.standings?.length" :rows="feed.standings" compact />
        </div>

        <aside class="panel headlines">
          <h2>Бомбардиры</h2>
          <EmptyState v-if="loaded && !feed?.scorers?.length" title="Голов пока нет" />
          <RouterLink v-for="p in feed?.scorers" :key="p.playerId" class="headline" :to="`/players/${p.playerId}`">
            <strong>{{ p.displayName || 'Игрок' }}</strong>
            <span>{{ p.goals }} гол.</span>
          </RouterLink>
          <h2>Ассистенты</h2>
          <RouterLink v-for="p in feed?.assists" :key="'a-' + p.playerId" class="headline" :to="`/players/${p.playerId}`">
            <strong>{{ p.displayName || 'Игрок' }}</strong>
            <span>{{ p.assists }}</span>
          </RouterLink>
        </aside>
      </div>

      <div class="panel stack moments">
        <div class="moments-head">
          <h2>Моменты</h2>
          <a v-if="feed?.vkAlbumUrl" :href="feed.vkAlbumUrl" target="_blank" rel="noreferrer">Все кадры →</a>
        </div>
        <EmptyState v-if="loaded && !feed?.photos?.length" title="Кадров ещё нет" text="Админ загружает фото во вкладке «Фото» — слот «Галерея»." />
        <div v-else class="photos">
          <figure v-for="photo in feed?.photos" :key="photo.id">
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
  border-radius: 18px;
  overflow: hidden;
  background: #001433;
}
.hero img { width: 100%; height: 280px; object-fit: cover; display: block; }
.hero-shade {
  position: absolute;
  inset: 0;
  background: linear-gradient(90deg, rgba(0, 20, 51, 0.82) 0%, rgba(0, 20, 51, 0.2) 70%);
}
.hero-copy {
  display: grid;
  gap: 0.55rem;
  padding: 1.25rem 1.5rem 1.35rem;
  color: #fff;
}
.hero.photo .hero-copy {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  max-width: 560px;
  padding: 1.2rem 1.5rem 1.35rem;
}
.hero-copy h1 { color: #fff; font-size: clamp(1.45rem, 3.2vw, 2.1rem); }
.hero-copy p { color: rgba(255,255,255,0.86); }
.hero-cta,
.hero-cta:hover {
  width: fit-content;
  border-radius: 999px;
  padding: 0.58rem 1.15rem;
  color: #fff;
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
.table-block { padding: 1.4rem 1.5rem; }
.headlines {
  display: grid;
  gap: 0.35rem;
  background: var(--navy);
  color: #fff;
  border: 0;
}
.headlines h2 { color: #fff; font-size: 1.15rem; margin: 0.35rem 0 0.2rem; }
.headlines :deep(.empty) {
  background: transparent;
  color: rgba(255,255,255,0.7);
  border-color: rgba(255,255,255,0.2);
}
.headlines :deep(.empty strong) { color: #fff; }
.headline {
  display: flex;
  justify-content: space-between;
  gap: 0.8rem;
  padding: 0.75rem 0;
  border-bottom: 1px solid rgba(255,255,255,0.12);
  text-decoration: none;
  color: #fff;
}
.headline:hover { color: var(--ice); }
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
  .hero-copy, .hero.photo .hero-copy { padding-inline: 1rem; }
  .stories-wrap { gap: 0.2rem; }
  .nudge { width: 28px; height: 28px; }
  .story { width: 72px; }
  .story img { width: 60px; height: 60px; }
  .hero img { height: 148px; }
  .hero-copy h1 { font-size: 1.25rem; }
  .hero-cta { display: none; }
  .blocks { padding-top: 0.75rem; gap: 0.7rem; }
  .table-block { padding: 0.85rem; }
}
</style>
