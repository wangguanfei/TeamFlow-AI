<template>
  <PageContainer title="角色管理" description="维护角色、状态和权限集合">
    <template #actions>
      <PermissionButton permission="system:role:create" type="primary" :icon="Plus" @click="openCreate">新建角色</PermissionButton>
    </template>

    <el-card shadow="never" class="system-card">
      <div class="table-toolbar">
        <el-input v-model="keyword" class="table-search" placeholder="搜索角色编码或名称" clearable :prefix-icon="Search" @keyup.enter="loadData" />
        <el-button :icon="Search" @click="loadData">查询</el-button>
      </div>
      <el-table v-loading="loading" :data="pageData.records" row-key="id">
        <el-table-column prop="roleCode" label="角色编码" min-width="160" />
        <el-table-column prop="roleName" label="角色名称" min-width="140" />
        <el-table-column prop="scopeType" label="作用域" width="110" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'">{{ row.status === 1 ? '启用' : '禁用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="180" />
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <PermissionButton permission="system:role:update" link type="primary" @click="openEdit(row)">编辑</PermissionButton>
            <PermissionButton permission="system:role:update" link type="primary" @click="openPermission(row)">分配权限</PermissionButton>
            <PermissionButton permission="system:role:delete" link type="danger" @click="remove(row)">删除</PermissionButton>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="size"
        class="table-pagination"
        layout="total, sizes, prev, pager, next"
        :total="pageData.total"
        @current-change="loadData"
        @size-change="loadData"
      />
    </el-card>

    <el-dialog v-model="formVisible" :title="editingId ? '编辑角色' : '新建角色'" width="520px">
      <el-form :model="form" label-width="92px">
        <el-form-item label="角色编码"><el-input v-model="form.roleCode" /></el-form-item>
        <el-form-item label="角色名称"><el-input v-model="form.roleName" /></el-form-item>
        <el-form-item label="作用域"><el-input v-model="form.scopeType" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="form.sortNo" :min="1" /></el-form-item>
        <el-form-item label="状态"><el-switch v-model="form.status" :active-value="1" :inactive-value="0" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" @click="saveRole">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="permissionVisible" title="分配权限" width="840px">
      <div class="permission-tree-toolbar">
        <el-input v-model="permissionKeyword" class="permission-tree-search" clearable :prefix-icon="Search" placeholder="搜索权限名称、编码或路径" @input="filterPermissionTree" />
        <el-button @click="checkAllPermissions">全选</el-button>
        <el-button @click="clearPermissions">清空</el-button>
      </div>
      <el-tree
        ref="permissionTreeRef"
        class="permission-tree"
        :data="permissionTreeData"
        :props="permissionTreeProps"
        node-key="key"
        show-checkbox
        default-expand-all
        :filter-node-method="filterPermissionNode"
        @check="syncCheckedPermissions"
      >
        <template #default="{ data }">
          <div class="permission-tree-node" :class="{ 'is-leaf': data.permissionId }">
            <div>
              <div class="permission-tree-title">
                <strong>{{ data.label }}</strong>
                <span v-if="data.code">{{ data.code }}</span>
              </div>
            </div>
            <div class="permission-tree-meta">
              <el-tooltip v-if="data.path" :content="data.path" placement="top" :show-after="120">
                <span class="permission-path-trigger" :title="data.path">路径</span>
              </el-tooltip>
              <el-tag v-if="data.type" size="small" effect="plain">{{ data.type }}</el-tag>
            </div>
          </div>
        </template>
      </el-tree>
      <template #footer>
        <span class="permission-tree-summary">已选择 {{ selectedPermissionIds.length }} 项权限</span>
        <el-button @click="permissionVisible = false">取消</el-button>
        <el-button type="primary" @click="savePermissions">保存权限</el-button>
      </template>
    </el-dialog>
  </PageContainer>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'
import PageContainer from '@/components/PageContainer.vue'
import PermissionButton from '@/components/PermissionButton.vue'
import {
  assignRolePermissionsApi,
  createRoleApi,
  deleteRoleApi,
  permissionPageApi,
  rolePageApi,
  rolePermissionIdsApi,
  updateRoleApi,
  type PageResult,
  type PermissionItem,
  type RoleItem
} from '@/api/system'

const keyword = ref('')
const page = ref(1)
const size = ref(10)
const pageData = ref<PageResult<RoleItem>>({ page: 1, size: 10, total: 0, records: [] })
const formVisible = ref(false)
const permissionVisible = ref(false)
const editingId = ref<number | null>(null)
const currentRoleId = ref<number | null>(null)
const permissions = ref<PermissionItem[]>([])
const selectedPermissionIds = ref<number[]>([])
const permissionKeyword = ref('')
const permissionTreeRef = ref()

interface PermissionTreeNode {
  key: string
  label: string
  code?: string
  path?: string
  type?: string
  permissionId?: number
  children?: PermissionTreeNode[]
}

const permissionTreeProps = {
  label: 'label',
  children: 'children'
}

const permissionTreeData = computed(() => buildPermissionTree(permissions.value))

const form = reactive({
  roleCode: '',
  roleName: '',
  scopeType: 'SYSTEM',
  sortNo: 100,
  status: 1,
  remark: ''
})

const loading = ref(false)

onMounted(loadData)

