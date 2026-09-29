export function safeInternalPath(value: unknown, fallback = '/'): string {
  if (typeof value !== 'string' || value.length === 0 || value.length > 2048) return fallback
  let path = value
  try {
    path = decodeURIComponent(value)
  } catch {
    return fallback
  }
  if (!path.startsWith('/') || path.startsWith('//') || path.startsWith('/\\')) return fallback
  if (path.includes('://') || path.includes('\\') || path.includes('\n') || path.includes('\r')) return fallback
  return path
}
