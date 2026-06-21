<template>
  <PageContainer title="知识库" description="沉淀团队知识、发布版本并支持 Markdown 编辑预览">
    <div v-loading="loading" class="knowledge-layout">
      <aside class="knowledge-sidebar system-card">
        <div class="knowledge-sidebar__search">
          <el-input
            v-model="keyword"
            placeholder="搜索文档标题或正文"
            clearable
            :prefix-icon="Search"
            @keyup.enter="loadDocs({ selectFirst: true })"
            @clear="loadDocs({ selectFirst: true })"
          />
        </div>
        <div class="knowledge-sidebar__actions">
          <PermissionButton permission="knowledge:doc:create" type="primary" :icon="DocumentAdd" :disabled="!activeSpaceId" @click="openDocDialog">新建文档</PermissionButton>
          <PermissionButton permission="knowledge:doc:create" :icon="UploadFilled" :disabled="!activeSpaceId" @click="openImportDialog">上传文档</PermissionButton>
        </div>

        <section class="knowledge-section knowledge-section--tree">
          <div class="knowledge-section__title">
            <div class="knowledge-section__heading">
              <span>资源树</span>
            </div>
            <div class="knowledge-section__tools">
              <el-button :icon="Refresh" circle aria-label="刷新资源树" title="刷新资源树" @click="refreshTree" />
              <PermissionButton permission="knowledge:space:create" :icon="Plus" circle aria-label="新建空间" title="新建空间" @click="openSpaceDialog" />
            </div>
          </div>
          <el-empty v-if="!spaces.length" description="暂无知识空间" :image-size="92" />
          <el-tree
            v-else
            class="knowledge-file-tree"
            :data="workspaceTree"
            node-key="treeKey"
            :props="treeProps"
            :current-node-key="activeTreeKey"
            :default-expand-all="true"
            :expand-on-click-node="false"
            highlight-current
            @node-click="handleTreeNodeClick"
          >
            <template #default="{ data }">
              <span class="knowledge-tree-node" :class="{ 'is-space': data.nodeType === 'space' }">
                <span class="knowledge-tree-node__icon" :class="{ 'is-folder': data.nodeType === 'space' || data.children?.length }">
                  <el-icon><component :is="data.nodeType === 'space' || data.children?.length ? FolderOpened : Document" /></el-icon>
                </span>
                <span class="knowledge-tree-node__name" :title="data.title">{{ data.title }}</span>
                <span v-if="data.nodeType === 'space'" class="knowledge-tree-node__count">{{ data.docCount || 0 }}</span>
                <span v-if="data.nodeType === 'space'" class="knowledge-tree-node__actions" @click.stop>
                  <el-button link type="danger" :icon="Delete" @click="removeSpace(data)" />
                </span>
                <template v-else>
                  <span class="knowledge-tree-node__status" :class="`is-${data.docStatus?.toLowerCase() || 'draft'}`" :title="statusLabel(data.docStatus)" />
                  <span class="knowledge-tree-node__version">v{{ data.versionNo || 0 }}</span>
                </template>
              </span>
            </template>
          </el-tree>
        </section>
      </aside>

      <section class="knowledge-editor system-card">
        <template v-if="currentDoc">
          <header class="knowledge-editor__header">
            <div class="knowledge-title-block">
              <div class="knowledge-title-row">
                <el-input v-model="editor.title" class="knowledge-title-input" placeholder="文档标题" />
              </div>
              <div class="knowledge-meta">
                <span class="knowledge-status-pill" :class="`is-${editor.docStatus.toLowerCase()}`">{{ statusLabel(editor.docStatus) }}</span>
                <span>版本 v{{ currentDoc.versionNo || 0 }}</span>
                <span>{{ currentDoc.authorName || '未知作者' }}</span>
                <span>{{ formatDate(currentDoc.updatedAt) }}</span>
              </div>
            </div>
            <div class="knowledge-editor__actions">
              <PermissionButton permission="knowledge:doc:publish" type="primary" :icon="UploadFilled" @click="publishDocument">发布</PermissionButton>
              <PermissionButton permission="knowledge:doc:update" :icon="Check" @click="saveDocument">保存</PermissionButton>
              <el-button :icon="Clock" @click="openHistory">历史版本</el-button>
              <PermissionButton permission="knowledge:doc:delete" type="danger" plain :icon="Delete" @click="removeDocument">删除</PermissionButton>
            </div>
          </header>

          <div class="knowledge-tag-row">
            <el-input v-model="editor.tagsText" placeholder="标签，用逗号分隔" :prefix-icon="CollectionTag" />
          </div>

          <el-tabs v-model="activePane" class="knowledge-tabs">
            <el-tab-pane label="编辑" name="edit">
              <MdEditor
                v-model="editor.contentMd"
                class="knowledge-md-editor"
                language="zh-CN"
                :preview="false"
              />
            </el-tab-pane>
            <el-tab-pane label="预览" name="preview">
              <div class="knowledge-preview">
                <MdPreview :model-value="editor.contentMd" />
              </div>
            </el-tab-pane>
          </el-tabs>
        </template>

        <el-empty v-else class="knowledge-empty" :description="emptyDescription">
          <div class="knowledge-empty__actions">
            <PermissionButton permission="knowledge:doc:create" type="primary" :icon="DocumentAdd" :disabled="!activeSpaceId" @click="openDocDialog">新建文档</PermissionButton>
            <PermissionButton permission="knowledge:doc:create" :icon="UploadFilled" :disabled="!activeSpaceId" @click="openImportDialog">上传文档</PermissionButton>
          </div>
        </el-empty>
      </section>
    </div>

    <el-dialog v-model="spaceDialogVisible" title="新建知识空间" width="520px">
      <el-form :model="spaceForm" label-width="88px">
        <el-form-item label="空间名称">
          <el-input v-model="spaceForm.spaceName" placeholder="例如：产品知识库" />
        </el-form-item>
        <el-form-item label="可见范围">
          <el-select v-model="spaceForm.visibility">
            <el-option label="团队可见" value="TEAM" />
            <el-option label="私有空间" value="PRIVATE" />
            <el-option label="公开空间" value="PUBLIC" />
          </el-select>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="spaceForm.description" type="textarea" :rows="3" placeholder="空间用途说明" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="spaceDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveSpace">创建空间</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="docDialogVisible" title="新建知识文档" width="760px" class="knowledge-doc-dialog">
      <el-form :model="docForm" label-width="88px">
        <el-form-item label="所属空间">
          <el-select v-model="docForm.spaceId" disabled>
            <el-option v-for="space in spaces" :key="space.id" :label="space.spaceName" :value="space.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="父级目录">
          <el-select v-model="docForm.parentId" clearable placeholder="根目录">
            <el-option label="根目录" :value="0" />
            <el-option v-for="item in parentDocOptions" :key="item.id" :label="item.label" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="文档标题">
          <el-input v-model="docForm.title" placeholder="请输入文档标题" />
        </el-form-item>
        <el-form-item label="标签">
          <el-input v-model="docForm.tagsText" placeholder="多个标签用逗号分隔" />
        </el-form-item>
        <el-form-item label="正文内容">
          <MdEditor
            v-model="docForm.contentMd"
            class="knowledge-dialog-editor"
            language="zh-CN"
            :preview="false"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="docDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="createDocument">创建文档</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="importDialogVisible" title="上传文档到知识库" width="640px" class="knowledge-import-dialog">
      <el-form :model="importForm" label-width="92px">
        <el-form-item label="知识空间">
          <el-select v-model="importForm.spaceId" placeholder="请选择知识空间">
            <el-option v-for="space in spaces" :key="space.id" :label="space.spaceName" :value="space.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="父级目录">
          <el-select v-model="importForm.parentId" clearable placeholder="根目录">
            <el-option label="根目录" :value="0" />
            <el-option v-for="item in parentDocOptions" :key="item.id" :label="item.label" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="文档标题">
          <el-input v-model="importForm.title" placeholder="单文件可指定标题，多文件默认使用文件名" />
        </el-form-item>
        <el-form-item label="标签">
          <el-input v-model="importForm.tagsText" placeholder="多个标签用逗号分隔" />
        </el-form-item>
        <el-form-item label="自动发布">
          <el-switch v-model="importForm.autoPublish" />
          <span class="knowledge-import-hint">开启后上传完成立即生成版本并刷新 AI 检索切片</span>
        </el-form-item>
        <el-form-item label="选择文件">
          <el-upload
            v-model:file-list="importFileList"
            drag
            multiple
            action="#"
            :http-request="importUploadRequest"
            :show-file-list="true"
            :disabled="importing"
          >
            <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
            <div class="el-upload__text">拖拽一个或多个 md、txt、pdf、docx 到此处，或 <em>点击上传</em></div>
            <template #tip>
              <div class="el-upload__tip">多选文件会批量导入；上传后保留原文件，并创建可编辑、可发布、可被 AI 检索的知识文档。</div>
            </template>
          </el-upload>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="importDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="historyVisible" title="历史版本" size="560px" class="knowledge-history-drawer">
      <el-table :data="versions" height="300">
        <el-table-column label="版本" width="80">
          <template #default="{ row }">v{{ row.versionNo }}</template>
        </el-table-column>
        <el-table-column prop="changeSummary" label="说明" min-width="150" show-overflow-tooltip />
        <el-table-column label="时间" width="160">
          <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="128" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="historyPreview = row">预览</el-button>
            <el-button link type="warning" @click="restoreVersion(row)">回滚</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="history-preview">
        <div class="history-preview__title">
          <strong>{{ historyPreview ? `v${historyPreview.versionNo} ${historyPreview.title}` : '选择版本查看内容' }}</strong>
          <span v-if="historyPreview">{{ historyPreview.editorName || '未知编辑者' }}</span>
        </div>
        <MdPreview v-if="historyPreview" :model-value="historyPreview.contentMd" />
        <el-empty v-else description="暂无预览" :image-size="80" />
      </div>
    </el-drawer>
  </PageContainer>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox, type UploadRequestOptions, type UploadUserFile } from 'element-plus'
