<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { onMounted, ref } from 'vue'
import { enable2fa, get2faStatus, setup2fa } from '../services/adminApi'
import { backendErrorMessage } from '../services/http'

const loading = ref(false)
const enabled = ref(false)
const secret = ref('')
const otpauthUri = ref('')
const verificationCode = ref('')
const step = ref<'idle' | 'show-qr' | 'verify'>('idle')

async function loadStatus() {
  loading.value = true
  try {
    const status = await get2faStatus()
    enabled.value = status.enabled
  } catch (error) {
    ElMessage.error(backendErrorMessage(error, '获取 2FA 状态失败'))
  } finally {
    loading.value = false
  }
}

async function startSetup() {
  loading.value = true
  try {
    const result = await setup2fa()
    secret.value = result.secret
    otpauthUri.value = result.otpauthUri
    step.value = 'show-qr'
  } catch (error) {
    ElMessage.error(backendErrorMessage(error, '生成 2FA 密钥失败'))
  } finally {
    loading.value = false
  }
}

async function confirmEnable() {
  if (verificationCode.value.length !== 6) {
    ElMessage.warning('请输入 6 位验证码')
    return
  }
  loading.value = true
  try {
    await enable2fa(secret.value, verificationCode.value)
    ElMessage.success('2FA 绑定成功')
    enabled.value = true
    step.value = 'idle'
    verificationCode.value = ''
  } catch (error) {
    ElMessage.error(backendErrorMessage(error, '验证码错误，请重试'))
  } finally {
    loading.value = false
  }
}

/** Build a Google Charts QR URL from an otpauth URI (no npm dependency needed). */
function qrImageUrl(uri: string): string {
  return `https://api.qrserver.com/v1/create-qr-code/?size=240x240&data=${encodeURIComponent(uri)}`
}

onMounted(loadStatus)
</script>

<template>
  <section v-loading="loading" class="page-section">
    <div class="page-header">
      <div>
        <h1>两步验证 (2FA)</h1>
        <p>绑定 Google Authenticator，批量提现打款时需要 6 位验证码确认。</p>
      </div>
    </div>

    <el-alert v-if="enabled" title="2FA 已启用" type="success" show-icon :closable="false" />

    <el-card v-if="!enabled && step === 'idle'" shadow="never">
      <p>2FA 未启用，批量提现打款需要先绑定。</p>
      <el-button type="primary" @click="startSetup">开始绑定</el-button>
    </el-card>

    <el-card v-if="step === 'show-qr' || step === 'verify'" shadow="never">
      <h3>第 1 步：扫描二维码</h3>
      <p>用 Google Authenticator 或类似 App 扫描下方二维码，或手动输入密钥。</p>
      <div style="text-align: center; margin: 16px 0">
        <img :src="qrImageUrl(otpauthUri)" alt="2FA QR Code" width="240" height="240" />
      </div>
      <p class="mono" style="word-break: break-all">密钥：{{ secret }}</p>

      <h3 style="margin-top: 24px">第 2 步：输入验证码</h3>
      <p>在 App 中查看 6 位验证码并输入确认。</p>
      <el-input
        v-model="verificationCode"
        placeholder="6 位验证码"
        maxlength="6"
        style="max-width: 200px"
        @keyup.enter="confirmEnable"
      />
      <div style="margin-top: 16px">
        <el-button type="primary" :loading="loading" @click="confirmEnable">确认绑定</el-button>
        <el-button @click="step = 'idle'">取消</el-button>
      </div>
    </el-card>
  </section>
</template>
