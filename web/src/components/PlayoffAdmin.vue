<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import api from '../api/client'
import TeamCrest from './TeamCrest.vue'
import { fromLocalInput } from '../lib/datetime'
import { apiError } from '../lib/errors'
import { useTeamDirectory } from '../lib/useTeamDirectory'

const props = defineProps<{
  tournamentId: string
  tables: { id: string; name: string; teamIds: string[] }[]
  playoff: any
}>()
const emit = defineEmits<{ changed: [] }>()

const names = useTeamDirectory()
const placeChoices = [1, 2, 3, 4, 5, 6, 7, 8]
const places = ref<number[]>([])
const pairRows = ref<{ homeTeamId: string; awayTeamId: string; scheduledAt: string; venue: string }[]>([])
const assignTable = ref<Record<string, string>>({})
const error = ref('')
const ok = ref('')
const pending = ref(false)

const playoffOpen = computed(() => props.playoff?.competitionStage === 'PLAYOFF')
const qualified = computed(() => props.playoff?.qualified ?? [])
const canPair = computed(() => [2, 4, 8, 16].includes(qualified.value.length))

watch(
  () => props.playoff,
  (value) => {
    const stored = (value?.advancingPlaces ?? []) as number[]
    places.value = [...stored]
    const pairs = (value?.qualified?.length ?? 0) / 2
    if (value?.competitionStage !== 'PLAYOFF' && [1, 2, 4, 8].includes(pairs)) {
      if (pairRows.value.length !== pairs) {
        pairRows.value = Array.from({ length: pairs }, (_, index) => pairRows.value[index] ?? blankPair())
      }
    }
  },
  { immediate: true },
)

function blankPair() {
  return { homeTeamId: '', awayTeamId: '', scheduledAt: '', venue: '' }
}

function togglePlace(place: number) {
  places.value = places.value.includes(place)
    ? places.value.filter((item) => item !== place)
    : [...places.value, place].sort((a, b) => a - b)
}

function groupsFor(match: any) {
  return props.tables.filter(
    (table) => table.teamIds.includes(match.homeTeamId) && table.teamIds.includes(match.awayTeamId),
  )
}

async function savePlaces() {
  error.value = ''
  ok.value = ''
  if (!places.value.length) {
    error.value = 'Отметьте места, которые выходят в плей-офф.'
    return
  }
  pending.value = true
  try {
    await api.put(`/tournaments/${props.tournamentId}/playoff/places`, { places: places.value })
    ok.value = 'Места выхода сохранены.'
    emit('changed')
  } catch (e: any) {
    error.value = apiError(e)
  } finally {
    pending.value = false
  }
}

async function attach(matchId: string) {
  error.value = ''
  ok.value = ''
  const tableId = assignTable.value[matchId]
  if (!tableId) {
    error.value = 'Выберите группу для матча.'
    return
  }
  pending.value = true
  try {
    await api.put(`/tournaments/${props.tournamentId}/matches/${matchId}/group`, { tableId })
    ok.value = 'Матч прикреплён к группе. Счёт и команды не менялись.'
    emit('changed')
  } catch (e: any) {
    error.value = apiError(e)
  } finally {
    pending.value = false
  }
}

async function openPlayoff() {
  error.value = ''
  ok.value = ''
  pending.value = true
  try {
    await api.post(`/tournaments/${props.tournamentId}/playoff`, {
      pairs: pairRows.value.map((pair) => ({
        homeTeamId: pair.homeTeamId,
        awayTeamId: pair.awayTeamId,
        scheduledAt: fromLocalInput(pair.scheduledAt),
        venue: pair.venue.trim() || undefined,
      })),
    })
    ok.value = 'Турнир переведён в плей-офф. Состав пар зафиксирован.'
    emit('changed')
  } catch (e: any) {
    error.value = apiError(e)
  } finally {
    pending.value = false
  }
}
</script>