import {
  Check,
  Clock,
  CollectionTag,
  Delete,
  Document,
  DocumentAdd,
  FolderOpened,
  Plus,
  Refresh,
  Search,
  UploadFilled
} from '@element-plus/icons-vue'
import { MdEditor, MdPreview } from 'md-editor-v3'
import 'md-editor-v3/lib/style.css'
import PageContainer from '@/components/PageContainer.vue'
import PermissionButton from '@/components/PermissionButton.vue'
import {
  createKnowledgeDocApi,
  createKnowledgeSpaceApi,
  deleteKnowledgeDocApi,
  deleteKnowledgeSpaceApi,
  importKnowledgeDocFileApi,
  knowledgeDocDetailApi,
  knowledgeDocPageApi,
  knowledgeDocTreeApi,
  knowledgeSpacePageApi,
  knowledgeVersionPageApi,
  publishKnowledgeDocApi,
  restoreKnowledgeDocApi,
  updateKnowledgeDocApi,
  type KnowledgeDocItem,
  type KnowledgeDocTreeNode,
  type KnowledgeSpaceItem,
  type KnowledgeVersionItem
} from '@/api/knowledge'

const route = useRoute()
const loading = ref(false)
const keyword = ref('')
const spaces = ref<KnowledgeSpaceItem[]>([])
const docs = ref<KnowledgeDocItem[]>([])
const docTree = ref<KnowledgeDocTreeNode[]>([])
const versions = ref<KnowledgeVersionItem[]>([])
const currentDoc = ref<KnowledgeDocItem | null>(null)
const activeSpaceId = ref<number | undefined>()
const activeDocId = ref<number | null>(null)
const activePane = ref('edit')
const historyVisible = ref(false)
const historyPreview = ref<KnowledgeVersionItem | null>(null)
const spaceDialogVisible = ref(false)
const docDialogVisible = ref(false)
const importDialogVisible = ref(false)
const importing = ref(false)
const importFileList = ref<UploadUserFile[]>([])
const treeProps = {
  label: 'title',
  children: 'children'
}

