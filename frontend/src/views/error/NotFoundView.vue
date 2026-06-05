<template>
  <main class="not-found">
    <h1>404</h1>
    <p>页面不存在或当前阶段尚未开放。</p>
    <el-button type="primary" @click="$router.replace('/dashboard')">返回工作台</el-button>
  </main>
</template>
