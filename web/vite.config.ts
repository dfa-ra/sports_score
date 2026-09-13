import { readFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

const root = dirname(fileURLToPath(import.meta.url))
const pkg = JSON.parse(readFileSync(join(root, 'package.json'), 'utf8')) as { version: string }
process.env.VITE_APP_VERSION ??= pkg.version
process.env.VITE_APP_REVISION ??= 'local'
process.env.VITE_APP_CHANNEL ??= 'local'
const rev = process.env.VITE_APP_REVISION
if (rev && /^[0-9a-f]{8,}$/i.test(rev)) {
  process.env.VITE_APP_REVISION = rev.slice(0, 7)
}

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080',
      '/media': 'http://localhost:8080',
      '/ws': {
        target: 'http://localhost:8080',
        ws: true,
      },
    },
  },
  define: {
    global: 'globalThis',
  },
})