interface KnowledgeTreeNode {
  treeKey: string
  nodeType: 'space' | 'doc'
  id: number
  title: string
  docStatus?: string
  versionNo?: number
  docCount?: number
  children?: KnowledgeTreeNode[]
}

const workspaceTree = computed<KnowledgeTreeNode[]>(() =>
  spaces.value.map((space) => ({
    treeKey: `space-${space.id}`,
    nodeType: 'space',
    id: space.id,
    title: space.spaceName,
    docCount: space.docCount,
    children: activeSpaceId.value === space.id ? docTree.value.map(toDocTreeNode) : []
  }))
)

const activeTreeKey = computed(() => {
  if (activeDocId.value) {
    return `doc-${activeDocId.value}`
  }
  return activeSpaceId.value ? `space-${activeSpaceId.value}` : undefined
})

const editor = reactive({
  spaceId: undefined as number | undefined,
  parentId: 0,
  title: '',
  contentMd: '',
  docStatus: 'DRAFT',
  sortNo: 100,
  tagsText: ''
})

const spaceForm = reactive({
  spaceName: '',
  description: '',
  visibility: 'TEAM'
})

const docForm = reactive({
  spaceId: undefined as number | undefined,
  parentId: 0,
  title: '',
  contentMd: '',
  tagsText: ''
})

const importForm = reactive({
  spaceId: undefined as number | undefined,
  parentId: 0,
  title: '',
  tagsText: '',
  autoPublish: true
})

const parentDocOptions = computed(() => {
  const options: Array<{ id: number; label: string }> = []
  const visit = (nodes: KnowledgeDocTreeNode[], level = 0) => {
    for (const node of nodes) {
      options.push({ id: node.id, label: `${'　'.repeat(level)}${node.title}` })
      visit(node.children || [], level + 1)
    }
  }
  visit(docTree.value)
  return options
})

const currentSpace = computed(() => spaces.value.find((space) => space.id === activeSpaceId.value))

const emptyDescription = computed(() => {
  if (activeSpaceId.value) {
    return `已选中「${currentSpace.value?.spaceName || '当前空间'}」根目录`
  }
  return '请选择或创建知识空间'
})

onMounted(() => {
  loadAll()
})

watch(
  () => route.fullPath,
  () => {
    loadAll()
  }
)

/**
 * 全量初始化/刷新：加载空间列表和文档树，按优先级决定激活哪个文档。
 * URL query 中若带 docId 则直接打开该文档，否则按 selectFirst 决定是否激活第一个。
 */
async function loadAll(options: { selectFirst?: boolean } = { selectFirst: true }) {
  loading.value = true
  try {
    await loadSpaces()
    const targetDocId = applyRouteQuery()
    await loadDocs({ selectFirst: targetDocId ? false : options.selectFirst ?? true })
    if (targetDocId) {
      await selectDoc(targetDocId)
    }
  } finally {
    loading.value = false
  }
}

