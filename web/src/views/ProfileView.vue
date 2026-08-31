<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { useFavorites } from '../stores/favorites'
import api from '../api/client'
import { apiError } from '../lib/errors'
import { labelOf, roleLabel } from '../lib/format'
import { useTeamDirectory } from '../lib/useTeamDirectory'
import PlayerAvatar from '../components/PlayerAvatar.vue'
import PlayerCardPanel from '../components/PlayerCardPanel.vue'
import MatchRow from '../components/MatchRow.vue'
import TeamCrest from '../components/TeamCrest.vue'

const auth = useAuthStore()
const fav = useFavorites()
const router = useRouter()
const teams = useTeamDirectory()
const firstName = ref('')
const lastName = ref('')
const displayName = ref('')
const jerseyNumber = ref<number | null>(null)
const position = ref('')
const bio = ref('')
const pending = ref(false)
const error = ref('')
const ok = ref('')
const exists = ref(false)
const editing = ref(false)
const avatarUrl = ref('')
const playerId = ref('')
const card = ref<any>(null)
const allMatches = ref<any[]>([])

const roles = computed(() => {
  const from = (auth.user?.roles ?? [])
    .filter((item) => item.status === 'APPROVED')
    .map((item) => item.role)
  return from.length ? from : (auth.user?.role ? [auth.user.role] : [])
})

const favTeams = computed(() => {
  const own = card.value?.team?.id
  return fav.teams
    .filter((id) => id !== own)
    .map((id) => ({ id, name: teams.fullName(id), logo: teams.logo(id) }))
})
const favMatches = computed(() => allMatches.value.filter((m) => fav.hasMatch(m.id)))

async function loadCard() {
  if (!playerId.value) {
    card.value = null
    return
  }
  try {
    const { data } = await api.get(`/players/${playerId.value}/card`)
    card.value = data
  } catch {
    card.value = null
  }
}

onMounted(async () => {
  await teams.load()
  try {
    const { data } = await api.get('/matches', { params: { size: 100 } })
    allMatches.value = data.content ?? []
  } catch {
    allMatches.value = []
  }
  try {
    const { data } = await api.get('/players/me')
    exists.value = true
    playerId.value = data.id
    firstName.value = data.firstName || ''
    lastName.value = data.lastName || ''
    displayName.value = data.displayName || ''
    jerseyNumber.value = data.jerseyNumber
    position.value = data.position || ''
    bio.value = data.bio || ''
    avatarUrl.value = data.avatarUrl || auth.user?.photoUrl || ''
    await loadCard()
  } catch {
    exists.value = false
  }
})

async function submit() {
  error.value = ''
  ok.value = ''
  pending.value = true
  try {
    const { data } = await api.put('/players/me', {
      firstName: firstName.value,
      lastName: lastName.value,
      displayName: displayName.value || undefined,
      jerseyNumber: jerseyNumber.value || undefined,
      position: position.value || undefined,
      bio: bio.value || undefined,
    })
    await auth.refreshMe()
    exists.value = true
    playerId.value = data.id
    ok.value = 'Профиль сохранён.'
    editing.value = false
    await loadCard()
  } catch (e: any) {
    error.value = apiError(e, 'Профиль не сохранился.')
  } finally {
    pending.value = false
  }
}

async function logout() {
  await auth.logout()
  router.push('/')
}
</script>

