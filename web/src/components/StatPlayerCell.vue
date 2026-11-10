<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { RouterLink } from 'vue-router'
import PlayerAvatar from './PlayerAvatar.vue'
import TeamCrest from './TeamCrest.vue'
import FitName from './FitName.vue'
import { nameForms, registeredName, type PersonFields } from '../lib/playerTitle'
import { useTeamDirectory } from '../lib/useTeamDirectory'

const props = defineProps<{
  player: PersonFields & {
    playerId: string
    avatarUrl?: string | null
    teamLogoUrl?: string | null
    teamId?: string | null
    jerseyNumber?: number | null
    playerJersey?: number | null
  }
}>()

const teams = useTeamDirectory()
onMounted(() => { void teams.load() })

const label = computed(() => registeredName(props.player) || 'Игрок')
const forms = computed(() => nameForms(props.player))
const shirt = computed(() => {
  const raw = props.player.jerseyNumber ?? props.player.playerJersey
  if (raw == null || Number.isNaN(Number(raw))) return ''
  return `#${raw}`
})
const clubName = computed(() => {
  const id = props.player.teamId
  if (!id) return ''
  return teams.fullName(id, '')
})
const clubLogo = computed(() => props.player.teamLogoUrl || (props.player.teamId ? teams.logo(props.player.teamId) : ''))
</script>

<template>
  <RouterLink class="stat-player" :to="`/players/${player.playerId}`">
    <PlayerAvatar
      :src="player.avatarUrl"
      :name="label"
      :first-name="player.playerFirstName ?? player.firstName"
      :last-name="player.playerLastName ?? player.lastName"
      :size="40"
    />
    <span class="name-line">
      <span v-if="shirt" class="shirt">{{ shirt }}</span>
      <FitName class="name" :full="forms.full" :short="forms.short" />
    </span>
    <TeamCrest v-if="player.teamLogoUrl" class="scorer-crest" :src="player.teamLogoUrl" name="" :size="24" />
    <span v-if="clubLogo || clubName" class="club-line">
      <TeamCrest v-if="clubLogo" class="club-crest" :src="clubLogo" :name="clubName" :size="16" />
      <span v-if="clubName" class="club-name">{{ clubName }}</span>
    </span>
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
.name-line {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  flex: 0 1 auto;
}
.shirt {
  flex: 0 0 auto;
  font-variant-numeric: tabular-nums;
  font-weight: 800;
}
.name {
  flex: 1 1 auto;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.club-line { display: none; }
@media (max-width: 720px) {
  .stat-player {
    display: grid;
    grid-template-columns: auto minmax(0, 1fr);
    grid-template-areas: "av name" "av club";
    column-gap: 8px;
    row-gap: 2px;
    align-items: center;
    overflow: visible;
  }
  .stat-player :deep(.player-avatar) { grid-area: av; }
  .name-line { grid-area: name; }
  .scorer-crest { display: none; }
  .club-line {
    grid-area: club;
    display: flex;
    align-items: center;
    gap: 6px;
    min-width: 0;
  }
  .club-line :deep(.club-crest) {
    width: 16px !important;
    height: 16px !important;
    flex: 0 0 16px;
  }
  .club-name {
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    font-size: 11px;
    line-height: 14px;
    font-weight: 500;
    color: #5b6b82;
  }
  .stat-player :deep(.scorer-crest) { display: none; }
}
</style>
