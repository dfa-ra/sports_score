<script setup lang="ts">
import { RouterLink } from 'vue-router'
</script>

<template>
  <section class="lost rise">
    <div class="panel stack">
      <p class="eyebrow">Офсайд</p>
      <h1>Такой страницы нет</h1>
      <p>Либо свисток ещё не прозвучал, либо кто-то придумал свой маршрут. Вернёмся на поле.</p>
      <div class="cta">
        <RouterLink class="btn" to="/">На главную</RouterLink>
        <RouterLink class="btn secondary" to="/matches">К матчам</RouterLink>
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
