<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import api from '../api/client'
import { apiError } from '../lib/errors'
import {
  QUALIFY_STAGES,
  firstPowerError,
  groupHeading,
  missingPlaces,
  placeOutOfRange,
  qualifyLines,
  ROSTER_MISMATCH_PLATE,
  SAVE_RULES_HINT,
  stageLabel,
  summaryTitle,
  winnersWord,
  type PlaceSpan,
  type QualifyStageId,
} from '../lib/qualifyStages'

const props = defineProps<{ tournamentId: string }>()
const emit = defineEmits<{
  changed: []
  active: [boolean]
  gate: [value: { canBuild: boolean; hint: string }]
}>()

type PlaceRow = { place: number; teamId?: string | null; teamName?: string | null; stage?: string | null }
type GroupBlock = { id: string; name: string; teamCount: number; finished: boolean; places: PlaceRow[] }

const loading = ref(true)
const loadError = ref('')
const groups = ref<GroupBlock[]>([])
const stages = ref<Record<string, Record<number, string>>>({})
const locked = ref(false)
const pending = ref(false)
const error = ref('')
const confirming = ref(false)
const selection = ref<{ groupId: string; anchor: number; focus: number } | null>(null)

const choices = QUALIFY_STAGES.filter((item) => item.id !== 'F')

const storedRules = ref<PlaceSpan[]>([])
const touched = ref(false)
const dismissedGhosts = ref<string[]>([])
const lines = computed(() => qualifyLines(directCounts()))
const powerError = computed(() => firstPowerError(lines.value))
const missingStage = computed(() =>
  groups.value.some((group) => group.places.some((row) => !stages.value[group.id]?.[row.place])),
)
const placeError = computed(() => {
  const live = placeOutOfRange(groups.value, rulesBody().rules)
  if (live) return live
  if (!touched.value) return placeOutOfRange(groups.value, storedRules.value)
  return ''
})
const ghostRows = computed(() => {
  const dropped = new Set(dismissedGhosts.value)
  return missingPlaces(groups.value, storedRules.value).filter((row) => !dropped.has(`${row.groupId}:${row.place}`))
})
const rosterMismatch = computed(() => ghostRows.value.length > 0)
function ghostsFor(groupId: string) {
  return ghostRows.value.filter((row) => row.groupId === groupId)
}
const canSave = computed(() =>
  !loading.value
  && !pending.value
  && !locked.value
  && !rosterMismatch.value
  && !missingStage.value
  && !powerError.value
  && !placeError.value
  && !error.value
  && lines.value.length > 0,
)

function directCounts() {
  const counts: Partial<Record<QualifyStageId, number>> = {}
  for (const group of groups.value) {
    for (const row of group.places) {
      const stage = stages.value[group.id]?.[row.place] as QualifyStageId | undefined
      if (stage === 'R16' || stage === 'R8' || stage === 'QF' || stage === 'SF') {
        counts[stage] = (counts[stage] || 0) + 1
      }
    }
  }
  return counts
}

function applyPayload(data: any) {
  groups.value = (data?.groups ?? []) as GroupBlock[]
  const next: Record<string, Record<number, string>> = {}
  for (const group of groups.value) {
    next[group.id] = {}
    for (const row of group.places) {
      if (row.stage) next[group.id][row.place] = row.stage
    }
  }
  stages.value = next
  storedRules.value = (Array.isArray(data?.rules) ? data.rules : []).map((rule: PlaceSpan) => ({
    groupId: String(rule.groupId),
    placeFrom: Number(rule.placeFrom),
    placeTo: Number(rule.placeTo),
  }))
  touched.value = false
  dismissedGhosts.value = []
  selection.value = null
  locked.value = data?.locked === true || data?.bracketBuilt === true
  const saved = storedRules.value.length > 0
  emit('active', saved)
}

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const { data } = await api.get(`/tournaments/${props.tournamentId}/playoff-qualify`)
    applyPayload(data)
  } catch (e: any) {
    loadError.value = apiError(e, 'Не удалось загрузить выход из групп.')
    emit('active', false)
  } finally {
    loading.value = false
  }
}

function range(groupId: string) {
  const current = selection.value
  if (!current || current.groupId !== groupId) return null
  const from = Math.min(current.anchor, current.focus)
  const to = Math.max(current.anchor, current.focus)
  return { from, to }
}

function selected(groupId: string, place: number) {
  const span = range(groupId)
  return !!span && place >= span.from && place <= span.to
}

function stopDrag() {
  window.removeEventListener('pointermove', onPointerMove)
  window.removeEventListener('pointerup', stopDrag)
  window.removeEventListener('pointercancel', stopDrag)
}

