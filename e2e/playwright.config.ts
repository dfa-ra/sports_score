import { defineConfig, devices } from '@playwright/test'
import { assertNotProduction, resolveBaseUrl } from './src/env'

const baseURL = resolveBaseUrl()
assertNotProduction(baseURL)

export default defineConfig({
  testDir: './tests',
  timeout: 60_000,
  globalTimeout: 60 * 60 * 1000,
  expect: { timeout: 12_000 },
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: 0,
  workers: 1,
  reporter: [
    ['list'],
    ['html', { open: 'never', outputFolder: 'playwright-report' }],
    ['json', { outputFile: 'test-results/results.json' }],
  ],
  use: {
    baseURL,
    locale: 'en-US',
    timezoneId: 'Europe/Moscow',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
    ignoreHTTPSErrors: true,
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
})
