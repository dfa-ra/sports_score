const observers = new WeakMap<HTMLElement, ResizeObserver>()

function scrollerOf(el: HTMLElement) {
  const host = el.closest('.scroll-fade')
  const row = host?.firstElementChild
  return row instanceof HTMLElement ? row : null
}

function publish(el: HTMLElement) {
  scrollerOf(el)?.dispatchEvent(new Event('fit-label'))
}

export function fitLabel(el: HTMLElement) {
  const full = el.dataset.full || ''
  const row = scrollerOf(el)
  const host = el.parentElement
  if (!row || !host || getComputedStyle(row).display === 'none') {
    if (el.textContent !== full) el.textContent = full
    return
  }
  const room = Math.max(72, row.clientWidth - 96)
  const cap = el.dataset.fit === 'tab' ? 220 : room
  const max = Math.min(room, cap)
  const previous = el.textContent || ''
  el.textContent = full
  const labelFits = () => host.scrollWidth <= max && el.scrollWidth <= el.clientWidth + 1
  if (labelFits()) {
    if (previous !== full) publish(el)
    return
  }
  let lo = 1
  let hi = full.length
  let best = '…'
  while (lo <= hi) {
    const mid = Math.floor((lo + hi) / 2)
    const next = `${full.slice(0, mid).trimEnd()}…`
    el.textContent = next || '…'
    if (labelFits()) {
      best = el.textContent
      lo = mid + 1
    } else {
      hi = mid - 1
    }
  }
  if (el.textContent !== best) el.textContent = best
  if (previous !== el.textContent) publish(el)
}

function remember(el: HTMLElement, binding: { value: unknown; arg?: string }) {
  el.dataset.full = String(binding.value ?? '')
  if (binding.arg) el.dataset.fit = binding.arg
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
    const observer = new ResizeObserver(() => fitLabel(el))
    observer.observe(row)
    observers.set(el, observer)
    void document.fonts?.ready.then(() => fitLabel(el))
  },
  updated(el: HTMLElement, binding: { value: unknown; arg?: string }) {
    remember(el, binding)
    fitLabel(el)
  },
  unmounted(el: HTMLElement) {
    observers.get(el)?.disconnect()
    observers.delete(el)
  },
}