async function refreshTree() {
  await loadAll({ selectFirst: false })
}

async function loadSpaces() {
  const result = await knowledgeSpacePageApi({ page: 1, size: 100 })
  spaces.value = result.records
  if (!activeSpaceId.value || !spaces.value.some((space) => space.id === activeSpaceId.value)) {
    activeSpaceId.value = spaces.value[0]?.id
  }
}

/**
 * 加载当前空间的文档列表和文档树（并发请求）。
 * activeDocId 如不在新列表中则清除，避免编辑器展示已删除文档的旧内容。
 */
async function loadDocs(options: { selectFirst?: boolean } = {}) {
  if (!activeSpaceId.value) {
    docs.value = []
    docTree.value = []
    currentDoc.value = null
    activeDocId.value = null
    return
  }
  const [pageResult, treeResult] = await Promise.all([
    knowledgeDocPageApi({
      page: 1,
      size: 200,
      spaceId: activeSpaceId.value,
      keyword: keyword.value
    }),
    knowledgeDocTreeApi({
      spaceId: activeSpaceId.value,
      keyword: keyword.value
    })
  ])
  docs.value = pageResult.records
  docTree.value = treeResult
  const activeDocStillExists = activeDocId.value ? docs.value.some((doc) => doc.id === activeDocId.value) : false
  if (activeDocId.value && !activeDocStillExists) {
    activeDocId.value = null
    currentDoc.value = null
    resetEditor()
  }
  if (docs.value.length && options.selectFirst) {
    await selectDoc(docs.value[0].id)
    return
  }
  if (!docs.value.length) {
    currentDoc.value = null
    activeDocId.value = null
    resetEditor()
  }
}

async function selectSpace(space: KnowledgeSpaceItem) {
  activeSpaceId.value = space.id
  activeDocId.value = null
  currentDoc.value = null
  resetEditor()
  await loadDocs({ selectFirst: false })
}

async function selectDoc(id: number) {
  activeDocId.value = id
  const detail = await knowledgeDocDetailApi(id)
  applyDoc(detail)
  activePane.value = 'edit'
}

async function handleTreeNodeClick(data: KnowledgeTreeNode) {
  if (data.nodeType === 'space') {
    const space = spaces.value.find((item) => item.id === data.id)
    if (space) {
      await selectSpace(space)
    }
    return
  }
  await selectDoc(data.id)
}

/**
 * 将后端文档树节点递归转换为带 treeKey 的本地树节点。
 * treeKey 格式 "doc-{id}" 与空间节点的 "space-{id}" 区分，方便 el-tree 定位当前节点。
 */
function toDocTreeNode(doc: KnowledgeDocTreeNode): KnowledgeTreeNode {
  return {
    treeKey: `doc-${doc.id}`,
    nodeType: 'doc',
    id: doc.id,
    title: doc.title,
    docStatus: doc.docStatus,
    versionNo: doc.versionNo,
    children: doc.children?.map(toDocTreeNode) || []
  }
}

/**
 * 将文档详情同步到本地编辑器状态（currentDoc + editor 响应式对象）。
 * 每次打开文档、保存、发布、回滚后都应调用此方法，确保本地状态与后端一致。
 */
function applyDoc(doc: KnowledgeDocItem) {
  currentDoc.value = doc
  activeDocId.value = doc.id
  activeSpaceId.value = doc.spaceId
  Object.assign(editor, {
    spaceId: doc.spaceId,
    parentId: doc.parentId || 0,
    title: doc.title,
    contentMd: doc.contentMd || '',
    docStatus: doc.docStatus || 'DRAFT',
    sortNo: doc.sortNo || 100,
    tagsText: doc.tags.map((tag) => tag.tagName).join(', ')
  })
}

/**
 * 从 URL query 参数读取并应用初始状态（keyword/spaceId/docId）。
 * 返回 docId 供 loadAll 判断是否跳过 selectFirst 逻辑。
 */
function applyRouteQuery() {
  if (Object.prototype.hasOwnProperty.call(route.query, 'keyword')) {
    keyword.value = parseStringQuery(route.query.keyword)
  }
  const spaceId = parseNumberQuery(route.query.spaceId)
  if (spaceId) {
    activeSpaceId.value = spaceId
  }
  return parseNumberQuery(route.query.docId)
}

function parseStringQuery(value: unknown) {
  const raw = Array.isArray(value) ? value[0] : value
  return typeof raw === 'string' ? raw : ''
}

function parseNumberQuery(value: unknown) {
  const raw = Array.isArray(value) ? value[0] : value
  const number = Number(raw)
  return Number.isFinite(number) && number > 0 ? number : undefined
}

function resetEditor() {
  Object.assign(editor, {
    spaceId: activeSpaceId.value,
    parentId: 0,
    title: '',
    contentMd: '',
    docStatus: 'DRAFT',
    sortNo: 100,
    tagsText: ''
  })
}

function openSpaceDialog() {
  Object.assign(spaceForm, {
    spaceName: '',
    description: '',
    visibility: 'TEAM'
  })
  spaceDialogVisible.value = true
}

