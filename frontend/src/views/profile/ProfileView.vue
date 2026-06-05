<template>
  <PageContainer title="个人中心" description="管理个人资料、安全设置、权限信息与使用偏好">
    <template #actions>
      <el-button :icon="Refresh" @click="loadProfile">刷新</el-button>
    </template>

    <div v-loading="loading" class="profile-layout">
      <aside class="profile-sidebar">
        <section class="profile-card profile-user-card">
          <el-avatar :size="76" :src="avatarSrc">{{ avatarText }}</el-avatar>
          <h2>{{ profile?.nickname || profile?.username || '-' }}</h2>
          <p>{{ profile?.username || '-' }}</p>
          <div class="profile-status">
            <el-tag :type="profile?.status === 1 ? 'success' : 'danger'" effect="plain">
              {{ profile?.status === 1 ? '启用中' : '已禁用' }}
            </el-tag>
            <el-tag effect="plain">{{ profile?.roles.length || 0 }} 个角色</el-tag>
          </div>
        </section>

        <section class="profile-card">
          <div class="profile-section-title">
            <el-icon><DataAnalysis /></el-icon>
            <span>我的数据概览</span>
          </div>
          <div class="profile-overview-grid">
            <div v-for="item in overviewItems" :key="item.label" class="profile-overview-item">
              <span :class="item.className">
                <el-icon><component :is="item.icon" /></el-icon>
              </span>
              <strong>{{ item.value }}</strong>
              <small>{{ item.label }}</small>
            </div>
          </div>
        </section>

        <section class="profile-card profile-login-card">
          <div class="profile-section-title">
            <el-icon><Connection /></el-icon>
            <span>账号信息</span>
          </div>
          <dl>
            <dt>最近登录</dt>
            <dd>{{ formatDate(profile?.lastLoginTime) }}</dd>
            <dt>登录 IP</dt>
            <dd>{{ profile?.lastLoginIp || '暂无记录' }}</dd>
            <dt>创建时间</dt>
            <dd>{{ formatDate(profile?.createdAt) }}</dd>
          </dl>
        </section>
      </aside>

      <section class="profile-main profile-card">
        <el-tabs v-model="activeTab" class="profile-tabs">
          <el-tab-pane label="基本资料" name="basic">
            <el-form label-width="96px" class="profile-form">
              <el-form-item label="头像">
                <div class="profile-avatar-uploader">
                  <el-avatar :size="58" :src="avatarSrc">{{ avatarText }}</el-avatar>
                  <el-upload
                    action="#"
                    accept="image/*"
                    :show-file-list="false"
                    :http-request="uploadAvatar"
                  >
                    <el-button :icon="Upload" :loading="uploadingAvatar">上传头像</el-button>
                  </el-upload>
                  <small>支持 JPG、PNG、WebP 等图片格式</small>
                </div>
              </el-form-item>
              <el-form-item label="账号">
                <el-input v-model="profileForm.username" disabled />
              </el-form-item>
              <el-form-item label="昵称">
                <el-input v-model="profileForm.nickname" maxlength="50" show-word-limit placeholder="请输入昵称" />
              </el-form-item>
              <el-form-item label="邮箱">
                <el-input v-model="profileForm.email" :prefix-icon="Message" placeholder="name@company.com" clearable />
              </el-form-item>
              <el-form-item label="手机号">
                <el-input v-model="profileForm.mobile" :prefix-icon="Iphone" placeholder="请输入手机号" clearable />
              </el-form-item>
              <el-form-item>
                <el-button type="primary" :icon="Check" :loading="savingProfile" @click="saveProfile">保存资料</el-button>
              </el-form-item>
            </el-form>
          </el-tab-pane>

          <el-tab-pane label="安全设置" name="security">
            <el-form label-width="112px" class="profile-form">
              <el-form-item label="旧密码">
                <el-input v-model="passwordForm.oldPassword" type="password" show-password placeholder="请输入旧密码" />
              </el-form-item>
              <el-form-item label="新密码">
                <el-input v-model="passwordForm.newPassword" type="password" show-password placeholder="至少 6 位" />
              </el-form-item>
              <el-form-item label="确认新密码">
                <el-input v-model="passwordForm.confirmPassword" type="password" show-password placeholder="再次输入新密码" />
              </el-form-item>
              <el-form-item>
                <el-button type="primary" :icon="Lock" :loading="savingPassword" @click="savePassword">修改密码</el-button>
              </el-form-item>
            </el-form>
          </el-tab-pane>

          <el-tab-pane label="权限信息" name="permissions">
            <div class="profile-permission-panel">
              <div class="profile-permission-summary">
                <article>
                  <strong>{{ profile?.roles.length || 0 }}</strong>
                  <span>角色</span>
                </article>
                <article>
                  <strong>{{ profile?.permissions.length || 0 }}</strong>
                  <span>权限点</span>
                </article>
                <article>
                  <strong>{{ permissionGroups.length }}</strong>
                  <span>业务范围</span>
                </article>
              </div>

              <div class="profile-permission-block">
                <div class="profile-section-title">
                  <el-icon><UserFilled /></el-icon>
                  <span>当前角色</span>
                </div>
                <div class="profile-tag-list">
                  <el-tag v-for="role in profile?.roles" :key="role" effect="plain">{{ role }}</el-tag>
                  <el-empty v-if="!profile?.roles.length" description="暂无角色" />
                </div>
              </div>
              <div class="profile-permission-block">
                <div class="profile-section-title">
                  <el-icon><Key /></el-icon>
                  <span>权限范围</span>
                </div>
                <div class="profile-scope-list">
                  <div v-for="group in permissionGroups" :key="group.key" class="profile-scope-item">
                    <span>{{ group.label }}</span>
                    <el-progress :percentage="group.percent" :show-text="false" />
                    <strong>{{ group.count }}</strong>
                  </div>
                  <el-empty v-if="!permissionGroups.length" description="暂无权限" />
                </div>
              </div>

              <el-collapse class="profile-advanced-permissions">
                <el-collapse-item title="技术权限编码" name="codes">
                  <div class="profile-tag-list profile-tag-list--scroll">
                    <el-tag v-for="permission in profile?.permissions" :key="permission" effect="plain" type="info">
                      {{ permission }}
                    </el-tag>
                  </div>
                </el-collapse-item>
              </el-collapse>
            </div>
          </el-tab-pane>

          <el-tab-pane label="偏好设置" name="preferences">
            <el-form label-width="128px" class="profile-form">
              <el-form-item label="默认 AI 模式">
                <el-select v-model="preferenceForm.defaultAiMode" class="profile-select">
                  <el-option label="普通问答" value="CHAT" />
                  <el-option label="知识库问答" value="KNOWLEDGE" />
                  <el-option label="文档总结" value="SUMMARY" />
                  <el-option label="代码生成" value="CODE" />
                  <el-option label="SQL 助手" value="SQL" />
                </el-select>
              </el-form-item>
              <el-form-item label="实时通知">
                <el-switch v-model="preferenceForm.notificationRealtime" active-text="开启" inactive-text="关闭" />
              </el-form-item>
              <el-form-item label="紧凑模式">
                <el-switch v-model="preferenceForm.compactMode" active-text="紧凑" inactive-text="舒适" />
              </el-form-item>
              <el-form-item label="主题偏好">
                <el-segmented v-model="preferenceForm.themeMode" :options="themeOptions" />
              </el-form-item>
              <el-form-item>
                <el-button type="primary" :icon="Check" @click="savePreferences">保存偏好</el-button>
                <el-button @click="resetPreferences">恢复默认</el-button>
              </el-form-item>
            </el-form>
          </el-tab-pane>
        </el-tabs>
      </section>
    </div>
  </PageContainer>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { UploadRequestOptions } from 'element-plus'
