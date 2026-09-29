<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { googleClientId, loadGoogleIdentityScript } from '../lib/googleIdentity'

const emit = defineEmits<{ success: [idToken: string]; error: [message: string] }>()
const host = ref<HTMLElement | null>(null)

onMounted(async () => {
  if (!googleClientId || !host.value) return
  try {
    await loadGoogleIdentityScript()
    const api = window.google?.accounts?.id
    if (!api || !host.value) {
      emit('error', 'Не удалось загрузить вход через Google.')
      return
    }
    api.initialize({
      client_id: googleClientId,
      callback: (response) => {
        if (!response.credential) {
          emit('error', 'Не удалось войти через Google.')
          return
        }
        emit('success', response.credential)
      },
    })
    const width = Math.min(400, Math.max(240, host.value.clientWidth || 320))
    api.renderButton(host.value, {
      type: 'standard',
      theme: 'outline',
      size: 'large',
      text: 'signin_with',
      shape: 'rectangular',
      width,
    })
  } catch {
    emit('error', 'Не удалось загрузить вход через Google.')
  }
})
</script>

<template>
  <div v-if="googleClientId" ref="host" class="google-host"></div>
</template>

<style scoped>
.google-host { min-height: 44px; }
</style>