async function saveSpace() {
  if (!spaceForm.spaceName.trim()) {
    ElMessage.warning('请填写空间名称')
    return
  }
  const space = await createKnowledgeSpaceApi({
    spaceName: spaceForm.spaceName.trim(),
    description: spaceForm.description,
    visibility: spaceForm.visibility
  })
  ElMessage.success('知识空间已创建')
  spaceDialogVisible.value = false
  activeSpaceId.value = space.id
  await loadSpaces()
  await loadDocs({ selectFirst: true })
}

function openDocDialog() {
  if (!activeSpaceId.value) {
    ElMessage.warning('请先创建知识空间')
    return
  }
  Object.assign(docForm, {
    spaceId: activeSpaceId.value,
    parentId: activeDocId.value || 0,
    title: '',
    contentMd: '',
    tagsText: ''
  })
  docDialogVisible.value = true
}

function openImportDialog() {
  if (!activeSpaceId.value) {
    ElMessage.warning('请先创建知识空间')
    return
  }
  Object.assign(importForm, {
    spaceId: activeSpaceId.value,
    parentId: activeDocId.value || 0,
    title: '',
    tagsText: '',
    autoPublish: true
  })
  importFileList.value = []
  importDialogVisible.value = true
}

async function createDocument() {
  if (!docForm.spaceId || !docForm.title.trim()) {
    ElMessage.warning('请选择空间并填写文档标题')
    return
  }
  const doc = await createKnowledgeDocApi({
    spaceId: docForm.spaceId,
    parentId: docForm.parentId || 0,
    title: docForm.title.trim(),
    contentMd: docForm.contentMd.trim() || `## ${docForm.title.trim()}\n\n在这里编写团队知识、方案记录或面试讲解内容。`,
    docStatus: 'DRAFT',
    sortNo: 100,
    tags: parseTags(docForm.tagsText)
  })
  ElMessage.success('知识文档已创建')
  docDialogVisible.value = false
  await loadDocs({ selectFirst: false })
  await selectDoc(doc.id)
}

/**
 * el-upload 自定义上传回调：调用 importKnowledgeDocFileApi 上传文件并解析为文档。
 * autoPublish=true 时后端会立即发布并入队 RAG 索引任务，弹窗提示已含索引状态。
 */
async function importUploadRequest(options: UploadRequestOptions) {
  if (!importForm.spaceId) {
    ElMessage.warning('请选择知识空间')
    throw new Error('请选择知识空间')
  }
  importing.value = true
  try {
    const file = options.file as File
    const result = await importKnowledgeDocFileApi(file, {
      spaceId: importForm.spaceId,
      parentId: importForm.parentId || undefined,
      title: importFileList.value.length <= 1 ? importForm.title.trim() || undefined : undefined,
      tags: parseTags(importForm.tagsText),
      autoPublish: importForm.autoPublish
    })
    options.onSuccess?.(result)
    ElMessage.success(result.indexed ? '文档已上传、发布并刷新 AI 检索索引' : '文档已上传到知识库')
    importDialogVisible.value = false
    activeSpaceId.value = result.doc.spaceId
    await loadSpaces()
    await loadDocs({ selectFirst: false })
    await selectDoc(result.doc.id)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '上传导入失败')
    throw error
  } finally {
    importing.value = false
  }
}

async function saveDocument() {
  if (!activeDocId.value || !editor.spaceId || !editor.title.trim()) {
    ElMessage.warning('请选择文档并填写标题')
    return
  }
  const doc = await updateKnowledgeDocApi(activeDocId.value, buildDocumentPayload())
  ElMessage.success('文档已保存')
  applyDoc(doc)
  await loadDocs()
}

async function removeSpace(node: KnowledgeTreeNode) {
  await ElMessageBox.confirm(`确定删除空间「${node.title}」吗？空间内所有文档将一并删除，且不可恢复。`, '删除空间', { type: 'warning' })
  await deleteKnowledgeSpaceApi(node.id as number)
  ElMessage.success('知识空间已删除')
  if (activeSpaceId.value === node.id) {
    activeSpaceId.value = undefined
    currentDoc.value = null
    activeDocId.value = null
    resetEditor()
  }
  await loadAll()
}

async function removeDocument() {
  if (!currentDoc.value || !activeDocId.value) {
    return
  }
  await ElMessageBox.confirm(`确定删除文档「${currentDoc.value.title}」吗？删除后不可在列表中查看。`, '删除文档', { type: 'warning' })
  await deleteKnowledgeDocApi(activeDocId.value)
  ElMessage.success('文档已删除')
  currentDoc.value = null
  activeDocId.value = null
  resetEditor()
  await loadDocs({ selectFirst: true })
}

/**
 * 发布文档：先保存当前编辑内容，再调用 publish 接口。
 * publish 接口会递增 versionNo 并触发 RAG 索引重建（异步入队）。
 */
