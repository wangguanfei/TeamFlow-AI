<template>
  <PageContainer title="用户管理" description="查看用户并分配系统角色">
    <template #actions>
      <PermissionButton permission="system:user:create" type="primary" :icon="Plus" @click="openCreate">新建用户</PermissionButton>
    </template>

    <el-card shadow="never" class="system-card">
      <div class="table-toolbar">
        <el-input v-model="keyword" class="table-search" placeholder="搜索账号、昵称或邮箱" clearable :prefix-icon="Search" @keyup.enter="loadData" />
        <el-button :icon="Search" @click="loadData">查询</el-button>
      </div>
      <el-table v-loading="loading" :data="pageData.records" row-key="id">
        <el-table-column prop="username" label="账号" min-width="140" />
        <el-table-column prop="nickname" label="昵称" min-width="140" />
        <el-table-column prop="email" label="邮箱" min-width="200" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'">{{ row.status === 1 ? '启用' : '禁用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="角色" min-width="220">
          <template #default="{ row }">
            <el-tag v-for="role in row.roles" :key="role" class="role-tag">{{ role }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="最后登录" min-width="180">
          <template #default="{ row }">{{ formatDate(row.lastLoginTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <PermissionButton permission="system:user:update" link type="primary" @click="openEdit(row)">编辑</PermissionButton>
            <PermissionButton permission="system:user:update" link type="primary" @click="openPassword(row)">重置密码</PermissionButton>
            <PermissionButton permission="system:user:update" link type="primary" @click="openRoles(row)">分配角色</PermissionButton>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination v-model:current-page="page" v-model:page-size="size" class="table-pagination" layout="total, sizes, prev, pager, next" :total="pageData.total" @current-change="loadData" @size-change="loadData" />
    </el-card>

    <el-dialog v-model="createVisible" title="新建用户" width="620px">
      <el-form :model="createForm" label-width="92px">
        <el-form-item label="账号"><el-input v-model="createForm.username" placeholder="请输入登录账号" /></el-form-item>
        <el-form-item label="密码"><el-input v-model="createForm.password" type="password" show-password placeholder="至少 6 位" /></el-form-item>
        <el-form-item label="昵称"><el-input v-model="createForm.nickname" placeholder="显示名称" /></el-form-item>
        <el-form-item label="邮箱"><el-input v-model="createForm.email" placeholder="name@company.com" /></el-form-item>
        <el-form-item label="手机号"><el-input v-model="createForm.mobile" placeholder="可选" /></el-form-item>
        <el-form-item label="状态"><el-switch v-model="createForm.status" :active-value="1" :inactive-value="0" /></el-form-item>
        <el-form-item label="角色">
          <el-checkbox-group v-model="createForm.roleIds" class="inline-role-group">
            <el-checkbox v-for="role in roles" :key="role.id" :label="role.id">{{ role.roleName }}</el-checkbox>
          </el-checkbox-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" @click="saveUser">创建用户</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="editVisible" title="编辑用户" width="620px">
      <el-form :model="editForm" label-width="92px">
        <el-form-item label="账号"><el-input v-model="editForm.username" disabled /></el-form-item>
        <el-form-item label="昵称"><el-input v-model="editForm.nickname" placeholder="显示名称" /></el-form-item>
        <el-form-item label="邮箱"><el-input v-model="editForm.email" placeholder="name@company.com" /></el-form-item>
        <el-form-item label="手机号"><el-input v-model="editForm.mobile" placeholder="可选" /></el-form-item>
        <el-form-item label="状态"><el-switch v-model="editForm.status" :active-value="1" :inactive-value="0" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" @click="saveEdit">保存修改</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="passwordVisible" title="重置用户密码" width="520px">
      <el-form :model="passwordForm" label-width="92px">
        <el-form-item label="账号"><el-input v-model="passwordForm.username" disabled /></el-form-item>
        <el-form-item label="新密码">
          <el-input v-model="passwordForm.password" type="password" show-password placeholder="至少 6 位" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="passwordVisible = false">取消</el-button>
        <el-button type="primary" @click="savePassword">确认重置</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="roleVisible" title="分配用户角色" width="560px">
      <el-checkbox-group v-model="selectedRoleIds" class="permission-grid">
        <el-checkbox v-for="role in roles" :key="role.id" :label="role.id">
          <strong>{{ role.roleName }}</strong>
          <span>{{ role.roleCode }}</span>
        </el-checkbox>
      </el-checkbox-group>
      <template #footer>
        <el-button @click="roleVisible = false">取消</el-button>
        <el-button type="primary" @click="saveRoles">保存角色</el-button>
      </template>
    </el-dialog>
  </PageContainer>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'
import PageContainer from '@/components/PageContainer.vue'
import PermissionButton from '@/components/PermissionButton.vue'
import {
  assignUserRolesApi,
  createUserApi,
  resetUserPasswordApi,
  rolePageApi,
  updateUserApi,
  userPageApi,
  userRoleIdsApi,
  type PageResult,
  type RoleItem,
  type UserCreateForm,
  type UserItem
} from '@/api/system'
import { formatDateTime } from '@/utils/format'

const keyword = ref('')
const page = ref(1)
const size = ref(10)
const pageData = ref<PageResult<UserItem>>({ page: 1, size: 10, total: 0, records: [] })
const createVisible = ref(false)
const editVisible = ref(false)
const passwordVisible = ref(false)
const roleVisible = ref(false)
const currentUserId = ref<number | null>(null)
const selectedRoleIds = ref<number[]>([])
const roles = ref<RoleItem[]>([])
const createForm = ref<UserCreateForm>({
  username: '',
  password: '',
  nickname: '',
  email: '',
  mobile: '',
  status: 1,
  roleIds: []
})
const editForm = ref({
  id: 0,
  username: '',
  nickname: '',
  email: '',
  mobile: '',
  status: 1
})
const passwordForm = ref({
  id: 0,
  username: '',
  password: ''
})

const loading = ref(false)

onMounted(loadData)

async function loadData() {
  loading.value = true
  try {
    pageData.value = await userPageApi({ page: page.value, size: size.value, keyword: keyword.value })
  } finally {
    loading.value = false
  }
}

async function openCreate() {
  roles.value = (await rolePageApi({ page: 1, size: 200 })).records
  const defaultRole = roles.value.find((role) => role.roleCode === 'DEVELOPER') || roles.value[0]
  createForm.value = {
    username: '',
    password: '',
    nickname: '',
    email: '',
    mobile: '',
    status: 1,
    roleIds: defaultRole ? [defaultRole.id] : []
  }
  createVisible.value = true
}

async function saveUser() {
  if (!createForm.value.username.trim() || createForm.value.password.length < 6) {
    ElMessage.warning('请填写账号和至少 6 位密码')
    return
  }
  await createUserApi(createForm.value)
  ElMessage.success('用户创建成功')
  createVisible.value = false
  await loadData()
}

function openEdit(row: UserItem) {
  editForm.value = {
    id: row.id,
    username: row.username,
    nickname: row.nickname || '',
    email: row.email || '',
    mobile: row.mobile || '',
    status: row.status
  }
  editVisible.value = true
}

async function saveEdit() {
  await updateUserApi(editForm.value.id, {
    nickname: editForm.value.nickname,
    email: editForm.value.email,
    mobile: editForm.value.mobile,
    status: editForm.value.status
  })
  ElMessage.success('用户信息已更新')
  editVisible.value = false
  await loadData()
}

function openPassword(row: UserItem) {
  passwordForm.value = {
    id: row.id,
    username: row.username,
    password: ''
  }
  passwordVisible.value = true
}

async function savePassword() {
  if (passwordForm.value.password.length < 6) {
    ElMessage.warning('密码长度不能少于 6 位')
    return
  }
  await resetUserPasswordApi(passwordForm.value.id, passwordForm.value.password)
  ElMessage.success('密码已重置')
  passwordVisible.value = false
}

async function openRoles(row: UserItem) {
  currentUserId.value = row.id
  roles.value = (await rolePageApi({ page: 1, size: 200 })).records
  selectedRoleIds.value = await userRoleIdsApi(row.id)
  roleVisible.value = true
}

async function saveRoles() {
  if (!currentUserId.value) return
  await assignUserRolesApi(currentUserId.value, selectedRoleIds.value)
  ElMessage.success('角色已更新')
  roleVisible.value = false
  await loadData()
}

function formatDate(value?: string) {
  return formatDateTime(value)
}
</script>
