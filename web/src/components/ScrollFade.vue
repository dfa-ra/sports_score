<script setup lang="ts">
import { onMounted, onUnmounted, ref, watch } from 'vue'
import { fitLabel } from '../lib/fitLabel'

const props = withDefaults(defineProps<{
  arrows?: boolean
  fade?: string
  watchKey?: string
  /** At phone width, hide any chip that the scroller would slice. Wider layouts keep the soft edge. */
  solid?: boolean
}>(), {
  arrows: false,
  fade: '#ffffff',
  watchKey: '',
  solid: false,
})

const root = ref<HTMLElement | null>(null)
const left = ref(false)
const right = ref(false)
const narrow = ref(false)
const plateWidth = ref(40)

function phoneWidth() {
  narrow.value = window.matchMedia('(max-width: 480px)').matches
}

function row() {
  const child = root.value?.firstElementChild
  return child instanceof HTMLElement ? child : null
}

function edgeFit() {
  const node = row()
  if (!node || getComputedStyle(node).display === 'none') return
  const labels = [...node.querySelectorAll<HTMLElement>('[data-fit="tab"]')]
  for (const label of labels) fitLabel(label)
  const gutter = 46
  const host = node.getBoundingClientRect()
  for (const label of labels) {
    const button = label.parentElement
    if (!(button instanceof HTMLElement) || button.classList.contains('on')) continue
    const box = button.getBoundingClientRect()
    if (box.left < host.left + gutter || box.right <= host.right - gutter) continue
    const full = label.dataset.full || ''
    const limit = host.right - gutter - box.left
    if (limit < 64) break
    let lo = 2
    let hi = full.length
    let best = ''
    while (lo <= hi) {
      const mid = Math.floor((lo + hi) / 2)
      const next = `${full.slice(0, mid).trimEnd()}…`
      label.textContent = next
      if (next.length > 2 && button.getBoundingClientRect().width <= limit && label.scrollWidth <= label.clientWidth + 1) {
        best = next
        lo = mid + 1
      } else {
        hi = mid - 1
      }
    }
    if (best) label.textContent = best
    else fitLabel(label)
    break
  }
}

let fitting = false

function coverCutChips() {
  plateWidth.value = 40
  const node = row()
  if (!props.solid || !narrow.value || !node) return
  const host = node.getBoundingClientRect()
  let plateLeft = host.right - 40
  for (const button of node.querySelectorAll('button')) {
    if (!(button instanceof HTMLElement)) continue
    const box = button.getBoundingClientRect()
    if (box.width < 1 || box.right <= host.left + 1 || box.left >= host.right - 1) continue
    if (box.right > plateLeft + 0.5) plateLeft = Math.min(plateLeft, box.left)
  }
  plateWidth.value = Math.min(host.width, Math.max(40, host.right - plateLeft))
}

function update() {
  const node = row()
  if (!node || getComputedStyle(node).display === 'none') {
    left.value = false
    right.value = false
    return
  }
  if (!fitting) {
    fitting = true
    try {
      edgeFit()
      revealActive()
      coverCutChips()
    } finally { fitting = false }
  }
  left.value = node.scrollLeft > 2
  right.value = node.scrollLeft + node.clientWidth < node.scrollWidth - 2
}

function revealActive() {
  const node = row()
  if (!node) return
  const active = node.querySelector('.on')
  if (!(active instanceof HTMLElement)) return
  const gutter = 46
  const host = node.getBoundingClientRect()
  const box = active.getBoundingClientRect()
  if (box.left < host.left + gutter) node.scrollLeft -= host.left + gutter - box.left
  const next = active.getBoundingClientRect()
  const again = node.getBoundingClientRect()
  if (next.right > again.right - gutter) node.scrollLeft += next.right - (again.right - gutter)
}

function nudge(direction: number) {
  const node = row()
  if (!node) return
  node.scrollBy({ left: direction * Math.max(140, Math.round(node.clientWidth * 0.72)), behavior: 'smooth' })
}

function onScroll() {
  update()
}

function onResize() {
  phoneWidth()
  update()
}

let observer: ResizeObserver | null = null
let bound: HTMLElement | null = null

onMounted(() => {
  phoneWidth()
  bound = row()
  bound?.addEventListener('scroll', onScroll, { passive: true })
  bound?.addEventListener('fit-label', update)
  update()
  revealActive()
  observer = new ResizeObserver(() => update())
  if (bound) observer.observe(bound)
  window.addEventListener('resize', onResize)
})

onUnmounted(() => {
  observer?.disconnect()
  bound?.removeEventListener('scroll', onScroll)
  bound?.removeEventListener('fit-label', update)
  window.removeEventListener('resize', onResize)
})

watch(() => props.watchKey, () => {
  update()
  requestAnimationFrame(() => update())
}, { flush: 'post' })
</script>

<template>
  <div ref="root" class="scroll-fade" :class="{ 'show-left': left, 'show-right': right }">
    <slot />
    <span
      v-if="left"
      class="scroll-fade-edge left"
      :style="{ background: `linear-gradient(90deg, ${fade} 18%, transparent)` }"
    />
    <span
      v-if="right && solid && narrow"
      class="scroll-fade-plate right"
      :style="{ width: `${plateWidth}px`, background: fade }"
    />
    <span
      v-else-if="right"
      class="scroll-fade-edge right"
      :style="{ background: `linear-gradient(90deg, transparent, ${fade} 82%)` }"
    />
    <button
      v-if="arrows && left"
      class="scroll-fade-arrow prev"
      type="button"
      aria-label="Прокрутить назад"
      @click="nudge(-1)"
    >‹</button>
    <button
      v-if="arrows && right"
      class="scroll-fade-arrow next"
      type="button"
      aria-label="Прокрутить дальше"
      @click="nudge(1)"
    >›</button>
  </div>
</template>

<style scoped>
.scroll-fade { position: relative; min-width: 0; max-width: 100%; }
.scroll-fade-edge {
  position: absolute;
  top: 0;
  bottom: 0;
  width: 52px;
  pointer-events: none;
  z-index: 1;
}
.scroll-fade-edge.left { left: 0; }
.scroll-fade-edge.right { right: 0; }
.scroll-fade-plate {
  position: absolute;
  top: 0;
  bottom: 0;
  right: 0;
  z-index: 1;
  pointer-events: none;
}
.scroll-fade-arrow {
  position: absolute;
  top: 50%;
  z-index: 2;
  width: 28px;
  height: 28px;
  margin: 0;
  padding: 0;
  transform: translateY(-50%);
  border: 1px solid var(--line-strong);
  border-radius: 50%;
  background: #fff;
  color: var(--navy);
  font: 800 1.15rem/1 var(--font-display);
  box-shadow: 0 2px 8px rgba(0, 32, 91, 0.18);
  cursor: pointer;
}
.scroll-fade-arrow.prev { left: 4px; }
.scroll-fade-arrow.next { right: 4px; }
</style>
