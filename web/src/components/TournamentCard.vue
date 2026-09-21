<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import StatusBadge from './StatusBadge.vue'
import { useAuthStore } from '../stores/auth'
import { formatDateRange, formatLabel, isTournamentOpenForApply, labelOf } from '../lib/format'

const props = defineProps<{
  tournament: {
    id: string
    name: string
    status?: string
    format?: string
    seasonYear?: number
    startDate?: string | null
    endDate?: string | null
  }
  sportName?: string
}>()
const emit = defineEmits<{ apply: [] }>()

const auth = useAuthStore()
const router = useRouter()
const route = useRoute()

const recruiting = computed(() => isTournamentOpenForApply(props.tournament.status))
const showApply = computed(() =>
  recruiting.value
  && (!auth.isAuthenticated || auth.hasRole('CAPTAIN') || auth.canManageLeague)
)
const meta = computed(() => {
  const parts = [
    props.sportName,
    props.tournament.seasonYear ? `сезон ${props.tournament.seasonYear}` : '',
    labelOf(formatLabel, props.tournament.format, ''),
  ].filter(Boolean)
  return parts.join(' · ')
})
const dates = computed(() => formatDateRange(props.tournament.startDate, props.tournament.endDate))

function apply(event: Event) {
  event.preventDefault()
  event.stopPropagation()
  if (!auth.isAuthenticated) {
    router.push({ name: 'login', query: { redirect: route.fullPath } })
    return
  }
  emit('apply')
}
</script>

<template>
  <article class="panel tournament-card rise">
    <RouterLink class="body" :to="`/tournaments/${tournament.id}`">
      <StatusBadge :status="tournament.status" />
      <h2>{{ tournament.name }}</h2>
      <p v-if="meta">{{ meta }}</p>
      <p v-if="dates" class="dates">{{ dates }}</p>
    </RouterLink>
    <button
      v-if="showApply"
      class="btn"
      type="button"
      @click="apply"
    >
      Заявиться
    </button>
  </article>
</template>

<style scoped>
.tournament-card {
  display: grid;
  gap: 0.85rem;
  align-content: start;
}
.body {
  display: grid;
  gap: 0.4rem;
  color: inherit;
  text-decoration: none;
  min-height: 6.4rem;
}
.body:hover { color: inherit; text-decoration: none; }
h2 { font-size: 1.25rem; }
.dates { font-size: 0.86rem; }
.btn { width: 100%; }
</style>