<template>
  <section class="stack page">
    <PlayerCardPanel v-if="card" :card="card" editable @edit="editing = true" />

    <div v-else class="identity">
      <PlayerAvatar
        :src="avatarUrl || auth.user?.photoUrl"
        :name="displayName || `${firstName} ${lastName}` || auth.user?.email"
        :size="76"
        tile
      />
      <div>
        <h1>{{ displayName || `${firstName} ${lastName}`.trim() || auth.user?.email }}</h1>
        <p class="chips">
          <span v-for="role in roles" :key="role" class="badge">{{ labelOf(roleLabel, role) }}</span>
        </p>
      </div>
      <button class="pen" type="button" aria-label="Изменить" @click="editing = true">✎</button>
    </div>

    <div v-if="editing" class="overlay" @click.self="editing = false">
      <div class="sheet-form" role="dialog" aria-modal="true">
        <h2>{{ exists ? 'Изменить анкету' : 'Стать игроком' }}</h2>
        <form class="stack" @submit.prevent="submit">
          <label class="field">Имя<input v-model="firstName" required maxlength="100" /></label>
          <label class="field">Фамилия<input v-model="lastName" required maxlength="100" /></label>
          <label class="field">Как писать на майке<input v-model="displayName" maxlength="150" /></label>
          <label class="field">Номер<input v-model.number="jerseyNumber" type="number" min="0" max="99" /></label>
          <label class="field">Позиция<input v-model="position" maxlength="64" placeholder="Нападающий" /></label>
          <label class="field">О себе<textarea v-model="bio" rows="3" /></label>
          <p v-if="error" class="form-error">{{ error }}</p>
          <p v-if="ok" class="form-ok">{{ ok }}</p>
          <button class="btn" type="submit" :disabled="pending">
            {{ pending ? 'Сохраняем…' : exists ? 'Сохранить' : 'Стать игроком' }}
          </button>
          <button class="btn ghost" type="button" @click="editing = false">Закрыть</button>
        </form>
      </div>
    </div>

    <div class="sheet">
      <div class="league-head">Команды</div>
      <RouterLink v-if="card?.team" class="fav" :to="`/teams/${card.team.id}`">
        <TeamCrest :src="card.team.logoUrl" :name="card.team.name" :size="22" />
        {{ card.team.name }}
      </RouterLink>
      <RouterLink v-for="team in favTeams" :key="team.id" class="fav" :to="`/teams/${team.id}`">
        <TeamCrest :src="team.logo" :name="team.name" :size="22" />
        {{ team.name }}
      </RouterLink>
      <p v-if="!card?.team && !favTeams.length" class="empty-line">Звезда на карточке команды — и она будет здесь.</p>
    </div>

    <div v-if="favMatches.length" class="sheet">
      <div class="league-head">Избранные матчи</div>
      <MatchRow
        v-for="m in favMatches"
        :key="m.id"
        :match="m"
        :home-name="teams.fullName(m.homeTeamId)"
        :away-name="teams.fullName(m.awayTeamId)"
      />
    </div>

    <div v-if="auth.canAccessMyTeam || auth.canOfficiate || auth.canManageLeague" class="shortcuts">
      <RouterLink v-if="auth.canAccessMyTeam" class="tile" to="/my-team">Моя команда</RouterLink>
      <RouterLink v-if="auth.canOfficiate" class="tile" to="/referee">Пульт судьи</RouterLink>
      <RouterLink v-if="auth.canManageLeague" class="tile" to="/admin">Админка</RouterLink>
    </div>

    <button class="btn secondary" type="button" @click="logout">Выйти</button>
  </section>
</template>

<style scoped>
.page { gap: 0.75rem; }
.identity {
  display: grid;
  grid-template-columns: auto 1fr auto;
  gap: 0.75rem;
  align-items: center;
}
.identity h1 { font-size: clamp(1.25rem, 5vw, 1.7rem); margin: 0; }
.chips { display: flex; flex-wrap: wrap; gap: 0.35rem; margin-top: 0.35rem; }
.pen {
  width: 40px;
  height: 40px;
  border: 1px solid var(--line);
  background: #fff;
  border-radius: 12px;
  color: var(--navy);
  font-size: 1.1rem;
  cursor: pointer;
}
.sheet {
  background: #fff;
  border: 1px solid var(--line);
  border-radius: 12px;
  overflow: hidden;
}
.empty-line { padding: 0.85rem; margin: 0; }
.fav {
  display: flex;
  align-items: center;
  gap: 0.55rem;
  padding: 0.8rem 0.9rem;
  border-top: 1px solid var(--line);
  color: var(--navy);
  font-weight: 700;
}
.shortcuts { display: grid; grid-template-columns: repeat(2, 1fr); gap: 0.5rem; }
.tile {
  background: #fff;
  border: 1px solid var(--line);
  border-radius: 12px;
  padding: 0.85rem 0.9rem;
  font-weight: 800;
  color: var(--navy);
}
.overlay {
  position: fixed;
  inset: 0;
  z-index: 40;
  display: grid;
  place-items: end center;
  padding: 0.75rem;
  background: rgba(0, 32, 91, 0.42);
}
.sheet-form {
  width: min(520px, 100%);
  max-height: min(82vh, 680px);
  overflow: auto;
  background: #fff;
  border-radius: 20px;
  padding: 1.1rem 1.15rem 1.2rem;
}
@media (min-width: 720px) {
  .overlay { place-items: center; }
}
</style>
