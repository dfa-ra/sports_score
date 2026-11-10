<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import api from '../api/client'
import { apiError } from '../lib/errors'
import { stageMeta } from '../lib/qualifyStages'

const props = withDefaults(defineProps<{
  tournamentId: string
  refreshKey?: number
  canBuild?: boolean
  hint?: string
}>(), {
  canBuild: true,
  hint: '',
})
const emit = defineEmits<{ changed: [] }>()

type Side = {
  groupId: string
  groupName: string
  place: number
  seed: string
  teamName?: string | null
}
type Pair = {
  stage: string
  stageLabel: string
  roundNumber: number
  pairIndex: number
  home: Side | null
  away: Side | null
}

const loading = ref(true)
const locked = ref(false)
const pairs = ref<Pair[]>([])
const pending = ref(false)
const error = ref('')
const confirming = ref(false)

function keyOf(side: Side) {
  return `${side.groupId}:${side.place}`
}

function labelOf(side: Side) {
  return side.teamName ? `${side.seed} · ${side.teamName}` : side.seed
}

function optionsFor(pair: Pair) {
  const both = Boolean(pair.home && pair.away)
  const seen = new Set<string>()
  const options: Side[] = []
  for (const item of pairs.value) {
    if (item.stage !== pair.stage || Boolean(item.home && item.away) !== both) continue
    for (const side of [item.home, item.away]) {
      if (!side || seen.has(keyOf(side))) continue
      seen.add(keyOf(side))
      options.push(side)
    }
  }
  return options
}

function applyPayload(data: any) {
  locked.value = data?.locked === true || data?.bracketBuilt === true
  pairs.value = (Array.isArray(data?.proposal) ? data.proposal : []).map((pair: Pair) => ({
    ...pair,
    home: pair.home ? { ...pair.home } : null,
    away: pair.away ? { ...pair.away } : null,
  }))
}

async function load() {
  loading.value = true
  try {
    const { data } = await api.get(`/tournaments/${props.tournamentId}/playoff-qualify`)
    applyPayload(data)
  } catch (e: any) {
    error.value = apiError(e, 'Не удалось загрузить пары.')
  } finally {
    loading.value = false
  }
}

function move(pair: Pair, side: 'home' | 'away', nextKey: string) {
  const current = pair[side]
  if (!current || keyOf(current) === nextKey) return
  let holder: { pair: Pair; side: 'home' | 'away' } | null = null
  for (const item of pairs.value) {
    if (item.home && keyOf(item.home) === nextKey) holder = { pair: item, side: 'home' }
    if (item.away && keyOf(item.away) === nextKey) holder = { pair: item, side: 'away' }
  }
  if (!holder) return
  const incoming = holder.pair[holder.side]
  holder.pair[holder.side] = current
  pair[side] = incoming
}

async function build() {
  if (!props.canBuild) return
  pending.value = true
  error.value = ''
  try {
    await api.post(`/tournaments/${props.tournamentId}/playoff-qualify/pairs`, {
      confirm: true,
      pairs: pairs.value.map((pair) => ({
        stage: pair.stage,
        pairIndex: pair.pairIndex,
        homeGroupId: pair.home?.groupId ?? null,
        homePlace: pair.home?.place ?? null,
        awayGroupId: pair.away?.groupId ?? null,
        awayPlace: pair.away?.place ?? null,
      })),
    })
    confirming.value = false
    await load()
    emit('changed')
  } catch (e: any) {
    error.value = apiError(e, 'Не удалось собрать сетку.')
  } finally {
    pending.value = false
  }
}

onMounted(load)
watch(() => props.refreshKey, () => {
  confirming.value = false
  error.value = ''
  load()
})
</script>

