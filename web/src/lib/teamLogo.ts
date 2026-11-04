export function bareLogoUrl(value?: string | null) {
  return (value || '').trim().split('?')[0]
}

/**
 * Logo address after a successful upload.
 * A new path is used as returned. The same path gets a cache-buster so the
 * browser does not keep painting the previous file.
 */
export function logoUrlAfterUpload(previous: string | null | undefined, uploaded: string, now = Date.now()) {
  const next = uploaded.trim()
  if (!next) return (previous || '').trim()
  if (previous && bareLogoUrl(previous) === bareLogoUrl(next)) {
    const joiner = next.includes('?') ? '&' : '?'
    return `${next}${joiner}v=${now}`
  }
  return next
}

/** Store, page header, and card preview all show the upload response. */
export function marksAfterLogoUpload(input: {
  storeLogo?: string | null
  teamLogo?: string | null
  uploadedUrl: string
  now?: number
}) {
  const previous = (input.storeLogo || '').trim() || (input.teamLogo || '').trim()
  const logoUrl = logoUrlAfterUpload(previous, input.uploadedUrl, input.now)
  return { storeLogo: logoUrl, header: logoUrl, preview: logoUrl }
}

type LogoTeam = {
  id: string
  name: string
  shortName?: string
  logoUrl?: string
}

/** Replace one crest in the team directory from the upload response. No refetch. */
export function applyLogoToCache<T extends LogoTeam>(
  cache: Record<string, T>,
  id: string,
  uploadedUrl: string,
  teamLogo?: string | null,
  now?: number,
) {
  const current = cache[id]
  const marks = marksAfterLogoUpload({
    storeLogo: current?.logoUrl,
    teamLogo: teamLogo ?? current?.logoUrl,
    uploadedUrl,
    now,
  })
  const next = {
    ...(current || { id, name: '' }),
    id,
    logoUrl: marks.storeLogo,
  } as T
  return {
    cache: { ...cache, [id]: next },
    marks,
  }
}
