<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import api from '../api/client'
import { apiError } from '../lib/errors'
import { tournamentDatesError } from '../lib/format'

const emit = defineEmits<{ created: [tournament: any] }>()
const router = useRouter()
const name = ref('')
const description = ref('')
const sportId = ref('')
const seasonYear = ref(new Date().getFullYear())
const startDate = ref('')
const endDate = ref('')
const status = ref('REGISTRATION')
const format = ref('ROUND_ROBIN')
const regulations = ref('')
const maxSquadSize = ref(18)
const formats = ref<any[]>([])
const pending = ref(false)
const error = ref('')

function footballId(list: { id?: string; code?: string }[]) {
  const football = list.find((sport) => sport.code === 'FOOTBALL')
  return football?.id || list[0]?.id || ''
}

onMounted(async () => {
  const [{ data }, formatsRes] = await Promise.all([api.get('/sports'), api.get('/tournaments/formats')])
  formats.value = formatsRes.data
  sportId.value = footballId(data ?? [])
})

async function submit(event: Event) {
  error.value = ''
  const form = event.currentTarget as HTMLFormElement
  const dates = [...form.querySelectorAll('input[type="date"]')] as HTMLInputElement[]
  const start = dates[0]?.value || startDate.value
  const end = dates[1]?.value || endDate.value
  startDate.value = start
  endDate.value = end
  const dateError = tournamentDatesError(start, end)
  if (dateError) {
    error.value = dateError
    return
  }
  if (!sportId.value) {
    error.value = 'Форма не готова, обновите страницу.'
    return
  }
  pending.value = true
  try {
    const { data } = await api.post('/tournaments', {
      name: name.value,
      description: description.value || undefined,
      sportId: sportId.value,
      seasonYear: Number(seasonYear.value),
      startDate: start || undefined,
      endDate: end || undefined,
      status: status.value,
      format: format.value,
      regulations: regulations.value || undefined,
      maxSquadSize: Number(maxSquadSize.value) || undefined,
    })
    emit('created', data)
    router.push(`/tournaments/${data.id}`)
  } catch (e: any) {
    error.value = apiError(e, 'Турнир не создался.')
  } finally {
    pending.value = false
  }
}
</script>

<template>
  <form class="stack" novalidate @submit.prevent="submit">
    <label class="field">Название
      <input v-model="name" maxlength="200" placeholder="Осенний кубок" />
    </label>
    <label class="field">Описание
      <textarea v-model="description" rows="3" placeholder="Коротко, без пафоса" />
    </label>
    <label class="field">Сезон
      <input v-model.number="seasonYear" type="number" min="2000" max="2100" />
    </label>
    <label class="field">Старт
      <input v-model="startDate" type="date" />
    </label>
    <label class="field">Финиш
      <input v-model="endDate" type="date" />
    </label>
    <label class="field">Статус
      <select v-model="status">
        <option value="DRAFT">Черновик</option>
        <option value="REGISTRATION">Набор команд</option>
        <option value="ACTIVE">Идёт</option>
        <option value="FINISHED">Завершён</option>
        <option value="CANCELLED">Отменён</option>
      </select>
    </label>
    <label class="field">Формат
      <select v-model="format">
        <option v-for="f in formats" :key="f.code" :value="f.code">{{ f.title }}</option>
      </select>
    </label>
    <label class="field">Регламент
      <textarea v-model="regulations" rows="4" placeholder="Лимит заявки, таймы, дисквалификации…" />
    </label>
    <label class="field">Максимум в заявке на турнир
      <input v-model.number="maxSquadSize" type="number" min="1" max="40" />
    </label>
    <p v-if="error" class="form-error">{{ error }}</p>
    <button class="btn" type="submit" :disabled="pending">{{ pending ? 'Печатаем сетку…' : 'Создать турнир' }}</button>
  </form>
</template>
