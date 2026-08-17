export function apiError(error: any, fallback = 'Не вышло. Попробуйте ещё раз.') {
  return error?.response?.data?.message || fallback
}