async function publishDocument() {
  if (!activeDocId.value || !editor.spaceId || !editor.title.trim()) {
    ElMessage.warning('请选择文档并填写标题')
    return
  }
  await ElMessageBox.confirm('发布会先保存当前编辑内容，并生成一个可回滚的历史版本，是否继续？', '发布文档', { type: 'info' })
  await updateKnowledgeDocApi(activeDocId.value, buildDocumentPayload())
  const doc = await publishKnowledgeDocApi(activeDocId.value, { changeSummary: '前端发布更新' })
  ElMessage.success('文档已发布')
  applyDoc(doc)
  await loadDocs()
}

async function openHistory() {
  if (!activeDocId.value) {
    return
  }
  const result = await knowledgeVersionPageApi({ page: 1, size: 50, docId: activeDocId.value })
  versions.value = result.records
  historyPreview.value = versions.value[0] || null
  historyVisible.value = true
}

/**
 * 回滚到历史版本：后端会创建一个新版本（versionNo 递增），并重置 contentMd。
 * 如历史版本的状态是 PUBLISHED，回滚后自动触发 RAG 索引重建。
 */
async function restoreVersion(version: KnowledgeVersionItem) {
  if (!activeDocId.value) {
    return
  }
  await ElMessageBox.confirm(`确认回滚到 v${version.versionNo}？当前编辑内容会被该版本覆盖。`, '回滚版本', { type: 'warning' })
  const doc = await restoreKnowledgeDocApi(activeDocId.value, version.id)
  ElMessage.success('版本已回滚')
  historyVisible.value = false
  applyDoc(doc)
  await loadDocs()
}

function parseTags(value: string) {
  return value
    .split(/[,，]/)
    .map((tag) => tag.trim())
    .filter(Boolean)
}

/** 从本地 editor 状态组装保存/发布接口所需的请求体。 */
function buildDocumentPayload() {
  return {
    spaceId: editor.spaceId!,
    parentId: editor.parentId,
    title: editor.title.trim(),
    contentMd: editor.contentMd,
    docStatus: editor.docStatus,
    sortNo: editor.sortNo,
    tags: parseTags(editor.tagsText)
  }
}

function statusLabel(status: string) {
  const map: Record<string, string> = {
    DRAFT: '草稿',
    PUBLISHED: '已发布',
    ARCHIVED: '已归档'
  }
  return map[status] || status
}

function formatDate(value?: string) {
  if (!value) {
    return '暂无时间'
  }
  return value.replace('T', ' ').slice(0, 16)
}
</script>

<style scoped>
.knowledge-layout {
  display: grid;
  grid-template-columns: minmax(300px, 336px) minmax(0, 1fr);
  gap: 20px;
  align-items: stretch;
  min-height: calc(100vh - 144px);
}

.knowledge-sidebar,
.knowledge-editor {
  min-height: 0;
}

.knowledge-sidebar {
  display: flex;
  flex-direction: column;
  padding: 14px;
  background: rgba(255, 255, 255, 0.96);
}

.knowledge-sidebar__search {
  margin-bottom: 12px;
}

.knowledge-sidebar__actions {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 10px;
  margin-bottom: 18px;
}

.knowledge-sidebar__actions .el-button {
  width: 100%;
  height: 40px;
  margin: 0;
  border-radius: 8px;
  font-weight: 760;
}

.knowledge-sidebar__actions .el-button--primary {
  border: 0;
  background: linear-gradient(135deg, var(--tf-primary), var(--tf-secondary));
  box-shadow: 0 10px 22px rgba(37, 99, 235, 0.2);
}

.knowledge-section {
  display: grid;
  gap: 8px;
}

.knowledge-section--tree {
  min-height: 0;
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: stretch;
  justify-content: flex-start;
  overflow: auto;
  padding-right: 0;
}

.knowledge-section__title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex: 0 0 auto;
  min-height: 34px;
  color: var(--tf-text);
  font-size: 12px;
  font-weight: 760;
}

.knowledge-section__heading {
  min-width: 0;
  display: grid;
  gap: 3px;
}

.knowledge-section__heading span {
  color: var(--tf-text);
  font-size: 14px;
  font-weight: 800;
}

.knowledge-section__heading small {
  color: var(--tf-muted);
  font-size: 11px;
  font-weight: 700;
}

.knowledge-section__tools {
  display: inline-flex;
  align-items: center;
  flex: 0 0 auto;
  gap: 2px;
  padding: 3px;
  border: 1px solid rgba(226, 232, 240, 0.92);
  border-radius: 999px;
  background: rgba(248, 250, 252, 0.82);
}

.knowledge-section__tools .el-button {
  width: 28px;
  height: 28px;
  margin: 0;
  border: 0;
  border-radius: 999px;
  background: transparent;
  color: #64748b;
  box-shadow: none;
}

.knowledge-section__tools .el-button:focus,
.knowledge-section__tools .el-button:focus-visible {
  outline: none;
  background: transparent;
  color: #64748b;
  box-shadow: none;
}

.knowledge-section__tools .el-button:hover {
  background: #fff;
  color: var(--tf-primary);
  box-shadow: 0 4px 12px rgba(37, 99, 235, 0.12);
}

