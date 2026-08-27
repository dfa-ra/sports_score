<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import api from '../api/client'
import { apiError } from '../lib/errors'

const emit = defineEmits<{ created: [team: any] }>()
const router = useRouter()
const name = ref('')
const shortName = ref('')
const foundedOn = ref('')
const captainPlayerId = ref('')
const players = ref<any[]>([])
const pending = ref(false)
const error = ref('')
const ok = ref('')

onMounted(async () => {
  const { data } = await api.get('/players', { params: { size: 100 } })
  players.value = data.content ?? []
  if (players.value[0]) captainPlayerId.value = players.value[0].id
})

async function submit() {
  error.value = ''
  ok.value = ''
  pending.value = true
  try {
    const { data } = await api.post('/teams', {
      name: name.value,
      shortName: shortName.value || undefined,
      captainPlayerId: captainPlayerId.value || undefined,
      foundedOn: foundedOn.value || undefined,
    })
    ok.value = 'Команда создана. Капитан может набирать состав.'
    name.value = ''
    shortName.value = ''
    emit('created', data)
    router.push(`/teams/${data.id}`)
  } catch (e: any) {
    error.value = apiError(e, 'Команду создаёт только админ.')
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
    <label class="field">Дата основания
      <input v-model="foundedOn" type="date" />
    </label>
    <label class="field">Капитан
      <select v-model="captainPlayerId">
        <option value="">Назначить позже</option>
        <option v-for="p in players" :key="p.id" :value="p.id">{{ p.displayName || `${p.firstName} ${p.lastName}` }}</option>
      </select>
    </label>
    <p v-if="error" class="form-error">{{ error }}</p>
    <p v-if="ok" class="form-ok">{{ ok }}</p>
    <button class="btn" type="submit" :disabled="pending">{{ pending ? 'Собираем состав…' : 'Создать команду' }}</button>
  </form>
</template>
