<script setup lang="ts">
import { nextTick, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { setPageMeta } from '../lib/pageMeta'

defineProps<{ heading?: string }>()

const route = useRoute()
const router = useRouter()

function applyTitle() {
  setPageMeta({
    title: 'Не найдено',
    description: 'Страница не найдена.',
    path: route.fullPath,
  })
}

function back() {
  if (window.history.length > 1) router.back()
  else void router.push('/')
}

onMounted(() => { void nextTick(applyTitle) })
watch(() => route.fullPath, () => { void nextTick(applyTitle) })
</script>

<template>
  <section class="lost rise">
    <div class="panel stack">
      <h1>{{ heading || 'Не найдено' }}</h1>
      <div class="cta">
        <button class="btn" type="button" @click="back">Назад</button>
      </div>
    </div>
  </section>
</template>

<style scoped>
.lost { display: grid; place-items: center; min-height: calc(100vh - 160px); }
.panel { width: min(460px, 100%); border-radius: 26px 18px 24px 16px; }
h1 { font-size: clamp(2rem, 5vw, 2.8rem); }
.cta { display: flex; gap: 0.65rem; flex-wrap: wrap; margin-top: 0.4rem; }
</style>
