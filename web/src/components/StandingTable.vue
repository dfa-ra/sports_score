<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink } from 'vue-router'
import { initials } from '../lib/format'

const props = defineProps<{
  rows: any[]
  compact?: boolean
}>()

const ranked = computed(() =>
  [...props.rows].sort((a, b) =>
    (Number(b.points) - Number(a.points))
    || ((Number(b.goalsFor) - Number(b.goalsAgainst)) - (Number(a.goalsFor) - Number(a.goalsAgainst)))
    || (Number(b.goalsFor) - Number(a.goalsFor))
    || String(a.teamName || '').localeCompare(String(b.teamName || ''), 'ru')
  )
)

function rankClass(index: number) {
  if (index < 2) return 'ice'
  if (index < 4) return 'navy'
  return ''
}
</script>

<template>
  <div class="table-wrap">
    <table class="table dense">
      <thead>
        <tr>
          <th>#</th>
          <th>Команда</th>
          <th>И</th>
          <th v-if="!compact" class="wide">В</th>
          <th v-if="!compact" class="wide">Н</th>
          <th v-if="!compact" class="wide">П</th>
          <th>Г</th>
          <th>О</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="(row, i) in ranked" :key="row.teamId">
          <td>
            <span class="rank" :class="rankClass(i)">{{ i + 1 }}</span>
          </td>
          <td>
            <RouterLink class="club" :to="`/teams/${row.teamId}`">
              <i>{{ initials(row.teamName) }}</i>
              <b>{{ row.teamName }}</b>
            </RouterLink>
          </td>
          <td>{{ row.played }}</td>
          <td v-if="!compact" class="wide">{{ row.wins }}</td>
          <td v-if="!compact" class="wide">{{ row.draws }}</td>
          <td v-if="!compact" class="wide">{{ row.losses }}</td>
          <td>{{ row.goalsFor }}:{{ row.goalsAgainst }}</td>
          <td><strong>{{ row.points }}</strong></td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<style scoped>
.dense th, .dense td { padding: 0.58rem 0.35rem; }
.rank {
  width: 1.35rem;
  height: 1.35rem;
  display: grid;
  place-items: center;
  border-radius: 50%;
  font-size: 0.7rem;
  font-weight: 800;
  color: var(--muted);
}
.rank.ice { background: var(--ice); color: var(--navy); }
.rank.navy { background: var(--navy); color: #fff; }
.club {
  display: grid;
  grid-template-columns: 22px 1fr;
  gap: 0.45rem;
  align-items: center;
  color: inherit;
  min-width: 0;
}
.club i {
  width: 22px;
  height: 22px;
  border-radius: 5px;
  display: grid;
  place-items: center;
  background: var(--accent-soft);
  color: var(--navy);
  font-size: 0.52rem;
  font-style: normal;
  font-weight: 800;
}
.club b {
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
@media (max-width: 640px) {
  .wide { display: none; }
}
</style>
