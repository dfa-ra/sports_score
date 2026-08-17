<script setup lang="ts">
import { computed, ref, watch } from 'vue'

const props = defineProps<{
  side: any
  editable?: boolean
  pending?: boolean
}>()

const emit = defineEmits<{
  save: [payload: { teamId: string; starterPlayerIds: string[]; benchPlayerIds: string[] }]
}>()

const selected = ref<string[]>([])

const roster = computed(() => [
  ...(props.side?.starters ?? []),
  ...(props.side?.bench ?? []),
])

watch(
  () => props.side,
  (side) => {
    selected.value = (side?.starters ?? []).map((p: any) => p.playerId)
  },
  { immediate: true },
)

function toggle(id: string) {
  if (selected.value.includes(id)) {
    selected.value = selected.value.filter((x) => x !== id)
  } else {
    selected.value = [...selected.value, id]
  }
}

function save() {
  const bench = roster.value
    .map((p: any) => p.playerId)
    .filter((id: string) => !selected.value.includes(id))
  emit('save', {
    teamId: props.side.teamId,
    starterPlayerIds: selected.value,
    benchPlayerIds: bench,
  })
}
</script>

<template>
  <div v-if="side" class="lineup">
    <header>
      <h3>{{ side.teamName }}</h3>
      <p class="muted">
        {{ side.confirmed ? 'Стартовый состав записан' : 'Капитан ещё не написал, кто выходит с первой минуты' }}
      </p>
    </header>

    <section>
      <h4>Основа</h4>
      <p v-if="!side.starters?.length && !editable" class="muted">Пока все в заявке, без «первых номеров».</p>
      <label v-for="p in (editable ? roster : side.starters)" :key="p.playerId" class="player" :class="{ starter: !editable || selected.includes(p.playerId) }">
        <input v-if="editable" type="checkbox" :checked="selected.includes(p.playerId)" @change="toggle(p.playerId)" />
        <span class="num">{{ p.jerseyNumber ?? '—' }}</span>
        <span>{{ p.name }}</span>
        <span class="muted">{{ p.position || 'универсал с пары' }}</span>
      </label>
    </section>

    <section v-if="!editable">
      <h4>Скамейка</h4>
      <p v-if="!side.bench?.length" class="muted">Пусто. Либо все в основе, либо ещё делают лабу.</p>
      <div v-for="p in side.bench" :key="p.playerId" class="player">
        <span class="num">{{ p.jerseyNumber ?? '—' }}</span>
        <span>{{ p.name }}</span>
        <span class="muted">{{ p.position || 'запас' }}</span>
      </div>
    </section>

    <button v-if="editable" class="btn" :disabled="pending || !selected.length" @click="save">
      Записать стартовых
    </button>
  </div>
</template>

<style scoped>
.lineup { display: grid; gap: 0.85rem; }
h3 { font-size: 1.15rem; margin: 0; }
h4 {
  margin: 0 0 0.4rem;
  font-size: 0.72rem;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--muted);
}
.player {
  display: grid;
  grid-template-columns: auto 36px 1fr auto;
  gap: 0.55rem;
  align-items: center;
  padding: 0.55rem 0.15rem;
  border-bottom: 1px solid var(--line);
}
.player.starter .num { color: var(--accent); }
.num {
  font-variant-numeric: tabular-nums;
  font-weight: 800;
  color: var(--text-strong);
}
</style>
