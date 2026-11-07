<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { useFavorites } from '../stores/favorites'
import api from '../api/client'
import { apiError } from '../lib/errors'
import { labelOf, outcomeMark, roleLabel, formatMatchDay } from '../lib/format'
import { DATE_PENDING, matchInstant } from '../lib/match'
import { calendarScore, playerStatRows, showsPlayerHistory } from '../lib/profileMyTeam'
import { useTeamDirectory } from '../lib/useTeamDirectory'
import PlayerAvatar from '../components/PlayerAvatar.vue'
import TeamCrest from '../components/TeamCrest.vue'
import MatchRow from '../components/MatchRow.vue'

const auth = useAuthStore()
const fav = useFavorites()
const router = useRouter()
const teams = useTeamDirectory()
const firstName = ref('')
const lastName = ref('')
const displayName = ref('')
const accountName = computed(() => {
  const legal = [lastName.value, firstName.value].map((part) => part.trim()).filter(Boolean).join(' ')
  return legal || auth.user?.email || ''
})
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
const fieldError = ref<Record<string, string>>({})
const editing = ref(false)
const avatarUrl = ref('')
const playerId = ref('')
const card = ref<any>(null)
const allMatches = ref<any[]>([])
const profileState = ref<'loading' | 'ready'>('loading')
const cardState = ref<'idle' | 'loading' | 'ready' | 'error'>('idle')
const matchState = ref<'loading' | 'ready' | 'error'>('loading')
const tab = ref<'games' | 'stats'>('games')
let profileTicket = 0
let cardTicket = 0
let matchTicket = 0
let onEscape: ((event: KeyboardEvent) => void) | null = null

const approvedRoles = computed(() => {
  const from = (auth.user?.roles ?? [])
    .filter((item) => item.status === 'APPROVED')
    .map((item) => item.role)
  return from.length ? from : (auth.user?.role ? [auth.user.role] : [])
})

const pendingRoles = computed(() =>
  (auth.user?.roles ?? [])
    .filter((item) => item.status === 'PENDING')
    .map((item) => item.role),
)

const applicationOpen = computed(() =>
  pendingRoles.value.some((role) => role === 'PLAYER' || role === 'CAPTAIN' || role === 'REFEREE'),
)

const badgeRoles = computed(() => {
  if (!pendingRoles.value.length) return approvedRoles.value
  return approvedRoles.value.filter((role) => role !== 'FAN')
})

const isPlayer = computed(() => approvedRoles.value.some((role) => role === 'PLAYER' || role === 'CAPTAIN' || role === 'ADMIN'))
const historyVisible = computed(() => showsPlayerHistory(approvedRoles.value, !!playerId.value))
const roleLine = computed(() => badgeRoles.value.map((role) => labelOf(roleLabel, role)).join(' · '))
const teamName = computed(() => card.value?.team?.name || '')
const stats = computed(() => playerStatRows(card.value?.statistics))
const history = computed(() => {
  const rows = Array.isArray(card.value?.matchHistory) ? [...card.value.matchHistory] : []
  return rows.sort((a, b) => String(matchInstant(b) || '').localeCompare(String(matchInstant(a) || '')))
})

const workLinks = computed(() => {
  const links: { to: string; label: string }[] = []
  const gated = auth.canAccessMyTeam || auth.canOfficiate || auth.canAnalyze || auth.canManageLeague
  if (auth.canAccessMyTeam) links.push({ to: '/my-team', label: 'Моя команда' })
  if (gated) links.push({ to: '/tournaments', label: 'Турниры' })
  if (auth.canOfficiate) links.push({ to: '/referee', label: 'Пульт судьи' })
  if (auth.canAnalyze) links.push({ to: '/analyst', label: 'Пульт аналитика' })
  if (auth.canManageLeague) links.push({ to: '/admin', label: 'Админка' })
  return links
})
const myTeamLink = computed(() => workLinks.value.find((item) => item.to === '/my-team') || null)
const otherLinks = computed(() => workLinks.value.filter((item) => item.to !== '/my-team'))

const favTeams = computed(() => {
  const own = card.value?.team?.id
  return fav.teams
    .filter((id) => id !== own)
    .map((id) => ({ id, name: teams.fullName(id), logo: teams.logo(id) }))
})
const favMatches = computed(() => allMatches.value.filter((match) => fav.hasMatch(match.id)))
const teamsTitle = computed(() => (historyVisible.value || card.value?.team ? 'Команды' : 'Избранные команды'))

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
  fieldError.value = {}
  editing.value = true
}

