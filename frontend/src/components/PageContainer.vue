<template>
  <section class="page-container">
    <div v-if="$slots.actions" class="page-container__toolbar">
      <div class="page-container__actions">
        <slot name="actions" />
      </div>
    </div>
    <main class="page-container__body">
      <slot />
    </main>
  </section>
</template>

<script setup lang="ts">
// title / description 仍保留为 props：各页面仍在传入，避免多余属性透传到根元素。
// 按用户要求，页面左上角标题与说明不再渲染，仅保留正文与顶部操作工具条。
defineProps<{
  title: string
  description?: string
}>()
</script>
