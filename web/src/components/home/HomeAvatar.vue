<script setup lang="ts">
import PlayerAvatar from '../PlayerAvatar.vue'

defineProps<{ src?: string | null; name?: string | null }>()
</script>

<template>
  <PlayerAvatar :src="src" :name="name" :size="36" tone="wash" />
</template>
