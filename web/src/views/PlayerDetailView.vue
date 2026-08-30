<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import api from '../api/client'
import { labelOf, statusLabel } from '../lib/format'
import PlayerAvatar from '../components/PlayerAvatar.vue'
import { useAuthStore } from '../stores/auth'
import AdminOnly from '../components/AdminOnly.vue'
import CopyChip from '../components/CopyChip.vue'
import EmptyState from '../components/EmptyState.vue'

const auth = useAuthStore()

const route = useRoute()
const card = ref<any>(null)

const stats = computed(() => {
  const raw = card.value?.statistics
  if (!raw || typeof raw !== 'object') return []
  return Object.entries(raw).map(([key, value]) => ({ key, value }))
})

onMounted(async () => {
  const { data } = await api.get(`/players/${route.params.id}/card`)
  card.value = data
})
</script>

<template>
  <section v-if="card" class="stack">
    <div class="page-title">
      <PlayerAvatar
        :src="card.avatarUrl"
        :name="card.displayName || `${card.firstName} ${card.lastName}`"
        :size="72"
      />
      <h1>{{ card.displayName || `${card.firstName} ${card.lastName}` }}</h1>
      <p>{{ card.position || 'Игрок' }} · №{{ card.jerseyNumber ?? '—' }}</p>
      <p v-if="card.team">
        Команда:
        <RouterLink :to="`/teams/${card.team.id}`">{{ card.team.name }}</RouterLink>
      </p>
    </div>

    <div class="panel">
      <h2>Цифры</h2>
      <EmptyState v-if="!stats.length" title="Статистика молчит" text="События ещё не успели стать легендой." />
      <div v-else class="stats">
        <div v-for="item in stats" :key="item.key" class="stat">
          <span>{{ item.key }}</span>
          <strong>{{ item.value }}</strong>
        </div>
      </div>
    </div>

    <div class="panel stack">
      <h2>История матчей</h2>
      <EmptyState v-if="!card.matchHistory?.length" title="Пока без протокола" />
      <RouterLink
        v-for="m in card.matchHistory || []"
        :key="m.matchId"
        class="row"
        :to="`/matches/${m.matchId}`"
      >
        <span>{{ m.opponentName || 'Соперник' }}</span>
        <span>{{ m.homeScore }}:{{ m.awayScore }}</span>
        <span class="muted">{{ labelOf(statusLabel, m.status) }}</span>
      </RouterLink>
    </div>

    <AdminOnly v-if="auth.canManageLeague" title="Для админа">
      <CopyChip :value="String(card.id)" label="Скопировать id игрока" />
    </AdminOnly>
  </section>
</template>

<style scoped>
h2 { font-size: 1.2rem; margin-bottom: 0.5rem; }
.stats { display: grid; grid-template-columns: repeat(auto-fill, minmax(140px, 1fr)); gap: 0.7rem; }
.stat {
  border: 1px solid var(--line);
  border-radius: 14px 11px 13px 10px;
  padding: 0.75rem 0.85rem;
  background: color-mix(in srgb, var(--navy) 55%, transparent);
}
.stat span { display: block; color: var(--muted); font-size: 0.75rem; text-transform: uppercase; letter-spacing: 0.05em; }
.stat strong { font-family: var(--font-display); font-size: 1.3rem; }
.row {
  display: grid;
  grid-template-columns: 1fr auto auto;
  gap: 0.8rem;
  padding: 0.7rem 0.1rem;
  border-bottom: 1px solid var(--line);
  color: var(--text-strong);
  text-decoration: none;
}
.row:hover { color: var(--accent); }
</style>
