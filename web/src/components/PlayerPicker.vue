<script setup lang="ts">
import { onMounted, onUnmounted, ref, watch } from 'vue'
import api from '../api/client'

const playerId = defineModel<string>({ default: '' })
const props = defineProps<{
  excludeIds?: string[]
}>()

const query = ref('')
const players = ref<any[]>([])
const pending = ref(false)
let timer: number | undefined

function nameOf(player: any) {
  return (player.displayName || `${player.firstName || ''} ${player.lastName || ''}`).trim() || 'Игрок'
}

async function search() {
  pending.value = true
  try {
    const { data } = await api.get('/players', {
      params: { size: 40, q: query.value.trim() || undefined, sort: 'lastName,asc' },
    })
    const skip = new Set(props.excludeIds ?? [])
    players.value = (data.content ?? []).filter((player: any) => !skip.has(player.id))
    if (!players.value.some((player: any) => player.id === playerId.value)) {
      playerId.value = players.value[0]?.id || ''
    }
  } finally {
    pending.value = false
  }
}

function schedule() {
  if (timer) window.clearTimeout(timer)
  timer = window.setTimeout(search, 250)
}

onMounted(search)
onUnmounted(() => {
  if (timer) window.clearTimeout(timer)
})
watch(() => (props.excludeIds ?? []).join(','), search)
</script>

<template>
  <div class="picker">
    <input
      v-model="query"
      type="search"
      placeholder="Поиск по имени"
      autocomplete="off"
      @input="schedule"
    />
    <select v-model="playerId" required :disabled="!players.length">
      <option v-if="!players.length" value="" disabled>
        {{ pending ? 'Ищем…' : query.trim() ? 'Никого не нашли' : 'Игроков пока нет' }}
      </option>
      <option v-for="player in players" :key="player.id" :value="player.id">
        {{ nameOf(player) }}
      </option>
    </select>
  </div>
</template>

<style scoped>
.picker {
  display: grid;
  gap: 0.45rem;
  min-width: min(100%, 260px);
  flex: 1 1 240px;
}
</style>
