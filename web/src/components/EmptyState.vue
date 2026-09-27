<script setup lang="ts">
defineProps<{ title: string; text?: string }>()
</script>

<template>
  <div class="empty">
    <strong>{{ title }}</strong>
    <p v-if="text">{{ text }}</p>
    <slot />
  </div>
</template>
