import { SITE_NAME } from './brand'

type PageMeta = {
  title: string
  description: string
  path?: string
}

function ensureMeta(attr: 'name' | 'property', key: string, content: string) {
  const selector = `meta[${attr}="${key}"]`
  let el = document.head.querySelector(selector)
  if (!el) {
    el = document.createElement('meta')
    el.setAttribute(attr, key)
    document.head.appendChild(el)
  }
  el.setAttribute('content', content)
}

function ensureCanonical(href: string) {
  let el = document.head.querySelector('link[rel="canonical"]')
  if (!el) {
    el = document.createElement('link')
    el.setAttribute('rel', 'canonical')
    document.head.appendChild(el)
  }
  el.setAttribute('href', href)
}

export function setPageMeta(input: PageMeta) {
  const title = input.title.includes(SITE_NAME) ? input.title : `${input.title} — ${SITE_NAME}`
  document.title = title
  const origin = window.location.origin
  const path = input.path && input.path.length ? input.path : window.location.pathname
  const url = `${origin}${path.startsWith('/') ? path : `/${path}`}`
  const image = `${origin}/brand/logo-mark.png`
  ensureMeta('name', 'description', input.description)
  ensureMeta('property', 'og:title', title)
  ensureMeta('property', 'og:description', input.description)
  ensureMeta('property', 'og:url', url)
  ensureMeta('property', 'og:image', image)
  ensureMeta('property', 'og:type', 'website')
  ensureMeta('property', 'og:locale', 'ru_RU')
  ensureMeta('name', 'twitter:card', 'summary')
  ensureMeta('name', 'twitter:title', title)
  ensureMeta('name', 'twitter:description', input.description)
  ensureMeta('name', 'twitter:image', image)
  ensureCanonical(url.split('?')[0] || url)
}
