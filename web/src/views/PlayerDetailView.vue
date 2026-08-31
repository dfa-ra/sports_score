<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import api from '../api/client'
import PlayerCardPanel from '../components/PlayerCardPanel.vue'
import { useAuthStore } from '../stores/auth'
import AdminOnly from '../components/AdminOnly.vue'
import CopyChip from '../components/CopyChip.vue'

const auth = useAuthStore()
const route = useRoute()
const card = ref<any>(null)

async function load() {
  const { data } = await api.get(`/players/${route.params.id}/card`)
  card.value = data
}

onMounted(load)
watch(() => route.params.id, load)
</script>

<template>
  <section v-if="card" class="stack page">
    <PlayerCardPanel :card="card" />
    <AdminOnly v-if="auth.canManageLeague" title="Для админа">
      <CopyChip :value="String(card.id)" label="Скопировать id игрока" />
    </AdminOnly>
  </section>
</template>

<style scoped>
.page { gap: 0.75rem; }
</style>
