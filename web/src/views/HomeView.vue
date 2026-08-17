<script setup lang="ts">
import { RouterLink } from 'vue-router'
</script>

<template>
  <section class="hero rise">
    <p class="eyebrow">Campus competition, live</p>
    <h1>Student League</h1>
    <p class="lede">
      Follow tournaments, live scores, and player stats across every campus sport —
      or step onto the pitch as captain, referee, or admin.
    </p>
    <div class="cta">
      <RouterLink class="btn" to="/matches">Live Matches</RouterLink>
      <RouterLink class="btn secondary" to="/tournaments">Browse Tournaments</RouterLink>
    </div>
  </section>
</template>

<style scoped>
.hero {
  min-height: calc(100vh - 120px);
  display: grid;
  align-content: center;
  gap: 1rem;
  padding: 2rem 0 4rem;
  background:
    linear-gradient(120deg, rgba(11, 31, 23, 0.2), rgba(11, 31, 23, 0.75)),
    url('https://images.unsplash.com/photo-1574629810360-7efbbe195018?auto=format&fit=crop&w=1800&q=80')
      center / cover no-repeat;
  border-radius: 28px;
  padding-inline: 1.5rem;
  border: 1px solid var(--line);
  overflow: hidden;
}
.eyebrow { text-transform: uppercase; letter-spacing: 0.18em; font-size: 0.75rem; color: var(--accent); margin: 0; }
h1 { font-size: clamp(3.5rem, 12vw, 7rem); line-height: 0.9; max-width: 8ch; }
.lede { max-width: 36rem; font-size: 1.1rem; }
.cta { display: flex; gap: 0.75rem; flex-wrap: wrap; margin-top: 0.5rem; }
</style>