.knowledge-section__tools :deep(.el-icon) {
  font-size: 15px;
}

.knowledge-space-item {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 10px;
  border: 1px solid transparent;
  background: transparent;
  color: var(--tf-text);
  cursor: pointer;
  text-align: left;
  transition: background 0.18s ease, border-color 0.18s ease, transform 0.18s ease, box-shadow 0.18s ease;
}

.knowledge-space-item {
  min-height: 58px;
  padding: 10px;
  border-radius: 12px;
}

.knowledge-space-item:hover {
  transform: translateY(-1px);
  border-color: rgba(37, 99, 235, 0.22);
  background: #f8fbff;
  box-shadow: 0 8px 20px rgba(37, 99, 235, 0.08);
}

.knowledge-space-item.is-active {
  border-color: rgba(37, 99, 235, 0.34);
  background: linear-gradient(135deg, rgba(37, 99, 235, 0.1), rgba(124, 58, 237, 0.07));
}

.knowledge-space-item__icon {
  width: 34px;
  height: 34px;
  display: grid;
  place-items: center;
  flex: 0 0 auto;
  border-radius: 12px;
  background: linear-gradient(135deg, rgba(37, 99, 235, 0.12), rgba(124, 58, 237, 0.1));
  color: var(--tf-primary);
}

.knowledge-space-item__body {
  min-width: 0;
  flex: 1;
  display: grid;
  gap: 3px;
}

.knowledge-space-item strong {
  overflow: hidden;
  color: var(--tf-text);
  font-weight: 760;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.knowledge-space-item small {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--tf-muted);
  font-size: 12px;
}

.knowledge-file-tree {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 2px 0 6px;
  background: transparent;
  --el-tree-node-hover-bg-color: transparent;
}

.knowledge-file-tree :deep(.el-tree-node) {
  position: relative;
}

.knowledge-file-tree :deep(.el-tree-node__content) {
  position: relative;
  height: 32px;
  margin: 0;
  border: 1px solid transparent;
  border-radius: 8px;
  color: #374151;
  transition: background 0.16s ease, border-color 0.16s ease, color 0.16s ease;
}

.knowledge-file-tree :deep(.el-tree-node__content:hover) {
  border-color: transparent;
  background: #f8fbff;
  color: var(--tf-primary);
}

.knowledge-file-tree :deep(.el-tree-node.is-current > .el-tree-node__content) {
  border-color: transparent;
  background: #eff6ff;
  color: var(--tf-primary);
  font-weight: 760;
}

.knowledge-file-tree :deep(.el-tree-node.is-current > .el-tree-node__content::before) {
  content: "";
  position: absolute;
  left: 0;
  top: 7px;
  width: 3px;
  height: 18px;
  border-radius: 999px;
  background: linear-gradient(180deg, var(--tf-primary), var(--tf-secondary));
}

.knowledge-file-tree :deep(.el-tree-node__expand-icon) {
  margin-left: 2px;
  color: #9ca3af;
}

.knowledge-file-tree :deep(.el-tree-node__expand-icon.is-leaf) {
  color: transparent;
}

.knowledge-file-tree :deep(.el-tree-node__children) {
  position: relative;
  margin-left: 4px;
}

.knowledge-file-tree :deep(.el-tree-node__children::before) {
  content: "";
  position: absolute;
  top: 0;
  bottom: 6px;
  left: 12px;
  border-left: 1px solid #e6edf7;
}

.knowledge-tree-node {
  min-width: 0;
  width: 100%;
  display: flex;
  align-items: center;
  gap: 6px;
  padding-right: 6px;
}

.knowledge-tree-node__icon {
  width: 20px;
  height: 20px;
  display: grid;
  place-items: center;
  flex: 0 0 auto;
  border-radius: 6px;
  background: transparent;
  color: var(--tf-primary);
  font-size: 14px;
}

.knowledge-tree-node__icon.is-folder {
  color: var(--tf-secondary);
}

.knowledge-tree-node.is-space .knowledge-tree-node__icon {
  background: transparent;
}

