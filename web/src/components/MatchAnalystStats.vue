<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import api from '../api/client'
import { analystRows } from '../lib/matchPresentation'

const props = defineProps<{ matchId: string }>()
const stats = ref<any>(null)
const ready = ref(false)
const failed = ref(false)
let timer = 0

const view = computed(() => (stats.value ? analystRows(stats.value) : null))

async function load() {
  if (!props.matchId) {
    stats.value = null
    ready.value = true
    failed.value = false
    return
  }
  try {
    const { data } = await api.get(`/matches/${props.matchId}/analyst-stats`)
    stats.value = data
    failed.value = false
  } catch {
    stats.value = null
    failed.value = true
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
  failed.value = false
  load()
})
</script>

<template>
  <section class="panel analyst">
    <h2>Статистика</h2>
    <p v-if="!ready" class="muted quiet">Загрузка…</p>
    <div v-else-if="failed" class="fail">
      <strong>Статистика не загрузилась</strong>
      <button class="btn secondary" type="button" @click="load">Повторить</button>
    </div>
    <template v-else-if="view">
      <div v-for="row in view.rows" :key="row.label" class="line">
        <strong>{{ row.home }}</strong>
        <span>
          {{ row.label }}
          <small v-if="row.hint">{{ row.hint }}</small>
        </span>
        <strong>{{ row.away }}</strong>
      </div>
      <div class="poss">
        <template v-if="view.possession.kind === 'nums'">
          <div class="bar">
            <i class="home" :style="{ width: view.possession.home + '%' }" />
            <i class="away" :style="{ width: view.possession.away + '%' }" />
          </div>
          <div class="pct">
            <b>{{ view.possession.home }}%</b>
            <span>Владение</span>
            <b>{{ view.possession.away }}%</b>
          </div>
        </template>
        <p v-else class="quiet">Владение — нет данных</p>
      </div>
    </template>
  </section>
</template>

<style scoped>
.analyst { display: grid; gap: 0.15rem; }
.quiet { margin: 0; font-size: 0.9rem; }
.fail { display: grid; justify-items: center; gap: 0.6rem; padding: 0.8rem 0; }
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
