<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'

const props = withDefaults(defineProps<{ title?: string; open?: boolean }>(), {
  title: 'Для админа',
  open: false,
})

const root = ref<HTMLDetailsElement | null>(null)

function applyOpen(value: boolean) {
  if (root.value) root.value.open = value
}

onMounted(() => applyOpen(props.open))
watch(() => props.open, (value) => applyOpen(!!value))
</script>

<template>
  <details ref="root" class="admin-only">
    <summary>{{ title }}</summary>
    <div class="admin-only-body stack">
      <slot />
    </div>
  </details>
</template>
