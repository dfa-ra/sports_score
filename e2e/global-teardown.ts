import { request as newRequest } from '@playwright/test'
import { printReport, sweepE2E } from './src/cleanup'
import { assertNotProduction, resolveBaseUrl } from './src/env'

export default async function globalTeardown() {
  const baseURL = resolveBaseUrl()
  assertNotProduction(baseURL)
  const request = await newRequest.newContext({ baseURL, ignoreHTTPSErrors: true })
  try {
    const report = await sweepE2E(request)
    printReport(report, 'global-teardown')
  } finally {
    await request.dispose()
  }
}
