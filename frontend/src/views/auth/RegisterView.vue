<template>
  <main class="auth-page">
    <section class="auth-panel auth-panel--compact">
      <el-card class="auth-card" shadow="never">
        <h2>注册账号</h2>
        <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @keyup.enter="handleRegister">
          <el-form-item label="账号" prop="username">
            <el-input v-model="form.username" placeholder="请输入登录账号" />
          </el-form-item>
          <el-form-item label="昵称" prop="nickname">
            <el-input v-model="form.nickname" placeholder="请输入昵称" />
          </el-form-item>
          <el-form-item label="邮箱" prop="email">
            <el-input v-model="form.email" placeholder="name@company.com" />
          </el-form-item>
          <el-form-item label="密码" prop="password">
            <el-input v-model="form.password" type="password" show-password placeholder="至少 6 位" />
          </el-form-item>
          <el-button class="auth-submit" type="primary" :loading="loading" @click="handleRegister">注册并进入</el-button>
        </el-form>
        <div class="auth-footer">
          <span>已有账号？</span>
          <router-link to="/login">返回登录</router-link>
        </div>
      </el-card>
    </section>
  </main>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import type { FormInstance, FormRules } from 'element-plus'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()
const formRef = ref<FormInstance>()
const loading = ref(false)

const form = reactive({
  username: '',
  nickname: '',
  email: '',
  password: ''
})

const rules: FormRules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码长度不能少于 6 位', trigger: 'blur' }
  ]
}

async function handleRegister() {
  await formRef.value?.validate()
  loading.value = true
  try {
    await userStore.register(form)
    router.replace('/dashboard')
  } finally {
    loading.value = false
  }
}
</script>
