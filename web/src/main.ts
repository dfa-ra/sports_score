import { createApp } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import App from './App.vue'
import router from './router'
import { installAnalytics } from './lib/analytics'
import { registerServiceWorker } from './lib/registerServiceWorker'
import { useAuthStore } from './stores/auth'
import './styles/main.css'

installAnalytics(router)
registerServiceWorker()

const pinia = createPinia()
setActivePinia(pinia)

void useAuthStore().restoreSession()
createApp(App).use(pinia).use(router).mount('#app')
