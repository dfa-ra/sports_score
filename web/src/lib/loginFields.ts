export const LOGIN_EMAIL_REQUIRED = 'Введите e-mail'
export const LOGIN_EMAIL_FORMAT = 'Неверный формат e-mail'
export const LOGIN_PASSWORD_REQUIRED = 'Введите пароль'

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
