<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import api from '../api/client'
import { useTeamDirectory } from '../lib/useTeamDirectory'
import EmptyState from '../components/EmptyState.vue'

const feed = ref<any>(null)
const loaded = ref(false)
const teams = useTeamDirectory()

onMounted(async () => {
  try {
    await teams.load()
    const { data } = await api.get('/home')
    feed.value = data
  } catch {
    feed.value = null
  } finally {
    loaded.value = true
  }
})
</script>

<template>
  <section class="home">
    <div class="grid split">
      <div class="panel">
        <div class="page-title">
          <p class="eyebrow">Текущий турнир</p>
          <h2>{{ feed?.tournament?.name || 'Таблица ещё пустая' }}</h2>
        </div>
        <EmptyState v-if="loaded && !feed?.standings?.length" title="Нет строк" text="Когда админ запустит турнир и сыграют матчи — таблица появится здесь." />
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

      <div class="stack">
        <div class="panel">
          <h2>Топ-5 бомбардиров</h2>
          <EmptyState v-if="loaded && !feed?.scorers?.length" title="Голов пока нет" />
          <ol v-else class="leaders">
            <li v-for="p in feed?.scorers" :key="p.playerId">
              <RouterLink :to="`/players/${p.playerId}`">{{ p.displayName || 'Игрок' }}</RouterLink>
              <strong>{{ p.goals }}</strong>
            </li>
          </ol>
        </div>
        <div class="panel">
          <h2>Топ-5 ассистентов</h2>
          <EmptyState v-if="loaded && !feed?.assists?.length" title="Передач пока нет" />
          <ol v-else class="leaders">
            <li v-for="p in feed?.assists" :key="p.playerId">
              <RouterLink :to="`/players/${p.playerId}`">{{ p.displayName || 'Игрок' }}</RouterLink>
              <strong>{{ p.assists }}</strong>
            </li>
          </ol>
        </div>
      </div>
    </div>

    <div class="panel stack">
      <div class="page-title">
        <h2>Фото с матчей</h2>
        <p v-if="feed?.vkAlbumUrl" class="muted">
          Альбом ВК:
          <a :href="feed.vkAlbumUrl" target="_blank" rel="noreferrer">открыть</a>
        </p>
      </div>
      <EmptyState v-if="loaded && !feed?.photos?.length" title="Кадров ещё нет" text="Админ может добавить ссылки на фото или указать альбом ВК." />
      <div v-else class="photos">
        <figure v-for="photo in feed?.photos" :key="photo.id">
          <img :src="photo.url" :alt="photo.caption || 'Фото матча'" />
          <figcaption v-if="photo.caption">{{ photo.caption }}</figcaption>
        </figure>
      </div>
    </div>
  </section>
</template>

<style scoped>
.home { display: grid; gap: 1.35rem; }
.split { grid-template-columns: 1.4fr 0.8fr; align-items: start; }
.leaders { display: grid; gap: 0.55rem; padding-left: 1.1rem; }
.leaders li { display: flex; justify-content: space-between; gap: 0.8rem; }
.photos { display: grid; grid-template-columns: repeat(auto-fill, minmax(180px, 1fr)); gap: 0.8rem; }
.photos img { width: 100%; height: 140px; object-fit: cover; border-radius: 14px; }
@media (max-width: 860px) { .split { grid-template-columns: 1fr; } }
</style>
