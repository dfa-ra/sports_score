<script setup lang="ts">
import { ref } from 'vue'

const props = withDefaults(defineProps<{ title?: string; open?: boolean }>(), {
  title: 'Для админа',
  open: false,
})

const opened = ref(props.open)

function onToggle(event: Event) {
  const details = event.currentTarget as HTMLDetailsElement
  opened.value = details.open
}
</script>

<template>
  <details class="admin-only" :open="opened" @toggle="onToggle">
    <summary>{{ title }}</summary>
    <div class="admin-only-body stack">
      <slot />
    </div>
  </details>
</template>
