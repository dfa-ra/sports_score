/** Non-empty photo address. Blank and whitespace-only sources stay on initials. */
export function trimmedPhotoSrc(src?: string | null) {
  return (src ?? '').trim()
}

function firstLetter(value?: string | null) {
  const text = (value ?? '').trim()
  if (!text) return ''
  return (Array.from(text)[0] ?? '').toLocaleUpperCase('ru')
}

/**
 * Avatar letters from the legal given name, then the surname.
 * A missing field leaves a single letter. Both missing stays on the empty mark
 * and is never parsed from the caption beside the avatar.
 */
export function personInitials(firstName?: string | null, lastName?: string | null) {
  return `${firstLetter(firstName)}${firstLetter(lastName)}` || 'SL'
}

/**
 * The photo covers the initials only after it has loaded.
 * A missing source, a source that is still loading, or a failed load leaves the initials visible.
 */
export function avatarPhotoCovers(src: string | null | undefined, loaded: boolean, failed: boolean) {
  return trimmedPhotoSrc(src).length > 0 && loaded && !failed
}
