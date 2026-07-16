<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { onMounted, ref } from 'vue'
import { enable2fa, get2faStatus, rebind2fa, setup2fa } from '../services/adminApi'
import { backendErrorMessage } from '../services/http'

const loading = ref(false)
const enabled = ref(false)
const secret = ref('')
const otpauthUri = ref('')
const verificationCode = ref('')
const step = ref<'idle' | 'show-qr' | 'verify'>('idle')

// Rebind state
const rebindMode = ref(false)
const rebindStep = ref<'old-code' | 'new-qr' | 'new-code'>('old-code')
const oldCode = ref('')
const newSecret = ref('')
const newOtpauthUri = ref('')
const newCode = ref('')

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

// --- Rebind flow ---
async function startRebind() {
  rebindMode.value = true
  rebindStep.value = 'old-code'
  oldCode.value = ''
  newSecret.value = ''
  newOtpauthUri.value = ''
  newCode.value = ''
}

async function verifyOldCode() {
  if (oldCode.value.length !== 6) {
    ElMessage.warning('请输入 6 位验证码')
    return
  }
  loading.value = true
  try {
    // Generate new secret for rebinding
    const result = await setup2fa()
    newSecret.value = result.secret
    newOtpauthUri.value = result.otpauthUri
    rebindStep.value = 'new-qr'
  } catch (error) {
    ElMessage.error(backendErrorMessage(error, '生成新密钥失败'))
  } finally {
    loading.value = false
  }
}

async function confirmRebind() {
  if (newCode.value.length !== 6) {
    ElMessage.warning('请输入 6 位验证码')
    return
  }
  loading.value = true
  try {
    await rebind2fa(oldCode.value, newSecret.value, newCode.value)
    ElMessage.success('2FA 换绑成功')
    rebindMode.value = false
    await loadStatus()
  } catch (error) {
    ElMessage.error(backendErrorMessage(error, '换绑失败，请检查验证码'))
  } finally {
    loading.value = false
  }
}

function cancelRebind() {
  rebindMode.value = false
}

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

    <!-- Status -->
    <el-alert v-if="enabled && !rebindMode" title="2FA 已启用" type="success" show-icon :closable="false" style="margin-bottom: 16px" />

    <!-- Not enabled: initial setup -->
    <el-card v-if="!enabled && step === 'idle'" shadow="never">
      <p>2FA 未启用，批量提现打款需要先绑定。</p>
      <el-button type="primary" @click="startSetup">开始绑定</el-button>
    </el-card>

    <!-- Initial setup QR + verify -->
    <el-card v-if="step === 'show-qr' || step === 'verify'" shadow="never">
      <h3>第 1 步：扫描二维码</h3>
      <p>用 Google Authenticator 或类似 App 扫描下方二维码，或手动输入密钥。</p>
      <div style="text-align: center; margin: 16px 0">
        <img :src="qrImageUrl(otpauthUri)" alt="2FA QR Code" width="240" height="240" />
      </div>
      <p class="mono" style="word-break: break-all">密钥：{{ secret }}</p>
      <h3 style="margin-top: 24px">第 2 步：输入验证码</h3>
      <p>在 App 中查看 6 位验证码并输入确认。</p>
      <el-input v-model="verificationCode" placeholder="6 位验证码" maxlength="6" style="max-width: 200px" @keyup.enter="confirmEnable" />
      <div style="margin-top: 16px">
        <el-button type="primary" :loading="loading" @click="confirmEnable">确认绑定</el-button>
        <el-button @click="step = 'idle'">取消</el-button>
      </div>
    </el-card>

    <!-- Enabled: rebind button -->
    <el-card v-if="enabled && !rebindMode" shadow="never">
      <p>如需更换绑定的设备，可以在此换绑。</p>
      <el-button type="warning" @click="startRebind">换绑 2FA</el-button>
    </el-card>

    <!-- Rebind flow: step 1 old code -->
    <el-card v-if="rebindMode && rebindStep === 'old-code'" shadow="never">
      <h3>换绑 第 1 步：验证旧验证码</h3>
      <p>请输入当前绑定的 Google Authenticator 的 6 位验证码。</p>
      <el-input v-model="oldCode" placeholder="旧验证码" maxlength="6" style="max-width: 200px" @keyup.enter="verifyOldCode" />
      <div style="margin-top: 16px">
        <el-button type="primary" :loading="loading" @click="verifyOldCode">下一步</el-button>
        <el-button @click="cancelRebind">取消</el-button>
      </div>
    </el-card>

    <!-- Rebind flow: step 2 new QR -->
    <el-card v-if="rebindMode && rebindStep === 'new-qr'" shadow="never">
      <h3>换绑 第 2 步：扫描新二维码</h3>
      <p>用新设备的 Google Authenticator 扫描下方二维码。</p>
      <div style="text-align: center; margin: 16px 0">
        <img :src="qrImageUrl(newOtpauthUri)" alt="New 2FA QR Code" width="240" height="240" />
      </div>
      <p class="mono" style="word-break: break-all">新密钥：{{ newSecret }}</p>
      <h3 style="margin-top: 24px">换绑 第 3 步：输入新验证码</h3>
      <p>在新设备上查看 6 位验证码并输入确认。</p>
      <el-input v-model="newCode" placeholder="新验证码" maxlength="6" style="max-width: 200px" @keyup.enter="confirmRebind" />
      <div style="margin-top: 16px">
        <el-button type="primary" :loading="loading" @click="confirmRebind">确认换绑</el-button>
        <el-button @click="cancelRebind">取消</el-button>
      </div>
    </el-card>
  </section>
</template>
