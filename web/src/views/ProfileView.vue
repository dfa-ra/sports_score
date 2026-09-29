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
const positionOptions = ['Вратарь', 'Защитник', 'Нападающий', 'Универсал']
const bio = ref('')
const pending = ref(false)
const passwordPending = ref(false)
const passwordNote = ref('')
const passwordError = ref('')
const error = ref('')
const ok = ref('')
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

const isPlayer = computed(() => roles.value.some((role) => role === 'PLAYER' || role === 'CAPTAIN' || role === 'ADMIN'))

function isUploadedPhoto(url?: string | null) {
  return !!url && url.includes('/media/')
}

function hasJersey(value: unknown) {
  if (value === '' || value == null) return false
  const number = typeof value === 'number' ? value : Number(value)
  return Number.isInteger(number) && number >= 0 && number <= 99
}

function openForm() {
  error.value = ''
  ok.value = ''
  editing.value = true
}

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
    playerId.value = data.id
    firstName.value = data.firstName || ''
    lastName.value = data.lastName || ''
    displayName.value = data.displayName || ''
    jerseyNumber.value = data.jerseyNumber
    position.value = data.position || ''
    if (!isPlayer.value && data.jerseyNumber == null && !data.bio && position.value === 'Нападающий') {
      position.value = ''
    }
    bio.value = data.bio || ''
    avatarUrl.value = data.avatarUrl || auth.user?.photoUrl || ''
    await loadCard()
  } catch {
    firstName.value = auth.user?.firstName || ''
    lastName.value = auth.user?.lastName || ''
    displayName.value = [firstName.value, lastName.value].filter(Boolean).join(' ')
    position.value = ''
    jerseyNumber.value = null
  }
})

async function submit() {
  error.value = ''
  ok.value = ''
  if (!isPlayer.value) {
    if (!firstName.value.trim() || !lastName.value.trim() || !displayName.value.trim()) {
      error.value = 'Укажите имя, фамилию и как писать на майке.'
      return
    }
    if (!hasJersey(jerseyNumber.value)) {
      error.value = 'Укажите номер на майке.'
      return
    }
    if (!position.value.trim()) {
      error.value = 'Выберите позицию.'
      return
    }
    if (!isUploadedPhoto(avatarUrl.value)) {
      error.value = 'Для регистрации игрока нужна фотография.'
      return
    }
  }
  pending.value = true
  try {
    const { data } = await api.put('/players/me', {
      firstName: firstName.value,
      lastName: lastName.value,
      displayName: displayName.value || undefined,
      jerseyNumber: hasJersey(jerseyNumber.value) ? Number(jerseyNumber.value) : undefined,
      position: position.value || undefined,
      bio: bio.value || undefined,
      avatarUrl: avatarUrl.value || undefined,
    })
    await auth.refreshMe()
    playerId.value = data.id
    avatarUrl.value = data.avatarUrl || avatarUrl.value
    ok.value = 'Профиль сохранён.'
    editing.value = false
    await loadCard()
  } catch (e: any) {
    error.value = apiError(e, 'Профиль не сохранился.')
  } finally {
    pending.value = false
  }
}

async function onPhoto(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  error.value = ''
  ok.value = ''
  pending.value = true
  try {
    const form = new FormData()
    form.append('file', file)
    const { data } = await api.post('/uploads/players/me/avatar', form)
    avatarUrl.value = data.url
    await auth.refreshMe()
    if (playerId.value) await loadCard()
    ok.value = 'Фото обновлено.'
  } catch (e: any) {
    error.value = apiError(e, 'Фото не загрузилось.')
  } finally {
    pending.value = false
    input.value = ''
  }
}

async function changePassword() {
  passwordError.value = ''
  passwordNote.value = ''
  passwordPending.value = true
  try {
    await auth.requestPasswordChangeEmail()
    passwordNote.value = 'Если почта настроена, ссылка придёт на ваш email.'
  } catch (e: any) {
    passwordError.value = apiError(e, 'Не удалось отправить ссылку.')
  } finally {
    passwordPending.value = false
  }
}

async function logout() {
  await auth.logout()
  router.push('/')
}
</script>

