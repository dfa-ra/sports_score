<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import api from '../api/client'
import { useAuthStore } from '../stores/auth'
import { initials } from '../lib/format'
import { apiError } from '../lib/errors'
import CopyChip from '../components/CopyChip.vue'
import EmptyState from '../components/EmptyState.vue'

const route = useRoute()
const auth = useAuthStore()
const team = ref<any>(null)
const members = ref<any[]>([])
const players = ref<any[]>([])
const me = ref<any>(null)
const name = ref('')
const shortName = ref('')
const playerId = ref('')
const error = ref('')
const ok = ref('')
const pending = ref(false)

const isCaptain = computed(() => me.value && team.value && me.value.id === team.value.captainId)
const canManage = computed(() => auth.canManageLeague || isCaptain.value)

onMounted(load)

async function load() {
  const id = route.params.id
  const [t, m] = await Promise.all([api.get(`/teams/${id}`), api.get(`/teams/${id}/members`)])
  team.value = t.data
  members.value = m.data
  name.value = t.data.name
  shortName.value = t.data.shortName || ''
  if (auth.isAuthenticated) {
    try {
      const { data } = await api.get('/players/me')
      me.value = data
    } catch {
      me.value = null
    }
    if (auth.canManageLeague || isCaptain.value) {
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
</script>

<template>
  <section v-if="team" class="stack">
    <div class="page-title">
      <span class="crest">{{ initials(team.shortName || team.name) }}</span>
      <h1>{{ team.name }}</h1>
      <p>{{ team.shortName || 'Команда без аббревиатуры, но с характером.' }}</p>
      <CopyChip :value="String(team.id)" label="Скопировать id команды" />
    </div>

    <div v-if="canManage" class="panel stack">
      <h2>Редактировать</h2>
      <form class="stack" @submit.prevent="saveTeam">
        <label class="field">Название<input v-model="name" required /></label>
        <label class="field">Короткое имя<input v-model="shortName" /></label>
        <button class="btn secondary" type="submit" :disabled="pending">Сохранить</button>
      </form>
    </div>

    <div class="panel stack">
      <h2>Состав</h2>
      <EmptyState v-if="!members.length" title="Раздевалка пуста" text="Капитан ещё собирает людей после пар." />
      <div v-for="m in members" :key="m.id" class="member">
        <RouterLink :to="`/players/${m.playerId}`">
          <strong>{{ m.displayName || `${m.playerFirstName} ${m.playerLastName}` }}</strong>
        </RouterLink>
        <span class="muted">№{{ m.jerseyNumber ?? '—' }}</span>
        <div v-if="canManage" class="actions">
          <button class="btn ghost" :disabled="pending || m.playerId === team.captainId" @click="makeCaptain(m.playerId)">Капитан</button>
          <button class="btn ghost" :disabled="pending || m.playerId === team.captainId" @click="removeMember(m.playerId)">Убрать</button>
        </div>
      </div>
      <form v-if="canManage" class="stack" @submit.prevent="addMember">
        <label class="field">Добавить игрока
          <select v-model="playerId" required>
            <option v-for="p in players" :key="p.id" :value="p.id">
              {{ p.displayName || `${p.firstName} ${p.lastName}` }}
            </option>
          </select>
        </label>
        <button class="btn" type="submit" :disabled="pending">Добавить в состав</button>
      </form>
      <p v-if="error" class="form-error">{{ error }}</p>
      <p v-if="ok" class="form-ok">{{ ok }}</p>
    </div>
  </section>
</template>

<style scoped>
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
