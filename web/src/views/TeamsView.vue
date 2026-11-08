<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import api from '../api/client'
import { useAuthStore } from '../stores/auth'
import CreateTeamForm from '../components/CreateTeamForm.vue'
import EmptyState from '../components/EmptyState.vue'
import TeamCrest from '../components/TeamCrest.vue'
import {
  canShowMore,
  initialTeamList,
  nextTeamPage,
  readTeamPage,
  reduceTeamList,
  teamListParams,
  teamSearchQuery,
} from '../lib/publicTeams'

const auth = useAuthStore()
const query = ref('')
const list = ref(initialTeamList())
const showForm = ref(false)
const captainNeedsTeam = ref(false)
const showMoreVisible = computed(() => canShowMore(list.value))
let ticket = 0
let timer: number | undefined

async function loadCaptain() {
  captainNeedsTeam.value = false
  if (!auth.hasRole('CAPTAIN') || auth.canManageLeague) return
  try {
    const { data } = await api.get('/teams/mine')
    captainNeedsTeam.value = !data?.id
  } catch {
    captainNeedsTeam.value = false
  }
}

async function send(mine: number, q: string, pageIndex: number, more: boolean) {
  if (mine !== ticket) return
  try {
    const { data } = await api.get('/teams', { params: teamListParams(q, pageIndex) })
    if (mine !== ticket || teamSearchQuery(query.value) !== q) return
    const parsed = readTeamPage(data)
    list.value = reduceTeamList(list.value, {
      type: 'success',
      query: q,
      page: pageIndex,
      rows: parsed.rows,
      total: parsed.total,
    })
  } catch {
    if (mine !== ticket || teamSearchQuery(query.value) !== q) return
    list.value = reduceTeamList(list.value, { type: 'failure', query: q, more })
  }
}

function begin(more: boolean) {
  if (timer) window.clearTimeout(timer)
  const q = teamSearchQuery(query.value)
  const pageIndex = nextTeamPage(list.value, more)
  const mine = ++ticket
  list.value = reduceTeamList(list.value, { type: 'start', query: q, more })
  const delay = !more && q ? 200 : 0
  timer = window.setTimeout(() => { void send(mine, q, pageIndex, more) }, delay)
}

function showMore() {
  if (list.value.pendingMore || list.value.status === 'loading') return
  if (!list.value.moreError && !showMoreVisible.value) return
  begin(true)
}

function retry() {
  begin(false)
}

function clearQuery() {
  query.value = ''
}

watch(query, () => begin(false))

onMounted(() => {
  void loadCaptain()
  begin(false)
})
</script>

<template>
  <section class="teams stack" :data-team-state="list.status" :aria-busy="list.status === 'loading'">
    <div class="page-title">
      <h1>Команды</h1>
    </div>
    <label class="field search">
      Название команды
      <span class="control">
        <input
          v-model="query"
          type="search"
          autocomplete="off"
          aria-label="Поиск команды"
          placeholder="Название команды"
        />
        <button v-if="query" class="clear" type="button" aria-label="Очистить" @click="clearQuery">×</button>
      </span>
    </label>
    <p v-if="captainNeedsTeam" class="muted">Команду назначает администратор</p>
    <div v-if="auth.canManageLeague" class="toolbar">
      <button class="btn" type="button" @click="showForm = !showForm">{{ showForm ? 'Скрыть форму' : 'Создать команду' }}</button>
    </div>
    <div v-if="showForm && auth.canManageLeague" class="panel">
      <CreateTeamForm @created="retry" />
    </div>

    <div v-if="list.status === 'loading'" class="grid cards" aria-hidden="true">
      <div v-for="n in 4" :key="n" class="skeleton" />
    </div>
    <div v-else-if="list.status === 'error'" class="empty">
      <strong>Команды не загрузились</strong>
      <button class="btn" type="button" @click="retry">Повторить</button>
    </div>
    <EmptyState
      v-else-if="list.status === 'empty'"
      :title="list.query ? 'Команд с таким названием нет' : 'Команд нет'"
    />
    <template v-else>
      <p v-if="list.query && list.total != null" class="found">Найдено · {{ list.total }}</p>
      <div class="grid cards">
        <RouterLink v-for="t in list.items" :key="t.id" class="panel card-link" :to="`/teams/${t.id}`">
          <TeamCrest :src="t.logoUrl" :name="t.shortName || t.name" :size="42" />
          <h2>{{ t.name }}</h2>
          <p v-if="t.shortName">{{ t.shortName }}</p>
        </RouterLink>
      </div>
      <div v-if="list.moreError" class="more-error">
        <p>Следующая страница не загрузилась</p>
        <button class="btn" type="button" @click="showMore">Повторить</button>
      </div>
      <button v-else-if="showMoreVisible" class="btn secondary" type="button" @click="showMore">Показать ещё</button>
    </template>
  </section>
</template>

<style scoped>
.teams { min-width: 0; max-width: 100%; }
.toolbar { display: flex; flex-wrap: wrap; gap: 0.6rem; }
.search { min-width: 0; max-width: 100%; }
.control { position: relative; display: block; min-width: 0; max-width: 100%; }
.control input { width: 100%; min-width: 0; max-width: 100%; padding-right: 2.7rem; }
.control input::-webkit-search-cancel-button { display: none; }
.clear {
  position: absolute;
  top: 50%;
  right: 0.35rem;
  transform: translateY(-50%);
  width: 2rem;
  height: 2rem;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: var(--muted);
  cursor: pointer;
  font-size: 1.2rem;
  line-height: 1;
}
.teams .cards {
  min-width: 0;
  grid-template-columns: repeat(auto-fill, minmax(min(100%, 240px), 1fr));
}
.card-link { display: grid; gap: 0.35rem; justify-items: start; min-width: 0; max-width: 100%; }
.card-link h2, .card-link p { max-width: 100%; overflow-wrap: anywhere; }
h2 { font-size: 1.2rem; }
.found { font-variant-numeric: tabular-nums; }
.more-error { display: grid; justify-items: start; gap: 0.55rem; }
.empty { display: grid; justify-items: center; gap: 0.75rem; }
</style>
