import { request as newRequest } from '@playwright/test'
import { printReport, sweepE2E } from '../src/cleanup'
import { assertNotProduction, resolveBaseUrl } from '../src/env'

async function main() {
  const baseURL = resolveBaseUrl()
  assertNotProduction(baseURL)
  const request = await newRequest.newContext({ baseURL, ignoreHTTPSErrors: true })
  try {
    const report = await sweepE2E(request)
    printReport(report, 'npm run cleanup')
    if (!process.env.E2E_ADMIN_EMAIL) {
      console.warn('Задайте E2E_ADMIN_EMAIL и E2E_ADMIN_PASSWORD — без админа API-чистка не работает.')
    }
    if (report.leftovers.length) {
      console.warn('Часть сущностей API не удаляет. SQL-критерии — в e2e/README.md.')
      process.exitCode = 2
    }
  } finally {
    await request.dispose()
  }
}

main().catch((error) => {
  console.error(error)
  process.exit(1)
})
