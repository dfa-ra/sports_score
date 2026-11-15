<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import api from '../../api/client'
import MatchStageNote from '../../components/MatchStageNote.vue'
import { isForfeit } from '../../lib/forfeit'
import { refereeListGroups, refereeListScore, refereeListWhen, refereePenaltyCaption } from '../../lib/refereePad'
import { useTeamDirectory } from '../../lib/useTeamDirectory'

const matches = ref<any[]>([])
const teams = useTeamDirectory()
const loading = ref(true)
const error = ref(false)
const groups = computed(() => refereeListGroups(matches.value))

function teamName(id?: string | null, fallback?: string | null) {
  const known = id ? teams.fullName(id, '') : ''
  if (known && known !== 'Команда') return known
  return fallback || known || 'Команда'
}

async function load() {
  error.value = false
  try {
    await teams.load()
    const { data } = await api.get('/referee/matches')
    matches.value = Array.isArray(data) ? data : []
  } catch {
    error.value = true
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <section class="referee">
    <header class="hero">
      <h1>Пульт судьи</h1>
      <p>Выберите матч для ведения протокола</p>
    </header>

    <div v-if="loading && !matches.length" class="sheet boot" role="status" aria-busy="true" aria-label="Загрузка матчей">
      <span class="skeleton bar"></span>
      <span class="skeleton bar short"></span>
    </div>
    <div v-else-if="error && !matches.length" class="sheet state">
      <strong>Не удалось загрузить матчи</strong>
      <p>Уже показанные матчи сохраняются при повторе</p>
      <button class="btn" type="button" @click="load">Повторить</button>
    </div>
    <div v-else-if="!groups.length" class="sheet state">
      <strong>Нет назначенных матчей</strong>
      <p>Новые назначения появятся здесь</p>
    </div>
    <div v-else class="sheet">
      <p v-if="error" class="retry">
        Не удалось загрузить матчи
        <button class="btn" type="button" @click="load">Повторить</button>
      </p>
      <section v-for="group in groups" :key="group.id" class="group">
        <h2>{{ group.title }}</h2>
        <RouterLink
          v-for="match in group.rows"
          :key="match.id"
          class="row"
          :to="`/referee/matches/${match.id}`"
        >
          <span class="who">
            <strong>{{ teamName(match.homeTeamId, match.homeTeamName) }} – {{ teamName(match.awayTeamId, match.awayTeamName) }}</strong>
            <MatchStageNote :match="match" />
            <small>{{ refereeListWhen(match) }}</small>
            <small v-if="refereePenaltyCaption(match)" class="pens">{{ refereePenaltyCaption(match) }}</small>
          </span>
          <span class="score">
            {{ refereeListScore(match) }}
            <small v-if="isForfeit(match)">Техническое поражение</small>
          </span>
        </RouterLink>
      </section>
    </div>
  </section>
</template>

<style scoped>
.referee { min-width: 0; max-width: 100%; display: grid; gap: 16px; }
.hero {
  background: #001433;
  color: #fff;
  border-radius: 22px;
  padding: 1.15rem 1.2rem 1.2rem;
  display: grid;
  gap: 0.35rem;
}
.hero h1 { margin: 0; color: #fff; font-size: clamp(1.6rem, 4vw, 2rem); }
.hero p { margin: 0; color: #d5e6f5; }
.sheet {
  background: #fff;
  border-radius: 22px;
  min-width: 0;
  padding: 0.35rem 0 0.6rem;
}
.boot { display: grid; gap: 0.7rem; padding: 1rem; }
.bar { display: block; height: 16px; border-radius: 8px; }
.bar.short { width: 58%; }
.state { display: grid; justify-items: start; gap: 0.45rem; padding: 1.1rem 1rem 1.2rem; }
.state strong { color: #001433; font-size: 1.05rem; }
.group h2 {
  margin: 0;
  padding: 0.85rem 1rem 0.2rem;
  color: #8b97a8;
  font-size: 0.72rem;
  letter-spacing: 0.08em;
}
.row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 0.75rem;
  align-items: start;
  padding: 0.85rem 1rem;
  border-top: 1px solid rgba(0, 32, 91, 0.08);
  color: inherit;
  text-decoration: none;
  min-height: 44px;
}
.row:hover { text-decoration: none; background: #f7fbfe; }
.who { display: grid; gap: 0.15rem; min-width: 0; }
.who strong { color: #001433; font-size: 1rem; overflow-wrap: anywhere; }
.who small { color: #5c6d82; overflow-wrap: anywhere; }
.score {
  display: grid;
  justify-items: end;
  gap: 2px;
  color: #001433;
  font-weight: 800;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}
.score small {
  color: #e5735f;
  font-size: 11px;
  font-weight: 700;
  white-space: normal;
  text-align: right;
  max-width: 9rem;
}
.pens { color: #5c6d82; }
.retry { display: flex; flex-wrap: wrap; gap: 0.6rem; align-items: center; margin: 0; padding: 0.8rem 1rem 0; }
.referee :focus-visible { outline: 2px solid #4cb4e5; outline-offset: 2px; }
</style>
