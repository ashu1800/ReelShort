import { createRouter, createWebHistory } from 'vue-router'
import { useSessionStore } from '../stores/session'
import AuditLogsView from '../views/AuditLogsView.vue'
import ContentCacheView from '../views/ContentCacheView.vue'
import DashboardView from '../views/DashboardView.vue'
import LoginView from '../views/LoginView.vue'
import OrdersView from '../views/OrdersView.vue'
import PaymentEventsView from '../views/PaymentEventsView.vue'
import SystemAlertsView from '../views/SystemAlertsView.vue'
import SystemConfigsView from '../views/SystemConfigsView.vue'
import SystemLogsView from '../views/SystemLogsView.vue'
import SystemRuntimeView from '../views/SystemRuntimeView.vue'
import UsersView from '../views/UsersView.vue'
import WithdrawalsView from '../views/WithdrawalsView.vue'

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
      path: '/orders',
      name: 'orders',
      component: OrdersView,
    },
    {
      path: '/payments/events',
      name: 'payment-events',
      component: PaymentEventsView,
    },
    {
      path: '/withdrawals',
      name: 'withdrawals',
      component: WithdrawalsView,
    },
    {
      path: '/content-cache',
      name: 'content-cache',
      component: ContentCacheView,
    },
    {
      path: '/system-configs',
      name: 'system-configs',
      component: SystemConfigsView,
    },
    {
      path: '/system-runtime',
      name: 'system-runtime',
      component: SystemRuntimeView,
    },
    {
      path: '/system-alerts',
      name: 'system-alerts',
      component: SystemAlertsView,
    },
    {
      path: '/system-logs',
      name: 'system-logs',
      component: SystemLogsView,
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
