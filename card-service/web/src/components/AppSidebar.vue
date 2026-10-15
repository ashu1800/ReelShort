<script setup lang="ts">
import { computed, type Component } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import {
  Collection,
  Connection,
  DataBoard,
  Document,
  Files,
  Setting,
  Ticket,
} from '@element-plus/icons-vue'
import { APP_BRAND, APP_BRAND_SUBTITLE } from '../constants'

const props = defineProps<{
  /** 必须改密时隐藏导航，避免跳到会被守卫拦回的页面 */
  navEnabled: boolean
}>()

const emit = defineEmits<{
  navigate: []
}>()

interface NavItem {
  name: string
  label: string
  icon: Component
}

const NAV_ITEMS: NavItem[] = [
  { name: 'dashboard', label: '概览', icon: DataBoard },
  { name: 'cards', label: '卡密管理', icon: Ticket },
  { name: 'card-types', label: '卡密类型', icon: Collection },
  { name: 'sessions', label: '在线会话', icon: Connection },
  { name: 'logs', label: '操作日志', icon: Document },
  { name: 'audit-logs', label: '审计日志', icon: Files },
  { name: 'settings', label: '系统设置', icon: Setting },
]

const route = useRoute()
const activeName = computed(() => (typeof route.name === 'string' ? route.name : ''))
</script>

<template>
  <div class="sidebar">
    <div class="brand">
      <span class="brand-mark" aria-hidden="true">卡</span>
      <span class="brand-text">
        <strong>{{ APP_BRAND }}</strong>
        <small>{{ APP_BRAND_SUBTITLE }}</small>
      </span>
    </div>

    <nav class="nav" aria-label="主导航">
      <template v-if="props.navEnabled">
        <RouterLink
          v-for="item in NAV_ITEMS"
          :key="item.name"
          :to="{ name: item.name }"
          class="nav-item"
          :class="{ 'is-active': activeName === item.name }"
          :aria-current="activeName === item.name ? 'page' : undefined"
          @click="emit('navigate')"
        >
          <el-icon :size="16" aria-hidden="true"><component :is="item.icon" /></el-icon>
          <span>{{ item.label }}</span>
        </RouterLink>
      </template>
      <p v-else class="nav-locked">首次登录需先修改密码，之后才能使用其他功能。</p>
    </nav>
  </div>
</template>

<style scoped>
.sidebar {
  display: flex;
  flex-direction: column;
  height: 100%;
  width: 100%;
  background: var(--c-sidebar);
  color: var(--c-sidebar-text);
}

.brand {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  height: var(--topbar-height);
  padding: 0 var(--space-4);
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.brand-mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: var(--radius);
  background: var(--c-accent);
  color: #ffffff;
  font-size: 13px;
  font-weight: 600;
  flex: none;
}

.brand-text {
  display: flex;
  align-items: baseline;
  gap: var(--space-1);
  min-width: 0;
}

.brand-text strong {
  color: #ffffff;
  font-size: var(--fs-body);
  font-weight: 600;
}

.brand-text small {
  color: var(--c-sidebar-muted);
  font-size: var(--fs-meta);
}

.nav {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: var(--space-3) var(--space-2);
  overflow-y: auto;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  height: 36px;
  padding: 0 var(--space-3);
  border-radius: var(--radius);
  color: var(--c-sidebar-text);
  font-size: var(--fs-body);
}

.nav-item:hover {
  background: var(--c-sidebar-hover);
  color: #ffffff;
}

.nav-item.is-active {
  background: var(--c-sidebar-hover);
  color: #ffffff;
  box-shadow: inset 3px 0 0 var(--c-accent);
  font-weight: 600;
}

.nav-item:focus-visible {
  outline: 2px solid var(--c-accent);
  outline-offset: -2px;
}

.nav-locked {
  padding: var(--space-2) var(--space-3);
  color: var(--c-sidebar-muted);
  font-size: var(--fs-meta);
  line-height: 1.6;
}
</style>
