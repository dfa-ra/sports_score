import type { APIRequestContext, APIResponse } from '@playwright/test'
import { resolveBaseUrl } from './env'

export function apiPath(path: string): string {
  const base = resolveBaseUrl()
  if (/^https?:\/\//i.test(path)) return path
  if (path.startsWith('/api/') || path.startsWith('/media/')) return `${base}${path}`
  const suffix = path.startsWith('/') ? path : `/${path}`
  return `${base}/api/v1${suffix}`
}

export type Json = Record<string, unknown> | unknown[] | string | number | boolean | null

export type ApiCall = {
  status: number
  body: unknown
  response: APIResponse
}

function authHeaders(token?: string, json = false): Record<string, string> {
  const headers: Record<string, string> = {}
  if (token) headers.Authorization = `Bearer ${token}`
  if (json) headers['Content-Type'] = 'application/json'
  return headers
}

export async function readBody(response: APIResponse): Promise<unknown> {
  const text = await response.text()
  if (!text) return null
  try {
    return JSON.parse(text)
  } catch {
    return text
  }
}

export async function apiGet(
  request: APIRequestContext,
  path: string,
  token?: string,
): Promise<ApiCall> {
  const response = await request.get(apiPath(path), {
    headers: authHeaders(token),
    failOnStatusCode: false,
  })
  return { status: response.status(), body: await readBody(response), response }
}

export async function apiSend(
  request: APIRequestContext,
  method: 'POST' | 'PUT' | 'PATCH' | 'DELETE',
  path: string,
  options: { token?: string; data?: unknown } = {},
): Promise<ApiCall> {
  const response = await request.fetch(apiPath(path), {
    method,
    headers: authHeaders(options.token, options.data !== undefined),
    data: options.data === undefined ? undefined : options.data,
    failOnStatusCode: false,
  })
  return { status: response.status(), body: await readBody(response), response }
}

export function errorMessage(body: unknown): string {
  if (body && typeof body === 'object' && 'message' in body) {
    return String((body as { message?: unknown }).message || '')
  }
  return ''
}

export function errorCode(body: unknown): string {
  if (body && typeof body === 'object' && 'code' in body) {
    return String((body as { code?: unknown }).code || '')
  }
  return ''
}

export function asRecord(body: unknown): Record<string, unknown> {
  return body && typeof body === 'object' && !Array.isArray(body)
    ? body as Record<string, unknown>
    : {}
}

export function asList(body: unknown): Record<string, unknown>[] {
  if (Array.isArray(body)) return body as Record<string, unknown>[]
  const record = asRecord(body)
  return Array.isArray(record.content) ? record.content as Record<string, unknown>[] : []
}