function onPointerMove(event: PointerEvent) {
  if (locked.value || pending.value || !selection.value) return
  const hit = document.elementFromPoint(event.clientX, event.clientY)
  const row = hit?.closest?.('[data-qualify-place]') as HTMLElement | null
  if (!row) return
  if (row.dataset.groupId !== selection.value.groupId) return
  const place = Number(row.dataset.place)
  if (!place) return
  selection.value = { ...selection.value, focus: place }
}

function onPointerDown(event: PointerEvent, groupId: string, place: number) {
  if (locked.value || pending.value) return
  if ((event.target as HTMLElement | null)?.closest('select')) return
  selection.value = { groupId, anchor: place, focus: place }
  window.addEventListener('pointermove', onPointerMove)
  window.addEventListener('pointerup', stopDrag)
  window.addEventListener('pointercancel', stopDrag)
}

function setStage(groupId: string, place: number, stage: string) {
  const span = selected(groupId, place) ? range(groupId) : null
  const from = span?.from ?? place
  const to = span?.to ?? place
  const next = { ...stages.value, [groupId]: { ...stages.value[groupId] } }
  for (let cursor = from; cursor <= to; cursor += 1) next[groupId][cursor] = stage
  stages.value = next
  touched.value = true
  error.value = ''
}

function rulesBody() {
  const rules: { groupId: string; placeFrom: number; placeTo: number; stage: string }[] = []
  for (const group of groups.value) {
    for (const row of group.places) {
      const stage = stages.value[group.id]?.[row.place]
      if (!stage) continue
      rules.push({ groupId: group.id, placeFrom: row.place, placeTo: row.place, stage })
    }
  }
  return { rules }
}

async function save() {
  if (!canSave.value) return
  error.value = ''
  pending.value = true
  try {
    const { data } = await api.put(`/tournaments/${props.tournamentId}/playoff-qualify`, rulesBody())
    applyPayload(data)
    emit('changed')
  } catch (e: any) {
    error.value = apiError(e)
  } finally {
    pending.value = false
  }
}

async function rebuild() {
  error.value = ''
  pending.value = true
  try {
    const { data } = await api.post(`/tournaments/${props.tournamentId}/playoff-qualify/rebuild`, { confirm: true })
    confirming.value = false
    applyPayload(data)
    emit('changed')
  } catch (e: any) {
    error.value = apiError(e)
  } finally {
    pending.value = false
  }
}

function dismissGhost(groupId: string, place: number) {
  if (locked.value || pending.value) return
  const key = `${groupId}:${place}`
  if (!dismissedGhosts.value.includes(key)) dismissedGhosts.value = [...dismissedGhosts.value, key]
  touched.value = true
  error.value = ''
}

const rulesReady = computed(() =>
  !rosterMismatch.value
  && !powerError.value
  && !placeError.value
  && !missingStage.value
  && !error.value
  && lines.value.length > 0
  && !touched.value,
)
const buildHint = computed(() => {
  if (loading.value || locked.value) return ''
  return rulesReady.value ? '' : SAVE_RULES_HINT
})
const canBuild = computed(() => !loading.value && !locked.value && !pending.value && buildHint.value === '')

watch([canBuild, buildHint], () => {
  emit('gate', { canBuild: canBuild.value, hint: buildHint.value })
}, { immediate: true })

onMounted(load)
onUnmounted(stopDrag)
</script>

