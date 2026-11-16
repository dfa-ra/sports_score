<script setup lang="ts">
import { computed } from 'vue'
import { standingFormSlots, type StandingFormMark, type StandingFormSlot } from '../lib/standings'

const props = defineProps<{
  form?: Array<StandingFormMark | string | null> | null
}>()

const slots = computed(() => standingFormSlots(props.form))

function letter(mark: Exclude<StandingFormSlot, null>) {
  const result = mark.result
  if (result === 'W' || result === 'В') return 'В'
  if (result === 'D' || result === 'Н') return 'Н'
  if (result === 'L' || result === 'П') return 'П'
  return ''
}

function kind(mark: Exclude<StandingFormSlot, null>) {
  const text = letter(mark)
  if (text === 'В') return 'win'
  if (text === 'Н') return 'draw'
  return 'loss'
}
</script>

<template>
  <span class="form-slots">
    <span
      v-for="(mark, index) in slots"
      :key="index"
      class="pip"
      :class="mark ? kind(mark) : 'blank'"
    >
      <b>{{ mark ? letter(mark) : '' }}</b>
      <i v-if="mark?.technical">ТП</i>
    </span>
  </span>
</template>

<style scoped>
.form-slots {
  display: inline-flex;
  flex: 0 0 auto;
  flex-wrap: nowrap;
  align-items: flex-start;
  justify-content: flex-end;
  gap: var(--standing-form-gap, 4px);
  width: var(--standing-form-col, 84px);
  min-width: var(--standing-form-col, 84px);
  max-width: var(--standing-form-col, 84px);
}
.pip {
  display: inline-flex;
  flex: 0 0 var(--standing-form-slot, 18px);
  flex-direction: column;
  align-items: center;
  gap: 1px;
  width: var(--standing-form-slot, 18px);
  min-width: var(--standing-form-slot, 18px);
  max-width: var(--standing-form-slot, 18px);
  overflow: visible;
}
.pip b {
  box-sizing: border-box;
  width: var(--standing-form-slot, 18px);
  height: var(--standing-form-slot, 18px);
  border-radius: 50%;
  display: grid;
  place-items: center;
  flex: 0 0 auto;
  min-width: var(--standing-form-slot, 18px);
  min-height: var(--standing-form-slot, 18px);
  font-size: 10px;
  font-weight: 800;
  line-height: 1;
  color: #fff;
}
.pip.win b { background: #1b8a4a; }
.pip.draw b { background: #c47b00; }
.pip.loss b { background: var(--danger); }
.pip.blank b {
  background: transparent;
  border: 1px solid #9aa8b8;
  color: transparent;
}
.pip i {
  box-sizing: border-box;
  width: auto;
  height: auto;
  padding: 0 3px;
  border-radius: 3px;
  background: var(--danger);
  color: #fff;
  font-style: normal;
  font-family: var(--font-display);
  font-size: 10px;
  font-weight: 700;
  line-height: 14px;
  letter-spacing: 0;
  text-align: center;
  white-space: nowrap;
}
</style>
