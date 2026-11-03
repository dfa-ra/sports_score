import { fromMoscowInput, toMoscowInput } from './moscowTime.ts'

/** `datetime-local` is Moscow wall time. `18:00` is stored as `15:00:00Z`. */
export function fromLocalInput(value: string) {
  return fromMoscowInput(value)
}

export function toLocalInput(value?: string | null) {
  return toMoscowInput(value)
}
