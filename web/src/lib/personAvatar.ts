/** Non-empty photo address. Blank and whitespace-only sources stay on initials. */
export function trimmedPhotoSrc(src?: string | null) {
  return (src ?? '').trim()
}

/**
 * The photo covers the initials only after it has loaded.
 * A missing source, a source that is still loading, or a failed load leaves the initials visible.
 */
export function avatarPhotoCovers(src: string | null | undefined, loaded: boolean, failed: boolean) {
  return trimmedPhotoSrc(src).length > 0 && loaded && !failed
}
