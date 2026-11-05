const version = import.meta.env.VITE_APP_VERSION || '0.2.62'
const revision = import.meta.env.VITE_APP_REVISION || 'local'
const channel = import.meta.env.VITE_APP_CHANNEL || 'local'

export const appVersion = version
export const appRevision = revision
export const appChannel = channel

/** Footer label: version only, without the commit hash. */
export const appVersionLabel = `v${version}`

export const appVersionTitle = `v${version}`