import {
  ChatDotRound,
  Check,
  Connection,
  DataAnalysis,
  Document,
  Files,
  FolderChecked,
  Iphone,
  Key,
  Lock,
  Message,
  Refresh,
  Upload,
  User,
  UserFilled
} from '@element-plus/icons-vue'
import PageContainer from '@/components/PageContainer.vue'
import {
  profileApi,
  profileOverviewApi,
  updateProfileApi,
  updateProfilePasswordApi,
  uploadProfileAvatarApi,
  type ProfileInfo,
  type ProfileOverview
} from '@/api/profile'
import { useNotificationStore } from '@/stores/notification'
import { useUserStore } from '@/stores/user'
import {
  DEFAULT_PROFILE_PREFERENCES,
  loadProfilePreferences,
  saveProfilePreferences,
  type ProfilePreferences
} from '@/utils/profilePreferences'
import { formatDateTime } from '@/utils/format'

const userStore = useUserStore()
const notificationStore = useNotificationStore()

const loading = ref(false)
const savingProfile = ref(false)
const savingPassword = ref(false)
const uploadingAvatar = ref(false)
const avatarVersion = ref(Date.now())
const activeTab = ref('basic')
const profile = ref<ProfileInfo | null>(null)
const overview = ref<ProfileOverview | null>(null)

