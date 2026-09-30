import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import { installAnalytics } from './lib/analytics'
import './styles/main.css'

installAnalytics(router)
createApp(App).use(createPinia()).use(router).mount('#app')