function rowWhen(row: { scheduledAt?: string | null; startedAt?: string | null; status?: string | null }) {
  const instant = matchInstant(row)
  if (!instant) return DATE_PENDING
  return formatMatchDay(instant)
}

function applyFieldDetails(caught: any) {
  const details = caught?.response?.data?.details
  if (!Array.isArray(details)) return false
  const next: Record<string, string> = {}
  for (const item of details) {
    if (typeof item?.field === 'string' && typeof item?.message === 'string' && item.message) {
      next[item.field] = item.message
    }
  }
  if (!Object.keys(next).length) return false
  fieldError.value = next
  return true
}

function validateForm() {
  const next: Record<string, string> = {}
  if (!firstName.value.trim()) next.firstName = 'Укажите имя.'
  if (!lastName.value.trim()) next.lastName = 'Укажите фамилию.'
  if (!isPlayer.value) {
    if (!displayName.value.trim()) next.displayName = 'Укажите, как писать на майке.'
    if (!hasJersey(jerseyNumber.value)) next.jerseyNumber = 'Укажите номер на майке.'
    if (!position.value.trim()) next.position = 'Выберите позицию.'
    if (!isUploadedPhoto(avatarUrl.value)) next.avatar = 'Для регистрации игрока нужна фотография.'
  }
  fieldError.value = next
  return Object.keys(next).length === 0
}

function forgetField(key: string) {
  if (!fieldError.value[key]) return
  const next = { ...fieldError.value }
  delete next[key]
  fieldError.value = next
}

async function loadCard() {
  if (!playerId.value) {
    card.value = null
    cardState.value = historyVisible.value ? 'error' : 'idle'
    return
  }
  const mine = ++cardTicket
  cardState.value = 'loading'
  try {
    const { data } = await api.get(`/players/${playerId.value}/card`)
    if (mine !== cardTicket) return
    card.value = data
    cardState.value = 'ready'
  } catch {
    if (mine !== cardTicket) return
    cardState.value = 'error'
  }
}

async function loadProfile() {
  const mine = ++profileTicket
  profileState.value = 'loading'
  if (historyVisible.value) cardState.value = 'loading'
  try {
    const { data } = await api.get('/players/me')
    if (mine !== profileTicket) return
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
    profileState.value = 'ready'
    await loadCard()
  } catch {
    if (mine !== profileTicket) return
    firstName.value = auth.user?.firstName || ''
    lastName.value = auth.user?.lastName || ''
    displayName.value = [lastName.value, firstName.value].filter(Boolean).join(' ')
    position.value = ''
    jerseyNumber.value = null
    profileState.value = 'ready'
    cardState.value = historyVisible.value ? 'error' : 'idle'
  }
}

async function loadMatches() {
  const mine = ++matchTicket
  matchState.value = 'loading'
  try {
    const { data } = await api.get('/matches', { params: { size: 100 } })
    if (mine !== matchTicket) return
    allMatches.value = data.content ?? []
    matchState.value = 'ready'
  } catch {
    if (mine !== matchTicket) return
    matchState.value = 'error'
  }
}

function retryHistory() {
  if (playerId.value) void loadCard()
  else void loadProfile()
}

onMounted(() => {
  void teams.load()
  void loadMatches()
  void loadProfile()
})

watch(editing, (open) => {
  if (onEscape) window.removeEventListener('keydown', onEscape)
  onEscape = null
  if (!open) return
  onEscape = (event: KeyboardEvent) => {
    if (event.key === 'Escape') editing.value = false
  }
  window.addEventListener('keydown', onEscape)
})

onUnmounted(() => {
  if (onEscape) window.removeEventListener('keydown', onEscape)
})

async function submit() {
  if (pending.value) return
  error.value = ''
  ok.value = ''
  if (!isPlayer.value && applicationOpen.value) {
    error.value = 'Заявка уже на рассмотрении.'
    return
  }
  if (!validateForm()) return
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
  } catch (caught: any) {
    if (!applyFieldDetails(caught)) error.value = apiError(caught, 'Профиль не сохранился.')
  } finally {
    pending.value = false
  }
}

