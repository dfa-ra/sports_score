<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { useTournamentApply } from '../lib/useTournamentApply'

const props = defineProps<{
  tournament: { id: string, name: string, status?: string } | null
}>()
const emit = defineEmits<{ close: [], applied: [] }>()

const auth = useAuthStore()
const router = useRouter()
const {
  teams,
  registeredTeamIds,
  teamId,
  pending,
  error,
  ok,
  canApply,
  loadTeams,
  apply,
} = useTournamentApply()
const loading = ref(false)

const selectedTeam = computed(() => teams.value.find((team) => team.id === teamId.value))
const needsPicker = computed(() => teams.value.length > 1 || auth.canManageLeague)
const alreadyIn = computed(() => !!teamId.value && registeredTeamIds.value.has(teamId.value))

watch(() => props.tournament?.id, async (id) => {
  if (!id) return
  if (!auth.isAuthenticated) {
    router.push({ name: 'login', query: { redirect: `/tournaments/${id}` } })
    emit('close')
    return
  }
  loading.value = true
  try {
    await loadTeams(id)
  } catch (e: any) {
    error.value = e.response?.data?.message || 'Команды не загрузились.'
  } finally {
    loading.value = false
  }
}, { immediate: true })

async function submit() {
  if (!props.tournament) return
  const done = await apply(props.tournament.id)
  if (done) emit('applied')
}

function onKey(event: KeyboardEvent) {
  if (event.key === 'Escape') emit('close')
}

onMounted(() => window.addEventListener('keydown', onKey))
onUnmounted(() => window.removeEventListener('keydown', onKey))
</script>

<template>
  <Teleport to="body">
    <div class="overlay" role="dialog" aria-modal="true" aria-labelledby="apply-title" @click.self="emit('close')">
      <div class="sheet">
        <h2 id="apply-title">Заявиться</h2>
        <p class="muted">{{ tournament?.name }}</p>
        <p v-if="loading" class="muted">Смотрим составы…</p>
        <template v-else-if="!canApply">
          <p>Заявить команду может капитан или администратор.</p>
        </template>
        <template v-else-if="!teams.length">
          <p>Нет команды, которую можно заявить. Капитану нужна своя команда, админ выбирает из списка лиги.</p>
        </template>
        <form v-else class="stack" @submit.prevent="submit">
          <label v-if="needsPicker" class="field" for="apply-team-select">Команда
            <select id="apply-team-select" v-model="teamId" required>
              <option v-for="team in teams" :key="team.id" :value="team.id">
                {{ team.name }}{{ registeredTeamIds.has(team.id) ? ' · уже в заявке' : '' }}
              </option>
            </select>
          </label>
          <p v-else>
            Заявить «{{ selectedTeam?.name }}» на этот турнир?
          </p>
          <p class="muted">Заявка уходит на рассмотрение. Админ ещё должен нажать «Допустить».</p>
          <p v-if="error" class="form-error">{{ error }}</p>
          <p v-if="ok" class="form-ok">{{ ok }}</p>
          <button
            class="btn"
            type="submit"
            :disabled="pending || alreadyIn"
          >
            {{ alreadyIn ? 'Уже заявлена' : pending ? 'Отправляем…' : 'Заявиться' }}
          </button>
        </form>
        <button class="btn ghost" type="button" @click="emit('close')">Закрыть</button>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.overlay {
  position: fixed;
  inset: 0;
  z-index: 50;
  display: grid;
  place-items: end center;
  padding: 0.75rem;
  background: rgba(0, 32, 91, 0.42);
}
.sheet {
  width: min(480px, 100%);
  display: grid;
  gap: 0.75rem;
  background: #fff;
  border-radius: 20px;
  padding: 1.15rem 1.2rem 1.25rem;
  box-shadow: var(--shadow-hover);
}
h2 { font-size: 1.25rem; }
@media (min-width: 720px) {
  .overlay { place-items: center; }
}
</style>
