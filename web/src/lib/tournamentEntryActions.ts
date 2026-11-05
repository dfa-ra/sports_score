/** Shown on a tournament entry only while it is still waiting for an admin. */
export const APPROVE_BUTTON_LABEL = 'Одобрить'

export function showApproveButton(status: string | null | undefined) {
  return status === 'PENDING'
}

/**
 * Text for a failed approve or restore. A 409 body message is shown as-is.
 * The helper never throws, so the admin view can render it instead of crashing.
 */
export function entryActionError(error: unknown, fallback = 'Не вышло. Попробуйте ещё раз.') {
  const message = (error as { response?: { data?: { message?: unknown } } } | null)?.response?.data?.message
  return typeof message === 'string' && message.trim() ? message : fallback
}