<template>
  <div class="stack">
    <h2>Места в плей-офф</h2>
    <p class="muted">
      Не количество команд, а сами места. Например, из каждой группы выходят 1-е и 2-е.
      Порядок в таблице — тот же, что на вкладке «Таблица».
    </p>
    <div class="places">
      <label v-for="place in placeChoices" :key="place" class="pick">
        <input
          type="checkbox"
          :checked="places.includes(place)"
          :disabled="playoffOpen || pending"
          @change="togglePlace(place)"
        />
        <span>{{ place }}-е место</span>
      </label>
    </div>
    <button v-if="!playoffOpen" class="btn secondary" type="button" :disabled="pending" @click="savePlaces">
      Сохранить места
    </button>

    <h2>Уже сыгранные матчи</h2>
    <p class="muted">
      Если групповой этап уже прошёл обычным календарём, прикрепите матч к группе.
      Счёт, время и команды при этом не переписываются. Матч команд из разных групп в таблицу не входит и не удаляется.
    </p>
    <p v-if="!(playoff?.unassignedMatches ?? []).length" class="muted">Неприкреплённых матчей нет.</p>
    <div v-for="match in playoff?.unassignedMatches ?? []" :key="match.id" class="row">
      <span class="club">
        <TeamCrest :src="names.logo(match.homeTeamId)" :name="match.homeTeamName" :size="18" />
        {{ match.homeTeamName }} {{ match.homeScore }}:{{ match.awayScore }} {{ match.awayTeamName }}
      </span>
      <div v-if="!playoffOpen && groupsFor(match).length" class="actions">
        <select v-model="assignTable[match.id]">
          <option value="">Группа</option>
          <option v-for="table in groupsFor(match)" :key="table.id" :value="table.id">{{ table.name }}</option>
        </select>
        <button class="btn secondary" type="button" :disabled="pending" @click="attach(match.id)">Прикрепить</button>
      </div>
      <span v-else-if="!groupsFor(match).length" class="muted">Разные группы</span>
    </div>

    <h2>Кто выходит</h2>
    <p v-if="!qualified.length" class="muted">Сначала сохраните группы и места выхода.</p>
    <div v-for="team in qualified" :key="`${team.groupName}-${team.place}-${team.teamId}`" class="row">
      <span class="club">
        <TeamCrest :src="names.logo(team.teamId)" :name="team.teamName" :size="22" />
        {{ team.teamName }}
      </span>
      <span class="muted">{{ team.groupName }}, {{ team.place }}-е место</span>
    </div>
    <p v-if="!playoffOpen && qualified.length && !canPair" class="form-error">
      В плей-офф сейчас {{ qualified.length }} команд. Сетка строится только на 2, 4, 8 или 16 участников, без автоматических пропусков.
    </p>

    <template v-if="!playoffOpen && canPair">
      <h2>Пары первого круга</h2>
      <p class="muted">
        Пары назначаете вы, только из списка выше. Порядок пар задаёт сетку: победитель первой играет с победителем второй.
        После создания сетки команды уже не меняются — только время и место проведения.
      </p>
      <form class="stack" @submit.prevent="openPlayoff">
        <div v-for="(pair, index) in pairRows" :key="index" class="pair">
          <h3>Пара {{ index + 1 }}</h3>
          <label class="field">Хозяева
            <select v-model="pair.homeTeamId" required>
              <option value="">Команда</option>
              <option v-for="team in qualified" :key="team.teamId" :value="team.teamId">
                {{ team.teamName }} · {{ team.groupName }}, {{ team.place }}-е
              </option>
            </select>
          </label>
          <label class="field">Гости
            <select v-model="pair.awayTeamId" required>
              <option value="">Команда</option>
              <option v-for="team in qualified" :key="`away-${team.teamId}`" :value="team.teamId">
                {{ team.teamName }} · {{ team.groupName }}, {{ team.place }}-е
              </option>
            </select>
          </label>
          <label class="field">Время
            <input v-model="pair.scheduledAt" type="datetime-local" required />
          </label>
          <label class="field">Место проведения
            <input v-model="pair.venue" maxlength="120" placeholder="Зал" />
          </label>
        </div>
        <button class="btn" type="submit" :disabled="pending">Перевести в плей-офф</button>
      </form>
    </template>
    <p v-else-if="playoffOpen" class="muted">
      Плей-офф открыт. Состав пар зафиксирован, время и место проведения правятся в сетке.
    </p>
    <p v-if="error" class="form-error">{{ error }}</p>
    <p v-if="ok" class="form-ok">{{ ok }}</p>
  </div>
</template>

<style scoped>
h2 { font-size: 1.2rem; margin-bottom: 0.45rem; }
h3 { margin: 0; font-size: 0.95rem; }
.places { display: flex; flex-wrap: wrap; gap: 0.45rem 0.9rem; }
.pick { display: inline-flex; gap: 0.4rem; align-items: center; color: var(--text-strong); }
.row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 0.75rem;
  padding: 0.55rem 0;
  border-bottom: 1px solid var(--line);
  flex-wrap: wrap;
}
.club { display: inline-flex; align-items: center; gap: 0.45rem; color: var(--text-strong); }
.actions { display: flex; gap: 0.45rem; align-items: center; }
.pair {
  display: grid;
  gap: 0.55rem;
  padding: 0.75rem 0;
  border-top: 1px solid var(--line);
}
</style>
