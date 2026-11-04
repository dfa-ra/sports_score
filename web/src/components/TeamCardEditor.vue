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

const nameMax = 150
const name = ref('')
const shortName = ref('')
const pending = ref(false)
const error = ref('')
const ok = ref('')
const logoInput = ref<HTMLInputElement | null>(null)
const logoFileName = ref('')

function saveError(e: any) {
  const details = e?.response?.data?.details
  if (Array.isArray(details)) {
    const nameError = details.find((item) => item?.field === 'name' && typeof item?.message === 'string' && item.message)
    if (nameError) return nameError.message as string
  }
  return apiError(e, 'Не удалось сохранить карточку.')
}

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
  const trimmed = name.value.trim()
  if (!trimmed) {
    error.value = 'Укажите название команды.'
    return
  }
  if (trimmed.length > nameMax) {
    error.value = 'Название не длиннее 150 символов.'
    return
  }
  pending.value = true
  try {
    await api.put(`/teams/${props.team.id}`, {
      name: trimmed,
      shortName: shortName.value || undefined,
    })
    ok.value = 'Карточка обновлена.'
    await teams.load(true)
    emit('saved')
  } catch (e: any) {
    error.value = saveError(e)
  } finally {
    pending.value = false
  }
}

function openLogoPicker() {
  logoInput.value?.click()
}

async function onLogo(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  logoFileName.value = file.name
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
      <div class="field grow">
        <span>Логотип</span>
        <div class="logo-actions">
          <button class="btn secondary" type="button" :disabled="pending" @click="openLogoPicker">
            Загрузить логотип
          </button>
          <span class="file-caption">{{ logoFileName || 'Файл не выбран' }}</span>
        </div>
        <input
          ref="logoInput"
          class="logo-input"
          type="file"
          accept="image/*"
          aria-label="Файл логотипа"
          :disabled="pending"
          @change="onLogo"
        />
        <span class="field-hint">PNG или JPG. Сохраняется сразу.</span>
      </div>
    </div>
    <form class="stack" novalidate @submit.prevent="save">
      <label class="field">Название
        <input v-model="name" :maxlength="nameMax" />
        <span class="field-hint">{{ name.length }}/{{ nameMax }}</span>
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
.logo-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.65rem 0.85rem;
}
.logo-actions .btn { justify-self: start; }
.file-caption {
  color: var(--ink);
  font-size: 0.9rem;
  overflow-wrap: anywhere;
}
.logo-input {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}
</style>
