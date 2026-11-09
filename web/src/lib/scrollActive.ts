import { onMounted, onUnmounted, type Ref } from 'vue'

/** Keep the active tab inside a horizontal scroller and mark overflow for the edge fade. */
export function revealActive(scroller: HTMLElement | null | undefined) {
  if (!scroller) return
  const overflow = scroller.scrollWidth > scroller.clientWidth + 1
  if (scroller.classList.contains('can-scroll') !== overflow) {
    scroller.classList.toggle('can-scroll', overflow)
  }
  const active = scroller.querySelector<HTMLElement>('.on')
  if (!active) return
  const host = scroller.getBoundingClientRect()
  const box = active.getBoundingClientRect()
  const edge = 36
  if (box.left < host.left + 2) {
    scroller.scrollLeft -= host.left + edge - box.left
  } else if (box.right > host.right - 2) {
    scroller.scrollLeft += box.right - (host.right - edge)
  }
}

export function bindActiveTab(scroller: Ref<HTMLElement | null>) {
  let observer: MutationObserver | null = null
  let resize: ResizeObserver | null = null
  onMounted(() => {
    const node = scroller.value
    if (!node) return
    const run = () => revealActive(node)
    observer = new MutationObserver(run)
    observer.observe(node, { attributes: true, subtree: true, attributeFilter: ['class'] })
    if (typeof ResizeObserver !== 'undefined') {
      resize = new ResizeObserver(run)
      resize.observe(node)
    }
    run()
    requestAnimationFrame(run)
  })
  onUnmounted(() => {
    observer?.disconnect()
    resize?.disconnect()
  })
}
