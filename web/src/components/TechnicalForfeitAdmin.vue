<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import api from '../api/client'
import { apiError } from '../lib/errors'
import { matchCardWhen } from '../lib/match'
import { moscowDateTimeLabel } from '../lib/moscowTime'
import {
  adminPlate,
  awardPreview,
  canAwardForfeit,
  isForfeit,
  isPlayoffMatch,
  PLAYOFF_MUTUAL_HINT,
} from '../lib/forfeit'

const props = defineProps<{
  match: any
  homeName: string
  awayName: string
}>()

const emit = defineEmits<{
  updated: [match: any]
}>()

const audit = ref<any>(null)
const open = ref(false)
const side = ref<'home' | 'away' | 'both'>('home')
const reason = ref<'no_show' | 'incomplete' | 'other'>('no_show')
const note = ref('')
const pending = ref(false)
const error = ref('')
const plateError = ref('')
const confirming = ref(false)

const view = computed(() => ({ ...(props.match || {}), ...(audit.value || {}) }))
const active = computed(() => isForfeit(view.value))
const playoff = computed(() => isPlayoffMatch(view.value))
const showAward = computed(() => canAwardForfeit(view.value))
const plate = computed(() => adminPlate(view.value, props.homeName, props.awayName))
const preview = computed(() => awardPreview(side.value, props.homeName, props.awayName))
const subtitle = computed(() => `${props.homeName}  —  ${props.awayName}  ·  ${matchCardWhen(view.value)}`)
const awardedWhen = computed(() => moscowDateTimeLabel(view.value.forfeitSetAt))

watch(() => props.match?.id, loadAudit, { immediate: true })
watch(() => props.match?.forfeit, loadAudit)

async function loadAudit() {
  if (!props.match?.id) return
  try {
    const { data } = await api.get(`/admin/matches/${props.match.id}`)
    audit.value = data
  } catch {
    audit.value = null
  }
}

function openDialog() {
  error.value = ''
  if (playoff.value && side.value === 'both') side.value = 'home'
  open.value = true
}

function closeDialog() {
  if (pending.value) return
  open.value = false
}

async function award() {
  if (pending.value || !props.match?.id) return
  pending.value = true
  error.value = ''
  try {
    const { data } = await api.post(`/admin/matches/${props.match.id}/forfeit`, {
      side: side.value,
      reason: reason.value,
      note: reason.value === 'other' ? note.value.trim() : null,
    })
    audit.value = data
    emit('updated', data)
    open.value = false
    plateError.value = ''
  } catch (err) {
    error.value = apiError(err, 'Не удалось засчитать техническое поражение')
  } finally {
    pending.value = false
  }
}

async function cancel() {
  if (pending.value || !props.match?.id) return
  pending.value = true
  plateError.value = ''
  try {
    const { data } = await api.post(`/admin/matches/${props.match.id}/forfeit/cancel`)
    audit.value = data
    confirming.value = false
    emit('updated', data)
  } catch (err) {
    plateError.value = apiError(err, 'Не удалось отменить техническое поражение')
  } finally {
    pending.value = false
  }
}
</script>

