<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import api from '../../api/client'
import { labelOf, roleLabel } from '../../lib/format'
import { apiError } from '../../lib/errors'
import { useTeamDirectory } from '../../lib/useTeamDirectory'
import CreateMatchForm from '../../components/CreateMatchForm.vue'
import CreateTeamForm from '../../components/CreateTeamForm.vue'
import CreateTournamentForm from '../../components/CreateTournamentForm.vue'
import StatusBadge from '../../components/StatusBadge.vue'

const users = ref<any[]>([])
const tournaments = ref<any[]>([])
const matches = ref<any[]>([])
const teams = ref<any[]>([])
const players = ref<any[]>([])
const sports = ref<any[]>([])
const tab = ref<'users' | 'tournaments' | 'matches' | 'teams' | 'players' | 'referees' | 'statistics' | 'gallery'>('users')
const roleRequests = ref<any[]>([])
const gallery = ref<any>({ photos: [], vkAlbumUrl: '' })
const photoUrl = ref('')
const photoTitle = ref('')
const photoCaption = ref('')
const photoSlot = ref<'HERO' | 'STORY' | 'GALLERY'>('HERO')
const photoLink = ref('')
const photoLinkLabel = ref('')
const photoSort = ref(0)
const vkAlbumUrl = ref('')
const slotLabel: Record<string, string> = { HERO: 'Герой', STORY: 'Сюжет', GALLERY: 'Галерея' }
const galleryPhotos = computed(() => {
  const order: Record<string, number> = { HERO: 0, STORY: 1, GALLERY: 2 }
  return [...(gallery.value.photos || [])].sort((a, b) => {
    const bySlot = (order[a.slot] ?? 9) - (order[b.slot] ?? 9)
    return bySlot || (Number(a.sortOrder) - Number(b.sortOrder))
  })
})
const teamStats = ref<any[]>([])
const playerStats = ref<any[]>([])
const error = ref('')
const ok = ref('')
const pending = ref(false)
const names = useTeamDirectory()

const tabs = [
  { id: 'users', label: 'Пользователи' },
  { id: 'tournaments', label: 'Турниры' },
  { id: 'matches', label: 'Матчи' },
  { id: 'teams', label: 'Команды' },
  { id: 'players', label: 'Игроки' },
  { id: 'referees', label: 'Судьи' },
  { id: 'statistics', label: 'Статистика' },
  { id: 'gallery', label: 'Фото' },
] as const

async function load() {
  const [u, t, m, tm, p, s, ts, ps, rr, g] = await Promise.all([
    api.get('/admin/users', { params: { size: 100 } }),
    api.get('/tournaments', { params: { size: 50 } }),
    api.get('/matches', { params: { size: 50 } }),
    api.get('/teams', { params: { size: 50, includeDisbanded: true } }),
    api.get('/players', { params: { size: 50 } }),
    api.get('/sports'),
    api.get('/statistics/teams'),
    api.get('/statistics/players'),
    api.get('/admin/role-requests'),
    api.get('/gallery'),
  ])
  users.value = u.data.content
  tournaments.value = t.data.content
  matches.value = m.data.content
  teams.value = tm.data.content
  players.value = p.data.content
  sports.value = s.data
  teamStats.value = ts.data
  playerStats.value = ps.data
  roleRequests.value = rr.data
  gallery.value = g.data
  vkAlbumUrl.value = g.data.vkAlbumUrl || ''
  await names.load()
}

onMounted(load)

async function updateUser(user: any) {
  error.value = ''
  ok.value = ''
  pending.value = true
  try {
    await api.patch(`/admin/users/${user.id}`, { role: user.role, enabled: user.enabled })
    ok.value = `${user.email} обновлён.`
    await load()
  } catch (e: any) {
    error.value = apiError(e)
  } finally {
    pending.value = false
  }
}

async function approveRole(req: any) {
  pending.value = true
  try {
    await api.post(`/admin/users/${req.userId}/roles/${req.role}/approve`)
    ok.value = 'Роль подтверждена.'
    await load()
  } catch (e: any) {
    error.value = apiError(e)
  } finally {
    pending.value = false
  }
}

async function addPhoto() {
  pending.value = true
  try {
    await api.post('/admin/gallery', {
      url: photoUrl.value,
      title: photoTitle.value || undefined,
      caption: photoCaption.value || undefined,
      slot: photoSlot.value,
      linkUrl: photoLink.value || undefined,
      linkLabel: photoLinkLabel.value || undefined,
      sortOrder: photoSort.value,
      source: photoUrl.value.startsWith('/media/') ? 'UPLOAD' : 'URL',
    })
    photoUrl.value = ''
    photoTitle.value = ''
    photoCaption.value = ''
    photoLink.value = ''
    photoLinkLabel.value = ''
    photoSort.value = 0
    ok.value = 'Слайд добавлен на главную.'
    await load()
  } catch (e: any) {
    error.value = apiError(e)
  } finally {
    pending.value = false
  }
}

