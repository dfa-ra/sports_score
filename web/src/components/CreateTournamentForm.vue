<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import api from '../api/client'
import { apiError } from '../lib/errors'

const emit = defineEmits<{ created: [tournament: any] }>()
const router = useRouter()
const sports = ref<any[]>([])
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

onMounted(async () => {
  const [{ data }, formatsRes] = await Promise.all([api.get('/sports'), api.get('/tournaments/formats')])
  sports.value = data
  formats.value = formatsRes.data
  if (!sportId.value && data[0]) sportId.value = data[0].id
})

async function submit() {
  error.value = ''
  pending.value = true
  try {
    const { data } = await api.post('/tournaments', {
      name: name.value,
      description: description.value || undefined,
      sportId: sportId.value,
      seasonYear: Number(seasonYear.value),
      startDate: startDate.value || undefined,
      endDate: endDate.value || undefined,
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
  <form class="stack" @submit.prevent="submit">
    <label class="field">Название
      <input v-model="name" required maxlength="200" placeholder="Осенний кубок" />
    </label>
    <label class="field">Описание
      <textarea v-model="description" rows="3" placeholder="Коротко, без пафоса" />
    </label>
    <label class="field">Вид спорта
      <select v-model="sportId" required>
        <option v-for="s in sports" :key="s.id" :value="s.id">{{ s.name }}</option>
      </select>
    </label>
    <label class="field">Сезон
      <input v-model.number="seasonYear" type="number" min="2000" max="2100" required />
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
        <option value="FINISHED">Финал</option>
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
