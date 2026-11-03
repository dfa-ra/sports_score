<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import api from '../api/client'
import TeamCrest from './TeamCrest.vue'
import { fromLocalInput, toLocalInput } from '../lib/datetime'
import { apiError } from '../lib/errors'
import { publicScore } from '../lib/match'
import { useTeamDirectory } from '../lib/useTeamDirectory'

const props = defineProps<{
  tournamentId: string
  rounds: any[]
  editable?: boolean
}>()
const emit = defineEmits<{ saved: [] }>()

const names = useTeamDirectory()
const drafts = reactive<Record<string, { scheduledAt: string; venue: string }>>({})
const timeError = reactive<Record<string, string>>({})
const error = ref('')
const pending = reactive<Record<string, boolean>>({})

watch(
  () => props.rounds,
  (rounds) => {
    for (const round of rounds ?? []) {
      for (const slot of round.slots ?? []) {
        drafts[slot.id] = {
          scheduledAt: toLocalInput(slot.scheduledAt),
          venue: slot.venue || '',
        }
      }
    }
  },
  { immediate: true },
)

function placeLabel(group?: string | null, place?: number | null) {
  if (!group || !place) return ''
  return `${group}, ${place}-е место`
}

function shortRound(title?: string | null) {
  return String(title || '').replace(/\s*финала\s*$/i, '').trim()
}

function placeholder(slot: any, home: boolean) {
  const roundNumber = Number(slot?.round || 0)
  const prev = props.rounds.find((round) => Number(round?.round) === roundNumber - 1)
  if (prev) {
    const feeder = Number(slot?.slotIndex ?? 0) * 2 + (home ? 1 : 2)
    const title = shortRound(prev.title) || 'пары'
    return `Победитель ${title} #${feeder}`
  }
  return '—'
}

function showScore(slot: any) {
  return slot.matchStatus === 'LIVE' || slot.matchStatus === 'PAUSED' || slot.matchStatus === 'FINISHED'
}

function slotScore(slot: any) {
  return publicScore({
    homeScore: slot.homeScore,
    awayScore: slot.awayScore,
    penaltyHome: slot.penaltyHome,
    penaltyAway: slot.penaltyAway,
  })
}

async function save(slot: any) {
  error.value = ''
  const draft = drafts[slot.id]
  if (!draft?.scheduledAt) {
    timeError[slot.id] = 'Укажите время пары.'
    return
  }
  timeError[slot.id] = ''
  pending[slot.id] = true
  try {
    await api.put(`/tournaments/${props.tournamentId}/playoff/slots/${slot.id}`, {
      scheduledAt: draft.scheduledAt ? fromLocalInput(draft.scheduledAt) : undefined,
      venue: draft.venue,
    })
    emit('saved')
  } catch (e: any) {
    error.value = apiError(e)
  } finally {
    pending[slot.id] = false
  }
}
</script>

<template>
  <div class="bracket-wrap">
    <div class="bracket" :class="{ admin: editable }">
      <section v-for="round in rounds" :key="round.round" class="round">
        <h3>{{ round.title }}</h3>
        <article v-for="slot in round.slots" :key="slot.id" class="slot">
          <component
            :is="slot.matchId ? RouterLink : 'div'"
            class="sides"
            :to="slot.matchId ? `/matches/${slot.matchId}` : undefined"
          >
            <div class="side">
              <template v-if="slot.homeTeamId">
                <TeamCrest :src="names.logo(slot.homeTeamId)" :name="slot.homeTeamName" :size="22" />
                <span>
                  <b>{{ slot.homeTeamName }}</b>
                  <small v-if="placeLabel(slot.homeGroupName, slot.homePlace)">
                    {{ placeLabel(slot.homeGroupName, slot.homePlace) }}
                  </small>
                </span>
              </template>
              <span v-else class="wait">{{ placeholder(slot, true) }}</span>
            </div>
            <div class="side">
              <template v-if="slot.awayTeamId">
                <TeamCrest :src="names.logo(slot.awayTeamId)" :name="slot.awayTeamName" :size="22" />
                <span>
                  <b>{{ slot.awayTeamName }}</b>
                  <small v-if="placeLabel(slot.awayGroupName, slot.awayPlace)">
                    {{ placeLabel(slot.awayGroupName, slot.awayPlace) }}
                  </small>
                </span>
              </template>
              <span v-else class="wait">{{ placeholder(slot, false) }}</span>
            </div>
          </component>
          <p v-if="showScore(slot)" class="score">{{ slotScore(slot) }}</p>
          <p v-else-if="slot.venue" class="meta">{{ slot.venue }}</p>
          <form v-if="editable && drafts[slot.id]" class="edit" @submit.prevent="save(slot)">
            <label class="field" :class="{ invalid: timeError[slot.id] }">Время
              <input
                v-model="drafts[slot.id].scheduledAt"
                type="datetime-local"
                @input="timeError[slot.id] = ''"
              />
            </label>
            <p v-if="timeError[slot.id]" class="field-error">{{ timeError[slot.id] }}</p>
            <label class="field">Место проведения
              <input v-model="drafts[slot.id].venue" maxlength="120" placeholder="Зал" />
            </label>
            <button class="btn secondary" type="submit" :disabled="pending[slot.id]">Сохранить</button>
          </form>
        </article>
      </section>
    </div>
    <p v-if="error" class="form-error">{{ error }}</p>
  </div>
</template>

<style scoped>
.bracket-wrap { min-width: 0; }
.bracket {
  display: flex;
  gap: 0.75rem;
  overflow-x: auto;
  padding-bottom: 0.35rem;
  align-items: stretch;
}
.round {
  flex: 0 0 240px;
  min-width: 220px;
  display: flex;
  flex-direction: column;
  justify-content: space-around;
  gap: 0.75rem;
}
.round h3 {
  margin: 0;
  font-size: 0.78rem;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--navy);
}
.slot {
  border: 1px solid var(--line);
  border-radius: var(--radius);
  background: #fff;
  padding: 0.55rem 0.65rem 0.7rem;
  min-height: 5.4rem;
}
.sides {
  display: grid;
  gap: 0.35rem;
  color: inherit;
  text-decoration: none;
}
.side {
  display: grid;
  grid-template-columns: 22px minmax(0, 1fr);
  gap: 0.45rem;
  align-items: center;
  min-height: 2rem;
}
.side span { min-width: 0; }
.side b {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.side small, .meta {
  color: var(--muted);
  font-size: 0.75rem;
}
.wait {
  grid-column: 1 / -1;
  color: var(--navy);
  font-size: 0.82rem;
  font-weight: 700;
  line-height: 1.3;
  white-space: normal;
}
.score {
  margin: 0.35rem 0 0;
  font-weight: 800;
  color: var(--navy);
}
.meta { margin: 0.3rem 0 0; }
.edit { display: grid; gap: 0.45rem; margin-top: 0.55rem; }
/* Admin forms make a card taller than a public row. Keep each round wide
   enough for the time field and stack cards from the top so the final
   cannot sit across the semifinals. */
.bracket.admin { align-items: flex-start; }
.bracket.admin .round {
  flex: 0 0 23rem;
  min-width: 23rem;
  justify-content: flex-start;
  overflow: hidden;
}
.bracket.admin .slot { width: 100%; }
.bracket.admin .edit,
.bracket.admin .edit input { min-width: 0; max-width: 100%; }
@media (max-width: 640px) {
  .round { flex-basis: 78vw; }
  .bracket.admin .round { flex-basis: min(100%, 23rem); min-width: min(100%, 23rem); }
}
</style>
