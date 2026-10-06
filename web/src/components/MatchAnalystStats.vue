<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import api from '../api/client'

const props = defineProps<{ matchId: string }>()
const stats = ref<any>(null)
const ready = ref(false)
let timer = 0

const visible = computed(() => {
  const current = stats.value
  if (!current) return false
  if (current.possessionTracked) return true
  return [current.home, current.away].some((side) => side && (
    side.shots || side.shotsOnTarget || side.saves || side.corners
    || side.fouls || side.freeKicks || side.kickIns || side.woodwork
  ))
})

type StatRow = { label: string; hint?: string; home: string | number; away: string | number }

const rows = computed<StatRow[]>(() => {
  const home = stats.value?.home ?? {}
  const away = stats.value?.away ?? {}
  return [
    {
      label: 'Удары',
      hint: 'в створ',
      home: `${home.shots ?? 0} / ${home.shotsOnTarget ?? 0}`,
      away: `${away.shots ?? 0} / ${away.shotsOnTarget ?? 0}`,
    },
    { label: 'Сейвы', home: home.saves ?? 0, away: away.saves ?? 0 },
    { label: 'Угловые', home: home.corners ?? 0, away: away.corners ?? 0 },
    { label: 'Фолы', home: home.fouls ?? 0, away: away.fouls ?? 0 },
    { label: 'Штрафные', home: home.freeKicks ?? 0, away: away.freeKicks ?? 0 },
    { label: 'Ауты', home: home.kickIns ?? 0, away: away.kickIns ?? 0 },
    { label: 'Каркас', home: home.woodwork ?? 0, away: away.woodwork ?? 0 },
  ]
})

const homeWidth = computed(() => {
  const total = (stats.value?.homePossessionSeconds ?? 0) + (stats.value?.awayPossessionSeconds ?? 0)
  if (!total) return 0
  return stats.value?.homePossessionPercent ?? 0
})

const awayWidth = computed(() => {
  const total = (stats.value?.homePossessionSeconds ?? 0) + (stats.value?.awayPossessionSeconds ?? 0)
  if (!total) return 0
  return stats.value?.awayPossessionPercent ?? 0
})

async function load() {
  if (!props.matchId) {
    stats.value = null
    ready.value = true
    return
  }
  try {
    const { data } = await api.get(`/matches/${props.matchId}/analyst-stats`)
    stats.value = data
  } catch {
    stats.value = null
  } finally {
    ready.value = true
  }
}

onMounted(() => {
  load()
  timer = window.setInterval(load, 4000)
})
onUnmounted(() => window.clearInterval(timer))
watch(() => props.matchId, () => {
  ready.value = false
  load()
})
</script>

<template>
  <section v-if="visible" class="panel analyst">
    <h2>Статистика</h2>
    <div v-for="row in rows" :key="row.label" class="line">
      <strong>{{ row.home }}</strong>
      <span>
        {{ row.label }}
        <small v-if="row.hint">{{ row.hint }}</small>
      </span>
      <strong>{{ row.away }}</strong>
    </div>
    <div v-if="stats.possessionTracked" class="poss">
      <div class="bar">
        <i class="home" :style="{ width: homeWidth + '%' }" />
        <i class="away" :style="{ width: awayWidth + '%' }" />
      </div>
      <div class="pct">
        <b>{{ stats.homePossessionPercent ?? 0 }}%</b>
        <span>Владение</span>
        <b>{{ stats.awayPossessionPercent ?? 0 }}%</b>
      </div>
    </div>
  </section>
  <section v-else-if="ready" class="panel">
    <p class="muted quiet">Статистики пока нет.</p>
  </section>
</template>

<style scoped>
.analyst { display: grid; gap: 0.15rem; }
.quiet { margin: 0; font-size: 0.9rem; }
h2 { font-size: 1.05rem; margin: 0 0 0.35rem; }
.line {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  gap: 0.75rem;
  align-items: center;
  padding: 0.38rem 0;
  border-top: 1px solid var(--line);
}
.line strong {
  font-variant-numeric: tabular-nums;
  color: var(--navy);
  font-weight: 800;
}
.line strong:first-child { text-align: right; }
.line span {
  text-align: center;
  color: var(--muted);
  font-size: 0.82rem;
  font-weight: 700;
}
.line small { display: block; font-weight: 650; font-size: 0.72rem; }
.poss { margin-top: 0.7rem; display: grid; gap: 0.35rem; }
.bar {
  display: flex;
  height: 12px;
  border-radius: 999px;
  overflow: hidden;
  background: #e6eef3;
}
.bar .home { background: var(--navy); }
.bar .away { background: var(--ice); }
.pct {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  color: var(--navy);
  font-variant-numeric: tabular-nums;
  font-weight: 800;
  font-size: 0.85rem;
}
.pct span { color: var(--muted); font-weight: 700; text-align: center; }
.pct b:last-child { text-align: right; }
</style>