<template>
  <section class="qualify" :class="{ 'is-locked': locked }" aria-labelledby="qualify-title">
    <h2 id="qualify-title">Выход из групп</h2>
    <p class="muted">Одно место — одна стадия. Порядок мест тот же, что на вкладке «Таблица».</p>

    <div v-if="loading" class="skel" aria-hidden="true">
      <div v-for="block in 2" :key="block" class="skel-block">
        <div class="skel-title"></div>
        <div v-for="row in 4" :key="row" class="skel-row"></div>
      </div>
    </div>
    <p v-else-if="loadError" class="form-error">{{ loadError }}</p>
    <p v-else-if="!groups.length" class="empty">
      В турнире нет групп.
      <a href="#groups">Собрать группы</a>
    </p>

    <template v-else>
      <p v-if="rosterMismatch" class="form-error mismatch">{{ ROSTER_MISMATCH_PLATE }}</p>
      <p v-if="locked" class="banner">
        <svg viewBox="0 0 24 24" aria-hidden="true">
          <rect x="5" y="11" width="14" height="10" rx="2" />
          <path d="M8 11V7a4 4 0 0 1 8 0v4" />
        </svg>
        Сетка уже сформирована
      </p>
      <div class="summary desk" aria-label="Сводка стадий">
        <p v-if="!lines.length" class="muted">Стадии плей-офф ещё не выбраны.</p>
        <div v-for="(line, index) in lines" :key="line.stage" class="sum" :class="line.powerOfTwo ? 'ok' : 'bad'">
          <i aria-hidden="true"></i>
          <b>{{ summaryTitle(line, lines, index) }}</b>
          <span>{{ line.direct }} напрямую + {{ line.fromPrev }} {{ winnersWord(line.fromPrev) }}{{ index > 0 ? ' ' + stageLabel(lines[index - 1].stage) : '' }} = {{ line.total }}</span>
          <span class="sr">{{ index }}</span>
        </div>
      </div>

      <section v-for="group in groups" :key="group.id" class="block">
        <h3>{{ groupHeading(group.name, group.teamCount) }}</h3>
        <div
          v-for="row in group.places"
          :key="row.place"
          class="qrow"
          :class="{ on: selected(group.id, row.place) }"
          :data-qualify="stages[group.id]?.[row.place] || null"
          data-qualify-place=""
          :data-group-id="group.id"
          :data-place="row.place"
          @pointerdown="onPointerDown($event, group.id, row.place)"
        >
          <span class="place">{{ row.place }}</span>
          <span class="team">{{ group.finished && row.teamName ? row.teamName : '—' }}</span>
          <select
            :value="stages[group.id]?.[row.place] || ''"
            :disabled="locked || pending"
            :aria-label="`Место ${row.place}, ${group.name}`"
            @change="setStage(group.id, row.place, ($event.target as HTMLSelectElement).value)"
            @pointerdown.stop
          >
            <option value="" disabled>Стадия</option>
            <option v-for="item in choices" :key="item.id" :value="item.id">{{ item.label }}</option>
          </select>
        </div>
        <div
          v-for="ghost in ghostsFor(group.id)"
          :key="`gone-${ghost.place}`"
          class="qrow gone"
          :data-missing-place="ghost.place"
          :data-group-id="group.id"
        >
          <span class="place">{{ ghost.place }}</span>
          <span class="team gone-label">{{ ghost.label }}</span>
          <button
            class="btn secondary drop"
            type="button"
            :disabled="locked || pending"
            @click="dismissGhost(group.id, ghost.place)"
          >Убрать</button>
        </div>
      </section>

      <div class="dock">
        <div class="summary phone" aria-label="Сводка стадий">
          <p v-if="!rosterMismatch && placeError" class="form-error">{{ placeError }}</p>
          <p v-if="powerError" class="form-error">{{ powerError }}</p>
          <p v-else-if="!rosterMismatch && !placeError && missingStage" class="muted">Укажите стадию для каждого места.</p>
          <p v-else-if="!rosterMismatch && !placeError && lines.length" class="ok-line">{{ lines.map((line, index) => summaryTitle(line, lines, index)).join(' · ') }}</p>
        </div>
        <p v-if="!rosterMismatch && placeError" class="form-error desk-error">{{ placeError }}</p>
        <p v-if="powerError" class="form-error desk-error">{{ powerError }}</p>
        <p v-else-if="missingStage" class="muted desk-error">Укажите стадию для каждого места. Проведите по местам 1–2 и выберите стадию для всех сразу.</p>
        <p v-if="error && error !== placeError" class="form-error">{{ error }}</p>
        <div v-if="confirming" class="confirm">
          <p>Сетка и результаты плей-офф будут сброшены. Правила выхода останутся.</p>
          <div class="actions">
            <button class="btn danger" type="button" :disabled="pending" @click="rebuild">Пересобрать</button>
            <button class="btn secondary" type="button" :disabled="pending" @click="confirming = false">Отмена</button>
          </div>
        </div>
        <button v-if="locked" class="btn secondary" type="button" :disabled="pending" @click="confirming = true">
          Пересобрать сетку
        </button>
        <button v-else class="btn" type="button" :disabled="!canSave" @click="save">Сохранить</button>
      </div>
    </template>
  </section>
</template>

