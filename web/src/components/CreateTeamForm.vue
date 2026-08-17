<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import api from '../api/client'
import { apiError } from '../lib/errors'

const emit = defineEmits<{ created: [team: any] }>()
const router = useRouter()
const name = ref('')
const shortName = ref('')
const pending = ref(false)
const error = ref('')
const ok = ref('')

async function submit() {
  error.value = ''
  ok.value = ''
  pending.value = true
  try {
    const { data } = await api.post('/teams', {
      name: name.value,
      shortName: shortName.value || undefined,
    })
    ok.value = 'Команда создана. Можно собирать состав.'
    name.value = ''
    shortName.value = ''
    emit('created', data)
    router.push(`/teams/${data.id}`)
  } catch (e: any) {
    error.value = apiError(e, 'Команда не создалась. Нужен профиль игрока — он на странице «Профиль».')
  } finally {
    pending.value = false
  }
}
</script>

<template>
  <form class="stack" @submit.prevent="submit">
    <label class="field">Название
      <input v-model="name" required maxlength="150" placeholder="ФК Общага" />
    </label>
    <label class="field">Короткое имя
      <input v-model="shortName" maxlength="32" placeholder="ОБЩ" />
    </label>
    <p v-if="error" class="form-error">{{ error }}</p>
    <p v-if="ok" class="form-ok">{{ ok }}</p>
    <button class="btn" type="submit" :disabled="pending">{{ pending ? 'Собираем состав…' : 'Создать команду' }}</button>
  </form>
</template>