<template>
  <section class="pairs">
    <h2>Пары</h2>
    <p v-if="loading" class="muted">Считаем посев…</p>
    <p v-else-if="locked" class="banner">Сетка уже сформирована. Пары меняются после пересборки.</p>
    <p v-else-if="!pairs.length && !hint" class="muted">Сначала сохраните выход из групп так, чтобы сводка была зелёной.</p>
    <template v-else>
      <p class="muted">Посев по местам. Команду в паре можно поменять, пока сетка не собрана.</p>
      <div v-for="pair in pairs" :key="`${pair.stage}-${pair.pairIndex}`" class="prow" :style="{ '--bar': stageMeta(pair.stage)?.color || '#d5dbe3' }">
        <span class="stage">{{ pair.stageLabel }}</span>
        <select
          v-if="pair.home"
          :value="keyOf(pair.home)"
          :aria-label="`${pair.stageLabel}, пара ${pair.pairIndex + 1}, хозяева`"
          @change="move(pair, 'home', ($event.target as HTMLSelectElement).value)"
        >
          <option v-for="side in optionsFor(pair)" :key="keyOf(side)" :value="keyOf(side)">{{ labelOf(side) }}</option>
        </select>
        <span v-else class="wait">Победитель</span>
        <span class="dash">—</span>
        <select
          v-if="pair.away"
          :value="keyOf(pair.away)"
          :aria-label="`${pair.stageLabel}, пара ${pair.pairIndex + 1}, гости`"
          @change="move(pair, 'away', ($event.target as HTMLSelectElement).value)"
        >
          <option v-for="side in optionsFor(pair)" :key="`a-${keyOf(side)}`" :value="keyOf(side)">{{ labelOf(side) }}</option>
        </select>
        <span v-else class="wait">Победитель</span>
      </div>
      <div class="dock">
        <p v-if="hint" class="form-error">{{ hint }}</p>
        <p v-if="error" class="form-error">{{ error }}</p>
        <div v-if="confirming" class="confirm">
          <p>Сетка соберётся по этим парам. Недоигранная группа останется посевом, например A1.</p>
          <div class="actions">
            <button class="btn" type="button" :disabled="pending || !canBuild" @click="build">Собрать</button>
            <button class="btn secondary" type="button" :disabled="pending" @click="confirming = false">Отмена</button>
          </div>
        </div>
        <button v-else class="btn" type="button" :disabled="pending || !canBuild" @click="confirming = true">Собрать сетку</button>
      </div>
    </template>
  </section>
</template>

<style scoped>
h2 { font-size: 1.2rem; margin: 1.2rem 0 0.35rem; }
.muted { color: var(--muted); margin: 0 0 0.8rem; }
.banner {
  margin: 0 0 0.7rem;
  padding: 0.7rem 0.85rem;
  border-radius: 12px;
  background: #fff6e8;
  color: #8a5a10;
  font-weight: 800;
}
.prow {
  display: grid;
  grid-template-columns: 4.5rem minmax(0, 1fr) auto minmax(0, 1fr);
  gap: 0.45rem;
  align-items: center;
  min-height: 44px;
  padding: 0.25rem 0.7rem 0.25rem 0.85rem;
  border-bottom: 1px solid var(--line);
  box-shadow: inset 4px 0 0 var(--bar, transparent);
  background: #fff;
}
.stage { font-weight: 800; color: var(--navy); font-size: 0.85rem; }
.dash { color: var(--muted); }
.wait { color: var(--muted); min-height: 44px; display: flex; align-items: center; }
select {
  min-height: 44px;
  width: 100%;
  border: 1px solid var(--line-strong);
  border-radius: 10px;
  background: #fff;
  color: var(--navy);
  font: inherit;
}
.dock { display: grid; gap: 0.55rem; margin-top: 0.7rem; }
.confirm {
  display: grid;
  gap: 0.55rem;
  padding: 0.75rem;
  border-radius: 12px;
  background: #fff4f2;
}
.confirm p { margin: 0; }
.actions { display: flex; gap: 0.45rem; flex-wrap: wrap; }
.actions .btn, .dock > .btn { min-height: 44px; }
@media (max-width: 720px) {
  .prow { grid-template-columns: 3.2rem minmax(0, 1fr); }
  .dash { display: none; }
  .dock > .btn { width: 100%; }
}
</style>
