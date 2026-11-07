export const LOGIN_EMAIL_REQUIRED = 'Введите email'
export const LOGIN_EMAIL_FORMAT = 'Введите email полностью'
export const LOGIN_PASSWORD_REQUIRED = 'Введите пароль'
export const LOGIN_PAIR_INVALID = 'Неверный email или пароль'
export const LOGIN_NETWORK = 'Не удалось войти. Повторите позже'

/** A missing response or a 5xx is a network failure. Any other status is a rejected pair. */
export function loginSubmitFailure(error: { response?: { status?: number } | null } | null | undefined): 'pair' | 'network' {
  const status = error?.response?.status
  if (status == null || status >= 500) return 'network'
  return 'pair'
}

export type LoginFieldErrors = {
  email?: string
  password?: string
}

function looksLikeEmail(value: string) {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value)
}

/** Same checks as registration: empty mail, mail shape, empty password. */
export function loginFieldErrors(email: string, password: string): LoginFieldErrors {
  const next: LoginFieldErrors = {}
  const mail = email.trim()
  if (!mail) next.email = LOGIN_EMAIL_REQUIRED
  else if (mail.length > 320 || !looksLikeEmail(mail)) next.email = LOGIN_EMAIL_FORMAT
  if (!password) next.password = LOGIN_PASSWORD_REQUIRED
  return next
}
