import { createRouter, createWebHistory } from 'vue-router'
import AppLayout from '../components/AppLayout.vue'
import LoginView from '../views/LoginView.vue'
import { useAuthStore } from '../stores/auth'
import { APP_TITLE } from '../constants'

declare module 'vue-router' {
  interface RouteMeta {
    /** 无需登录 */
    public?: boolean
    /** 页面标题（顶部条与 document.title） */
    title?: string
    /** 必须改密时仍可访问的页面 */
    passwordPage?: boolean
  }
}

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: LoginView,
      meta: { public: true, title: '登录' },
    },
    {
      path: '/',
      component: AppLayout,
      children: [
        {
          path: '',
          name: 'dashboard',
          component: () => import('../views/DashboardView.vue'),
          meta: { title: '概览' },
        },
        {
          path: 'password',
          name: 'password',
          component: () => import('../views/PasswordView.vue'),
          meta: { title: '修改密码', passwordPage: true },
        },
        {
          path: 'cards',
          name: 'cards',
          component: () => import('../views/CardsView.vue'),
          meta: { title: '卡密管理' },
        },
        {
          path: 'card-types',
          name: 'card-types',
          component: () => import('../views/CardTypesView.vue'),
          meta: { title: '卡密类型' },
        },
        {
          path: 'sessions',
          name: 'sessions',
          component: () => import('../views/SessionsView.vue'),
          meta: { title: '在线会话' },
        },
        {
          path: 'logs',
          name: 'logs',
          component: () => import('../views/LogsView.vue'),
          meta: { title: '操作日志' },
        },
        {
          path: 'audit-logs',
          name: 'audit-logs',
          component: () => import('../views/AuditLogsView.vue'),
          meta: { title: '审计日志' },
        },
        {
          path: 'settings',
          name: 'settings',
          component: () => import('../views/SettingsView.vue'),
          meta: { title: '系统设置' },
        },
        {
          path: ':pathMatch(.*)*',
          name: 'not-found',
          component: () => import('../views/NotFoundView.vue'),
          meta: { title: '页面不存在' },
        },
      ],
    },
  ],
})

router.beforeEach(async (to) => {
  const auth = useAuthStore()

  if (to.meta.public === true) {
    if (to.name === 'login') {
      await auth.loadSession()
      if (auth.isAuthenticated) {
        return auth.mustChangePassword ? { name: 'password' } : { name: 'dashboard' }
      }
    }
    return true
  }

  if (!auth.isAuthenticated) {
    await auth.loadSession()
  }
  if (!auth.isAuthenticated) {
    return { name: 'login', query: to.fullPath === '/' ? {} : { redirect: to.fullPath } }
  }
  if (auth.mustChangePassword && to.meta.passwordPage !== true) {
    return { name: 'password' }
  }
  return true
})

router.afterEach((to) => {
  document.title = typeof to.meta.title === 'string' && to.meta.title !== ''
    ? `${to.meta.title} · ${APP_TITLE}`
    : APP_TITLE
})