<template>
  <div class="tf">
    <button v-if="showAward" class="btn tf-open" type="button" @click="openDialog">Засчитать ТП</button>

    <div v-else-if="active" class="plate">
      <div>
        <p>{{ plate }}</p>
        <p v-if="view.forfeitSetBy?.name || awardedWhen" class="audit">
          <template v-if="view.forfeitSetBy?.name">{{ view.forfeitSetBy.name }}</template>
          <template v-if="view.forfeitSetBy?.name && awardedWhen"> · </template>
          <template v-if="awardedWhen">{{ awardedWhen }}</template>
        </p>
        <p v-if="plateError" class="form-error">{{ plateError }}</p>
        <p v-if="confirming" class="ask">Отменить техническое поражение? Счёт и статус матча вернутся к состоянию до засчёта.</p>
      </div>
      <div class="plate-actions">
        <button v-if="!confirming" class="btn secondary" type="button" :disabled="pending" @click="confirming = true">Отменить ТП</button>
        <template v-else>
          <button class="btn secondary" type="button" :disabled="pending" @click="confirming = false">Отмена</button>
          <button class="btn danger" type="button" :disabled="pending" @click="cancel">Отменить ТП</button>
        </template>
      </div>
    </div>

    <Teleport to="body">
      <div v-if="open" class="overlay" @click.self="closeDialog">
        <div class="dialog" role="dialog" aria-modal="true" aria-labelledby="tf-title">
          <h2 id="tf-title">Засчитать техническое поражение</h2>
          <p class="sub">{{ subtitle }}</p>

          <fieldset>
            <legend>Кому засчитать поражение</legend>
            <label><input v-model="side" type="radio" value="home" :disabled="pending" /> {{ homeName }}</label>
            <label><input v-model="side" type="radio" value="away" :disabled="pending" /> {{ awayName }}</label>
            <label :class="{ off: playoff }">
              <input v-model="side" type="radio" value="both" :disabled="pending || playoff" />
              Обоим (обоюдное)
            </label>
            <p v-if="playoff" class="hint">{{ PLAYOFF_MUTUAL_HINT }}</p>
          </fieldset>

          <fieldset>
            <legend>Причина</legend>
            <label><input v-model="reason" type="radio" value="no_show" :disabled="pending" /> Неявка</label>
            <label><input v-model="reason" type="radio" value="incomplete" :disabled="pending" /> Неполный состав</label>
            <label><input v-model="reason" type="radio" value="other" :disabled="pending" /> Другое</label>
            <label v-if="reason === 'other'" class="field">Комментарий, зрителям не показывается
              <textarea v-model="note" rows="2" maxlength="500" :disabled="pending" />
            </label>
          </fieldset>

          <p class="preview">{{ preview }}</p>
          <p v-if="error" class="form-error" role="alert">{{ error }}</p>
          <div class="actions">
            <button class="btn secondary" type="button" :disabled="pending" @click="closeDialog">Отмена</button>
            <button class="btn" type="button" :disabled="pending" @click="award">Засчитать</button>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<style scoped>
.tf { display: grid; gap: 0.55rem; }
.tf-open, .plate-actions .btn, .actions .btn { min-height: 44px; }
.plate {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 0.75rem;
  padding: 0.75rem 0.85rem;
  border-radius: 12px;
  background: #fff4f2;
  color: var(--navy);
}
.plate p { margin: 0; font-weight: 800; }
.audit, .ask { margin-top: 0.25rem !important; font-weight: 600 !important; color: var(--muted); font-size: 0.82rem; }
.plate-actions, .actions { display: flex; flex-wrap: wrap; gap: 0.45rem; }
.overlay {
  position: fixed;
  inset: 0;
  z-index: 40;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 1rem;
  background: rgba(0, 18, 51, 0.45);
}
.dialog {
  width: min(440px, 100%);
  max-height: min(92vh, 720px);
  overflow: auto;
  background: #fff;
  border-radius: 16px;
  padding: 1.1rem 1.15rem 1rem;
  display: grid;
  gap: 0.75rem;
}
.dialog h2 { margin: 0; font-size: 1.15rem; color: var(--navy); text-transform: none; letter-spacing: 0; }
.sub { margin: 0; color: var(--muted); font-weight: 650; }
fieldset {
  margin: 0;
  padding: 0;
  border: 0;
  display: grid;
  gap: 0.4rem;
}
legend { font-weight: 800; color: var(--navy); margin-bottom: 0.15rem; }
label { display: flex; align-items: center; gap: 0.45rem; min-height: 44px; font-weight: 650; }
label.off { color: var(--muted); }
.hint { margin: 0; color: var(--muted); font-size: 0.82rem; }
.field { display: grid; align-items: stretch; font-size: 0.85rem; color: var(--muted); }
textarea {
  width: 100%;
  margin-top: 0.25rem;
  border: 1px solid var(--line-strong);
  border-radius: 10px;
  padding: 0.55rem 0.7rem;
  font: inherit;
  color: var(--ink);
}
.preview { margin: 0; font-weight: 800; color: var(--navy); }
.actions { justify-content: flex-end; }
@media (max-width: 720px) {
  .overlay { align-items: flex-end; padding: 0; }
  .dialog { width: 100%; border-radius: 16px 16px 0 0; }
  .plate { flex-direction: column; }
  .plate-actions { width: 100%; }
  .plate-actions .btn { flex: 1; }
}
</style>
