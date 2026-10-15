<script setup lang="ts">
import { computed, type Component } from 'vue'
import { CircleCheck, CircleClose, Clock, Remove } from '@element-plus/icons-vue'
import { displayStateLabel, displayStateTagType } from '../utils/labels.mjs'

const props = defineProps<{
  /** 契约 `display_state`：unused | active | expired | disabled */
  state: string
}>()

const STATE_ICONS: Record<string, Component> = {
  unused: Remove,
  active: CircleCheck,
  expired: Clock,
  disabled: CircleClose,
}

const label = computed(() => displayStateLabel(props.state))
const tagType = computed(() => displayStateTagType(props.state))
const icon = computed<Component>(() => STATE_ICONS[props.state] ?? Remove)
</script>

<template>
  <el-tag :type="tagType" size="small" effect="light" disable-transitions>
    <span class="state-tag">
      <el-icon :size="12" aria-hidden="true"><component :is="icon" /></el-icon>
      <span>{{ label }}</span>
    </span>
  </el-tag>
</template>

<style scoped>
.state-tag {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
}
</style>
