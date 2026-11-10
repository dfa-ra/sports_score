import { onMounted, onUnmounted, type Ref } from 'vue'

/** Keep the active tab in view from the nearest edge. Do not center it. */
export function revealActive(scroller: HTMLElement | null | undefined) {
  if (!scroller) return
  const active = scroller.querySelector<HTMLElement>('.on')
  if (active && window.matchMedia('(max-width: 720px)').matches) {
    const pad = 16
    const host = scroller.getBoundingClientRect()
    const box = active.getBoundingClientRect()
    if (box.left < host.left + pad) scroller.scrollLeft -= host.left + pad - box.left
    else if (box.right > host.right - pad) scroller.scrollLeft += box.right - (host.right - pad)
  }
  const max = scroller.scrollWidth - scroller.clientWidth
  const overflow = max > 1
  scroller.classList.toggle('can-scroll', overflow)
  scroller.classList.toggle('can-scroll-left', overflow && scroller.scrollLeft > 2)
  scroller.classList.toggle('can-scroll-right', overflow && scroller.scrollLeft < max - 2)
}

export function bindActiveTab(scroller: Ref<HTMLElement | null>) {
  let observer: MutationObserver | null = null
  let resize: ResizeObserver | null = null
  let node: HTMLElement | null = null
  const run = () => revealActive(node)
  onMounted(() => {
    node = scroller.value
    if (!node) return
    observer = new MutationObserver(run)
    observer.observe(node, { attributes: true, subtree: true, attributeFilter: ['class'] })
    if (typeof ResizeObserver !== 'undefined') {
      resize = new ResizeObserver(run)
      resize.observe(node)
    }
    node.addEventListener('scroll', run, { passive: true })
    run()
    requestAnimationFrame(run)
  })
  onUnmounted(() => {
    observer?.disconnect()
    resize?.disconnect()
    node?.removeEventListener('scroll', run)
  })
}
