<template>
  <div class="page-container">
    <div class="page-header">
      <h2 class="page-title">团队管理</h2>
    </div>

    <!-- 搜索栏 -->
    <el-card shadow="never" class="system-card filter-card">
      <div class="filter-row">
        <el-input
          v-model="keyword"
          placeholder="搜索团队名称或编码"
          clearable
          class="filter-input"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        >
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-select v-model="statusFilter" placeholder="状态" clearable class="filter-select" @change="handleSearch">
          <el-option label="启用" :value="1" />
          <el-option label="停用" :value="0" />
        </el-select>
        <el-button type="primary" @click="handleSearch">搜索</el-button>
        <el-button type="primary" @click="openCreateDialog">
          <el-icon><Plus /></el-icon>新建团队
        </el-button>
      </div>
    </el-card>

    <!-- 列表 -->
    <el-card shadow="never" class="system-card">
      <el-table :data="tableData" v-loading="loading" row-key="id">
        <el-table-column prop="teamCode" label="编码" width="110" />
        <el-table-column prop="teamName" label="团队名称" min-width="150" />
        <el-table-column prop="ownerName" label="负责人" width="100" />
        <el-table-column prop="description" label="描述" min-width="180" show-overflow-tooltip />
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="150">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="230" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEditDialog(row)">编辑</el-button>
            <el-button link type="primary" @click="openMemberDrawer(row)">成员</el-button>
            <el-button link :type="row.status === 1 ? 'warning' : 'success'" @click="toggleStatus(row)">
              {{ row.status === 1 ? '停用' : '启用' }}
            </el-button>
            <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-row">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @size-change="loadData"
          @current-change="loadData"
        />
      </div>
    </el-card>

    <!-- 新建/编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="editingId ? '编辑团队' : '新建团队'"
      width="520px"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="团队名称" prop="teamName">
          <el-input v-model="form.teamName" placeholder="请输入团队名称" />
        </el-form-item>
        <el-form-item label="团队编码" prop="teamCode">
          <el-input v-model="form.teamCode" placeholder="如 TEAM-001" :disabled="!!editingId" />
        </el-form-item>
        <el-form-item label="负责人" prop="ownerId">
          <el-select v-model="form.ownerId" placeholder="选择负责人" filterable class="full-width">
            <el-option
              v-for="u in userOptions"
              :key="u.id"
              :label="u.nickname || u.username"
              :value="u.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="3" placeholder="团队描述（可选）" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>

    <!-- 成员管理抽屉 -->
    <el-drawer
      v-model="memberDrawerVisible"
      :title="`${currentTeam?.teamName || ''} — 成员管理`"
      size="480px"
      :destroy-on-close="true"
    >
      <div class="member-toolbar">
        <el-button type="primary" size="small" @click="openAddMemberDialog">
          <el-icon><Plus /></el-icon>添加成员
        </el-button>
      </div>

      <el-table :data="members" v-loading="memberLoading" size="small">
        <el-table-column label="成员">
          <template #default="{ row }">
            <span>{{ row.nickname || row.username }}</span>
            <el-tag size="small" class="member-username-tag">{{ row.username }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="角色" width="140">
          <template #default="{ row }">
            <el-select
              v-model="row.memberRole"
              size="small"
              :disabled="row.memberRole === 'OWNER'"
              @change="(val: string) => changeRole(row, val)"
            >
              <el-option label="负责人" value="OWNER" />
              <el-option label="管理员" value="ADMIN" />
              <el-option label="成员" value="MEMBER" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="70">
          <template #default="{ row }">
            <el-button
              link
              type="danger"
              :disabled="row.memberRole === 'OWNER'"
              @click="removeMember(row)"
            >移除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!memberLoading && members.length === 0" description="暂无成员" />
    </el-drawer>

    <!-- 添加成员对话框 -->
    <el-dialog v-model="addMemberVisible" title="添加成员" width="400px" :close-on-click-modal="false">
      <el-form ref="memberFormRef" :model="memberForm" label-width="80px">
        <el-form-item label="用户" prop="userId" :rules="[{ required: true, message: '请选择用户' }]">
          <el-select v-model="memberForm.userId" filterable placeholder="选择用户" class="full-width">
            <el-option
              v-for="u in userOptions"
              :key="u.id"
              :label="`${u.nickname || u.username} (${u.username})`"
              :value="u.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="memberForm.memberRole" class="full-width">
            <el-option label="管理员" value="ADMIN" />
            <el-option label="成员" value="MEMBER" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addMemberVisible = false">取消</el-button>
        <el-button type="primary" :loading="addMemberLoading" @click="submitAddMember">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus'
import { Search, Plus } from '@element-plus/icons-vue'
import {
  teamPageApi, createTeamApi, updateTeamApi, updateTeamStatusApi, deleteTeamApi,
  teamMembersApi, addTeamMemberApi, removeTeamMemberApi, updateTeamMemberRoleApi,
  type TeamItem, type TeamMemberItem
} from '@/api/project'
import { userOptionsApi, type UserItem } from '@/api/system'
import { formatDateTime } from '@/utils/format'

const loading = ref(false)
const submitting = ref(false)
const tableData = ref<TeamItem[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const keyword = ref('')
const statusFilter = ref<number | undefined>(undefined)

const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()
const form = ref({ teamName: '', teamCode: '', ownerId: undefined as number | undefined, description: '', status: 1 })

const rules = {
  teamName: [{ required: true, message: '团队名称不能为空', trigger: 'blur' }],
  teamCode: [{ required: true, message: '团队编码不能为空', trigger: 'blur' }]
}

const userOptions = ref<UserItem[]>([])

const memberDrawerVisible = ref(false)
const currentTeam = ref<TeamItem | null>(null)
const members = ref<TeamMemberItem[]>([])
const memberLoading = ref(false)

const addMemberVisible = ref(false)
const addMemberLoading = ref(false)
const memberFormRef = ref<FormInstance>()
const memberForm = ref({ userId: undefined as number | undefined, memberRole: 'MEMBER' })

async function loadData() {
  loading.value = true
  try {
    const res = await teamPageApi({ page: page.value, size: size.value, keyword: keyword.value || undefined, status: statusFilter.value })
    tableData.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  page.value = 1
  loadData()
}

async function loadUserOptions() {
  try {
    userOptions.value = await userOptionsApi()
  } catch {
    // ignore
  }
}

function openCreateDialog() {
  editingId.value = null
  form.value = { teamName: '', teamCode: '', ownerId: undefined, description: '', status: 1 }
  dialogVisible.value = true
  formRef.value?.clearValidate()
}

function openEditDialog(row: TeamItem) {
  editingId.value = row.id
  form.value = {
    teamName: row.teamName,
    teamCode: row.teamCode,
    ownerId: row.ownerId,
    description: row.description || '',
    status: row.status
  }
  dialogVisible.value = true
  formRef.value?.clearValidate()
}

async function submitForm() {
  await formRef.value?.validate()
  submitting.value = true
  try {
    if (editingId.value) {
      await updateTeamApi(editingId.value, form.value)
      ElMessage.success('更新成功')
    } else {
      await createTeamApi(form.value)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    loadData()
  } finally {
    submitting.value = false
  }
}

async function toggleStatus(row: TeamItem) {
  const newStatus = row.status === 1 ? 0 : 1
  const label = newStatus === 1 ? '启用' : '停用'
  await ElMessageBox.confirm(`确定要${label}团队「${row.teamName}」吗？`, '提示', { type: 'warning' })
  await updateTeamStatusApi(row.id, newStatus)
  ElMessage.success(`${label}成功`)
  loadData()
}

async function handleDelete(row: TeamItem) {
  await ElMessageBox.confirm(`确定要删除团队「${row.teamName}」吗？删除后不可恢复。`, '确认删除', { type: 'error' })
  await deleteTeamApi(row.id)
  ElMessage.success('删除成功')
  loadData()
}

async function openMemberDrawer(row: TeamItem) {
  currentTeam.value = row
  memberDrawerVisible.value = true
  await loadMembers()
}

async function loadMembers() {
  if (!currentTeam.value) return
  memberLoading.value = true
  try {
    members.value = await teamMembersApi(currentTeam.value.id)
  } finally {
    memberLoading.value = false
  }
}

function openAddMemberDialog() {
  memberForm.value = { userId: undefined, memberRole: 'MEMBER' }
  addMemberVisible.value = true
  memberFormRef.value?.clearValidate()
}

async function submitAddMember() {
  await memberFormRef.value?.validate()
  if (!currentTeam.value || !memberForm.value.userId) return
  addMemberLoading.value = true
  try {
    await addTeamMemberApi(currentTeam.value.id, memberForm.value as { userId: number; memberRole: string })
    ElMessage.success('添加成功')
    addMemberVisible.value = false
    await loadMembers()
  } finally {
    addMemberLoading.value = false
  }
}

async function removeMember(row: TeamMemberItem) {
  if (!currentTeam.value) return
  await ElMessageBox.confirm(`确定要移除成员「${row.nickname || row.username}」吗？`, '提示', { type: 'warning' })
  await removeTeamMemberApi(currentTeam.value.id, row.id)
  ElMessage.success('移除成功')
  await loadMembers()
}

async function changeRole(row: TeamMemberItem, role: string) {
  if (!currentTeam.value) return
  try {
    await updateTeamMemberRoleApi(currentTeam.value.id, row.id, role)
    ElMessage.success('角色已更新')
  } catch {
    await loadMembers()
  }
}

onMounted(() => {
  loadData()
  loadUserOptions()
})
</script>

<style scoped>
.page-container {
  padding: 20px;
}
.page-header {
  margin-bottom: 16px;
}
.page-title {
  font-size: 20px;
  font-weight: 600;
  margin: 0;
}
.filter-card {
  margin-bottom: 16px;
}
.filter-row {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  align-items: center;
}
.filter-input {
  width: 240px;
}
.filter-select {
  width: 120px;
}
.pagination-row {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
.full-width {
  width: 100%;
}
.member-toolbar {
  margin-bottom: 12px;
}
.member-username-tag {
  margin-left: 6px;
  color: #909399;
}
</style>