async function onPhoto(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file || pending.value) return
  error.value = ''
  ok.value = ''
  forgetField('avatar')
  pending.value = true
  try {
    const form = new FormData()
    form.append('file', file)
    const { data } = await api.post('/uploads/players/me/avatar', form)
    avatarUrl.value = data.url
    await auth.refreshMe()
    if (playerId.value) await loadCard()
    ok.value = 'Фото обновлено.'
  } catch (caught: any) {
    fieldError.value = { ...fieldError.value, avatar: apiError(caught, 'Фото не загрузилось.') }
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
  } catch (caught: any) {
    passwordError.value = apiError(caught, 'Не удалось отправить ссылку.')
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
  <section class="profile">
    <header class="hero">
      <PlayerAvatar
        :src="avatarUrl || auth.user?.photoUrl"
        :name="accountName"
        :first-name="firstName"
        :last-name="lastName"
        :size="72"
      />
      <div class="who">
        <h1>{{ accountName || 'Профиль' }}</h1>
        <p v-if="roleLine || teamName" class="sub">
          <span v-if="roleLine">{{ roleLine }}</span>
          <span v-if="roleLine && teamName"> · </span>
          <span v-if="teamName">{{ teamName }}</span>
        </p>
        <p v-if="historyVisible" class="kicker">Личный профиль</p>
        <p v-for="role in pendingRoles" :key="role" class="pending">
          Заявка на роль «{{ labelOf(roleLabel, role) }}» на рассмотрении
        </p>
        <button v-if="!isPlayer && !applicationOpen" class="btn cta" type="button" @click="openForm">Стать игроком</button>
      </div>
      <button v-if="isPlayer" class="btn edit" type="button" @click="openForm">Редактировать</button>
    </header>

    <div class="aside">
      <nav v-if="myTeamLink" class="card jump" data-section="jump" :aria-label="myTeamLink.label">
        <RouterLink :to="myTeamLink.to">
          <span>Моя команда</span>
          <strong>{{ teamName || myTeamLink.label }}</strong>
          <i aria-hidden="true"></i>
        </RouterLink>
      </nav>
      <nav v-if="otherLinks.length" class="card sections" data-section="rest" aria-label="Мои разделы">
        <h2>Мои разделы</h2>
        <RouterLink v-for="link in otherLinks" :key="link.to" :to="link.to">{{ link.label }}</RouterLink>
        <p v-if="roleLine" class="rights">Права: {{ roleLine }}</p>
      </nav>
      <section class="card account" data-section="account">
        <h2>Аккаунт</h2>
        <button class="btn secondary wide" type="button" :disabled="passwordPending" @click="changePassword">
          {{ passwordPending ? 'Отправляем…' : 'Сменить пароль' }}
        </button>
        <p v-if="passwordNote" class="form-ok">{{ passwordNote }}</p>
        <p v-if="passwordError" class="form-error">{{ passwordError }}</p>
        <button class="btn secondary wide" type="button" @click="logout">Выйти</button>
      </section>
    </div>

    <div class="content">
      <section v-if="historyVisible" class="card history" data-section="history">
        <div v-if="cardState === 'loading' || profileState === 'loading'" class="skeleton" aria-hidden="true">
          <span></span><span></span><span></span>
        </div>
        <div v-else-if="cardState === 'error'" class="block-error">
          <p>Не удалось загрузить профиль</p>
          <button class="btn" type="button" @click="retryHistory">Повторить</button>
        </div>
        <template v-else>
          <div class="tabs" role="tablist">
            <button type="button" role="tab" :aria-selected="tab === 'games'" :class="{ on: tab === 'games' }" @click="tab = 'games'">Игры</button>
            <button type="button" role="tab" :aria-selected="tab === 'stats'" :class="{ on: tab === 'stats' }" @click="tab = 'stats'">Цифры</button>
          </div>
          <div v-if="tab === 'stats'" class="stats">
            <p v-if="!stats.length" class="empty">
              <strong>Пока без цифр.</strong>
              <span>Сыгранные матчи появятся здесь</span>
            </p>
            <div v-for="item in stats" :key="item.key" class="stat">
              <span>{{ item.label }}</span>
              <strong>{{ item.value }}</strong>
            </div>
          </div>
          <div v-else class="games">
            <h2>История игр</h2>
            <p v-if="!history.length" class="empty">
              <strong>Истории игр пока нет</strong>
              <span>Сыгранные матчи появятся здесь</span>
            </p>
            <RouterLink v-for="row in history" :key="row.matchId" class="game" :to="`/matches/${row.matchId}`">
              <span class="when">{{ rowWhen(row) }}</span>
              <span class="sides">
                <span>{{ row.homeTeamName }}</span>
                <span>{{ row.awayTeamName }}</span>
              </span>
              <span class="nums">
                <span class="score">{{ calendarScore(row) }}</span>
                <span v-if="row.outcome" class="mark">{{ outcomeMark[row.outcome] || row.outcome }}</span>
              </span>
            </RouterLink>
          </div>
        </template>
      </section>

      <section class="card teams" data-section="teams">
        <h2>{{ teamsTitle }}</h2>
        <RouterLink v-if="card?.team" class="team" :to="`/teams/${card.team.id}`">
          <TeamCrest :src="card.team.logoUrl" :name="card.team.name" :size="36" />
          <span>{{ card.team.name }}</span>
        </RouterLink>
        <RouterLink v-for="team in favTeams" :key="team.id" class="team" :to="`/teams/${team.id}`">
          <TeamCrest :src="team.logo" :name="team.name" :size="36" />
          <span>{{ team.name }}</span>
        </RouterLink>
        <p v-if="profileState !== 'loading' && cardState !== 'loading' && !card?.team && !favTeams.length && approvedRoles.includes('CAPTAIN')" class="empty">
          <strong>Команду назначает администратор</strong>
        </p>
        <p v-else-if="profileState !== 'loading' && cardState !== 'loading' && !card?.team && !favTeams.length" class="empty">
          <strong>Избранных команд пока нет</strong>
        </p>
      </section>

      <section v-if="favMatches.length || matchState === 'error'" class="card fav-matches">
        <h2>Избранные матчи</h2>
        <div v-if="matchState === 'error'" class="block-error">
          <p>Не удалось загрузить матчи</p>
          <button class="btn" type="button" @click="loadMatches">Повторить</button>
        </div>
        <MatchRow
          v-for="match in favMatches"
          :key="match.id"
          :match="match"
          :home-name="teams.fullName(match.homeTeamId)"
          :away-name="teams.fullName(match.awayTeamId)"
        />
      </section>
    </div>

    <Teleport to="body">
      <div v-if="editing" class="overlay" @click.self="editing = false">
        <div class="sheet-form" role="dialog" aria-modal="true" aria-labelledby="profile-form-title">
          <div class="dialog-head">
            <h2 id="profile-form-title">{{ isPlayer ? 'Изменить анкету' : 'Стать игроком' }}</h2>
            <button class="btn secondary" type="button" @click="editing = false">Закрыть</button>
          </div>
          <form class="stack" @submit.prevent="submit">
            <label class="field">Имя
              <input v-model="firstName" maxlength="100" :aria-invalid="!!fieldError.firstName" @input="forgetField('firstName')" />
              <span v-if="fieldError.firstName" class="field-error">{{ fieldError.firstName }}</span>
            </label>
            <label class="field">Фамилия
              <input v-model="lastName" maxlength="100" :aria-invalid="!!fieldError.lastName" @input="forgetField('lastName')" />
              <span v-if="fieldError.lastName" class="field-error">{{ fieldError.lastName }}</span>
            </label>
            <label class="field">Как писать на майке
              <input v-model="displayName" maxlength="150" :aria-invalid="!!fieldError.displayName" @input="forgetField('displayName')" />
              <span v-if="fieldError.displayName" class="field-error">{{ fieldError.displayName }}</span>
            </label>
            <div class="photo-row">
              <PlayerAvatar
                :src="avatarUrl || auth.user?.photoUrl"
                :name="accountName"
                :first-name="firstName"
                :last-name="lastName"
                :size="56"
              />
              <label class="field grow">Фото
                <input type="file" accept="image/png,image/jpeg,image/webp,image/gif" :disabled="pending" @change="onPhoto" />
                <span class="field-hint">{{ isPlayer ? 'PNG, JPG, WebP или GIF. Сохраняется сразу.' : 'Нужна фотография. PNG, JPG, WebP или GIF.' }}</span>
                <span v-if="fieldError.avatar" class="field-error">{{ fieldError.avatar }}</span>
              </label>
            </div>
            <label class="field">Номер
              <input v-model.number="jerseyNumber" type="number" min="0" max="99" :aria-invalid="!!fieldError.jerseyNumber" @input="forgetField('jerseyNumber')" />
              <span v-if="fieldError.jerseyNumber" class="field-error">{{ fieldError.jerseyNumber }}</span>
            </label>
            <label class="field">Позиция
              <select v-model="position" :aria-invalid="!!fieldError.position" @change="forgetField('position')">
                <option value="">Выберите позицию</option>
                <option v-for="option in positionOptions" :key="option" :value="option">{{ option }}</option>
                <option v-if="position && !positionOptions.includes(position)" :value="position">{{ position }}</option>
              </select>
              <span v-if="fieldError.position" class="field-error">{{ fieldError.position }}</span>
            </label>
            <label class="field">О себе
              <textarea v-model="bio" rows="3"></textarea>
            </label>
            <p v-if="ok" class="form-ok">{{ ok }}</p>
            <p v-if="error" class="form-error">{{ error }}</p>
            <button class="btn" type="submit" :disabled="pending" :aria-busy="pending">
              {{ pending ? 'Сохраняем…' : isPlayer ? 'Сохранить' : 'Стать игроком' }}
            </button>
            <button class="btn ghost" type="button" @click="editing = false">Отмена</button>
          </form>
        </div>
      </div>
    </Teleport>
  </section>
</template>

<style scoped>
.profile { min-width: 0; max-width: 100%; }
.hero {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  gap: 0.8rem 1rem;
  align-items: center;
  background: #001433;
  color: #fff;
  border-radius: 22px;
  padding: 1.15rem 1.2rem;
}
.who { min-width: 0; }
.hero h1 {
  margin: 0;
  color: #fff;
  font-size: 2rem;
  line-height: 1.05;
  letter-spacing: 0.03em;
  text-transform: uppercase;
  overflow-wrap: anywhere;
}
.sub, .kicker, .pending { margin: 0.25rem 0 0; color: rgba(255, 255, 255, 0.82); }
.pending { font-weight: 700; }
.cta { margin-top: 0.85rem; min-height: 44px; background: #4cb4e5; }
.edit {
  min-height: 44px;
  background: #fff;
  color: var(--navy);
}
.aside, .content { display: grid; gap: 16px; min-width: 0; }
.profile { display: grid; gap: 16px; }
.card {
  min-width: 0;
  background: #fff;
  border-radius: 18px;
  padding: 1rem 1.05rem 1.05rem;
  box-shadow: var(--shadow);
}
.card h2 {
  margin: 0 0 0.75rem;
  color: var(--navy);
  font-size: 0.78rem;
  letter-spacing: 0.06em;
  text-transform: uppercase;
}
.jump a, .sections a, .team {
  display: flex;
  align-items: center;
  gap: 0.7rem;
  min-height: 44px;
  color: var(--navy);
  font-weight: 700;
  text-decoration: none;
}
.jump a { display: grid; grid-template-columns: minmax(0, 1fr) auto; align-items: center; }
.jump span { grid-column: 1; color: var(--muted); font-size: 0.78rem; letter-spacing: 0.05em; text-transform: uppercase; }
.jump strong { grid-column: 1; font-size: 1rem; overflow-wrap: anywhere; }
.jump i {
  width: 8px;
  height: 8px;
  border-right: 2px solid var(--navy);
  border-top: 2px solid var(--navy);
  transform: rotate(45deg);
}
.sections a, .account .btn { width: 100%; }
.sections a {
  justify-content: center;
  margin-top: 0.45rem;
  border: 1px solid var(--line-strong);
  border-radius: 12px;
  background: #fff;
}
.rights { margin: 0.8rem 0 0; color: var(--muted); font-size: 0.85rem; }
.account { display: grid; gap: 0.55rem; }
.wide { width: 100%; min-height: 44px; }
.tabs { display: flex; gap: 0.45rem; margin-bottom: 0.85rem; }
.tabs button {
  min-height: 44px;
  border: 0;
  border-radius: 12px;
  padding: 0.4rem 1rem;
  background: #fff;
  color: var(--navy);
  box-shadow: inset 0 0 0 1px var(--line-strong);
  cursor: pointer;
  font-weight: 800;
}
.tabs button.on { background: #4cb4e5; box-shadow: none; }
.tabs button:focus-visible, .edit:focus-visible, .sections a:focus-visible, .jump a:focus-visible {
  outline: 3px solid rgba(76, 180, 229, 0.45);
  outline-offset: 2px;
}
.games { display: grid; }
.game {
  display: grid;
  grid-template-columns: 4.6rem minmax(0, 1fr) auto;
  gap: 0.45rem 0.7rem;
  align-items: center;
  min-height: 44px;
  padding: 0.7rem 0;
  border-top: 1px solid var(--line);
  color: var(--navy);
  text-decoration: none;
}
.when { color: var(--muted); font-size: 0.85rem; }
.sides { min-width: 0; display: grid; overflow-wrap: break-word; }
.nums { display: inline-flex; align-items: center; gap: 0.45rem; white-space: nowrap; }
.score, .mark { font-weight: 800; }
.stats { display: grid; grid-template-columns: repeat(auto-fit, minmax(92px, 1fr)); gap: 0.6rem; }
.stat {
  display: grid;
  gap: 0.15rem;
  padding: 0.7rem;
  border-radius: 12px;
  background: #f4f8fb;
}
.stat span { color: var(--muted); font-size: 0.8rem; }
.stat strong { color: var(--navy); font-size: 1.3rem; }
.empty { display: grid; gap: 0.2rem; margin: 0; color: var(--navy); }
.empty span, .empty strong { font-weight: 700; }
.empty span { color: var(--muted); font-weight: 500; }
.block-error { display: grid; justify-items: start; gap: 0.7rem; }
.block-error p { margin: 0; color: var(--navy); font-weight: 800; }
.skeleton { display: grid; gap: 0.55rem; }
.skeleton span {
  display: block;
  height: 14px;
  border-radius: 999px;
  background: linear-gradient(90deg, #e3ebf3, #f7fafc, #e3ebf3);
}
.skeleton span:first-child { width: 42%; height: 28px; border-radius: 50%; }
.team span { min-width: 0; overflow-wrap: anywhere; }
.overlay {
  position: fixed;
  inset: 0;
  z-index: 80;
  display: grid;
  place-items: center;
  padding: max(0.75rem, env(safe-area-inset-top)) 0.75rem max(0.75rem, env(safe-area-inset-bottom));
  background: rgba(0, 32, 91, 0.55);
}
.sheet-form {
  width: min(560px, 100%);
  max-height: min(90dvh, 760px);
  overflow: auto;
  background: #fff;
  color: var(--text);
  border-radius: 20px;
  padding: 1rem 1.1rem 1.15rem;
}
.dialog-head { display: flex; align-items: center; justify-content: space-between; gap: 0.75rem; margin-bottom: 0.8rem; }
.dialog-head h2 { margin: 0; color: var(--navy); }
.photo-row { display: flex; align-items: flex-start; gap: 0.75rem; }
.grow { flex: 1; min-width: 0; }
.sheet-form input[type="file"] { display: block; width: 100%; min-width: 0; font-size: 0.85rem; }
@media (min-width: 720px) {
  .profile {
    grid-template-columns: minmax(240px, 300px) minmax(0, 1fr);
    gap: 24px;
    align-items: start;
  }
  .hero, .content { grid-column: 1 / -1; }
  .hero { grid-column: 1 / -1; }
  .aside { grid-column: 1; grid-row: 2; align-content: start; }
  .content { grid-column: 2; grid-row: 2; }
}
@media (max-width: 719px) {
  .profile { display: flex; flex-direction: column; }
  .aside, .content { display: contents; }
  .hero { order: 1; grid-template-columns: auto minmax(0, 1fr); padding: 0.95rem 0.85rem; border-radius: 16px; }
  .hero h1 { font-size: 1.45rem; }
  .edit { grid-column: 1 / -1; justify-self: start; }
  .jump { order: 2; }
  .history { order: 3; }
  .teams { order: 4; }
  .fav-matches { order: 5; }
  .sections { order: 6; }
  .account { order: 7; }
  .overlay { place-items: end stretch; padding: 0.5rem; }
  .sheet-form { width: 100%; max-height: calc(100dvh - 0.75rem); border-radius: 18px 18px 12px 12px; }
  .photo-row { flex-direction: column; }
}
</style>
