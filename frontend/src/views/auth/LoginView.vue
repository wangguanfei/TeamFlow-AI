<template>
  <main class="auth-page">
    <section class="auth-panel">
      <div class="auth-copy">
        <div class="brand brand--auth">
          <div class="brand__mark">TF</div>
          <div>
            <strong>TeamFlow AI</strong>
            <span>AI 原生企业协同平台</span>
          </div>
        </div>
        <h1>
          <span>以 AI 为核心</span>
          <span>重塑团队协作方式</span>
        </h1>
        <p>从项目规划到知识沉淀，AI 助手贯穿协作全流程——智能问答、文档摘要、代码与 SQL 生成即问即得，叠加企业级 RBAC 权限治理与全链路审计，让每一次决策更快、更准、更可控。</p>
        <div class="auth-status" aria-label="平台状态">
          <div>
            <strong>AI 智能引擎已就绪</strong>
            <span>智能问答 · 知识检索 · 决策提速</span>
          </div>
          <div class="auth-signal" aria-hidden="true">
            <i></i>
            <i></i>
            <i></i>
          </div>
        </div>
      </div>

      <el-card class="auth-card" shadow="never">
        <div class="brand auth-login-brand">
          <div class="brand__mark">TF</div>
          <div>
            <strong>TeamFlow AI</strong>
            <span>企业级安全认证</span>
          </div>
        </div>
        <h2>欢迎回来</h2>
        <p class="auth-card-subtitle">登录以开启你的智能协作工作区</p>
        <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @keyup.enter="handleLogin">
          <el-form-item label="登录账号" prop="username">
            <el-input v-model="form.username" placeholder="请输入登录账号" :prefix-icon="User" />
          </el-form-item>
          <el-form-item label="账户密码" prop="password">
            <el-input v-model="form.password" type="password" show-password placeholder="请输入账户密码" :prefix-icon="Lock" />
          </el-form-item>
          <div class="auth-options">
            <el-checkbox v-model="form.rememberMe">保持登录状态</el-checkbox>
            <span>忘记密码？请联系系统管理员</span>
          </div>
          <el-button class="auth-submit" type="primary" :loading="loading" @click="handleLogin">进入智能工作区</el-button>
        </el-form>
        <div class="auth-foot">
          <span>端到端加密会话</span>
          <span>全链路操作审计</span>
        </div>
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
