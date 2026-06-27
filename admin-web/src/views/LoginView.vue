<script setup lang="ts">
import { Lock, User } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { login } from '../services/adminApi'
import { useSessionStore } from '../stores/session'

const router = useRouter()
const route = useRoute()
const session = useSessionStore()
const loading = ref(false)
const form = reactive({
  username: 'admin',
  password: '',
})

async function submit() {
  if (!form.username.trim() || !form.password) {
    ElMessage.warning('请输入管理员账号和密码')
    return
  }
  loading.value = true
  try {
    const response = await login(form.username.trim(), form.password)
    session.setSession(response.username, response.token)
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/'
    router.push(redirect)
  } catch {
    ElMessage.error('登录失败，请检查账号或密码')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <main class="login-page">
    <section class="login-panel">
      <div class="login-copy">
        <p class="eyebrow">ReelShort Admin</p>
        <h1>后台管理</h1>
      </div>
      <el-form class="login-form" @submit.prevent="submit">
        <el-form-item>
          <el-input
            v-model="form.username"
            :prefix-icon="User"
            autocomplete="username"
            placeholder="管理员账号"
            size="large"
          />
        </el-form-item>
        <el-form-item>
          <el-input
            v-model="form.password"
            :prefix-icon="Lock"
            autocomplete="current-password"
            placeholder="密码"
            show-password
            size="large"
            type="password"
            @keyup.enter="submit"
          />
        </el-form-item>
        <el-button :loading="loading" native-type="submit" size="large" type="primary">
          登录
        </el-button>
      </el-form>
    </section>
  </main>
</template>
