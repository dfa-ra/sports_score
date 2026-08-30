<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import api from '../api/client'
import { useAuthStore } from '../stores/auth'
import { initials } from '../lib/format'
import { apiError } from '../lib/errors'
import { useTeamDirectory } from '../lib/useTeamDirectory'
import { useFavorites } from '../stores/favorites'
import AdminOnly from '../components/AdminOnly.vue'
import CopyChip from '../components/CopyChip.vue'
import EmptyState from '../components/EmptyState.vue'
import MatchRow from '../components/MatchRow.vue'

const route = useRoute()
const auth = useAuthStore()
const fav = useFavorites()
const names = useTeamDirectory()
const team = ref<any>(null)
const members = ref<any[]>([])
const matches = ref<any[]>([])
const tab = ref<'results' | 'calendar' | 'squad'>('results')
const played = computed(() => matches.value.filter((m) => m.status === 'FINISHED' || m.status === 'CANCELLED'))
const upcoming = computed(() => matches.value.filter((m) => m.status === 'SCHEDULED' || m.status === 'LIVE' || m.status === 'PAUSED'))
const players = ref<any[]>([])
const me = ref<any>(null)
const name = ref('')
const shortName = ref('')
const playerId = ref('')
const error = ref('')
const ok = ref('')
const pending = ref(false)

const isCaptain = computed(() => me.value && team.value && me.value.id === team.value.captainId)
const canManage = computed(() => !team.value?.disbanded && (auth.canManageLeague || isCaptain.value))

onMounted(load)

async function load() {
  const id = route.params.id
  await names.load()
  const [t, m, games] = await Promise.all([
    api.get(`/teams/${id}`),
    api.get(`/teams/${id}/members`),
    api.get('/matches', { params: { size: 100, sort: 'scheduledAt,desc' } }),
  ])
  team.value = t.data
  members.value = m.data
  matches.value = (games.data.content ?? []).filter((row: any) =>
    row.homeTeamId === t.data.id || row.awayTeamId === t.data.id
  )
  name.value = t.data.name
  shortName.value = t.data.shortName || ''
  if (auth.isAuthenticated) {
    try {
      const { data } = await api.get('/players/me')
      me.value = data
    } catch {
      me.value = null
    }
    if (canManage.value) {
      const { data } = await api.get('/players', { params: { size: 100 } })
      players.value = data.content ?? []
      if (!playerId.value && players.value[0]) playerId.value = players.value[0].id
    }
  }
}

async function saveTeam() {
  error.value = ''
  ok.value = ''
  pending.value = true
  try {
    await api.put(`/teams/${team.value.id}`, { name: name.value, shortName: shortName.value || undefined })
    ok.value = 'Команда обновлена.'
    await load()
  } catch (e: any) {
    error.value = apiError(e)
  } finally {
    pending.value = false
  }
}

async function addMember() {
  error.value = ''
  ok.value = ''
  pending.value = true
  try {
    await api.post(`/teams/${team.value.id}/members`, { playerId: playerId.value })
    ok.value = 'Игрок в составе.'
    await load()
  } catch (e: any) {
    error.value = apiError(e)
  } finally {
    pending.value = false
  }
}

async function removeMember(id: string) {
  error.value = ''
  pending.value = true
  try {
    await api.delete(`/teams/${team.value.id}/members/${id}`)
    await load()
  } catch (e: any) {
    error.value = apiError(e)
  } finally {
    pending.value = false
  }
}

async function makeCaptain(id: string) {
  error.value = ''
  pending.value = true
  try {
    await api.put(`/teams/${team.value.id}/captain`, { playerId: id })
    await auth.refreshMe()
    await load()
  } catch (e: any) {
    error.value = apiError(e)
  } finally {
    pending.value = false
  }
}

async function disbandTeam() {
  if (!confirm(`Расформировать «${team.value.name}»? Состав снимут, заявки на турниры снимут.`)) return
  error.value = ''
  pending.value = true
  try {
    await api.delete(`/teams/${team.value.id}`)
    ok.value = 'Команда расформирована.'
    await load()
  } catch (e: any) {
    error.value = apiError(e)
  } finally {
    pending.value = false
  }
}
</script>