<template>
  <section class="stack page">
    <PlayerCardPanel v-if="isPlayer && card" :card="card" editable @edit="openForm" />

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
      <button v-if="isPlayer" class="pen" type="button" aria-label="Изменить" @click="openForm">✎</button>
    </div>

    <button v-if="!isPlayer" class="btn become" type="button" @click="openForm">Стать игроком</button>

    <Teleport to="body">
      <div v-if="editing" class="overlay" @click.self="editing = false" @keydown.esc="editing = false">
        <div class="sheet-form" role="dialog" aria-modal="true" aria-labelledby="profile-form-title">
          <h2 id="profile-form-title">{{ isPlayer ? 'Изменить анкету' : 'Стать игроком' }}</h2>
          <form class="stack" @submit.prevent="submit">
            <label class="field">Имя<input v-model="firstName" required maxlength="100" /></label>
            <label class="field">Фамилия<input v-model="lastName" required maxlength="100" /></label>
            <label class="field">Как писать на майке<input v-model="displayName" :required="!isPlayer" maxlength="150" /></label>
            <div class="photo-row">
              <PlayerAvatar
                :src="avatarUrl || auth.user?.photoUrl"
                :name="displayName || `${firstName} ${lastName}`"
                :size="56"
                tile
              />
              <label class="field grow">
                Фото
                <input type="file" accept="image/png,image/jpeg,image/webp,image/gif" :disabled="pending" @change="onPhoto" />
                <span class="field-hint">{{ isPlayer ? 'PNG, JPG, WebP или GIF. Сохраняется сразу.' : 'Нужна фотография. PNG, JPG, WebP или GIF.' }}</span>
              </label>
            </div>
            <label class="field">Номер<input v-model.number="jerseyNumber" type="number" min="0" max="99" :required="!isPlayer" /></label>
            <label class="field">Позиция
              <select v-model="position" :required="!isPlayer">
                <option value="">Выберите позицию</option>
                <option v-for="option in positionOptions" :key="option" :value="option">{{ option }}</option>
                <option v-if="position && !positionOptions.includes(position)" :value="position">{{ position }}</option>
              </select>
            </label>
            <label class="field">О себе<textarea v-model="bio" rows="3" /></label>
            <p v-if="error" class="form-error">{{ error }}</p>
            <p v-if="ok" class="form-ok">{{ ok }}</p>
            <button class="btn" type="submit" :disabled="pending">
              {{ pending ? 'Сохраняем…' : isPlayer ? 'Сохранить' : 'Стать игроком' }}
            </button>
            <button class="btn ghost" type="button" @click="editing = false">Закрыть</button>
          </form>
        </div>
      </div>
    </Teleport>

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
      <p v-if="!card?.team && !favTeams.length" class="empty-line">Избранных команд нет.</p>
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
      <RouterLink class="tile" to="/tournaments">Турниры</RouterLink>
      <RouterLink v-if="auth.canAccessMyTeam" class="tile" to="/my-team">Моя команда</RouterLink>
      <RouterLink v-if="auth.canOfficiate" class="tile" to="/referee">Пульт судьи</RouterLink>
      <RouterLink v-if="auth.canManageLeague" class="tile" to="/admin">Админка</RouterLink>
    </div>

    <button class="btn secondary" type="button" :disabled="passwordPending" @click="changePassword">
      {{ passwordPending ? 'Отправляем…' : 'Сменить пароль' }}
    </button>
    <p v-if="passwordNote" class="form-ok">{{ passwordNote }}</p>
    <p v-if="passwordError" class="form-error">{{ passwordError }}</p>
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
.become { width: 100%; }
.overlay {
  position: fixed;
  inset: 0;
  z-index: 80;
  display: grid;
  place-items: center;
  padding:
    max(0.75rem, env(safe-area-inset-top))
    0.75rem
    max(0.75rem, env(safe-area-inset-bottom));
  background: rgba(0, 32, 91, 0.55);
}
.photo-row {
  display: flex;
  align-items: flex-start;
  gap: 0.75rem;
}
.grow { flex: 1; min-width: 0; }
.sheet-form {
  width: min(520px, 100%);
  max-height: min(90dvh, 760px);
  overflow: auto;
  background: #fff;
  color: var(--text, #1c2430);
  border-radius: 20px;
  padding: 1.15rem 1.15rem 1.25rem;
  box-shadow: 0 24px 60px rgba(0, 32, 91, 0.28);
}
.sheet-form h2 {
  margin: 0 0 0.85rem;
  color: var(--navy, #00205b);
}
.sheet-form input[type="file"] {
  display: block;
  width: 100%;
  max-width: 100%;
  min-width: 0;
  font-size: 0.85rem;
}
@media (max-width: 560px) {
  .overlay {
    place-items: end center;
    padding: 0.5rem;
    padding-bottom: max(0.5rem, env(safe-area-inset-bottom));
  }
  .sheet-form {
    width: 100%;
    max-height: calc(100dvh - 0.75rem);
    border-radius: 18px 18px 12px 12px;
  }
  .photo-row { flex-direction: column; }
}
</style>