const profileForm = reactive({
  username: '',
  nickname: '',
  avatarUrl: '',
  email: '',
  mobile: ''
})

const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

const preferenceForm = reactive<ProfilePreferences>(loadProfilePreferences())

const themeOptions = [
  { label: '跟随系统', value: 'SYSTEM' },
  { label: '浅色', value: 'LIGHT' },
  { label: '深色', value: 'DARK' }
]

const avatarText = computed(() => {
  const name = profile.value?.nickname || profile.value?.username || 'U'
  return name.slice(0, 1).toUpperCase()
})

const avatarSrc = computed(() => {
  if (!profile.value?.avatarUrl) {
    return ''
  }
  const separator = profile.value.avatarUrl.includes('?') ? '&' : '?'
  return `${profile.value.avatarUrl}${separator}t=${avatarVersion.value}`
})

const overviewItems = computed(() => [
  { label: '负责任务', value: overview.value?.responsibleTaskCount || 0, icon: User, className: 'is-blue' },
  { label: '执行任务', value: overview.value?.executingTaskCount || 0, icon: Check, className: 'is-green' },
  { label: '负责项目', value: overview.value?.ownedProjectCount || 0, icon: FolderChecked, className: 'is-purple' },
  { label: '知识文档', value: overview.value?.knowledgeDocCount || 0, icon: Document, className: 'is-amber' },
  { label: '上传文件', value: overview.value?.fileCount || 0, icon: Files, className: 'is-sky' },
  { label: 'AI 会话', value: overview.value?.aiSessionCount || 0, icon: ChatDotRound, className: 'is-violet' }
])

const permissionGroups = computed(() => {
  const permissions = profile.value?.permissions || []
  const grouped = permissions.reduce<Record<string, number>>((result, permission) => {
    const key = permission.split(':')[0] || 'other'
    result[key] = (result[key] || 0) + 1
    return result
  }, {})
  const max = Math.max(...Object.values(grouped), 1)
  return Object.entries(grouped)
    .map(([key, count]) => ({
      key,
      label: permissionScopeLabel(key),
      count,
      percent: Math.max(8, Math.round((count / max) * 100))
    }))
    .sort((left, right) => right.count - left.count)
})

onMounted(() => {
  loadProfile()
})

async function loadProfile() {
  loading.value = true
  try {
    const [profileResult, overviewResult] = await Promise.all([profileApi(), profileOverviewApi()])
    profile.value = profileResult
    overview.value = overviewResult
    Object.assign(profileForm, {
      username: profileResult.username,
      nickname: profileResult.nickname || '',
      avatarUrl: profileResult.avatarUrl || '',
      email: profileResult.email || '',
      mobile: profileResult.mobile || ''
    })
  } finally {
    loading.value = false
  }
}

