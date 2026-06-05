<template>
  <PageContainer title="菜单管理" description="维护前端动态路由、图标和菜单权限码">
    <template #actions>
      <PermissionButton permission="system:menu:create" type="primary" :icon="Plus" @click="openCreate">新建菜单</PermissionButton>
    </template>

    <el-card shadow="never" class="system-card">
      <div class="table-toolbar">
        <el-input v-model="keyword" class="table-search" placeholder="搜索菜单名称、路由或权限码" clearable :prefix-icon="Search" @keyup.enter="loadData" />
        <el-button :icon="Search" @click="loadData">查询</el-button>
      </div>
      <el-table :data="pageData.records" row-key="id">
        <el-table-column prop="menuName" label="菜单名称" min-width="150" />
        <el-table-column prop="path" label="路由" min-width="180" />
        <el-table-column prop="component" label="组件" min-width="180" />
        <el-table-column prop="permissionCode" label="权限码" min-width="180" />
        <el-table-column prop="menuType" label="类型" width="90" />
        <el-table-column prop="sortNo" label="排序" width="90" />
        <el-table-column label="显示" width="90">
          <template #default="{ row }">
            <el-tag :type="row.visible === 1 ? 'success' : 'info'">{{ row.visible === 1 ? '显示' : '隐藏' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <PermissionButton permission="system:menu:update" link type="primary" @click="openEdit(row)">编辑</PermissionButton>
            <PermissionButton permission="system:menu:delete" link type="danger" @click="remove(row)">删除</PermissionButton>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination v-model:current-page="page" v-model:page-size="size" class="table-pagination" layout="total, sizes, prev, pager, next" :total="pageData.total" @current-change="loadData" @size-change="loadData" />
    </el-card>

    <el-dialog v-model="formVisible" :title="editingId ? '编辑菜单' : '新建菜单'" width="620px">
      <el-form :model="form" label-width="96px">
        <el-form-item label="父级ID"><el-input-number v-model="form.parentId" :min="0" /></el-form-item>
        <el-form-item label="菜单名称"><el-input v-model="form.menuName" /></el-form-item>
        <el-form-item label="路由"><el-input v-model="form.path" /></el-form-item>
        <el-form-item label="组件"><el-input v-model="form.component" placeholder="如 RoleManagementView" /></el-form-item>
        <el-form-item label="图标"><el-input v-model="form.icon" placeholder="Element Plus 图标名" /></el-form-item>
        <el-form-item label="权限码"><el-input v-model="form.permissionCode" /></el-form-item>
        <el-form-item label="类型">
          <el-select v-model="form.menuType">
            <el-option label="目录" value="DIR" />
            <el-option label="菜单" value="MENU" />
            <el-option label="按钮" value="BUTTON" />
          </el-select>
        </el-form-item>
        <el-form-item label="排序"><el-input-number v-model="form.sortNo" :min="1" /></el-form-item>
        <el-form-item label="显示"><el-switch v-model="form.visible" :active-value="1" :inactive-value="0" /></el-form-item>
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
import { createMenuApi, deleteMenuApi, menuPageApi, updateMenuApi, type MenuItem, type PageResult } from '@/api/system'

const keyword = ref('')
const page = ref(1)
const size = ref(10)
const pageData = ref<PageResult<MenuItem>>({ page: 1, size: 10, total: 0, records: [] })
const formVisible = ref(false)
const editingId = ref<number | null>(null)
const form = reactive({ parentId: 0, menuName: '', path: '', component: '', icon: '', permissionCode: '', menuType: 'MENU', sortNo: 100, visible: 1 })

onMounted(loadData)

async function loadData() {
  pageData.value = await menuPageApi({ page: page.value, size: size.value, keyword: keyword.value })
}

function openCreate() {
  editingId.value = null
  Object.assign(form, { parentId: 0, menuName: '', path: '', component: '', icon: '', permissionCode: '', menuType: 'MENU', sortNo: 100, visible: 1 })
  formVisible.value = true
}

function openEdit(row: MenuItem) {
  editingId.value = row.id
  Object.assign(form, row)
  formVisible.value = true
}

async function save() {
  if (editingId.value) await updateMenuApi(editingId.value, form)
  else await createMenuApi(form)
  ElMessage.success('保存成功')
  formVisible.value = false
  await loadData()
}

async function remove(row: MenuItem) {
  await ElMessageBox.confirm(`确认删除菜单 ${row.menuName}？`, '删除确认', { type: 'warning' })
  await deleteMenuApi(row.id)
  ElMessage.success('删除成功')
  await loadData()
}
</script>
