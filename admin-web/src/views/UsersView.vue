<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { fetchUsers } from '../services/adminApi'
import type { AdminUserSummary } from '../services/adminApi'

const loading = ref(false)
const error = ref('')
const users = ref<AdminUserSummary[]>([])

async function loadUsers() {
  loading.value = true
  error.value = ''
  try {
    users.value = await fetchUsers()
  } catch {
    error.value = '用户列表加载失败'
  } finally {
    loading.value = false
  }
}

onMounted(loadUsers)
</script>

<template>
  <section class="page-section">
    <div class="page-header">
      <div>
        <h1>用户管理</h1>
        <p>查看用户状态、积分余额和创建时间</p>
      </div>
      <el-button @click="loadUsers">刷新</el-button>
    </div>
    <el-alert v-if="error" :title="error" show-icon type="error" />
    <el-table v-loading="loading" :data="users" border>
      <el-table-column label="用户名" min-width="180" prop="username" />
      <el-table-column label="状态" width="120">
        <template #default="{ row }">
          <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'danger'" effect="plain">
            {{ row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column align="right" label="积分" prop="pointBalance" width="120" />
      <el-table-column label="创建时间" min-width="220" prop="createdAt" />
    </el-table>
  </section>
</template>
