<script setup lang="ts">
defineProps<{ title: string; text?: string }>()
</script>

<template>
  <div class="empty">
    <strong>{{ title }}</strong>
    <p>{{ text || 'Пока тихо. Как раздевалка до разминки.' }}</p>
    <slot />
  </div>
</template>
