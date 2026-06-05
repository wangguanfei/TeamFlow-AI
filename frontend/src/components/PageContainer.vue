<template>
  <section class="page-container">
    <header class="page-container__header">
      <div>
        <h1>{{ title }}</h1>
        <p v-if="description">{{ description }}</p>
      </div>
      <div class="page-container__actions">
        <slot name="actions" />
      </div>
    </header>
    <main class="page-container__body">
      <slot />
    </main>
  </section>
</template>

<script setup lang="ts">
defineProps<{
  title: string
  description?: string
}>()
</script>
