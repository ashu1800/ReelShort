<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { RouterView, useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Expand, Key, SwitchButton } from '@element-plus/icons-vue'
import AppSidebar from './AppSidebar.vue'
import { useAuthStore } from '../stores/auth'
import { useIsMobile } from '../composables/useMediaQuery'
import { APP_TITLE } from '../constants'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const isMobile = useIsMobile()
const drawerOpen = ref(false)

const pageTitle = computed(() =>
  typeof route.meta.title === 'string' && route.meta.title !== '' ? route.meta.title : APP_TITLE,
)

watch(
  () => route.fullPath,
  () => {
    drawerOpen.value = false
  },
)

async function handleLogout(): Promise<void> {
  try {
    await ElMessageBox.confirm('退出后需要重新登录才能继续使用后台。', '确认退出登录？', {
      confirmButtonText: '退出登录',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return
  }
  await auth.logout()
  ElMessage.success('已退出登录')
  void router.replace({ name: 'login' })
}

function openPasswordPage(): void {
  void router.push({ name: 'password' })
}
</script>

<template>
  <div class="layout">
    <aside v-if="!isMobile" class="sidebar-slot">
      <AppSidebar :nav-enabled="!auth.mustChangePassword" />
    </aside>

    <el-drawer
      v-else
      v-model="drawerOpen"
      direction="ltr"
      size="216px"
      :with-header="false"
      class="sidebar-drawer"
    >
      <AppSidebar :nav-enabled="!auth.mustChangePassword" @navigate="drawerOpen = false" />
    </el-drawer>

    <div class="main">
      <header class="topbar">
        <el-button
          v-if="isMobile"
          text
          :icon="Expand"
          aria-label="打开导航菜单"
          title="导航菜单"
          @click="drawerOpen = true"
        />
        <h2 class="topbar-title">{{ pageTitle }}</h2>
        <div class="topbar-spacer" />
        <span class="admin-name" :title="auth.displayName">{{ auth.displayName }}</span>
        <el-button v-if="!auth.mustChangePassword" text :icon="Key" @click="openPasswordPage">
          修改密码
        </el-button>
        <el-button text :icon="SwitchButton" @click="handleLogout">退出</el-button>
      </header>

      <main class="content">
        <el-alert
          v-if="auth.mustChangePassword"
          class="password-notice"
          type="warning"
          :closable="false"
          show-icon
          title="首次登录需要先修改密码"
          description="修改密码后请使用新密码重新登录，之后才能访问其他页面。"
        />
        <RouterView />
      </main>
    </div>
  </div>
</template>

<style scoped>
.layout {
  min-height: 100vh;
  background: var(--c-bg);
}

.sidebar-slot {
  position: fixed;
  top: 0;
  left: 0;
  bottom: 0;
  width: var(--sidebar-width);
  z-index: 20;
}

.main {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  min-width: 0;
  margin-left: var(--sidebar-width);
}

.topbar {
  position: sticky;
  top: 0;
  z-index: 10;
  display: flex;
  align-items: center;
  gap: var(--space-2);
  height: var(--topbar-height);
  padding: 0 var(--space-4);
  background: var(--c-surface);
  border-bottom: 1px solid var(--c-border);
}

.topbar-title {
  font-size: var(--fs-section-title);
  font-weight: 600;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.topbar-spacer {
  flex: 1 1 auto;
}

.admin-name {
  max-width: 160px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--c-text-secondary);
  font-size: var(--fs-meta);
}

.content {
  flex: 1 1 auto;
  min-width: 0;
  padding: var(--space-5);
  overflow-x: auto;
}

.password-notice {
  margin-bottom: var(--space-4);
}

.sidebar-drawer :deep(.el-drawer__body) {
  padding: 0;
  background: var(--c-sidebar);
}

@media (max-width: 767px) {
  .main {
    margin-left: 0;
  }

  .content {
    padding: var(--space-4);
  }
}
</style>
