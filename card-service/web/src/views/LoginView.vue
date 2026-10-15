<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Lock, User } from '@element-plus/icons-vue'
import { useAuthStore } from '../stores/auth'
import { isApiError } from '../api/http'
import { APP_BRAND, APP_TITLE } from '../constants'

interface LoginForm {
  username: string
  password: string
}

const auth = useAuthStore()
const router = useRouter()
const route = useRoute()

const formRef = ref<FormInstance>()
const form = reactive<LoginForm>({ username: '', password: '' })
const errorText = ref('')
const submitting = ref(false)

const rules: FormRules<LoginForm> = {
  username: [{ required: true, message: '请输入管理员账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

function describeLoginError(error: unknown): string {
  if (isApiError(error)) {
    if (error.status === 401 || error.code === 'invalid_credentials') return '账号或密码错误'
    if (error.status === 429 || error.code === 'rate_limited') {
      return '登录失败次数过多，账号已被临时锁定，请 15 分钟后重试'
    }
    return error.message
  }
  return '登录失败，请稍后重试'
}

async function handleSubmit(): Promise<void> {
  if (submitting.value) return
  errorText.value = ''

  const valid = await formRef.value?.validate().catch(() => false)
  if (valid !== true) return

  submitting.value = true
  try {
    const admin = await auth.login(form.username.trim(), form.password)
    ElMessage.success(`欢迎回来，${admin.display_name || admin.username}`)

    if (admin.must_change_password) {
      await router.replace({ name: 'password' })
      return
    }
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : ''
    await router.replace(redirect !== '' ? redirect : { name: 'dashboard' })
  } catch (error) {
    errorText.value = describeLoginError(error)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <div class="login-card">
      <div class="brand">
        <span class="brand-mark" aria-hidden="true">卡</span>
        <div>
          <h1 class="brand-title">{{ APP_BRAND }}</h1>
          <p class="page-subtitle">{{ APP_TITLE }}</p>
        </div>
      </div>

      <el-alert
        v-if="errorText"
        class="login-error"
        type="error"
        :closable="false"
        show-icon
        :title="errorText"
      />

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        @submit.prevent="handleSubmit"
      >
        <el-form-item label="管理员账号" prop="username">
          <el-input
            v-model="form.username"
            :prefix-icon="User"
            autocomplete="username"
            placeholder="请输入管理员账号"
            :disabled="submitting"
          />
        </el-form-item>

        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            :prefix-icon="Lock"
            autocomplete="current-password"
            placeholder="请输入密码"
            :disabled="submitting"
          />
        </el-form-item>

        <el-button
          class="submit"
          type="primary"
          native-type="submit"
          :loading="submitting"
          :disabled="submitting"
        >
          {{ submitting ? '登录中…' : '登录' }}
        </el-button>
      </el-form>

      <p class="form-hint">连续 5 次密码错误会锁定账号 15 分钟。</p>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--space-5) var(--space-4);
  background: var(--c-bg);
}

.login-card {
  width: 100%;
  max-width: 360px;
  background: var(--c-surface);
  border: 1px solid var(--c-border);
  border-radius: var(--radius);
  padding: var(--space-6) var(--space-5) var(--space-5);
}

.brand {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  margin-bottom: var(--space-5);
}

.brand-mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: var(--radius);
  background: var(--c-accent);
  color: #ffffff;
  font-size: 16px;
  font-weight: 600;
  flex: none;
}

.brand-title {
  font-size: var(--fs-page-title);
  font-weight: 600;
}

.login-error {
  margin-bottom: var(--space-4);
}

.submit {
  width: 100%;
  margin-top: var(--space-1);
}

.form-hint {
  margin-top: var(--space-4);
  text-align: center;
}
</style>
