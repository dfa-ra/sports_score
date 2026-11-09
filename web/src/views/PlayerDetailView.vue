<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import api from '../api/client'
import PlayerCardPanel from '../components/PlayerCardPanel.vue'
import { setPageMeta } from '../lib/pageMeta'
import { playerHeader } from '../lib/playerTitle'
import { useAuthStore } from '../stores/auth'
import AdminOnly from '../components/AdminOnly.vue'
import CopyChip from '../components/CopyChip.vue'
import NotFoundView from './NotFoundView.vue'

const auth = useAuthStore()
const route = useRoute()
const card = ref<any>(null)
const missing = ref(false)
const failed = ref(false)

async function load() {
  const id = String(route.params.id || '')
  missing.value = false
  failed.value = false
  card.value = null
  try {
    const { data } = await api.get(`/players/${id}/card`)
    if (String(route.params.id || '') !== id) return
    card.value = data
    const name = playerHeader(data).title
    const team = String(data.team?.name || '').trim()
    setPageMeta({
      title: name,
      description: team ? `Игрок Лиги ИТМО: ${name}, ${team}` : `Игрок Лиги ИТМО: ${name}`,
      path: `/players/${id}`,
    })
  } catch (error: any) {
    if (String(route.params.id || '') !== id) return
    const status = error?.response?.status
    if (status === 400 || status === 404) {
      card.value = null
      missing.value = true
      return
    }
    failed.value = true
  }
}

onMounted(load)
watch(() => route.params.id, load)
</script>

<template>
  <NotFoundView v-if="missing" heading="Игрок не найден" />
  <section v-else-if="failed" class="stack page">
    <strong>Игрок не загрузился</strong>
    <button class="btn secondary" type="button" @click="load">Повторить</button>
  </section>
  <section v-else-if="card" class="stack page">
    <PlayerCardPanel :card="card" />
    <AdminOnly v-if="auth.canManageLeague" title="Для админа">
      <CopyChip :value="String(card.id)" label="Скопировать id игрока" />
    </AdminOnly>
  </section>
</template>

<style scoped>
.page { gap: 0.75rem; }
</style>
