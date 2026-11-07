<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { googleClientId, loadGoogleIdentityScript } from '../lib/googleIdentity'

const props = withDefaults(defineProps<{ labeled?: boolean }>(), { labeled: false })
const emit = defineEmits<{ success: [idToken: string]; error: [message: string] }>()
const host = ref<HTMLElement | null>(null)

function pressOfficial() {
  if (!props.labeled) return
  const button = host.value?.querySelector('[role="button"]') as HTMLElement | null
  if (button) {
    button.click()
    return
  }
  emit('error', 'Не удалось загрузить вход через Google.')
}

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
  <div v-if="labeled" class="google-line-wrap">
    <button class="google-line" type="button" @click="pressOfficial">
      <span class="g-mark" aria-hidden="true">G</span>
      Войти через Google
    </button>
    <div v-if="googleClientId" ref="host" class="google-host ghost"></div>
  </div>
  <div v-else-if="googleClientId" ref="host" class="google-host"></div>
</template>

<style scoped>
.google-host { min-height: 44px; }
.google-line-wrap { position: relative; }
.google-line {
  width: 100%;
  min-height: 44px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 0.6rem;
  border: 1px solid #dadce0;
  background: #fff;
  color: #1f2a44;
  border-radius: 12px;
  font-weight: 700;
  cursor: pointer;
}
.g-mark {
  width: 18px;
  height: 18px;
  border-radius: 50%;
  display: inline-grid;
  place-items: center;
  font-weight: 800;
  color: #4285f4;
  border: 1px solid #dadce0;
  font-size: 0.8rem;
  line-height: 1;
}
.google-host.ghost {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  min-height: 0;
  overflow: hidden;
  opacity: 0;
  pointer-events: none;
}
</style>
