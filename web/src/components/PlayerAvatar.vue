<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { avatarPhotoCovers, personInitials, trimmedPhotoSrc } from '../lib/personAvatar'

const props = withDefaults(
  defineProps<{
    src?: string | null
    name?: string | null
    firstName?: string | null
    lastName?: string | null
    size?: number
    /** Accepted so older call sites compile. The photo is always a plain circle. */
    tile?: boolean
    /** Home scorers keep the wash circle; every other person avatar uses the accent circle. */
    tone?: 'accent' | 'wash'
  }>(),
  { size: 56, tile: false, tone: 'accent' },
)

const failed = ref(false)
const ready = ref(false)

watch(
  () => props.src,
  () => {
    failed.value = false
    ready.value = false
  },
)

const letters = computed(() => personInitials(props.firstName, props.lastName))
const photoSrc = computed(() => trimmedPhotoSrc(props.src))
const showPhoto = computed(() => photoSrc.value.length > 0 && !failed.value)
const covered = computed(() => avatarPhotoCovers(props.src, ready.value, failed.value))

function onLoad(event: Event) {
  const img = event.target as HTMLImageElement
  if (img.naturalWidth > 0) ready.value = true
  else failed.value = true
}

function onError() {
  failed.value = true
  ready.value = false
}

/** A cached image can finish before the load listener is attached. */
function rememberImg(el: unknown) {
  if (!(el instanceof HTMLImageElement) || !el.complete) return
  if (el.naturalWidth > 0) ready.value = true
  else failed.value = true
}

void props.tile
</script>

<template>
  <span
    class="player-avatar"
    :class="[tone, { 'is-covered': covered }]"
    :style="{ width: `${size}px`, height: `${size}px`, minWidth: `${size}px`, minHeight: `${size}px`, flex: `0 0 ${size}px` }"
  >
    <span class="letters" aria-hidden="true">{{ letters }}</span>
    <img
      v-if="showPhoto"
      :ref="rememberImg"
      :src="photoSrc"
      :alt="name || 'Фото'"
      :class="{ ready: covered }"
      @load="onLoad"
      @error="onError"
    />
  </span>
</template>

<style scoped>
.player-avatar {
  position: relative;
  display: grid;
  place-items: center;
  flex: 0 0 auto;
  box-sizing: border-box;
  padding: 0;
  border: 0;
  border-radius: 50%;
  overflow: hidden;
  background: var(--accent-soft);
  color: var(--accent);
  box-shadow: none;
  font-weight: 800;
  font-size: 0.95em;
  line-height: 1;
}
.player-avatar.wash {
  background: var(--bg-wash);
  color: var(--navy);
  box-shadow: inset 0 0 0 1px var(--line-strong);
  font-size: 0.75rem;
}
.letters {
  grid-area: 1 / 1;
  line-height: 1;
}
.player-avatar img {
  grid-area: 1 / 1;
  width: 100%;
  height: 100%;
  max-width: 100%;
  max-height: 100%;
  min-width: 0;
  min-height: 0;
  object-fit: contain;
  object-position: center;
  image-orientation: from-image;
  display: block;
  border: 0;
  opacity: 0;
}
.player-avatar img.ready {
  opacity: 1;
}
</style>