<template>
  <section v-if="team" class="stack">
    <div class="page-title team-head">
      <span class="crest">{{ initials(team.shortName || team.name) }}</span>
      <div>
        <h1>{{ team.name }}</h1>
        <p v-if="team.disbanded">Команда расформирована. История матчей остаётся.</p>
        <p v-else>{{ team.shortName || 'Команда без аббревиатуры' }}{{ team.foundedOn ? ' · осн. ' + team.foundedOn : '' }}</p>
      </div>
      <button
        class="star"
        type="button"
        :class="{ on: fav.hasTeam(team.id) }"
        @click="fav.toggleTeam(team.id)"
      >★</button>
    </div>

    <div class="fs-tabs">
      <button type="button" :class="{ on: tab === 'results' }" @click="tab = 'results'">Результаты</button>
      <button type="button" :class="{ on: tab === 'calendar' }" @click="tab = 'calendar'">Календарь</button>
      <button type="button" :class="{ on: tab === 'squad' }" @click="tab = 'squad'">Состав</button>
      <RouterLink to="/table">Таблица</RouterLink>
    </div>

    <div v-if="canManage && isCaptain" class="panel stack">
      <h2>Редактировать</h2>
      <form class="stack" @submit.prevent="saveTeam">
        <label class="field">Название<input v-model="name" required /></label>
        <label class="field">Короткое имя<input v-model="shortName" /></label>
        <button class="btn secondary" type="submit" :disabled="pending">Сохранить</button>
      </form>
    </div>

    <div v-if="tab === 'results'" class="sheet">
      <EmptyState v-if="!played.length" title="Сыгранных матчей пока нет" />
      <MatchRow
        v-for="m in played"
        :key="m.id"
        :match="m"
        :home-name="names.name(m.homeTeamId)"
        :away-name="names.name(m.awayTeamId)"
        :highlight-team-id="team.id"
      />
    </div>

    <div v-else-if="tab === 'calendar'" class="sheet">
      <EmptyState v-if="!upcoming.length" title="Ближайших матчей нет" />
      <MatchRow
        v-for="m in upcoming"
        :key="m.id"
        :match="m"
        :home-name="names.name(m.homeTeamId)"
        :away-name="names.name(m.awayTeamId)"
        :highlight-team-id="team.id"
      />
    </div>

    <div v-else class="panel stack">
      <h2>Состав</h2>
      <EmptyState v-if="!members.length" title="Раздевалка пуста" text="Капитан ещё собирает людей после пар." />
      <div v-for="m in members" :key="m.id" class="member">
        <RouterLink :to="`/players/${m.playerId}`">
          <strong>{{ m.displayName || `${m.playerFirstName} ${m.playerLastName}` }}</strong>
        </RouterLink>
        <span class="muted">№{{ m.jerseyNumber ?? '—' }}</span>
        <div v-if="canManage && isCaptain" class="actions">
          <button class="btn ghost" :disabled="pending || m.playerId === team.captainId" @click="makeCaptain(m.playerId)">Капитан</button>
          <button class="btn ghost" :disabled="pending || m.playerId === team.captainId" @click="removeMember(m.playerId)">Убрать</button>
        </div>
      </div>
      <form v-if="canManage && isCaptain" class="stack" @submit.prevent="addMember">
        <label class="field">Добавить игрока
          <select v-model="playerId" required>
            <option v-for="p in players" :key="p.id" :value="p.id">
              {{ p.displayName || `${p.firstName} ${p.lastName}` }}
            </option>
          </select>
        </label>
        <button class="btn" type="submit" :disabled="pending">Добавить в состав</button>
      </form>
      <p v-if="error && !auth.canManageLeague" class="form-error">{{ error }}</p>
      <p v-if="ok && !auth.canManageLeague" class="form-ok">{{ ok }}</p>
    </div>

    <AdminOnly v-if="auth.canManageLeague" title="Для админа">
      <p class="muted">Служебные действия. На публичной карточке их нет.</p>
      <CopyChip :value="String(team.id)" label="Скопировать id команды" />
      <form v-if="!team.disbanded" class="stack" @submit.prevent="saveTeam">
        <label class="field">Название<input v-model="name" required /></label>
        <label class="field">Короткое имя<input v-model="shortName" /></label>
        <button class="btn secondary" type="submit" :disabled="pending">Сохранить карточку</button>
      </form>
      <form v-if="!team.disbanded" class="stack" @submit.prevent="addMember">
        <label class="field">Добавить игрока
          <select v-model="playerId" required>
            <option v-for="p in players" :key="p.id" :value="p.id">
              {{ p.displayName || `${p.firstName} ${p.lastName}` }}
            </option>
          </select>
        </label>
        <button class="btn" type="submit" :disabled="pending">Добавить в состав</button>
      </form>
      <div v-if="!team.disbanded && members.length" class="stack">
        <div v-for="m in members" :key="`admin-${m.id}`" class="member">
          <span>{{ m.displayName || `${m.playerFirstName} ${m.playerLastName}` }}</span>
          <div class="actions">
            <button class="btn ghost" :disabled="pending || m.playerId === team.captainId" @click="makeCaptain(m.playerId)">Капитан</button>
            <button class="btn ghost" :disabled="pending || m.playerId === team.captainId" @click="removeMember(m.playerId)">Убрать</button>
          </div>
        </div>
      </div>
      <button v-if="!team.disbanded" class="btn danger" :disabled="pending" @click="disbandTeam">Расформировать команду</button>
      <p v-else class="muted">Уже расформирована — править состав нельзя.</p>
      <p v-if="error" class="form-error">{{ error }}</p>
      <p v-if="ok" class="form-ok">{{ ok }}</p>
    </AdminOnly>
  </section>
</template>

<style scoped>
.team-head {
  display: grid;
  grid-template-columns: auto 1fr auto;
  align-items: center;
  gap: 0.8rem;
}
.star {
  border: 0;
  background: transparent;
  color: #c5ced8;
  font-size: 1.5rem;
  cursor: pointer;
}
.star.on { color: var(--ice); }
.sheet {
  background: #fff;
  border: 1px solid var(--line);
  border-radius: 12px;
  overflow: hidden;
}
.crest {
  width: 48px;
  height: 48px;
  display: grid;
  place-items: center;
  border-radius: 16px 12px 14px 11px;
  background: var(--accent-soft);
  color: var(--accent);
  font-weight: 800;
  transform: rotate(-4deg);
}
h2 { font-size: 1.2rem; }
.member {
  display: grid;
  grid-template-columns: 1fr auto auto;
  gap: 0.8rem;
  align-items: center;
  padding: 0.75rem 0.1rem;
  border-bottom: 1px solid var(--line);
}
.member a { color: var(--text-strong); text-decoration: none; }
.member a:hover { color: var(--accent); }
.actions { display: flex; gap: 0.3rem; }
</style>