async function uploadPhoto(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (!file) return
  pending.value = true
  error.value = ''
  try {
    const form = new FormData()
    form.append('file', file)
    const { data } = await api.post('/uploads/admin/gallery', form)
    photoUrl.value = data.url
  } catch (e: any) {
    error.value = apiError(e, 'Файл не загрузился.')
  } finally {
    pending.value = false
  }
}

async function removePhoto(id: string) {
  if (!confirm('Убрать этот кадр с сайта?')) return
  pending.value = true
  try {
    await api.delete(`/admin/gallery/${id}`)
    await load()
  } catch (e: any) {
    error.value = apiError(e)
  } finally {
    pending.value = false
  }
}

async function saveVkAlbum() {
  pending.value = true
  try {
    await api.put('/admin/gallery/vk-album', { url: vkAlbumUrl.value })
    ok.value = 'Ссылка на альбом ВК сохранена.'
    await load()
  } catch (e: any) {
    error.value = apiError(e)
  } finally {
    pending.value = false
  }
}

async function disbandTeam(team: any) {
  if (team.disbanded) return
  if (!confirm(`Расформировать «${team.name}»? Состав снимут, заявки на турниры снимут.`)) return
  error.value = ''
  ok.value = ''
  pending.value = true
  try {
    await api.delete(`/teams/${team.id}`)
    ok.value = `${team.name} расформирована.`
    await load()
  } catch (e: any) {
    error.value = apiError(e)
  } finally {
    pending.value = false
  }
}
</script>