async function loadData() {
  loading.value = true
  try {
    pageData.value = await rolePageApi({ page: page.value, size: size.value, keyword: keyword.value })
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingId.value = null
  Object.assign(form, { roleCode: '', roleName: '', scopeType: 'SYSTEM', sortNo: 100, status: 1, remark: '' })
  formVisible.value = true
}

function openEdit(row: RoleItem) {
  editingId.value = row.id
  Object.assign(form, row)
  formVisible.value = true
}

async function saveRole() {
  if (editingId.value) {
    await updateRoleApi(editingId.value, form)
  } else {
    await createRoleApi(form)
  }
  ElMessage.success('保存成功')
  formVisible.value = false
  await loadData()
}

async function remove(row: RoleItem) {
  await ElMessageBox.confirm(`确认删除角色 ${row.roleName}？`, '删除确认', { type: 'warning' })
  await deleteRoleApi(row.id)
  ElMessage.success('删除成功')
  await loadData()
}

async function openPermission(row: RoleItem) {
  currentRoleId.value = row.id
  permissions.value = (await permissionPageApi({ page: 1, size: 300 })).records
  selectedPermissionIds.value = await rolePermissionIdsApi(row.id)
  permissionVisible.value = true
  permissionKeyword.value = ''
  await nextTick()
  permissionTreeRef.value?.setCheckedKeys(selectedPermissionIds.value.map(permissionKey))
}

async function savePermissions() {
  if (!currentRoleId.value) return
  selectedPermissionIds.value = currentCheckedPermissionIds()
  await assignRolePermissionsApi(currentRoleId.value, selectedPermissionIds.value)
  ElMessage.success('权限已更新')
  permissionVisible.value = false
}

function buildPermissionTree(items: PermissionItem[]): PermissionTreeNode[] {
  const moduleMap = new Map<string, PermissionTreeNode>()
  const sorted = [...items].sort((left, right) => left.permissionCode.localeCompare(right.permissionCode))
  for (const item of sorted) {
    const parts = item.permissionCode.split(':')
    const moduleKey = parts[0] || 'other'
    const moduleNode = ensureNode(moduleMap, `module:${moduleKey}`, moduleLabel(moduleKey))
    const subgroupKey = subgroupCode(parts)
    const parentNode = subgroupKey
      ? ensureChildNode(moduleNode, `group:${moduleKey}:${subgroupKey}`, subgroupLabel(moduleKey, subgroupKey))
      : moduleNode
    parentNode.children = parentNode.children || []
    parentNode.children.push({
      key: permissionKey(item.id),
      label: item.permissionName,
      code: item.permissionCode,
      path: item.resourcePath,
      type: item.resourceType,
      permissionId: item.id
    })
  }
  return [...moduleMap.values()].sort((left, right) => moduleSort(left.key) - moduleSort(right.key))
}

function ensureNode(map: Map<string, PermissionTreeNode>, key: string, label: string) {
  const existing = map.get(key)
  if (existing) return existing
  const node: PermissionTreeNode = { key, label, children: [] }
  map.set(key, node)
  return node
}

function ensureChildNode(parent: PermissionTreeNode, key: string, label: string) {
  parent.children = parent.children || []
  const existing = parent.children.find((child) => child.key === key)
  if (existing) return existing
  const node: PermissionTreeNode = { key, label, children: [] }
  parent.children.push(node)
  parent.children.sort((left, right) => left.label.localeCompare(right.label))
  return node
}

function subgroupCode(parts: string[]) {
  if (parts.length < 3) return ''
  return parts[1]
}

function moduleLabel(moduleKey: string) {
  const labels: Record<string, string> = {
    dashboard: '工作台',
    system: '系统管理',
    user: '用户',
    team: '团队',
    project: '项目',
    task: '任务',
    knowledge: '知识库',
    file: '文件中心',
    ai: 'AI 助手',
    notification: '通知中心'
  }
  return labels[moduleKey] || moduleKey
}

function subgroupLabel(moduleKey: string, subgroupKey: string) {
  const labels: Record<string, string> = {
    'system:user': '用户管理',
    'system:role': '角色管理',
    'system:permission': '权限管理',
    'system:menu': '菜单管理',
    'knowledge:space': '知识空间',
    'knowledge:doc': '知识文档'
  }
  return labels[`${moduleKey}:${subgroupKey}`] || subgroupKey
}

function moduleSort(key: string) {
  const order = ['dashboard', 'project', 'task', 'knowledge', 'file', 'ai', 'notification', 'team', 'system']
  const moduleKey = key.replace('module:', '')
  const index = order.indexOf(moduleKey)
  return index === -1 ? 999 : index
}

function permissionKey(id: number) {
  return `permission:${id}`
}

function currentCheckedPermissionIds() {
  const keys = permissionTreeRef.value?.getCheckedKeys(false) || []
  return keys
    .filter((key: string) => key.startsWith('permission:'))
    .map((key: string) => Number(key.replace('permission:', '')))
    .filter((id: number) => Number.isFinite(id))
}

function syncCheckedPermissions() {
  selectedPermissionIds.value = currentCheckedPermissionIds()
}

function checkAllPermissions() {
  const ids = permissions.value.map((permission) => permission.id)
  permissionTreeRef.value?.setCheckedKeys(ids.map(permissionKey))
  selectedPermissionIds.value = ids
}

function clearPermissions() {
  permissionTreeRef.value?.setCheckedKeys([])
  selectedPermissionIds.value = []
}

function filterPermissionTree() {
  permissionTreeRef.value?.filter(permissionKeyword.value)
}

function filterPermissionNode(keyword: string, data: PermissionTreeNode) {
  if (!keyword) return true
  const value = keyword.toLowerCase()
  return [data.label, data.code, data.path, data.type]
    .filter(Boolean)
    .some((item) => String(item).toLowerCase().includes(value))
}
</script>
