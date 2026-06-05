<template>
  <main class="auth-page">
    <section class="auth-panel">
      <div class="auth-copy">
        <div class="brand brand--auth">
          <div class="brand__mark">TF</div>
          <div>
            <strong>TeamFlow AI</strong>
            <span>企业协同平台</span>
          </div>
        </div>
        <h1>企业协同工作台</h1>
        <p>统一身份入口，连接项目协作、任务流转、知识资产与 AI 助手能力。</p>
      </div>

      <el-card class="auth-card" shadow="never">
        <h2>账号登录</h2>
        <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @keyup.enter="handleLogin">
          <el-form-item label="账号" prop="username">
            <el-input v-model="form.username" placeholder="请输入账号" :prefix-icon="User" />
          </el-form-item>
          <el-form-item label="密码" prop="password">
            <el-input v-model="form.password" type="password" show-password placeholder="请输入密码" :prefix-icon="Lock" />
          </el-form-item>
          <div class="auth-options">
            <el-checkbox v-model="form.rememberMe">记住我</el-checkbox>
            <span>请联系系统管理员重置密码</span>
          </div>
          <el-button class="auth-submit" type="primary" :loading="loading" @click="handleLogin">登录</el-button>
        </el-form>
      </el-card>
    </section>
  </main>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { FormInstance, FormRules } from 'element-plus'
import { Lock, User } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const formRef = ref<FormInstance>()
const loading = ref(false)

const form = reactive({
  username: '',
  password: '',
  rememberMe: true
})

const rules: FormRules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

async function handleLogin() {
  await formRef.value?.validate()
  loading.value = true
  try {
    await userStore.login(form)
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/dashboard'
    router.replace(redirect)
  } finally {
    loading.value = false
  }
}
</script>