<style scoped>
h2 { font-size: 1.2rem; margin-bottom: 0.35rem; }
h3 {
  margin: 0 0 0.45rem;
  font-size: 0.82rem;
  letter-spacing: 0.06em;
  color: var(--navy);
}
.muted { color: var(--muted); margin: 0 0 0.8rem; }
.empty { margin: 0.4rem 0 1rem; }
.empty a { color: var(--ice); font-weight: 800; }
.banner {
  display: flex;
  align-items: center;
  gap: 0.55rem;
  margin: 0 0 0.7rem;
  padding: 0.7rem 0.85rem;
  border-radius: 12px;
  background: #e6ebf4;
  color: #00205b;
  font-weight: 800;
}
.banner svg {
  width: 1.15rem;
  height: 1.15rem;
  flex: 0 0 auto;
  stroke: currentColor;
  fill: none;
  stroke-width: 1.8;
  stroke-linecap: round;
  stroke-linejoin: round;
}
.summary.desk {
  position: sticky;
  top: var(--chrome-h);
  z-index: 8;
  display: flex;
  flex-wrap: wrap;
  gap: 0.45rem;
  margin: 0 0 0.9rem;
  padding: 0.55rem;
  background: #fff;
  border: 1px solid var(--line);
  border-radius: 12px;
}
.sum {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  min-height: 2rem;
  padding: 0.2rem 0.55rem;
  border-radius: 999px;
  background: #f4f7fa;
  font-size: 0.82rem;
}
.sum i {
  width: 0.55rem;
  height: 0.55rem;
  border-radius: 50%;
  background: var(--muted);
}
.sum.ok i { background: var(--qualify-sum-ok); }
.sum.bad i { background: var(--qualify-sum-bad); }
.sum.bad,
.sum.bad b,
.sum.bad span { color: var(--danger); font-weight: 800; }
.sum b { font-size: 0.78rem; }
.sr { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0); }
.block { margin: 0 0 1rem; }
.qrow {
  display: grid;
  grid-template-columns: 2rem minmax(0, 1fr) minmax(9rem, 14rem);
  gap: 0.5rem;
  align-items: center;
  min-height: 44px;
  padding: 0.25rem 0.7rem 0.25rem 0.85rem;
  border-bottom: 1px solid var(--line);
  box-shadow: inset var(--q-bar-width, 0px) 0 0 var(--q-bar, transparent);
  background: #fff;
}
.qrow.on { background: color-mix(in srgb, var(--q-bar, var(--stage-r8)) 14%, #fff); }
.qrow.gone {
  background: #fff1ef;
  box-shadow: inset 4px 0 0 var(--danger);
}
.gone-label {
  color: var(--danger);
  font-weight: 800;
  white-space: normal;
}
.mismatch { margin: 0 0 0.8rem; font-weight: 800; }
.drop { min-height: 44px; }
.place, .team { touch-action: none; }
.place { font-weight: 800; color: var(--navy); }
.team {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.qrow.gone .team {
  color: var(--danger);
  font-weight: 800;
  white-space: normal;
  overflow: visible;
}
select {
  min-height: 44px;
  width: 100%;
  border: 1px solid var(--line-strong);
  border-radius: 10px;
  background: #fff;
  color: var(--navy);
  font: inherit;
}
.is-locked .qrow {
  cursor: not-allowed;
  user-select: none;
}
.is-locked select,
select:disabled {
  color: var(--stage-silver-ink);
  -webkit-text-fill-color: var(--stage-silver-ink);
  background-color: var(--stage-silver-row);
  background-image: none;
  border-color: #e1e6ec;
  cursor: not-allowed;
  opacity: 1;
  appearance: none;
  -webkit-appearance: none;
}
.dock { display: grid; gap: 0.55rem; margin-top: 0.4rem; }
.dock > .form-error {
  color: var(--danger);
  font-weight: 800;
}
.summary.phone, .desk-error { margin: 0; }
.summary.phone { display: none; }
.confirm {
  display: grid;
  gap: 0.55rem;
  padding: 0.75rem;
  border-radius: 12px;
  background: #fff4f2;
}
.confirm p { margin: 0; }
.actions { display: flex; gap: 0.45rem; flex-wrap: wrap; }
.actions .btn { min-height: 44px; }
.skel { display: grid; gap: 0.8rem; }
.skel-block, .skel-title, .skel-row {
  border-radius: 12px;
  background: linear-gradient(90deg, #eef2f6 0%, #f7f9fb 50%, #eef2f6 100%);
  background-size: 200% 100%;
  animation: pulse 1.2s ease-in-out infinite;
}
.skel-title { height: 1rem; width: 40%; margin-bottom: 0.45rem; }
.skel-row { height: 44px; margin-bottom: 0.35rem; }
.ok-line { margin: 0; color: #00205b; font-weight: 800; }
@keyframes pulse {
  0% { background-position: 100% 0; }
  100% { background-position: -100% 0; }
}
@media (max-width: 720px) {
  .summary.desk { position: static; }
  .summary.phone { display: block; }
  .desk-error { display: none; }
  .dock {
    position: sticky;
    bottom: 0;
    z-index: 5;
    margin: 0.4rem -0.2rem 0;
    padding: 0.7rem 0.2rem calc(0.7rem + env(safe-area-inset-bottom));
    background: #fff;
    border-top: 1px solid var(--line);
  }
  .dock .btn { width: 100%; min-height: 44px; }
  .qrow { grid-template-columns: 1.6rem minmax(0, 1fr) minmax(7.5rem, 46%); }
}
</style>
