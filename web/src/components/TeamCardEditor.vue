<script setup lang="ts">
import { ref, watch } from 'vue'
import api from '../api/client'
import { apiError } from '../lib/errors'
import { useTeamDirectory } from '../lib/useTeamDirectory'
import TeamCrest from './TeamCrest.vue'

const props = defineProps<{
  team: any
}>()
const emit = defineEmits<{ saved: [] }>()
const teams = useTeamDirectory()

const name = ref('')
const shortName = ref('')
const pending = ref(false)
const error = ref('')
const ok = ref('')

watch(
  () => props.team,
  (team) => {
    name.value = team?.name || ''
    shortName.value = team?.shortName || ''
  },
  { immediate: true },
)

async function save() {
  error.value = ''
  ok.value = ''
  pending.value = true
  try {
    await api.put(`/teams/${props.team.id}`, {
      name: name.value,
      shortName: shortName.value || undefined,
    })
    ok.value = 'Карточка обновлена.'
    await teams.load(true)
    emit('saved')
  } catch (e: any) {
    error.value = apiError(e, 'Не удалось сохранить карточку.')
  } finally {
    pending.value = false
  }
}

async function onLogo(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  error.value = ''
  ok.value = ''
  pending.value = true
  try {
    const form = new FormData()
    form.append('file', file)
    await api.post(`/uploads/teams/${props.team.id}/logo`, form)
    ok.value = 'Логотип обновлён.'
    await teams.load(true)
    emit('saved')
  } catch (e: any) {
    error.value = apiError(e, 'Логотип не загрузился.')
  } finally {
    pending.value = false
    input.value = ''
  }
}
</script>

<template>
  <div class="stack">
    <div class="logo-row">
      <TeamCrest :src="team.logoUrl" :name="shortName || name || team.name" :size="56" />
      <label class="field grow">
        Логотип
        <input type="file" accept="image/*" :disabled="pending" @change="onLogo" />
        <span class="field-hint">PNG или JPG. Сохраняется сразу.</span>
      </label>
    </div>
    <form class="stack" @submit.prevent="save">
      <label class="field">Название
        <input v-model="name" required maxlength="200" />
      </label>
      <label class="field">Короткое имя
        <input v-model="shortName" maxlength="32" placeholder="ЛП" />
      </label>
      <p v-if="error" class="form-error">{{ error }}</p>
      <p v-if="ok" class="form-ok">{{ ok }}</p>
      <button class="btn secondary" type="submit" :disabled="pending">
        {{ pending ? 'Сохраняем…' : 'Сохранить карточку' }}
      </button>
    </form>
  </div>
</template>

<style scoped>
.logo-row {
  display: flex;
  align-items: center;
  gap: 0.9rem;
}
.grow { flex: 1; min-width: 0; }
</style>