.knowledge-tree-node__name {
  min-width: 0;
  flex: 1;
  overflow: hidden;
  font-size: 13px;
  line-height: 1.3;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.knowledge-tree-node.is-space .knowledge-tree-node__name {
  color: var(--tf-text);
  font-weight: 760;
}

.knowledge-tree-node__count {
  min-width: 20px;
  height: 18px;
  display: inline-grid;
  place-items: center;
  flex: 0 0 auto;
  border-radius: 999px;
  background: #f1f5f9;
  color: var(--tf-primary);
  font-size: 11px;
  font-weight: 800;
}

.knowledge-tree-node__status {
  width: 7px;
  height: 7px;
  flex: 0 0 auto;
  border-radius: 999px;
  background: var(--tf-warning);
  box-shadow: 0 0 0 3px rgba(245, 158, 11, 0.12);
}

.knowledge-tree-node__status.is-published {
  background: var(--tf-success);
  box-shadow: 0 0 0 3px rgba(16, 185, 129, 0.12);
}

.knowledge-tree-node__status.is-archived {
  background: #9ca3af;
  box-shadow: 0 0 0 3px rgba(156, 163, 175, 0.12);
}

.knowledge-tree-node__version {
  flex: 0 0 auto;
  color: #94a3b8;
  font-size: 11px;
  font-weight: 700;
}

.knowledge-tree-node__actions {
  flex: 0 0 auto;
  display: inline-flex;
  align-items: center;
  opacity: 0;
  transition: opacity 0.15s ease;
}

.knowledge-tree-node__actions .el-button {
  height: 22px;
  padding: 0 4px;
  margin: 0;
}

.knowledge-file-tree :deep(.el-tree-node__content:hover) .knowledge-tree-node__actions,
.knowledge-file-tree :deep(.el-tree-node.is-current > .el-tree-node__content) .knowledge-tree-node__actions {
  opacity: 1;
}

.knowledge-editor {
  display: flex;
  flex-direction: column;
  padding: 0;
}

.knowledge-editor__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 18px 20px 14px;
  border-bottom: 1px solid var(--tf-border);
}

.knowledge-title-block {
  min-width: 0;
  flex: 1;
  display: grid;
  gap: 10px;
}

.knowledge-title-row {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 10px;
}

.knowledge-title-input :deep(.el-input__wrapper) {
  padding-left: 0;
  border-radius: 0;
  box-shadow: none;
  background: transparent;
}

.knowledge-title-input :deep(.el-input__inner) {
  height: 36px;
  color: var(--tf-text);
  font-size: 24px;
  font-weight: 800;
}

.knowledge-meta,
.knowledge-editor__actions,
.knowledge-tag-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.knowledge-meta {
  color: var(--tf-muted);
  font-size: 12px;
}

.knowledge-status-pill {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 22px;
  padding: 0 8px;
  border-radius: 999px;
  background: #f8fafc;
  color: #64748b;
  font-size: 12px;
  font-weight: 760;
}

.knowledge-status-pill::before {
  content: "";
  width: 6px;
  height: 6px;
  border-radius: 999px;
  background: currentColor;
}

.knowledge-status-pill.is-published {
  background: #f0fdf4;
  color: #16a34a;
}

.knowledge-status-pill.is-draft {
  background: #fffbeb;
  color: #d97706;
}

.knowledge-status-pill.is-archived {
  background: #f1f5f9;
  color: #64748b;
}

.knowledge-editor__actions {
  flex: 0 0 auto;
  flex-wrap: nowrap;
  justify-content: flex-end;
  white-space: nowrap;
}

.knowledge-editor__actions .el-button {
  height: 38px;
  margin: 0;
  border-radius: 8px;
  font-weight: 700;
}

.knowledge-tag-row {
  padding: 14px 20px;
  border-bottom: 1px solid rgba(229, 231, 235, 0.72);
}

.knowledge-tabs {
  min-height: 0;
  flex: 1;
  display: flex;
  flex-direction: column;
  padding: 0 20px 20px;
}

.knowledge-tabs :deep(.el-tabs__content) {
  min-height: 0;
  flex: 1;
}

.knowledge-tabs :deep(.el-tab-pane) {
  height: 100%;
}

.knowledge-md-editor {
  height: calc(100vh - 370px);
  min-height: 460px;
  border: 1px solid var(--tf-border);
  border-radius: 14px;
  overflow: hidden;
}

.knowledge-dialog-editor {
  width: 100%;
  height: 320px;
  border: 1px solid var(--tf-border);
  border-radius: 12px;
  overflow: hidden;
}

.knowledge-preview,
.history-preview {
  min-height: 460px;
  padding: 20px;
  border: 1px solid var(--tf-border);
  border-radius: 14px;
  background: #fff;
}

.knowledge-empty {
  flex: 1;
  min-height: 520px;
}

.knowledge-empty :deep(.el-empty__bottom) {
  margin-top: 14px;
}

.knowledge-empty__actions {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  flex-wrap: wrap;
}

.knowledge-empty__actions .el-button {
  height: 38px;
  margin: 0;
  border-radius: 8px;
  font-weight: 700;
}

.history-preview {
  min-height: 260px;
  margin-top: 16px;
}

.history-preview__title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.history-preview__title strong {
  color: var(--tf-text);
}

.history-preview__title span {
  color: var(--tf-muted);
  font-size: 12px;
}

.knowledge-import-hint {
  margin-left: 10px;
  color: var(--tf-muted);
  font-size: 12px;
}

@media (max-width: 1100px) {
  .knowledge-layout {
    grid-template-columns: 1fr;
  }

  .knowledge-sidebar {
    max-height: none;
  }
}

@media (max-width: 760px) {
  .knowledge-editor__header {
    flex-direction: column;
  }

  .knowledge-editor__actions {
    width: 100%;
    flex: 0 0 auto;
    justify-content: flex-start;
  }

  .knowledge-md-editor {
    min-height: 380px;
  }
}
</style>
