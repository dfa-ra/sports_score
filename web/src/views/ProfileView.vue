<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useAuthStore } from '../stores/auth'
import api from '../api/client'
import { apiError } from '../lib/errors'
import { labelOf, roleLabel } from '../lib/format'

const auth = useAuthStore()
const firstName = ref('')
const lastName = ref('')
const displayName = ref('')
const jerseyNumber = ref<number | null>(null)
const position = ref('')
const bio = ref('')
const pending = ref(false)
const error = ref('')
const ok = ref('')
const exists = ref(false)

onMounted(async () => {
  try {
    const { data } = await api.get('/players/me')
    exists.value = true
    firstName.value = data.firstName || ''
    lastName.value = data.lastName || ''
    displayName.value = data.displayName || ''
    jerseyNumber.value = data.jerseyNumber
    position.value = data.position || ''
    bio.value = data.bio || ''
  } catch {
    exists.value = false
  }
})

async function submit() {
  error.value = ''
  ok.value = ''
  pending.value = true
  try {
    await api.put('/players/me', {
      firstName: firstName.value,
      lastName: lastName.value,
      displayName: displayName.value || undefined,
      jerseyNumber: jerseyNumber.value || undefined,
      position: position.value || undefined,
      bio: bio.value || undefined,
    })
    await auth.refreshMe()
    exists.value = true
    ok.value = 'Профиль сохранён. Теперь можно собирать команду.'
  } catch (e: any) {
    error.value = apiError(e, 'Профиль не сохранился.')
  } finally {
    pending.value = false
  }
}
</script>

<template>
  <section class="stack">
    <div class="page-title">
      <p class="eyebrow">Кабинет</p>
      <h1>Профиль</h1>
      <p>Роль: {{ labelOf(roleLabel, auth.role) }}. Чтобы создать команду, нужен профиль игрока.</p>
    </div>
    <div class="panel stack">
      <form class="stack" @submit.prevent="submit">
        <label class="field">Имя<input v-model="firstName" required maxlength="100" /></label>
        <label class="field">Фамилия<input v-model="lastName" required maxlength="100" /></label>
        <label class="field">Как писать на майке<input v-model="displayName" maxlength="150" /></label>
        <label class="field">Номер<input v-model.number="jerseyNumber" type="number" min="0" max="99" /></label>
        <label class="field">Позиция<input v-model="position" maxlength="64" placeholder="Нападающий" /></label>
        <label class="field">О себе<textarea v-model="bio" rows="3" /></label>
        <p v-if="error" class="form-error">{{ error }}</p>
        <p v-if="ok" class="form-ok">{{ ok }}</p>
        <button class="btn" type="submit" :disabled="pending">
          {{ pending ? 'Сохраняем…' : exists ? 'Обновить профиль' : 'Стать игроком' }}
        </button>
      </form>
    </div>
  </section>
</template>
