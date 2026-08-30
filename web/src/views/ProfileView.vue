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
import MatchRow from '../components/MatchRow.vue'

const auth = useAuthStore()
const fav = useFavorites()
const router = useRouter()
const pane = ref<'fav' | 'card'>('fav')
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
const avatarUrl = ref('')
const playerId = ref('')
const allMatches = ref<any[]>([])

const roles = computed(() => {
  const from = (auth.user?.roles ?? [])
    .filter((item) => item.status === 'APPROVED')
    .map((item) => item.role)
  return from.length ? from : (auth.user?.role ? [auth.user.role] : [])
})

const favTeams = computed(() => fav.teams.map((id) => ({ id, name: teams.fullName(id) })))
const favMatches = computed(() => allMatches.value.filter((m) => fav.hasMatch(m.id)))

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
  } catch {
    exists.value = false
  }
})

async function submit() {
  error.value = ''
  ok.value = ''
  pending.value = true
  try {
    await api.put('/players/me', {
      firstName: firstName.value,
      lastName: lastName.value,
      displayName: displayName.value || undefined,
      jerseyNumber: jerseyNumber.value || undefined,
      position: position.value || undefined,
      bio: bio.value || undefined,
    })
    await auth.refreshMe()
    exists.value = true
    ok.value = 'Профиль сохранён.'
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
    <div class="hero">
      <PlayerAvatar
        :src="avatarUrl || auth.user?.photoUrl"
        :name="displayName || `${firstName} ${lastName}` || auth.user?.email"
        :size="72"
      />
      <div>
        <p class="eyebrow">Профиль</p>
        <h1>{{ displayName || `${firstName} ${lastName}`.trim() || auth.user?.email }}</h1>
        <p class="chips">
          <span v-for="role in roles" :key="role" class="badge">{{ labelOf(roleLabel, role) }}</span>
        </p>
      </div>
    </div>

    <div class="shortcuts">
      <RouterLink v-if="playerId" class="tile" :to="`/players/${playerId}`">Карточка игрока</RouterLink>
      <RouterLink v-if="auth.canAccessMyTeam" class="tile" to="/my-team">Моя команда</RouterLink>
      <RouterLink v-if="auth.canOfficiate" class="tile" to="/referee">Пульт судьи</RouterLink>
      <RouterLink v-if="auth.canManageLeague" class="tile" to="/admin">Админка</RouterLink>
      <RouterLink class="tile phone" to="/players">Игроки</RouterLink>
      <RouterLink class="tile phone" to="/table?tab=scorers">Бомбардиры</RouterLink>
    </div>

    <div class="fs-tabs">
      <button type="button" :class="{ on: pane === 'fav' }" @click="pane = 'fav'">Избранное</button>
      <button type="button" :class="{ on: pane === 'card' }" @click="pane = 'card'">Анкета</button>
    </div>

    <template v-if="pane === 'fav'">
      <div class="sheet">
        <div class="league-head">Избранные команды</div>
        <p v-if="!favTeams.length" class="empty-line">Звезда на карточке команды — и она будет здесь.</p>
        <RouterLink v-for="team in favTeams" :key="team.id" class="fav" :to="`/teams/${team.id}`">
          {{ team.name }}
        </RouterLink>
      </div>

      <div class="sheet">
        <div class="league-head">Избранные матчи</div>
        <p v-if="!favMatches.length" class="empty-line">Отмечайте игры звездой в календаре.</p>
        <MatchRow
          v-for="m in favMatches"
          :key="m.id"
          :match="m"
          :home-name="teams.fullName(m.homeTeamId)"
          :away-name="teams.fullName(m.awayTeamId)"
        />
      </div>
    </template>

    <div v-else class="panel stack">
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
          {{ pending ? 'Сохраняем…' : exists ? 'Обновить' : 'Стать игроком' }}
        </button>
      </form>
    </div>

    <button class="btn secondary" type="button" @click="logout">Выйти</button>
  </section>
</template>

<style scoped>
.page { gap: 0.75rem; }
.hero {
  display: flex;
  align-items: center;
  gap: 0.9rem;
}
.hero h1 { font-size: clamp(1.3rem, 4vw, 1.8rem); }
.chips { display: flex; flex-wrap: wrap; gap: 0.35rem; margin-top: 0.35rem; }
.shortcuts {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 0.5rem;
}
.tile {
  background: #fff;
  border: 1px solid var(--line);
  border-radius: 12px;
  padding: 0.85rem 0.9rem;
  font-weight: 800;
  color: var(--navy);
}
.sheet {
  background: #fff;
  border: 1px solid var(--line);
  border-radius: 12px;
  overflow: hidden;
}
.empty-line { padding: 0.85rem; margin: 0; }
.fav {
  display: block;
  padding: 0.8rem 0.9rem;
  border-top: 1px solid var(--line);
  color: var(--navy);
  font-weight: 700;
}
.phone { display: none; }
@media (max-width: 719px) {
  .phone { display: block; }
}
</style>
