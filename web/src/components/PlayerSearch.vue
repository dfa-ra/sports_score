<script setup lang="ts">
import { onUnmounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import api from '../api/client'
import { playerHeader } from '../lib/playerTitle'
import PlayerAvatar from './PlayerAvatar.vue'

const query = ref('')
const items = ref<any[]>([])
const pending = ref(false)
const searched = ref(false)
let timer: number | undefined

function labelOf(player: any) {
  return playerHeader(player)
}

async function search() {
  const q = query.value.trim()
  if (q.length < 2) {
    items.value = []
    searched.value = false
    pending.value = false
    return
  }
  pending.value = true
  try {
    const { data } = await api.get('/players', { params: { q, size: 8, sort: 'lastName,asc' } })
    items.value = data.content ?? []
    searched.value = true
  } finally {
    pending.value = false
  }
}

function schedule() {
  if (timer) window.clearTimeout(timer)
  timer = window.setTimeout(search, 250)
}

onUnmounted(() => {
  if (timer) window.clearTimeout(timer)
})
</script>

<template>
  <div class="search">
    <label class="field">Имя
      <input
        v-model="query"
        type="search"
        placeholder="Начните вводить фамилию"
        autocomplete="off"
        @input="schedule"
      />
    </label>
    <p v-if="query.trim().length < 2" class="muted">Введите хотя бы 2 буквы — покажем подсказки.</p>
    <p v-else-if="pending" class="muted">Ищем…</p>
    <p v-else-if="searched && !items.length" class="muted">Никого не нашли</p>
    <div v-else-if="items.length" class="hints">
      <RouterLink v-for="player in items" :key="player.id" class="hint" :to="`/players/${player.id}`">
        <PlayerAvatar :src="player.avatarUrl" :name="labelOf(player).title" :size="32" />
        <span>
          <strong>{{ labelOf(player).title }}</strong>
          <small>
            <template v-if="labelOf(player).shirt">На майке {{ labelOf(player).shirt }} · </template>{{ player.position || 'Игрок' }} · №{{ player.jerseyNumber ?? '—' }}
          </small>
        </span>
      </RouterLink>
    </div>
  </div>
</template>

<style scoped>
.search { display: grid; gap: 0.6rem; }
.hints {
  display: grid;
  gap: 0.35rem;
}
.hint {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 0.7rem;
  align-items: center;
  padding: 0.55rem 0.7rem;
  border: 1px solid var(--line);
  border-radius: 14px;
  background: #fff;
  color: inherit;
  text-decoration: none;
}
.hint strong { display: block; }
.hint small { color: var(--muted); }
</style>
