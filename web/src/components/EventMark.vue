<script setup lang="ts">
import { computed } from 'vue'
import { eventMark, type MarkSource } from '../lib/eventMark'

const props = defineProps<{ event: MarkSource }>()
const mark = computed(() => eventMark(props.event))
</script>

<template>
  <i class="ev-mark" :class="mark.kind" :title="mark.label" aria-hidden="true" />
</template>

<style scoped>
.ev-mark {
  display: inline-block;
  flex: 0 0 auto;
  width: 10px;
  height: 14px;
  margin-top: 1px;
  border-radius: 1.5px;
  background: #9aa6b2;
  box-shadow: inset 0 0 0 1px rgba(0, 20, 40, 0.22);
}
.ev-mark.goal,
.ev-mark.penalty_scored {
  width: 14px;
  height: 14px;
  margin-top: 0;
  border-radius: 50%;
  background:
    radial-gradient(circle at 50% 50%, #fff 0 28%, transparent 29%),
    repeating-conic-gradient(#16325c 0 18deg, #fff 0 36deg);
  box-shadow: inset 0 0 0 1px #16325c;
}
.ev-mark.own_goal {
  width: 14px;
  height: 14px;
  margin-top: 0;
  border-radius: 50%;
  background: #c2410c;
  box-shadow: inset 0 0 0 2px #fff, inset 0 0 0 3px #c2410c;
}
.ev-mark.yellow_card { background: #f5c400; }
.ev-mark.red_card { background: #d11f2a; }
.ev-mark.second_yellow {
  width: 16px;
  background: linear-gradient(90deg, #f5c400 0 7px, transparent 7px 9px, #d11f2a 9px 16px);
  box-shadow: none;
}
.ev-mark.substitution {
  width: 14px;
  height: 14px;
  margin-top: 0;
  border-radius: 3px;
  background: var(--ice, #2f6fed);
}
.ev-mark.foul,
.ev-mark.timeout,
.ev-mark.assist,
.ev-mark.point,
.ev-mark.other,
.ev-mark.penalty_missed,
.ev-mark.period {
  width: 12px;
  height: 12px;
  margin-top: 2px;
  border-radius: 50%;
}
.ev-mark.foul { background: #e07a00; }
.ev-mark.timeout { background: #5b6b7c; }
.ev-mark.assist,
.ev-mark.point { background: var(--navy, #16325c); }
.ev-mark.period { width: 8px; height: 8px; background: #8ea0b3; }
.ev-mark.penalty_missed {
  background: #fff;
  box-shadow: inset 0 0 0 2px #d11f2a;
}
.ev-mark.other { background: #9aa6b2; }
</style>