<template>
  <section class="stack">
    <div class="page-title">
      <p class="eyebrow">Служебный вход</p>
      <h1>Админ-панель</h1>
      <p>Всё управление лигой — здесь. Без curl, без Swagger, только кнопки.</p>
    </div>
    <div class="tabs">
      <button
        v-for="t in tabs"
        :key="t.id"
        class="btn secondary"
        :class="{ on: tab === t.id }"
        @click="tab = t.id"
      >{{ t.label }}</button>
    </div>
    <p v-if="error" class="form-error">{{ error }}</p>
    <p v-if="ok" class="form-ok">{{ ok }}</p>

    <div v-if="tab === 'users'" class="stack">
      <div class="panel" v-if="roleRequests.length">
        <h2>Заявки на роли</h2>
        <div v-for="req in roleRequests" :key="req.id" class="row">
          <span>{{ req.role }} · {{ req.userId }}</span>
          <button class="btn" :disabled="pending" @click="approveRole(req)">Подтвердить</button>
        </div>
      </div>
      <div class="panel">
      <h2>Пользователи</h2>
      <p class="muted">Админа через форму не назначают — он из .env. Остальным роли ставятся здесь.</p>
      <table class="table">
        <thead><tr><th>Email</th><th>Роль</th><th>Активен</th><th></th></tr></thead>
        <tbody>
          <tr v-for="u in users" :key="u.id">
            <td>{{ u.email }}</td>
            <td>
              <select v-if="u.role !== 'ADMIN'" v-model="u.role">
                <option value="FAN">Зритель</option>
                <option value="PLAYER">Игрок</option>
                <option value="CAPTAIN">Капитан</option>
                <option value="REFEREE">Судья</option>
              </select>
              <span v-else>{{ labelOf(roleLabel, u.role) }}</span>
            </td>
            <td>
              <label class="check">
                <input v-model="u.enabled" type="checkbox" :disabled="u.role === 'ADMIN'" />
                {{ u.enabled ? 'да' : 'нет' }}
              </label>
            </td>
            <td>
              <button v-if="u.role !== 'ADMIN'" class="btn" :disabled="pending" @click="updateUser(u)">Сохранить</button>
            </td>
          </tr>
        </tbody>
      </table>
      </div>
    </div>

    <div v-else-if="tab === 'tournaments'" class="grid two">
      <div class="panel stack">
        <h2>Новый турнир</h2>
        <CreateTournamentForm @created="load" />
      </div>
      <div class="panel">
        <h2>Уже идут</h2>
        <div v-for="t in tournaments" :key="t.id" class="row">
          <RouterLink :to="`/tournaments/${t.id}`">{{ t.name }}</RouterLink>
          <StatusBadge :status="t.status" />
        </div>
      </div>
    </div>

    <div v-else-if="tab === 'matches'" class="grid two">
      <div class="panel stack">
        <h2>Назначить матч</h2>
        <CreateMatchForm @created="load" />
      </div>
      <div class="panel">
        <h2>Сетка</h2>
        <div v-for="m in matches" :key="m.id" class="row">
          <RouterLink :to="`/matches/${m.id}`">
            {{ names.name(m.homeTeamId) }} {{ m.homeScore }}:{{ m.awayScore }} {{ names.name(m.awayTeamId) }}
          </RouterLink>
          <StatusBadge :status="m.status" />
        </div>
      </div>
    </div>

    <div v-else-if="tab === 'teams'" class="grid two">
      <div class="panel stack">
        <h2>Новая команда</h2>
        <CreateTeamForm @created="load" />
      </div>
      <div class="panel stack">
      <h2>Клубы</h2>
      <p class="muted">Название, лого, капитан и дата основания. Расформирование — только отсюда.</p>
      <div v-for="t in teams" :key="t.id" class="row">
        <RouterLink :to="`/teams/${t.id}`">{{ t.name }}</RouterLink>
        <span v-if="t.disbanded" class="muted">расформирована</span>
        <button v-else class="btn danger" :disabled="pending" @click="disbandTeam(t)">Расформировать</button>
      </div>
      </div>
    </div>

    <div v-else-if="tab === 'players'" class="panel">
      <h2>Игроки</h2>
      <div v-for="p in players" :key="p.id" class="row">
        <RouterLink :to="`/players/${p.id}`">{{ p.displayName || `${p.firstName} ${p.lastName}` }}</RouterLink>
      </div>
    </div>

    <div v-else-if="tab === 'referees'" class="panel stack">
      <h2>Судьи</h2>
      <p class="muted">Роль ставится во вкладке «Пользователи». Потом судью назначают в карточке матча.</p>
      <div v-for="u in users.filter(x => x.role === 'REFEREE')" :key="u.id" class="row">{{ u.email }}</div>
      <p class="muted">Виды спорта: {{ sports.map(s => s.code).join(', ') || 'пока не заданы' }}</p>
    </div>

    <div v-else-if="tab === 'gallery'" class="panel stack">
      <h2>Главная: слайды и фото</h2>
      <p class="muted">
        <strong>Герой</strong> — большая карусель. <strong>Сюжет</strong> — круглые сторис сверху.
        <strong>Галерея</strong> — блок «Моменты» внизу.
      </p>
      <label class="field">Слот
        <select v-model="photoSlot">
          <option value="HERO">Герой (карусель)</option>
          <option value="STORY">Сюжет (кружок)</option>
          <option value="GALLERY">Галерея / моменты</option>
        </select>
      </label>
      <label class="field">Загрузить файл
        <input type="file" accept="image/*" @change="uploadPhoto" />
      </label>
      <label class="field">Или URL картинки
        <input v-model="photoUrl" placeholder="/media/gallery/... или https://..." />
      </label>
      <label class="field">Заголовок
        <input v-model="photoTitle" maxlength="200" placeholder="Как лига помогает кампусу" />
      </label>
      <label class="field">Подпись
        <input v-model="photoCaption" maxlength="300" />
      </label>
      <label class="field">Ссылка (необязательно)
        <input v-model="photoLink" placeholder="/calendar или https://..." />
      </label>
      <label class="field">Текст кнопки
        <input v-model="photoLinkLabel" maxlength="80" placeholder="Подробнее" />
      </label>
      <label class="field">Порядок в карусели
        <input v-model.number="photoSort" type="number" min="0" />
      </label>
      <button class="btn" :disabled="pending || !photoUrl" @click="addPhoto">Опубликовать</button>
      <label class="field">Альбом ВК
        <input v-model="vkAlbumUrl" placeholder="https://vk.com/album-..." />
      </label>
      <button class="btn secondary" :disabled="pending" @click="saveVkAlbum">Сохранить ссылку ВК</button>
      <div v-for="photo in galleryPhotos" :key="photo.id" class="slide-row">
        <img :src="photo.url" alt="" />
        <div>
          <strong>{{ slotLabel[photo.slot] || photo.slot }} · {{ photo.title || photo.caption || 'Без подписи' }}</strong>
          <p class="muted">{{ photo.sortOrder }} · {{ photo.url }}</p>
        </div>
        <button class="btn danger" type="button" :disabled="pending" @click="removePhoto(photo.id)">Удалить</button>
      </div>
    </div>

    <div v-else class="panel stack">
      <h2>Статистика</h2>
      <p>Топ игроков по голам:</p>
      <div v-for="p in playerStats.slice(0, 10)" :key="p.playerId" class="row">
        {{ p.displayName }} · G{{ p.goals }} A{{ p.assists }}
      </div>
      <p>Команды:</p>
      <div v-for="t in teamStats.slice(0, 10)" :key="t.teamId" class="row">
        {{ t.teamName }} · {{ t.points }} очков
      </div>
    </div>
  </section>
</template>

<style scoped>
.tabs { display: flex; flex-wrap: wrap; gap: 0.5rem; }
.btn.on { background: var(--accent); color: var(--navy); border-color: transparent; }
h2 { font-size: 1.15rem; margin-bottom: 0.55rem; }
.row {
  display: flex;
  justify-content: space-between;
  gap: 1rem;
  padding: 0.65rem 0;
  border-bottom: 1px solid var(--line);
}
.slide-row {
  display: grid;
  grid-template-columns: 72px 1fr auto;
  gap: 0.8rem;
  align-items: center;
  padding: 0.65rem 0;
  border-bottom: 1px solid var(--line);
}
.slide-row img { width: 72px; height: 48px; object-fit: cover; border-radius: 8px; }
.grid.two { display: grid; grid-template-columns: 1fr 1fr; gap: 1rem; }
.check { display: inline-flex; align-items: center; gap: 0.4rem; }
@media (max-width: 860px) {
  .grid.two { grid-template-columns: 1fr; }
}
</style>
