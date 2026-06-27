import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import App from './App.vue'
import { router } from './router'
import './style.css'

createApp(App).use(createPinia()).use(router).use(ElementPlus).mount('#app')

window.addEventListener('admin-session-expired', () => {
  if (router.currentRoute.value.name !== 'login') {
    router.push({ name: 'login' })
  }
})
