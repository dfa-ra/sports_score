const observers = new WeakMap<HTMLElement, ResizeObserver>()
const applied = new WeakMap<HTMLElement, string>()

type MeasureContext = CanvasRenderingContext2D & { letterSpacing?: string }

let measureContext: MeasureContext | null | undefined
let ownWrites = 0

const frameJobs = new Set<() => void>()
let frame = 0

/** One animation frame for resize, font, and watch recalculations. */
export function scheduleFrame(job: () => void) {
  frameJobs.add(job)
  if (frame) return
  frame = requestAnimationFrame(() => {
    frame = 0
    const batch = [...frameJobs]
    frameJobs.clear()
    for (const run of batch) run()
  })
}

/** True while a label write from this module can still notify ResizeObserver. */
export function fitLabelOwnsResize() {
  return ownWrites > 0
}

function scrollerOf(el: HTMLElement) {
  const host = el.closest('.scroll-fade')
  const row = host?.firstElementChild
  return row instanceof HTMLElement ? row : null
}

function publish(el: HTMLElement) {
  scrollerOf(el)?.dispatchEvent(new Event('fit-label'))
}

function noteWrite() {
  ownWrites += 1
  requestAnimationFrame(() => {
    ownWrites -= 1
  })
}

function canvas(): MeasureContext | null {
  if (measureContext !== undefined) return measureContext
  if (typeof document === 'undefined') {
    measureContext = null
    return null
  }
  measureContext = document.createElement('canvas').getContext('2d') as MeasureContext | null
  return measureContext
}

function shownText(text: string, transform: string) {
  if (transform === 'uppercase') return text.toLocaleUpperCase('ru')
  if (transform === 'lowercase') return text.toLocaleLowerCase('ru')
  return text
}

function textWidth(el: HTMLElement, text: string, style = getComputedStyle(el)) {
  const shown = shownText(text, style.textTransform)
  const ctx = canvas()
  if (!ctx || !shown) return shown.length * 8
  ctx.font = style.font
  if ('letterSpacing' in ctx) ctx.letterSpacing = style.letterSpacing
  const width = ctx.measureText(shown).width
  if (!width) return shown.length * 8
  return width
}

/**
 * Longest string that fits. A min length above 1 forces an ellipsis and
 * returns an empty string when even that cannot fit.
 */
export function truncateToWidth(
  full: string,
  widthOf: (text: string) => number,
  max: number,
  minLength = 1,
) {
  if (minLength <= 1 && widthOf(full) <= max) return full
  let lo = 1
  let hi = full.length
  let best = ''
  while (lo <= hi) {
    const mid = Math.floor((lo + hi) / 2)
    const next = `${full.slice(0, mid).trimEnd()}…` || '…'
    if (next.length >= minLength && widthOf(next) <= max) {
      best = next
      lo = mid + 1
    } else hi = mid - 1
  }
  if (best) return best
  return minLength <= 1 ? '…' : ''
}

function applyText(el: HTMLElement, text: string) {
  const previous = applied.get(el)
  if (previous === text && (el.textContent || '') === text) return
  if ((el.textContent || '') !== text) {
    noteWrite()
    el.textContent = text
  }
  applied.set(el, text)
  if (previous !== undefined && previous !== text) publish(el)
}

function widthFrom(el: HTMLElement, basisText: string, basisWidth: number) {
  const style = getComputedStyle(el)
  const basis = textWidth(el, basisText, style)
  return (text: string) => basisWidth + textWidth(el, text, style) - basis
}

export function fitLabel(el: HTMLElement) {
  const full = el.dataset.full || ''
  const row = scrollerOf(el)
  const host = el.parentElement
  if (!row || !host || row.clientWidth < 1 || getComputedStyle(row).display === 'none') {
    applyText(el, full)
    return
  }
  const room = Math.max(72, row.clientWidth - 96)
  const cap = el.dataset.fit === 'tab' ? 220 : room
  const max = Math.min(room, cap)
  const basisText = el.textContent || ''
  const next = truncateToWidth(full, widthFrom(el, basisText, host.scrollWidth), max, 1)
  applyText(el, next)
}

/** Shorten one tab so its border box ends before the fade. Empty when it cannot. */
export function fitEdgeLabel(el: HTMLElement, limit: number) {
  const full = el.dataset.full || ''
  const host = el.parentElement
  if (!host || limit < 1) return false
  const basisText = el.textContent || ''
  const next = truncateToWidth(full, widthFrom(el, basisText, host.getBoundingClientRect().width), limit, 3)
  if (!next) return false
  applyText(el, next)
  return true
}

function remember(el: HTMLElement, binding: { value: unknown; arg?: string }) {
  el.dataset.full = String(binding.value ?? '')
  if (binding.arg) el.dataset.fit = binding.arg
}

const pendingFit = new Set<HTMLElement>()

function scheduleFit(el: HTMLElement) {
  if (fitLabelOwnsResize()) return
  pendingFit.add(el)
  scheduleFrame(() => {
    const batch = [...pendingFit]
    pendingFit.clear()
    for (const node of batch) {
      if (node.isConnected) fitLabel(node)
    }
  })
}

export const vFitText = {
  beforeMount(el: HTMLElement, binding: { value: unknown; arg?: string }) {
    remember(el, binding)
    el.textContent = el.dataset.full || ''
  },
  mounted(el: HTMLElement) {
    fitLabel(el)
    const row = scrollerOf(el)
    if (!row || observers.has(el)) return
    const observer = new ResizeObserver(() => scheduleFit(el))
    observer.observe(row)
    observers.set(el, observer)
    void document.fonts?.ready.then(() => scheduleFit(el))
  },
  updated(el: HTMLElement, binding: { value: unknown; arg?: string }) {
    const previous = el.dataset.full
    remember(el, binding)
    if (previous === el.dataset.full && (el.textContent || '') === applied.get(el)) return
    fitLabel(el)
  },
  unmounted(el: HTMLElement) {
    observers.get(el)?.disconnect()
    observers.delete(el)
    pendingFit.delete(el)
    applied.delete(el)
  },
}
