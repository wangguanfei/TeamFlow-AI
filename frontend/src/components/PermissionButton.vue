<template>
  <el-button v-if="visible" v-bind="$attrs">
    <slot />
  </el-button>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useUserStore } from '@/stores/user'

defineOptions({ inheritAttrs: false })

const props = defineProps<{
  permission: string
}>()

const userStore = useUserStore()
const visible = computed(() => userStore.hasPermission(props.permission))
</script>
