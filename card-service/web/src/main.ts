import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'
import './styles/element-plus.css'
import './styles/base.css'
import App from './App.vue'
import { router } from './router'
import { onAuthFailure } from './api/auth-events'
import { useAuthStore } from './stores/auth'

const app = createApp(App)

app.use(createPinia())
app.use(router)
app.use(ElementPlus, { locale: zhCn })

/**
 * 全局认证失败处理（契约 3.1）：
 * - 401：清空登录态并跳登录页
 * - 403 + password_change_required：跳改密页且不允许进入其他页面
 */
onAuthFailure((kind) => {
  const auth = useAuthStore()

  if (kind === 'unauthorized') {
    auth.reset()
    const current = router.currentRoute.value
    if (current.name !== 'login') {
      void router.replace({
        name: 'login',
        query: current.fullPath === '/' ? {} : { redirect: current.fullPath },
      })
    }
    return
  }

  auth.markMustChangePassword()
  if (router.currentRoute.value.name !== 'password') {
    void router.replace({ name: 'password' })
  }
})

app.mount('#app')
