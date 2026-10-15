<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()

function goHome(): void {
  void router.replace({ name: 'dashboard' })
}
</script>

<template>
  <el-result icon="warning" title="页面不存在" sub-title="该地址没有对应的后台页面，可能已被移除。">
    <template #extra>
      <el-button type="primary" @click="goHome">返回概览</el-button>
    </template>
  </el-result>
</template>
