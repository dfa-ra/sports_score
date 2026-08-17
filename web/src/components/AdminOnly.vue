<script setup lang="ts">
withDefaults(defineProps<{ title?: string }>(), {
  title: 'Для админа',
})
</script>

<template>
  <details class="admin-only">
    <summary>{{ title }}</summary>
    <div class="admin-only-body stack">
      <slot />
    </div>
  </details>
</template>
