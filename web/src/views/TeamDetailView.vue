<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import api from '../api/client'
import { useAuthStore } from '../stores/auth'
import { apiError } from '../lib/errors'
import { useTeamDirectory } from '../lib/useTeamDirectory'
import { useFavorites } from '../stores/favorites'
import AdminOnly from '../components/AdminOnly.vue'
import CopyChip from '../components/CopyChip.vue'
import EmptyState from '../components/EmptyState.vue'
import MatchRow from '../components/MatchRow.vue'
import PlayerPicker from '../components/PlayerPicker.vue'
import TeamCardEditor from '../components/TeamCardEditor.vue'
import TeamCrest from '../components/TeamCrest.vue'
import { registeredName } from '../lib/playerTitle'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const fav = useFavorites()
const names = useTeamDirectory()
const team = ref<any>(null)
const members = ref<any[]>([])
const matches = ref<any[]>([])
const tab = ref<'results' | 'calendar' | 'squad'>('results')
const played = computed(() => matches.value.filter((m) => m.status === 'FINISHED' || m.status === 'CANCELLED'))
const upcoming = computed(() => matches.value.filter((m) => m.status === 'SCHEDULED' || m.status === 'LIVE' || m.status === 'PAUSED'))
const me = ref<any>(null)
const memberIds = computed(() => members.value.map((item) => item.playerId))
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
  if (auth.isAuthenticated) {
    try {
      const { data } = await api.get('/players/me')
      me.value = data
    } catch {
      me.value = null
    }
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
  if (!team.value || id === team.value.captainId) return
  error.value = ''
  const previous = team.value.captainId
  const teamId = team.value.id
  team.value = { ...team.value, captainId: id }
  pending.value = true
  try {
    await api.put(`/teams/${teamId}/captain`, { playerId: id })
  } catch (e: any) {
    if (team.value) team.value = { ...team.value, captainId: previous }
    error.value = apiError(e)
    return
  } finally {
    pending.value = false
  }
  try {
    await auth.refreshMe()
    await load()
  } catch {
    // The captain already moved. A later refresh hiccup should not restore the old badge.
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

async function deleteTeam() {
  if (!confirm(`Удалить «${team.value.name}»? Это можно, только если у команды нет матчей.`)) return
  error.value = ''
  pending.value = true
  try {
    await api.delete(`/teams/${team.value.id}`, { params: { purge: true } })
    await router.push('/teams')
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
      <TeamCrest :src="team.logoUrl" :name="team.shortName || team.name" :size="48" />
      <div>
        <h1>{{ team.name }}</h1>
        <p v-if="team.disbanded">Команда расформирована. История матчей остаётся.</p>
        <p v-else-if="team.shortName || team.foundedOn">{{ team.shortName }}{{ team.shortName && team.foundedOn ? ' · ' : '' }}{{ team.foundedOn ? 'осн. ' + team.foundedOn : '' }}</p>
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
    </div>

    <div v-if="canManage && isCaptain" class="panel stack">
      <h2>Карточка</h2>
      <TeamCardEditor :team="team" @saved="load" />
    </div>

    <div v-if="tab === 'results'" class="sheet">
      <EmptyState v-if="!played.length" title="Сыгранных матчей пока нет" />
      <MatchRow
        v-for="m in played"
        :key="m.id"
        :match="m"
        :home-name="names.fullName(m.homeTeamId)"
        :away-name="names.fullName(m.awayTeamId)"
        :highlight-team-id="team.id"
      />
    </div>

    <div v-else-if="tab === 'calendar'" class="sheet">
      <EmptyState v-if="!upcoming.length" title="Ближайших матчей нет" />
      <MatchRow
        v-for="m in upcoming"
        :key="m.id"
        :match="m"
        :home-name="names.fullName(m.homeTeamId)"
        :away-name="names.fullName(m.awayTeamId)"
        :highlight-team-id="team.id"
      />
    </div>

    <div v-else class="panel stack">
      <h2>Состав</h2>
      <EmptyState v-if="!members.length" title="В составе никого нет" />
      <div v-for="m in members" :key="m.id" class="member">
        <div class="who">
          <RouterLink :to="`/players/${m.playerId}`">
            <strong>{{ registeredName(m) }}</strong>
          </RouterLink>
          <span v-if="m.playerId === team.captainId" class="captain-badge">Капитан</span>
        </div>
        <span class="muted jersey">№{{ m.jerseyNumber ?? '—' }}</span>
        <div v-if="canManage && isCaptain" class="actions">
          <button
            v-if="m.playerId !== team.captainId"
            type="button"
            class="make-captain"
            :disabled="pending"
            @click="makeCaptain(m.playerId)"
          >Сделать капитаном</button>
          <button class="btn ghost" :disabled="pending || m.playerId === team.captainId" @click="removeMember(m.playerId)">Убрать</button>
        </div>
      </div>
      <form v-if="canManage && isCaptain" class="stack" @submit.prevent="addMember">
        <label class="field">Добавить игрока
          <PlayerPicker v-model="playerId" :exclude-ids="memberIds" />
        </label>
        <button class="btn" type="submit" :disabled="pending || !playerId">Добавить в состав</button>
      </form>
      <p v-if="error && !auth.canManageLeague" class="form-error">{{ error }}</p>
      <p v-if="ok && !auth.canManageLeague" class="form-ok">{{ ok }}</p>
    </div>

    <AdminOnly v-if="auth.canManageLeague" title="Для админа">
      <p class="muted">Служебные действия. На публичной карточке их нет.</p>
      <CopyChip :value="String(team.id)" label="Скопировать id команды" />
      <TeamCardEditor v-if="!team.disbanded" :team="team" @saved="load" />
      <form v-if="!team.disbanded" class="stack" @submit.prevent="addMember">
        <label class="field">Добавить игрока
          <PlayerPicker v-model="playerId" :exclude-ids="memberIds" />
        </label>
        <button class="btn" type="submit" :disabled="pending || !playerId">Добавить в состав</button>
      </form>
      <div v-if="!team.disbanded && members.length" class="stack">
        <div v-for="m in members" :key="`admin-${m.id}`" class="member">
          <div class="who">
            <span>{{ registeredName(m) }}</span>
            <span v-if="m.playerId === team.captainId" class="captain-badge">Капитан</span>
          </div>
          <div class="actions">
            <button
              v-if="m.playerId !== team.captainId"
              type="button"
              class="make-captain"
              :disabled="pending"
              @click="makeCaptain(m.playerId)"
            >Сделать капитаном</button>
            <button class="btn ghost" :disabled="pending || m.playerId === team.captainId" @click="removeMember(m.playerId)">Убрать</button>
          </div>
        </div>
      </div>
      <button v-if="!team.disbanded" class="btn danger" :disabled="pending" @click="disbandTeam">Расформировать команду</button>
      <button v-else class="btn danger" :disabled="pending" @click="deleteTeam">Удалить команду</button>
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
h2 { font-size: 1.2rem; }
.member {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.4rem 0.75rem;
  padding: 0.75rem 0.1rem;
  border-bottom: 1px solid var(--line);
}
.who {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  flex: 1 1 9rem;
  min-width: 0;
}
.member a { color: var(--text-strong); text-decoration: none; }
.member a:hover { color: var(--accent); }
.jersey { font-variant-numeric: tabular-nums; }
.captain-badge {
  flex: 0 0 auto;
  padding: 0.08rem 0.42rem;
  border-radius: 999px;
  background: color-mix(in srgb, var(--ice) 18%, #fff);
  color: var(--navy);
  font-size: 0.68rem;
  font-weight: 700;
  line-height: 1.45;
  letter-spacing: 0.01em;
}
.actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: flex-end;
  gap: 0.35rem 0.8rem;
  margin-left: auto;
}
.make-captain {
  border: 0;
  background: none;
  margin: 0;
  padding: 0;
  font: inherit;
  font-size: 0.78rem;
  font-weight: 600;
  letter-spacing: 0.01em;
  color: color-mix(in srgb, var(--muted) 58%, #fff);
  cursor: pointer;
  white-space: nowrap;
}
.make-captain:hover,
.make-captain:focus-visible { color: var(--navy); }
.make-captain:disabled { cursor: wait; opacity: 0.45; }
@media (hover: hover) and (pointer: fine) and (min-width: 721px) {
  .make-captain {
    max-width: 0;
    overflow: hidden;
    opacity: 0;
  }
  .member:hover .make-captain,
  .member:focus-within .make-captain {
    max-width: 11rem;
    opacity: 1;
  }
}
</style>
