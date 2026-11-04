<script setup lang="ts">
import PlayerAvatar from '../PlayerAvatar.vue'

defineProps<{
  src?: string | null
  name?: string | null
  firstName?: string | null
  lastName?: string | null
}>()
</script>

<template>
  <PlayerAvatar
    :src="src"
    :name="name"
    :first-name="firstName"
    :last-name="lastName"
    :size="36"
    tone="wash"
  />
</template>
