import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import { installAnalytics } from './lib/analytics'
import { registerServiceWorker } from './lib/registerServiceWorker'
import './styles/main.css'

installAnalytics(router)
registerServiceWorker()
createApp(App).use(createPinia()).use(router).mount('#app')
