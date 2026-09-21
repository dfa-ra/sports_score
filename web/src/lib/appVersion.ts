const version = import.meta.env.VITE_APP_VERSION || '0.2.10'
const revision = import.meta.env.VITE_APP_REVISION || 'local'
const channel = import.meta.env.VITE_APP_CHANNEL || 'local'

export const appVersion = version
export const appRevision = revision
export const appChannel = channel

/** Short build label for the footer, e.g. `v0.2.10 · 4bd85f3`. */
export const appVersionLabel = revision && revision !== 'local'
  ? `v${version} · ${revision}`
  : `v${version}`

export const appVersionTitle = `${channel} · v${version} · ${revision}`
