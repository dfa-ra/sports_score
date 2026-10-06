<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink } from 'vue-router'
import PlayerAvatar from './PlayerAvatar.vue'
import TeamCrest from './TeamCrest.vue'
import { registeredName, type PersonFields } from '../lib/playerTitle'

const props = defineProps<{
  player: PersonFields & {
    playerId: string
    avatarUrl?: string | null
    teamLogoUrl?: string | null
  }
}>()

const label = computed(() => registeredName(props.player) || 'Игрок')
</script>

<template>
  <RouterLink class="stat-player" :to="`/players/${player.playerId}`">
    <PlayerAvatar :src="player.avatarUrl" :name="label" :size="40" />
    <span class="name">{{ label }}</span>
    <TeamCrest v-if="player.teamLogoUrl" :src="player.teamLogoUrl" name="" :size="18" />
  </RouterLink>
</template>

<style scoped>
.stat-player {
  display: flex;
  align-items: center;
  gap: 0.45rem;
  min-width: 0;
  max-width: 100%;
  overflow: hidden;
  color: var(--navy);
  line-height: 1.2;
}
.stat-player :deep(.player-avatar),
.stat-player :deep(.team-crest) {
  flex: 0 0 auto;
}
.name {
  flex: 0 1 auto;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