async function saveProfile() {
  if (!profileForm.nickname.trim()) {
    ElMessage.warning('请输入昵称')
    return
  }
  savingProfile.value = true
  try {
    profile.value = await updateProfileApi({
      nickname: profileForm.nickname.trim(),
      avatarUrl: profile.value?.avatarUrl,
      email: profileForm.email.trim() || undefined,
      mobile: profileForm.mobile.trim() || undefined
    })
    await userStore.fetchCurrentUser()
    ElMessage.success('个人资料已更新')
  } finally {
    savingProfile.value = false
  }
}

async function uploadAvatar(options: UploadRequestOptions) {
  const file = options.file
  if (!file.type.startsWith('image/')) {
    ElMessage.warning('请选择图片文件')
    return
  }
  uploadingAvatar.value = true
  try {
    profile.value = await uploadProfileAvatarApi(file)
    options.onSuccess?.(profile.value)
    avatarVersion.value = Date.now()
    await userStore.fetchCurrentUser()
    ElMessage.success('头像已更新')
  } catch (error) {
    throw error
  } finally {
    uploadingAvatar.value = false
  }
}

async function savePassword() {
  if (!passwordForm.oldPassword || !passwordForm.newPassword) {
    ElMessage.warning('请输入旧密码和新密码')
    return
  }
  if (passwordForm.newPassword.length < 6) {
    ElMessage.warning('新密码至少 6 位')
    return
  }
  if (passwordForm.newPassword !== passwordForm.confirmPassword) {
    ElMessage.warning('两次输入的新密码不一致')
    return
  }
  savingPassword.value = true
  try {
    await updateProfilePasswordApi({
      oldPassword: passwordForm.oldPassword,
      newPassword: passwordForm.newPassword
    })
    Object.assign(passwordForm, {
      oldPassword: '',
      newPassword: '',
      confirmPassword: ''
    })
    ElMessage.success('密码已修改，请牢记新密码')
  } finally {
    savingPassword.value = false
  }
}

function savePreferences() {
  saveProfilePreferences({ ...preferenceForm })
  if (preferenceForm.notificationRealtime) {
    notificationStore.connect()
  } else {
    notificationStore.disconnect()
  }
  ElMessage.success('偏好设置已保存')
}

function resetPreferences() {
  Object.assign(preferenceForm, DEFAULT_PROFILE_PREFERENCES)
  savePreferences()
}

function formatDate(value?: string) {
  return formatDateTime(value, '暂无记录')
}

function permissionScopeLabel(scope: string) {
  const map: Record<string, string> = {
    dashboard: '工作台',
    project: '项目协作',
    task: '任务管理',
    knowledge: '知识库',
    file: '文件中心',
    ai: 'AI 助手',
    notification: '通知中心',
    system: '系统设置',
    user: '用户管理',
    role: '角色管理',
    permission: '权限管理',
    menu: '菜单管理'
  }
  return map[scope] || scope
}
</script>

<style scoped>
.profile-layout {
  display: grid;
  grid-template-columns: 320px minmax(0, 1fr);
  gap: 20px;
}

.profile-sidebar {
  display: grid;
  align-content: start;
  gap: 16px;
}

.profile-card {
  border: 1px solid var(--tf-border);
  border-radius: 16px;
  background: #fff;
  box-shadow: var(--tf-shadow);
}

.profile-user-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 24px 20px;
  text-align: center;
}

.profile-user-card h2 {
  margin: 14px 0 4px;
  color: var(--tf-text);
  font-size: 20px;
}

.profile-user-card p {
  margin: 0;
  color: var(--tf-muted);
}

.profile-status {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 8px;
  margin-top: 14px;
}

.profile-section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--tf-text);
  font-weight: 800;
}

.profile-section-title .el-icon {
  color: var(--tf-primary);
}

.profile-sidebar .profile-card:not(.profile-user-card) {
  padding: 18px;
}

