import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import MainLayout from '@/layouts/MainLayout.vue'
import DashboardView from '@/views/dashboard/DashboardView.vue'
import NotFoundView from '@/views/error/NotFoundView.vue'
import { useUserStore } from '@/stores/user'
import type { MenuRoute } from '@/types/api'

const dynamicRouteNames = new Set<string>()

const componentMap: Record<string, RouteRecordRaw['component']> = {
  DashboardView,
  ProjectListView: () => import('@/views/project/ProjectListView.vue'),
  TaskBoardView: () => import('@/views/task/TaskBoardView.vue'),
  TaskListView: () => import('@/views/task/TaskListView.vue'),
  TaskGanttView: () => import('@/views/task/TaskGanttView.vue'),
  KnowledgeBaseView: () => import('@/views/knowledge/KnowledgeBaseView.vue'),
  FileCenterView: () => import('@/views/file/FileCenterView.vue'),
  AiChatView: () => import('@/views/ai/AiChatView.vue'),
  NotificationCenterView: () => import('@/views/notification/NotificationCenterView.vue'),
  ProfileView: () => import('@/views/profile/ProfileView.vue'),
  TeamManagementView: () => import('@/views/system/TeamManagementView.vue'),
  UserManagementView: () => import('@/views/system/UserManagementView.vue'),
  RoleManagementView: () => import('@/views/system/RoleManagementView.vue'),
  PermissionManagementView: () => import('@/views/system/PermissionManagementView.vue'),
  MenuManagementView: () => import('@/views/system/MenuManagementView.vue'),
  LoginLogView: () => import('@/views/system/LoginLogView.vue'),
  OperationLogView: () => import('@/views/system/OperationLogView.vue'),
  DeployManagementView: () => import('@/views/system/DeployManagementView.vue')
}

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'Login',
      component: () => import('@/views/auth/LoginView.vue'),
      meta: { guestOnly: true }
    },
    {
      path: '/register',
      redirect: '/login'
    },
    {
      path: '/file/share/:code',
      name: 'FileShare',
      component: () => import('@/views/file/FileShareView.vue'),
      meta: { public: true }
    },
    {
      path: '/',
      name: 'Root',
      component: MainLayout,
      redirect: '/dashboard',
      children: [
        {
          path: 'profile',
          name: 'Profile',
          component: () => import('@/views/profile/ProfileView.vue')
        }
      ]
    },
    {
      path: '/:pathMatch(.*)*',
      name: 'NotFound',
      component: NotFoundView
    }
  ]
})

router.beforeEach(async (to) => {
  const userStore = useUserStore()
  // 仅游客可访问的页面（登录页）：已登录用户跳转工作台
  if (to.meta.guestOnly) {
    return userStore.isLoggedIn ? '/dashboard' : true
  }
  // 公开页面（如文件分享链接）：登录与否均可访问，不触发登录拦截
  if (to.meta.public) {
    return true
  }
  if (!userStore.isLoggedIn) {
    return {
      path: '/login',
      query: { redirect: to.fullPath }
    }
  }
  if (!userStore.initialized) {
    await userStore.fetchCurrentUser()
    const changed = syncDynamicRoutes(userStore.menus)
    if (changed) {
      return { path: to.fullPath, replace: true }
    }
    return { ...to, replace: true }
  }
  const changed = syncDynamicRoutes(userStore.menus)
  if (changed && to.name === 'NotFound') {
    return { path: to.fullPath, replace: true }
  }
  if (to.name === 'NotFound' && to.path.startsWith('/system')) {
    return '/dashboard'
  }
  const isSuperAdmin = userStore.roles?.includes('SUPER_ADMIN')
  if (!isSuperAdmin && typeof to.meta.permissionCode === 'string' && !userStore.hasPermission(to.meta.permissionCode)) {
    return '/dashboard'
  }
  return true
})

export function syncDynamicRoutes(menus: MenuRoute[]) {
  let changed = false
  const routes = flattenMenus(menus)
  for (const menu of routes) {
    if (!menu.path || dynamicRouteNames.has(menu.path)) {
      continue
    }
    const routeName = menu.path.replace(/\//g, '_').replace(/^_/, '') || `menu_${menu.id}`
    router.addRoute('Root', {
      path: menu.path.replace(/^\//, ''),
      name: routeName,
      component: componentMap[menu.component] || NotFoundView,
      meta: {
        title: menu.name,
        icon: menu.icon,
        permissionCode: menu.permissionCode
      }
    })
    dynamicRouteNames.add(menu.path)
    changed = true
  }
  return changed
}

function flattenMenus(menus: MenuRoute[]) {
  const result: MenuRoute[] = []
  const walk = (items: MenuRoute[]) => {
    for (const item of items) {
      if (item.type === 'MENU') {
        result.push(item)
      }
      if (item.children?.length) {
        walk(item.children)
      }
    }
  }
  walk(menus)
  return result
}

export default router
