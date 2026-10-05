<script setup lang="ts">
import { computed, nextTick, onUnmounted, ref, watch } from 'vue'

export type SquadOption = {
  playerId: string
  teamId: string
  teamName: string
  label: string
}

const model = defineModel<string>({ default: '' })
const props = defineProps<{
  options: SquadOption[]
  emptyLabel?: string
  fallback?: string
  disabled?: boolean
}>()

const open = ref(false)
const query = ref('')
const root = ref<HTMLElement | null>(null)
const searchEl = ref<HTMLInputElement | null>(null)

const currentLabel = computed(() => {
  if (!model.value) return props.emptyLabel || 'Выберите игрока'
  return props.options.find((row) => row.playerId === model.value)?.label
    || props.fallback
    || 'Игрок'
})

const grouped = computed(() => {
  const needle = query.value.trim().toLowerCase()
  const buckets = new Map<string, { teamId: string; teamName: string; rows: SquadOption[] }>()
  for (const row of props.options) {
    if (needle && !row.label.toLowerCase().includes(needle) && !row.teamName.toLowerCase().includes(needle)) {
      continue
    }
    const bucket = buckets.get(row.teamId) ?? { teamId: row.teamId, teamName: row.teamName, rows: [] }
    bucket.rows.push(row)
    buckets.set(row.teamId, bucket)
  }
  return [...buckets.values()]
})

function pick(playerId: string) {
  model.value = playerId
  open.value = false
  query.value = ''
}

function onDoc(event: Event) {
  if (!root.value?.contains(event.target as Node)) open.value = false
}

watch(open, async (isOpen) => {
  document.removeEventListener('pointerdown', onDoc)
  if (!isOpen) return
  query.value = ''
  window.setTimeout(() => document.addEventListener('pointerdown', onDoc), 0)
  await nextTick()
  searchEl.value?.focus()
})

onUnmounted(() => document.removeEventListener('pointerdown', onDoc))
</script>

<template>
  <div ref="root" class="squad">
    <button type="button" class="current" :disabled="disabled" @click="open = !open">
      <span>{{ currentLabel }}</span>
      <span class="chevron" aria-hidden="true">▾</span>
    </button>
    <div v-if="open" class="pop">
      <input
        ref="searchEl"
        v-model="query"
        type="search"
        placeholder="Фамилия или имя"
        autocomplete="off"
      />
      <button
        v-if="emptyLabel"
        type="button"
        class="choice"
        :class="{ on: !model }"
        @click="pick('')"
      >{{ emptyLabel }}</button>
      <p v-if="!grouped.length" class="muted">Никого не нашли</p>
      <section v-for="group in grouped" :key="group.teamId">
        <p class="team">{{ group.teamName }}</p>
        <button
          v-for="row in group.rows"
          :key="row.playerId"
          type="button"
          class="choice"
          :class="{ on: row.playerId === model }"
          @click="pick(row.playerId)"
        >{{ row.label }}</button>
      </section>
    </div>
  </div>
</template>

<style scoped>
.squad { position: relative; }
.current {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.4rem;
  text-align: left;
  border-radius: var(--radius-sm);
  border: 1px solid var(--line-strong);
  background: var(--white);
  color: var(--ink);
  padding: 0.78rem 0.9rem;
  cursor: pointer;
}
.current span:first-child {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.current:disabled { opacity: 0.6; cursor: default; }
.chevron { color: var(--muted); font-size: 0.75rem; }
.pop {
  position: absolute;
  z-index: 30;
  top: calc(100% + 4px);
  left: 0;
  min-width: 100%;
  width: max-content;
  max-width: 18rem;
  max-height: 16rem;
  overflow: auto;
  display: grid;
  gap: 0.25rem;
  padding: 0.45rem;
  border: 1px solid var(--line-strong);
  border-radius: 12px;
  background: #fff;
  box-shadow: 0 10px 28px rgba(15, 35, 64, 0.14);
}
.pop input { width: 100%; }
.team {
  margin: 0.35rem 0 0.1rem;
  font-size: 0.72rem;
  font-weight: 800;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  color: var(--muted);
}
.choice {
  display: block;
  width: 100%;
  text-align: left;
  border: 0;
  background: transparent;
  border-radius: 8px;
  padding: 0.4rem 0.5rem;
  cursor: pointer;
  color: var(--ink);
}
.choice:hover, .choice.on { background: #f3fbff; }
.muted { margin: 0.2rem 0.4rem; font-size: 0.85rem; }
</style>