.profile-overview-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  margin-top: 14px;
}

.profile-overview-item {
  min-width: 0;
  padding: 12px;
  border: 1px solid rgba(229, 231, 235, 0.9);
  border-radius: 14px;
  background: #f9fafb;
}

.profile-overview-item > span {
  width: 34px;
  height: 34px;
  display: grid;
  place-items: center;
  margin-bottom: 10px;
  border-radius: 10px;
}

.profile-overview-item strong,
.profile-overview-item small {
  display: block;
}

.profile-overview-item strong {
  color: var(--tf-text);
  font-size: 22px;
  line-height: 1.2;
}

.profile-overview-item small {
  margin-top: 3px;
  color: var(--tf-muted);
  font-size: 12px;
}

.is-blue {
  background: #eff6ff;
  color: var(--tf-primary);
}

.is-green {
  background: #ecfdf5;
  color: var(--tf-success);
}

.is-purple {
  background: #f5f3ff;
  color: var(--tf-secondary);
}

.is-amber {
  background: #fffbeb;
  color: var(--tf-warning);
}

.is-sky {
  background: #f0f9ff;
  color: #0284c7;
}

.is-violet {
  background: #f5f3ff;
  color: #7c3aed;
}

.profile-login-card dl {
  display: grid;
  grid-template-columns: 76px minmax(0, 1fr);
  gap: 10px 12px;
  margin: 14px 0 0;
  color: var(--tf-muted);
  font-size: 13px;
}

.profile-login-card dt {
  color: var(--tf-muted);
}

.profile-login-card dd {
  min-width: 0;
  margin: 0;
  overflow: hidden;
  color: var(--tf-text);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.profile-main {
  min-width: 0;
  padding: 4px 20px 20px;
}

.profile-tabs :deep(.el-tabs__header) {
  margin-bottom: 22px;
}

.profile-form {
  max-width: 640px;
}

.profile-select {
  width: 240px;
}

.profile-avatar-uploader {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.profile-avatar-uploader small {
  width: 100%;
  color: var(--tf-muted);
  font-size: 12px;
}

.profile-permission-panel {
  display: grid;
  gap: 18px;
}

.profile-permission-summary {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.profile-permission-summary article {
  padding: 14px;
  border: 1px solid rgba(37, 99, 235, 0.14);
  border-radius: 14px;
  background: linear-gradient(135deg, rgba(37, 99, 235, 0.08), rgba(124, 58, 237, 0.05));
}

.profile-permission-summary strong,
.profile-permission-summary span {
  display: block;
}

.profile-permission-summary strong {
  color: var(--tf-text);
  font-size: 24px;
  line-height: 1.2;
}

.profile-permission-summary span {
  margin-top: 4px;
  color: var(--tf-muted);
  font-size: 13px;
}

.profile-permission-block {
  padding: 16px;
  border: 1px solid rgba(229, 231, 235, 0.9);
  border-radius: 14px;
  background: #f9fafb;
}

.profile-tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 14px;
}

.profile-tag-list--scroll {
  max-height: 280px;
  overflow: auto;
  padding-right: 4px;
}

.profile-scope-list {
  display: grid;
  gap: 12px;
  margin-top: 14px;
}

.profile-scope-item {
  display: grid;
  grid-template-columns: 112px minmax(0, 1fr) 36px;
  gap: 12px;
  align-items: center;
}

.profile-scope-item span {
  color: var(--tf-text);
  font-weight: 700;
}

.profile-scope-item strong {
  color: var(--tf-primary);
  text-align: right;
}

.profile-advanced-permissions {
  border: 1px solid var(--tf-border);
  border-radius: 14px;
  padding: 0 14px;
}

@media (max-width: 1100px) {
  .profile-layout {
    grid-template-columns: 1fr;
  }

  .profile-overview-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 680px) {
  .profile-overview-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .profile-main {
    padding: 4px 14px 16px;
  }

  .profile-form {
    max-width: none;
  }
}
</style>
