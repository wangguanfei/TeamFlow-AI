<template>
  <PageContainer title="权限管理" description="维护菜单、按钮和接口权限编码">
    <template #actions>
      <PermissionButton permission="system:permission:create" type="primary" :icon="Plus" @click="openCreate">新建权限</PermissionButton>
    </template>

    <el-card shadow="never" class="system-card">
      <div class="table-toolbar">
        <el-input v-model="keyword" class="table-search" placeholder="搜索权限编码、名称或路径" clearable :prefix-icon="Search" @keyup.enter="loadData" />
        <el-button :icon="Search" @click="loadData">查询</el-button>
      </div>
      <el-table v-loading="loading" :data="pageData.records" row-key="id">
        <el-table-column prop="permissionCode" label="权限编码" min-width="220" />
        <el-table-column prop="permissionName" label="权限名称" min-width="160" />
        <el-table-column prop="resourceType" label="类型" width="110" />
        <el-table-column prop="resourcePath" label="资源路径" min-width="220" />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <PermissionButton permission="system:permission:update" link type="primary" @click="openEdit(row)">编辑</PermissionButton>
            <PermissionButton permission="system:permission:delete" link type="danger" @click="remove(row)">删除</PermissionButton>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination v-model:current-page="page" v-model:page-size="size" class="table-pagination" layout="total, sizes, prev, pager, next" :total="pageData.total" @current-change="loadData" @size-change="loadData" />
    </el-card>

    <el-dialog v-model="formVisible" :title="editingId ? '编辑权限' : '新建权限'" width="560px">
      <el-form :model="form" label-width="92px">
        <el-form-item label="权限编码"><el-input v-model="form.permissionCode" /></el-form-item>
        <el-form-item label="权限名称"><el-input v-model="form.permissionName" /></el-form-item>
        <el-form-item label="资源类型">
          <el-select v-model="form.resourceType">
            <el-option label="菜单" value="MENU" />
            <el-option label="按钮" value="BUTTON" />
            <el-option label="接口" value="API" />
          </el-select>
        </el-form-item>
        <el-form-item label="资源路径"><el-input v-model="form.resourcePath" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </PageContainer>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'
import PageContainer from '@/components/PageContainer.vue'
import PermissionButton from '@/components/PermissionButton.vue'
import { createPermissionApi, deletePermissionApi, permissionPageApi, updatePermissionApi, type PageResult, type PermissionItem } from '@/api/system'

const keyword = ref('')
const page = ref(1)
const size = ref(10)
const pageData = ref<PageResult<PermissionItem>>({ page: 1, size: 10, total: 0, records: [] })
const formVisible = ref(false)
const editingId = ref<number | null>(null)
const form = reactive({ permissionCode: '', permissionName: '', resourceType: 'API', resourcePath: '' })

const loading = ref(false)

onMounted(loadData)

async function loadData() {
  loading.value = true
  try {
    pageData.value = await permissionPageApi({ page: page.value, size: size.value, keyword: keyword.value })
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingId.value = null
  Object.assign(form, { permissionCode: '', permissionName: '', resourceType: 'API', resourcePath: '' })
  formVisible.value = true
}

function openEdit(row: PermissionItem) {
  editingId.value = row.id
  Object.assign(form, row)
  formVisible.value = true
}

async function save() {
  if (editingId.value) await updatePermissionApi(editingId.value, form)
  else await createPermissionApi(form)
  ElMessage.success('保存成功')
  formVisible.value = false
  await loadData()
}

async function remove(row: PermissionItem) {
  await ElMessageBox.confirm(`确认删除权限 ${row.permissionName}？`, '删除确认', { type: 'warning' })
  await deletePermissionApi(row.id)
  ElMessage.success('删除成功')
  await loadData()
}
</script>
