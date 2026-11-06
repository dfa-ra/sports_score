<script setup lang="ts">
defineProps<{
  autocomplete: string
  maxlength?: number
}>()

const value = defineModel<string>({ required: true })
const visible = defineModel<boolean>('visible', { default: false })
const emit = defineEmits<{ input: [] }>()
</script>

<template>
  <span class="pass-field">
    <input
      v-model="value"
      :type="visible ? 'text' : 'password'"
      :maxlength="maxlength ?? 100"
      :autocomplete="autocomplete"
      @input="emit('input')"
    />
    <button
      class="peek"
      type="button"
      :aria-label="visible ? 'Скрыть пароль' : 'Показать пароль'"
      :aria-pressed="visible"
      @click="visible = !visible"
    >
      <svg v-if="!visible" viewBox="0 0 24 24" aria-hidden="true">
        <path d="M2 12s3.5-6 10-6 10 6 10 6-3.5 6-10 6S2 12 2 12z" />
        <circle cx="12" cy="12" r="2.5" />
      </svg>
      <svg v-else viewBox="0 0 24 24" aria-hidden="true">
        <path d="M3 3l18 18" />
        <path d="M10.6 6.2A10.8 10.8 0 0 1 12 6c6.5 0 10 6 10 6a18.4 18.4 0 0 1-3.1 3.7" />
        <path d="M6.2 6.8C3.7 8.5 2 12 2 12s3.5 6 10 6c1.2 0 2.4-.2 3.5-.7" />
        <path d="M9.9 9.9a2.5 2.5 0 0 0 3.2 3.2" />
      </svg>
    </button>
  </span>
</template>

<style scoped>
.pass-field { position: relative; display: block; width: 100%; }
.pass-field input { width: 100%; padding-right: 2.7rem; }
.peek {
  position: absolute;
  top: 50%;
  right: 0.35rem;
  transform: translateY(-50%);
  width: 2.15rem;
  height: 2.15rem;
  display: grid;
  place-items: center;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: var(--muted);
  padding: 0;
  cursor: pointer;
}
.peek svg {
  width: 1.2rem;
  height: 1.2rem;
  stroke: currentColor;
  fill: none;
  stroke-width: 1.8;
  stroke-linecap: round;
  stroke-linejoin: round;
}
</style>
