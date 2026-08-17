<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import api from '../api/client'
import { initials } from '../lib/format'
import CopyChip from '../components/CopyChip.vue'
import EmptyState from '../components/EmptyState.vue'

const route = useRoute()
const team = ref<any>(null)
const members = ref<any[]>([])

onMounted(async () => {
  const id = route.params.id
  const [t, m] = await Promise.all([api.get(`/teams/${id}`), api.get(`/teams/${id}/members`)])
  team.value = t.data
  members.value = m.data
})
</script>

<template>
  <section v-if="team" class="stack">
    <div class="page-title">
      <span class="crest">{{ initials(team.shortName || team.name) }}</span>
      <h1>{{ team.name }}</h1>
      <p>{{ team.shortName || 'Команда без аббревиатуры, но с характером.' }}</p>
      <CopyChip :value="String(team.id)" label="Скопировать id команды" />
    </div>
    <div class="panel stack">
      <h2>Состав</h2>
      <EmptyState v-if="!members.length" title="Раздевалка пуста" text="Капитан ещё собирает людей после пар." />
      <RouterLink
        v-for="m in members"
        :key="m.id"
        class="member"
        :to="`/players/${m.playerId || m.id}`"
      >
        <strong>{{ m.displayName || `${m.playerFirstName} ${m.playerLastName}` }}</strong>
        <span class="muted">№{{ m.jerseyNumber ?? '—' }}</span>
      </RouterLink>
    </div>
  </section>
</template>

<style scoped>
.crest {
  width: 48px;
  height: 48px;
  display: grid;
  place-items: center;
  border-radius: 16px 12px 14px 11px;
  background: var(--accent-soft);
  color: var(--accent);
  font-weight: 800;
  transform: rotate(-4deg);
}
h2 { font-size: 1.2rem; }
.member {
  display: flex;
  justify-content: space-between;
  gap: 1rem;
  padding: 0.75rem 0.1rem;
  border-bottom: 1px solid var(--line);
  color: var(--text-strong);
  text-decoration: none;
}
.member:hover { color: var(--accent); }
</style>
