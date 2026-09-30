import type { RouteLocationNormalized, Router } from 'vue-router'

/** Public GA4 measurement id. Same class of value as a Google OAuth client id. */
export const GA_MEASUREMENT_ID = 'G-HQRVZQD9M1'

const fromEnv = (import.meta.env.VITE_GA_MEASUREMENT_ID || '').trim()
export const gaMeasurementId = fromEnv || GA_MEASUREMENT_ID

declare global {
  interface Window {
    dataLayer?: unknown[]
    gtag?: (...args: unknown[]) => void
  }
}

let installed = false

function gtag(...args: unknown[]) {
  window.dataLayer = window.dataLayer || []
  // gtag.js expects the Arguments object from the official snippet, not a copy.
  window.dataLayer.push(arguments)
  void args
}

/** Path plus query, without the password-reset token. */
function pagePath(to: RouteLocationNormalized): string {
  const params = new URLSearchParams()
  for (const [key, raw] of Object.entries(to.query)) {
    if (key === 'token') continue
    const values = Array.isArray(raw) ? raw : [raw]
    for (const value of values) {
      if (typeof value === 'string') params.append(key, value)
    }
  }
  const search = params.toString()
  return search ? `${to.path}?${search}` : to.path
}

/**
 * Official gtag.js snippet. The automatic first page_view is disabled so
 * router.afterEach is the only sender, including the initial navigation.
 * No-op in unit tests (Vite mode `test`).
 */
export function installAnalytics(router: Router) {
  if (installed) return
  if (import.meta.env.MODE === 'test') return
  if (typeof document === 'undefined') return
  const id = gaMeasurementId
  if (!id) return
  installed = true

  window.gtag = gtag as Window['gtag']
  gtag('js', new Date())
  gtag('config', id, { send_page_view: false })

  const script = document.createElement('script')
  script.async = true
  script.src = `https://www.googletagmanager.com/gtag/js?id=${encodeURIComponent(id)}`
  document.head.appendChild(script)

  router.afterEach((to) => {
    const path = pagePath(to)
    gtag('event', 'page_view', {
      page_path: path,
      page_location: `${window.location.origin}${path}`,
      page_title: document.title,
    })
  })
}
