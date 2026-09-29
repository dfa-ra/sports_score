<script setup lang="ts">
import { onUnmounted, ref, watch } from 'vue'
import api from '../api/client'

const playerId = defineModel<string>({ default: '' })
const props = defineProps<{
  excludeIds?: string[]
}>()

const query = ref('')
const players = ref<any[]>([])
const pending = ref(false)
const open = ref(false)
let timer: number | undefined

function nameOf(player: any) {
  return (player.displayName || `${player.firstName || ''} ${player.lastName || ''}`).trim() || 'Игрок'
}

async function search() {
  const q = query.value.trim()
  if (q.length < 2) {
    players.value = []
    open.value = false
    pending.value = false
    return
  }
  pending.value = true
  try {
    const { data } = await api.get('/players', { params: { q, size: 8, sort: 'lastName,asc' } })
    const skip = new Set(props.excludeIds ?? [])
    players.value = (data.content ?? []).filter((player: any) => !skip.has(player.id))
    open.value = true
  } finally {
    pending.value = false
  }
}

function schedule() {
  playerId.value = ''
  if (timer) window.clearTimeout(timer)
  timer = window.setTimeout(search, 250)
}

function pick(player: any) {
  playerId.value = player.id
  query.value = nameOf(player)
  open.value = false
}

onUnmounted(() => {
  if (timer) window.clearTimeout(timer)
})
watch(() => (props.excludeIds ?? []).join(','), () => {
  if (playerId.value && (props.excludeIds ?? []).includes(playerId.value)) {
    playerId.value = ''
    query.value = ''
  }
})
</script>

<template>
  <div class="picker">
    <input
      v-model="query"
      type="search"
      placeholder="Начните вводить имя"
      autocomplete="off"
      @input="schedule"
      @focus="open = players.length > 0"
    />
    <p v-if="pending" class="muted">Ищем…</p>
    <div v-else-if="open && query.trim().length >= 2" class="hints">
      <p v-if="!players.length" class="muted">Никого не нашли</p>
      <button
        v-for="player in players"
        :key="player.id"
        type="button"
        class="hint"
        :class="{ on: player.id === playerId }"
        @click="pick(player)"
      >
        {{ nameOf(player) }}
      </button>
    </div>
  </div>
</template>

<style scoped>
.picker { display: grid; gap: 0.35rem; min-width: min(100%, 260px); flex: 1 1 240px; }
.hints { display: grid; gap: 0.25rem; }
.hint {
  text-align: left;
  padding: 0.45rem 0.65rem;
  border: 1px solid var(--line);
  border-radius: 10px;
  background: #fff;
  cursor: pointer;
}
.hint.on { border-color: var(--ice); background: #f3fbff; }
</style>
