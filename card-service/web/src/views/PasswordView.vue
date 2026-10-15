<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormItemRule, type FormRules } from 'element-plus'
import PageHeader from '../components/PageHeader.vue'
import PasswordStrengthMeter from '../components/PasswordStrengthMeter.vue'
import { useAuthStore } from '../stores/auth'
import { errorMessage } from '../api/http'
import { validateNewPassword, MIN_PASSWORD_LENGTH } from '../utils/password.mjs'

interface PasswordForm {
  oldPassword: string
  newPassword: string
  confirmPassword: string
}

const auth = useAuthStore()
const router = useRouter()

const formRef = ref<FormInstance>()
const form = reactive<PasswordForm>({ oldPassword: '', newPassword: '', confirmPassword: '' })
const errorText = ref('')
const submitting = ref(false)

const newPasswordRule: FormItemRule = {
  validator: (_rule, value, callback) => {
    const errors = validateNewPassword(value, form.oldPassword)
    if (errors.length > 0) {
      callback(new Error(errors[0]))
      return
    }
    callback()
  },
  trigger: 'blur',
}

const rules: FormRules<PasswordForm> = {
  oldPassword: [{ required: true, message: '请输入旧密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    newPasswordRule,
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (value !== form.newPassword) {
          callback(new Error('两次输入的新密码不一致'))
          return
        }
        callback()
      },
      trigger: 'blur',
    },
  ],
}

async function handleSubmit(): Promise<void> {
  if (submitting.value) return
  errorText.value = ''

  const valid = await formRef.value?.validate().catch(() => false)
  if (valid !== true) return

  submitting.value = true
  try {
    const message = await auth.changePassword(form.oldPassword, form.newPassword)
    ElMessage.success(message !== '' ? message : '密码已修改，请重新登录')
    await router.replace({ name: 'login' })
  } catch (error) {
    errorText.value = errorMessage(error, '修改密码失败，请稍后重试')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="page">
    <PageHeader title="修改密码" subtitle="修改后所有管理会话立即失效，需要使用新密码重新登录。" />

    <section class="panel password-panel">
      <el-alert
        v-if="errorText"
        class="form-alert"
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
        <el-form-item label="旧密码" prop="oldPassword">
          <el-input
            v-model="form.oldPassword"
            type="password"
            show-password
            autocomplete="current-password"
            placeholder="请输入当前密码"
            :disabled="submitting"
          />
        </el-form-item>

        <el-form-item label="新密码" prop="newPassword">
          <el-input
            v-model="form.newPassword"
            type="password"
            show-password
            autocomplete="new-password"
            :placeholder="`至少 ${MIN_PASSWORD_LENGTH} 位`"
            :disabled="submitting"
          />
        </el-form-item>

        <PasswordStrengthMeter :password="form.newPassword" />

        <el-form-item label="确认新密码" prop="confirmPassword">
          <el-input
            v-model="form.confirmPassword"
            type="password"
            show-password
            autocomplete="new-password"
            placeholder="请再次输入新密码"
            :disabled="submitting"
          />
        </el-form-item>

        <div class="actions">
          <el-button type="primary" native-type="submit" :loading="submitting" :disabled="submitting">
            {{ submitting ? '提交中…' : '确认修改' }}
          </el-button>
        </div>
      </el-form>
    </section>
  </div>
</template>

<style scoped>
.password-panel {
  max-width: 480px;
}

.form-alert {
  margin-bottom: var(--space-4);
}

.actions {
  display: flex;
  gap: var(--space-2);
}
</style>
