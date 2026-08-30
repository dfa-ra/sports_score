<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import api from '../api/client'
import EmptyState from '../components/EmptyState.vue'

type Slide = {
  id: string
  url?: string
  title?: string
  caption?: string
  linkUrl?: string
  linkLabel?: string
}

const feed = ref<any>(null)
const loaded = ref(false)
const slide = ref(0)
const storyRail = ref<HTMLElement | null>(null)
let timer: number | undefined

const heroes = computed<Slide[]>(() => feed.value?.heroes ?? [])
const stories = computed<Slide[]>(() => feed.value?.stories ?? [])
const current = computed(() => heroes.value[slide.value] || null)

onMounted(async () => {
  try {
    const { data } = await api.get('/home')
    feed.value = data
    start()
  } catch {
    feed.value = null
  } finally {
    loaded.value = true
  }
})

onUnmounted(() => {
  if (timer) window.clearInterval(timer)
})

function start() {
  if (timer) window.clearInterval(timer)
  if (heroes.value.length < 2) return
  timer = window.setInterval(() => {
    slide.value = (slide.value + 1) % heroes.value.length
  }, 6500)
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
</script>

<template>
  <section class="home">
    <div class="stage">
      <div class="container">
        <div v-if="stories.length" class="stories-wrap">
          <button class="nudge" type="button" aria-label="Сюжеты назад" @click="nudgeStories(-1)">‹</button>
          <div ref="storyRail" class="stories">
            <template v-for="item in stories" :key="item.id">
              <a
                v-if="isExternal(hrefOf(item))"
                class="story"
                :href="hrefOf(item)"
                target="_blank"
                rel="noreferrer"
              >
                <img :src="item.url" :alt="item.title || item.caption || 'Сюжет'" />
                <span>{{ item.title || item.caption || 'Сюжет' }}</span>
              </a>
              <RouterLink v-else class="story" :to="hrefOf(item)">
                <img :src="item.url" :alt="item.title || item.caption || 'Сюжет'" />
                <span>{{ item.title || item.caption || 'Сюжет' }}</span>
              </RouterLink>
            </template>
          </div>
          <button class="nudge" type="button" aria-label="Сюжеты вперёд" @click="nudgeStories(1)">›</button>
        </div>

        <div class="hero">
          <img v-if="current?.url" :src="current.url" :alt="current.title || 'Главный кадр'" />
          <div class="hero-shade" />
          <div class="hero-copy">
            <p class="eyebrow">{{ feed?.tournament?.name || 'Студенческая лига' }}</p>
            <h1>{{ current?.title || 'Живой сезон Kronbars' }}</h1>
            <p>{{ current?.caption || 'Таблица, календарь и статистика — как на большом сайте, только своя лига.' }}</p>
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
      <div class="hero-grid">
        <div class="panel table-block">
          <div class="page-title">
            <p class="eyebrow">Таблица</p>
            <h2>{{ feed?.tournament?.name || 'Турнир ещё не открыт' }}</h2>
          </div>
          <EmptyState v-if="loaded && !feed?.standings?.length" title="Нет строк" text="Когда админ запустит турнир — таблица появится здесь." />
          <table v-else-if="feed?.standings?.length" class="table">
            <thead>
              <tr><th>Команда</th><th>И</th><th>В</th><th>Н</th><th>П</th><th>О</th></tr>
            </thead>
            <tbody>
              <tr v-for="row in feed.standings" :key="row.teamId">
                <td><RouterLink :to="`/teams/${row.teamId}`">{{ row.teamName }}</RouterLink></td>
                <td>{{ row.played }}</td>
                <td>{{ row.wins }}</td>
                <td>{{ row.draws }}</td>
                <td>{{ row.losses }}</td>
                <td><strong>{{ row.points }}</strong></td>
              </tr>
            </tbody>
          </table>
        </div>

        <aside class="panel headlines">
          <h2>Лидеры</h2>
          <EmptyState v-if="loaded && !feed?.scorers?.length" title="Голов пока нет" />
          <RouterLink v-for="p in feed?.scorers" :key="p.playerId" class="headline" :to="`/players/${p.playerId}`">
            <strong>{{ p.displayName || 'Игрок' }}</strong>
            <span>{{ p.goals }} гол.</span>
          </RouterLink>
          <h2>Передачи</h2>
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
  </section>
</template>

<style scoped>
.stage {
  background:
    radial-gradient(720px 280px at 80% 0%, rgba(76, 180, 229, 0.22), transparent 60%),
    var(--navy);
  padding: 1.1rem 0 1.6rem;
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
  font-size: 0.72rem;
  text-align: center;
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
  min-height: 380px;
  border-radius: 18px;
  overflow: hidden;
  background: #001433;
}
.hero img { width: 100%; height: 420px; object-fit: cover; display: block; }
.hero-shade {
  position: absolute;
  inset: 0;
  background: linear-gradient(90deg, rgba(0, 20, 51, 0.82) 0%, rgba(0, 20, 51, 0.2) 70%);
}
.hero-copy {
  position: absolute;
  left: 2rem;
  bottom: 2.4rem;
  right: 2rem;
  max-width: 560px;
  color: #fff;
  display: grid;
  gap: 0.7rem;
}
.hero-copy h1 { color: #fff; font-size: clamp(1.7rem, 4vw, 2.6rem); }
.hero-copy p { color: rgba(255,255,255,0.86); }
.hero-cta { width: fit-content; border-radius: 999px; padding-inline: 1.35rem; }
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
@media (max-width: 860px) {
  .hero-grid { grid-template-columns: 1fr; }
  .hero-copy { left: 1rem; right: 1rem; }
  .stories-wrap { grid-template-columns: 1fr; }
  .nudge { display: none; }
}
</style>
