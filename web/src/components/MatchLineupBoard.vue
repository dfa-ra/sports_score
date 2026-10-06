<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { registeredName } from '../lib/playerTitle'
import { useTeamDirectory } from '../lib/useTeamDirectory'
import TeamCrest from './TeamCrest.vue'

const props = defineProps<{
  side: any
  editable?: boolean
  pending?: boolean
}>()

const emit = defineEmits<{
  save: [payload: { teamId: string; starterPlayerIds: string[]; benchPlayerIds: string[] }]
}>()

const teams = useTeamDirectory()
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

function rowNote(player: any, onBench: boolean) {
  const position = String(player?.position || '').replace(/\s+/g, ' ').trim()
  const benchLabel = 'запас'
  if (!onBench) return position
  if (!position || position.toLocaleLowerCase('ru') === benchLabel) return benchLabel
  return `${position} · ${benchLabel}`
}
</script>

<template>
  <div v-if="side" class="lineup">
    <header>
      <h3>
        <TeamCrest :src="teams.logo(side.teamId)" :name="side.teamName" :size="22" />
        {{ side.teamName }}
      </h3>
      <p class="muted">
        {{ side.confirmed ? 'Стартовый состав записан' : 'Состав не подан' }}
      </p>
    </header>

    <section>
      <h4>Основа</h4>
      <p v-if="!side.starters?.length && !editable" class="muted">Стартовый состав не указан.</p>
      <component
        :is="editable ? 'label' : 'div'"
        v-for="p in (editable ? roster : side.starters)"
        :key="p.playerId"
        class="player"
        :class="{ starter: !editable || selected.includes(p.playerId), picking: editable }"
      >
        <input v-if="editable" type="checkbox" :checked="selected.includes(p.playerId)" @change="toggle(p.playerId)" />
        <span class="num">{{ p.jerseyNumber ?? '—' }}</span>
        <span class="name" :title="registeredName(p)">{{ registeredName(p) }}</span>
        <span class="meta" :title="rowNote(p, editable && !selected.includes(p.playerId))">
          {{ rowNote(p, editable && !selected.includes(p.playerId)) }}
        </span>
      </component>
    </section>

    <section v-if="!editable">
      <h4>Скамейка</h4>
      <p v-if="!side.bench?.length" class="muted">Скамейка пуста.</p>
      <div v-for="p in side.bench" :key="p.playerId" class="player">
        <span class="num">{{ p.jerseyNumber ?? '—' }}</span>
        <span class="name" :title="registeredName(p)">{{ registeredName(p) }}</span>
        <span class="meta" :title="rowNote(p, true)">{{ rowNote(p, true) }}</span>
      </div>
    </section>

    <button v-if="editable" class="btn" :disabled="pending || !selected.length" @click="save">
      Записать стартовых
    </button>
  </div>
</template>

<style scoped>
.lineup { display: grid; gap: 0.85rem; min-width: 0; }
h3 {
  display: flex;
  align-items: center;
  gap: 0.45rem;
  font-size: 1.15rem;
  margin: 0;
}
h4 {
  margin: 0 0 0.4rem;
  font-size: 0.72rem;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--muted);
}
.player {
  display: grid;
  grid-template-columns: 2.5rem minmax(0, 1fr) minmax(0, max-content);
  column-gap: 0.7rem;
  align-items: center;
  min-height: 2.35rem;
  padding: 0.28rem 0;
  border-bottom: 1px solid var(--line);
  min-width: 0;
}
.player.picking {
  grid-template-columns: 1.15rem 2.5rem minmax(0, 1fr) minmax(0, max-content);
}
.player input { margin: 0; }
.player.starter .num { color: var(--ice); }
.num {
  font-variant-numeric: tabular-nums;
  font-weight: 800;
  text-align: right;
  color: var(--navy);
}
.name {
  min-width: 0;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  color: var(--navy);
  font-weight: 700;
  line-height: 1.25;
}
.meta {
  max-width: 9.5rem;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  text-align: right;
  color: var(--muted);
  font-size: 0.78rem;
  font-weight: 600;
  line-height: 1.25;
}
</style>
