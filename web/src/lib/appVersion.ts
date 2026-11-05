const env = import.meta.env ?? {}
const version = env.VITE_APP_VERSION || '0.2.63'
const revision = env.VITE_APP_REVISION || 'local'
const channel = env.VITE_APP_CHANNEL || 'local'

export const appVersion = version
export const appRevision = revision
export const appChannel = channel

/**
 * Footer text. `VITE_APP_CHANNEL=prod` (the release build) keeps the version only.
 * Dev, CI, and local builds keep the short commit hash when one was baked in.
 */
export function footerVersionLabel(version: string, revision: string, channel: string) {
  const showRevision = channel !== 'prod' && !!revision && revision !== 'local'
  return showRevision ? `v${version} · ${revision}` : `v${version}`
}

export function footerVersionTitle(version: string, revision: string, channel: string) {
  if (channel === 'prod') return `v${version}`
  return `${channel} · v${version} · ${revision}`
}

export const appVersionLabel = footerVersionLabel(version, revision, channel)
export const appVersionTitle = footerVersionTitle(version, revision, channel)
