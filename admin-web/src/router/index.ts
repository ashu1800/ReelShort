import { createRouter, createWebHistory } from 'vue-router'
import { useSessionStore } from '../stores/session'
import AuditLogsView from '../views/AuditLogsView.vue'
import ContentCacheView from '../views/ContentCacheView.vue'
import DashboardView from '../views/DashboardView.vue'
import LoginView from '../views/LoginView.vue'
import UsersView from '../views/UsersView.vue'

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: LoginView,
      meta: { public: true },
    },
    {
      path: '/',
      name: 'dashboard',
      component: DashboardView,
    },
    {
      path: '/users',
      name: 'users',
      component: UsersView,
    },
    {
      path: '/content-cache',
      name: 'content-cache',
      component: ContentCacheView,
    },
    {
      path: '/audit-logs',
      name: 'audit-logs',
      component: AuditLogsView,
    },
  ],
})

router.beforeEach((to) => {
  const session = useSessionStore()
  if (to.meta.public) {
    if (to.name === 'login' && session.isAuthenticated) {
      return { name: 'dashboard' }
    }
    return true
  }
  if (!session.isAuthenticated) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  return true
})
