<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import api from '../api/client'
import EmptyState from '../components/EmptyState.vue'
import PlayerAvatar from '../components/PlayerAvatar.vue'

const items = ref<any[]>([])
const teams = ref<any[]>([])
const query = ref('')
const teamId = ref('')
const loading = ref(true)

async function load() {
  loading.value = true
  try {
    const { data } = await api.get('/players', {
      params: { size: 100, q: query.value || undefined, teamId: teamId.value || undefined },
    })
    items.value = data.content ?? []
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  const { data } = await api.get('/teams', { params: { size: 100 } })
  teams.value = data.content ?? []
  await load()
})
</script>

<template>
  <section class="stack">
    <div class="page-title">
      <h1>Игроки</h1>
      <p>Поиск по ФИО или команде. Карточка — статистика выступлений.</p>
    </div>
    <form class="filters" @submit.prevent="load">
      <label class="field">ФИО
        <input v-model="query" placeholder="Иванов" />
      </label>
      <label class="field">Команда
        <select v-model="teamId">
          <option value="">Все команды</option>
          <option v-for="t in teams" :key="t.id" :value="t.id">{{ t.name }}</option>
        </select>
      </label>
      <button class="btn" type="submit">Найти</button>
    </form>
    <div v-if="loading" class="grid cards">
      <div v-for="n in 4" :key="n" class="skeleton" />
    </div>
    <EmptyState v-else-if="!items.length" title="Никого не нашли" />
    <div v-else class="grid cards people">
      <RouterLink v-for="p in items" :key="p.id" class="panel card-link person" :to="`/players/${p.id}`">
        <PlayerAvatar
          :src="p.avatarUrl"
          :name="p.displayName || `${p.firstName} ${p.lastName}`"
          :size="42"
        />
        <div>
          <h2>{{ p.displayName || `${p.firstName} ${p.lastName}` }}</h2>
          <p>{{ p.position || 'Игрок' }} · №{{ p.jerseyNumber ?? '—' }}</p>
        </div>
      </RouterLink>
    </div>
  </section>
</template>

<style scoped>
.filters { display: grid; grid-template-columns: 1fr 1fr auto; gap: 0.7rem; align-items: end; }
.card-link { display: grid; gap: 0.3rem; justify-items: start; }
h2 { font-size: 1.2rem; margin: 0; }
@media (max-width: 760px) {
  .filters { grid-template-columns: 1fr; }
  .people { grid-template-columns: 1fr; }
  .person {
    display: grid;
    grid-template-columns: auto 1fr;
    gap: 0.7rem;
    align-items: center;
  }
}
</style>
