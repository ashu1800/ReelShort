<script setup lang="ts">
import {
  Coin,
  DataAnalysis,
  Document,
  FolderOpened,
  GoldMedal,
  Lock,
  Money,
  Monitor,
  Notebook,
  Warning,
  Tickets,
  Setting,
  SwitchButton,
  User,
} from '@element-plus/icons-vue'
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { logout as logoutAdminSession } from './services/adminApi'
import { useSessionStore } from './stores/session'

const route = useRoute()
const router = useRouter()
const session = useSessionStore()

const isLoginRoute = computed(() => route.name === 'login')

async function logout() {
  try {
    if (session.isAuthenticated) {
      await logoutAdminSession()
    }
  } finally {
    session.clearSession()
    router.push({ name: 'login' })
  }
}
</script>

<template>
  <router-view v-if="isLoginRoute" />
  <el-container v-else class="app-shell">
    <el-aside width="220px" class="sidebar">
      <div class="brand">ReelShort Admin</div>
      <el-menu router :default-active="route.path">
        <el-menu-item index="/">
          <el-icon><DataAnalysis /></el-icon>
          <span>控制台</span>
        </el-menu-item>
        <el-menu-item index="/users">
          <el-icon><User /></el-icon>
          <span>用户管理</span>
        </el-menu-item>
        <el-menu-item index="/orders">
          <el-icon><Tickets /></el-icon>
          <span>订单管理</span>
        </el-menu-item>
        <el-menu-item index="/payments/events">
          <el-icon><Money /></el-icon>
          <span>支付事件</span>
        </el-menu-item>
        <el-menu-item index="/withdrawals">
          <el-icon><Coin /></el-icon>
          <span>提现申请</span>
        </el-menu-item>
        <el-menu-item index="/vip-orders">
          <el-icon><GoldMedal /></el-icon>
          <span>VIP 订单</span>
        </el-menu-item>
        <el-menu-item index="/2fa">
          <el-icon><Lock /></el-icon>
          <span>两步验证</span>
        </el-menu-item>
        <el-menu-item index="/content-cache">
          <el-icon><FolderOpened /></el-icon>
          <span>内容缓存</span>
        </el-menu-item>
        <el-menu-item index="/system-configs">
          <el-icon><Setting /></el-icon>
          <span>系统配置</span>
        </el-menu-item>
        <el-menu-item index="/system-runtime">
          <el-icon><Monitor /></el-icon>
          <span>运行诊断</span>
        </el-menu-item>
        <el-menu-item index="/system-alerts">
          <el-icon><Warning /></el-icon>
          <span>异常告警</span>
        </el-menu-item>
        <el-menu-item index="/system-logs">
          <el-icon><Notebook /></el-icon>
          <span>系统日志</span>
        </el-menu-item>
        <el-menu-item index="/audit-logs">
          <el-icon><Document /></el-icon>
          <span>审计日志</span>
        </el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="topbar">
        <span>聚合播放平台后台</span>
        <div class="topbar-actions">
          <el-tag type="info" effect="plain">
            <el-icon><Coin /></el-icon>
            {{ session.adminName || 'admin' }}
          </el-tag>
          <el-button :icon="SwitchButton" text @click="logout">退出</el-button>
        </div>
      </el-header>
      <el-main>
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>
