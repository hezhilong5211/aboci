import { defineStore } from 'pinia'
import { markRaw } from 'vue'
import { getRouters } from '@/api/login'
import BasicLayout from '@/layout/BasicLayout.vue'

const ALL_PERMISSION = '*:*:*'

function readStoredPermissions(): string[] {
  try {
    const value = JSON.parse(localStorage.getItem('permissions') || '[]')
    return Array.isArray(value)
      ? [...new Set(value.filter((item): item is string => typeof item === 'string' && item.length > 0))]
      : []
  } catch {
    return []
  }
}

// 后端返回的菜单数据结构
interface BackendMenu {
  menuId: number
  menuName: string
  parentId: number
  orderNum: number
  path?: string
  component?: string
  isFrame: number
  menuType: string
  visible: string
  status: string
  perms?: string
  icon?: string
  children?: BackendMenu[]
}

// 前端路由数据结构
interface FrontendRoute {
  path: string
  component?: any
  name?: string
  meta?: {
    title: string
    icon?: string
    noCache?: boolean
    link?: string
  }
  redirect?: string
  children?: FrontendRoute[]
  hidden?: boolean
}

export const usePermissionStore = defineStore('permission', {
  state: () => ({
    routes: [] as FrontendRoute[],
    addRoutes: [] as FrontendRoute[],
    sidebarRouters: [] as FrontendRoute[],
    // 按钮权限必须是响应式状态，不能让指令在 mounted 时只读一次 localStorage。
    permissions: readStoredPermissions(),
    // localStorage 只用于冷启动占位；必须等本次会话 getInfo 校验后才允许展示敏感按钮。
    permissionsReady: false
  }),

  getters: {
    hasAnyPermission: (state) => (required: string | string[]) => {
      if (!state.permissionsReady) return false
      const values = Array.isArray(required) ? required : [required]
      return state.permissions.includes(ALL_PERMISSION)
        || values.some(permission => state.permissions.includes(permission))
    }
  },

  actions: {
    setPermissions(permissions: unknown) {
      const normalized = Array.isArray(permissions)
        ? [...new Set(permissions.filter((item): item is string => typeof item === 'string' && item.length > 0))]
        : []
      this.permissions = normalized
      this.permissionsReady = true
      localStorage.setItem('permissions', JSON.stringify(normalized))
    },

    clearPermissions() {
      this.permissions = []
      this.permissionsReady = false
      localStorage.removeItem('permissions')
    },

    // 生成路由
    async generateRoutes() {
      try {
        // 调用后端API获取菜单数据
        const menuTree = await getRouters()
        const menus = Array.isArray(menuTree) ? menuTree : []

        // 如果获取菜单失败（可能是401），直接返回空数组，让路由守卫处理跳转
        if (!Array.isArray(menuTree)) {
          return []
        }

        // 将后端菜单转换为前端路由
        const accessedRoutes = this.transformBackendMenuToRoutes(menus)
        const validRoutes = this.filterValidRoutes(accessedRoutes)

        // 构建完整的路由树（包含Layout）
        const completeRoutes = this.buildCompleteRouteTree(validRoutes)

        this.addRoutes = completeRoutes
        this.routes = completeRoutes
        this.sidebarRouters = validRoutes

        return validRoutes
      } catch (error: any) {
        console.error('获取路由失败:', error)

        // 如果是401错误，重新抛出，让路由守卫处理
        if (error?.response?.status === 401 ||
          error?.message?.includes('401') ||
          error?.message?.includes('登录已过期') ||
          error?.message?.includes('服务已重启') ||
          error?.message?.includes('认证失败')) {
          throw error // 重新抛出，让路由守卫捕获
        }

        // 其他错误，返回空数组
        return []
      }
    },

    // 将后端菜单转换为前端路由
    transformBackendMenuToRoutes(menus: BackendMenu[], parentPath: string = ''): FrontendRoute[] {
      return menus
        .filter(menu => menu.menuType !== 'F' && menu.visible === '0' && menu.status === '0') // 过滤按钮和隐藏菜单
        .sort((a, b) => a.orderNum - b.orderNum) // 按排序号排序
        .map(menu => {
          // 构建完整路径：如果是根路径，直接使用；否则拼接父路径
          let fullPath = menu.path || ''

          // 如果 path 为空，根据菜单类型生成默认路径
          if (!fullPath || fullPath.trim() === '') {
            if (menu.menuType === 'M') {
              // 目录菜单：使用菜单名称或ID
              fullPath = menu.menuName ? menu.menuName.toLowerCase().replace(/\s+/g, '') : `menu-${menu.menuId}`
            } else if (menu.menuType === 'C') {
              // 菜单：使用菜单名称或ID
              fullPath = menu.menuName ? menu.menuName.toLowerCase().replace(/\s+/g, '') : `menu-${menu.menuId}`
            } else {
              fullPath = `menu-${menu.menuId}`
            }
          }

          if (fullPath && !fullPath.startsWith('/')) {
            // 相对路径，需要拼接父路径
            if (parentPath) {
              fullPath = `${parentPath}/${fullPath}`
            } else {
              fullPath = `/${fullPath}`
            }
          }

          // 确保路径以/开头
          if (!fullPath.startsWith('/')) {
            fullPath = `/${fullPath}`
          }

          const route: FrontendRoute = {
            path: fullPath,
            name: this.generateRouteName(menu, fullPath),
            meta: {
              title: menu.menuName,
              icon: menu.icon || '',
              noCache: menu.isFrame === 0 // 外链不缓存
            }
          }

          // 处理组件
          if (menu.component && menu.component.trim() !== '') {
            if (menu.component === 'Layout' || menu.component === 'BasicLayout') {
              route.component = markRaw(BasicLayout)
            } else {
              // 动态导入组件
              route.component = this.loadView(menu.component)
            }
          } else {
            // 没有 component 的情况
            if (menu.menuType === 'M') {
              // 目录菜单：使用 RouterView
              route.component = () => import('@/layout/components/RouterView.vue')
            } else if (menu.menuType === 'C') {
              // 菜单类型但没有 component：尝试根据 path 生成 component 路径
              if (menu.path) {
                const componentPath = menu.path.startsWith('/') ? menu.path.substring(1) : menu.path
                route.component = this.loadView(componentPath + '/index')
              } else {
                // 如果连 path 都没有，使用占位组件
                route.component = () => import('@/layout/components/RouterView.vue')
              }
            }
          }

          // 处理子菜单（先处理子菜单，以便计算重定向路径）
          if (menu.children && menu.children.length > 0) {
            route.children = this.transformBackendMenuToRoutes(menu.children, fullPath)
          }

          // 处理重定向（目录菜单且有子菜单时）
          if (menu.menuType === 'M' && route.children && route.children.length > 0) {
            const firstChild = route.children[0]
            if (firstChild && firstChild.path) {
              route.redirect = firstChild.path
            }
          }

          return route
        })
    },

    // 生成路由名称
    generateRouteName(menu: BackendMenu, fullPath?: string): string {
      const pathToUse = fullPath || menu.path
      if (pathToUse) {
        // 将路径转换为驼峰命名
        return pathToUse
          .replace(/^\//, '')
          .replace(/\//g, '-')
          .replace(/([A-Z])/g, '-$1')
          .toLowerCase()
          .replace(/^-/, '')
          .replace(/-/g, '')
      }
      return `menu-${menu.menuId}`
    },

    // 动态加载视图组件
    loadView(componentPath: string) {
      // 移除可能的扩展名
      let viewPath = componentPath.replace(/\.vue$/, '')

      // 处理不同的组件路径格式
      if (viewPath.startsWith('@/views/')) {
        // 去掉 @/views/ 前缀
        viewPath = viewPath.replace('@/views/', '')
      } else if (viewPath.startsWith('views/')) {
        viewPath = viewPath.replace('views/', '')
      } else if (viewPath.startsWith('/')) {
        // 绝对路径，去掉开头的斜杠
        viewPath = viewPath.substring(1)
      }

      // Vite 生产构建下动态导入必须用 import.meta.glob 在构建期静态收集所有 view,
      // 再按路径取对应懒加载器;若用运行时字符串 import(@vite-ignore) 则 build 后 view 未被打包、
      // 运行时解析不到 → 组件加载失败 → 空白页(dev 模式能跑、仅生产构建暴露)。glob 参数须为字符串字面量。
      const viewModules = import.meta.glob('../views/**/*.vue')
      const key = `../views/${viewPath}.vue`
      const loader = viewModules[key]
      if (loader) {
        return loader
      }
      console.error(`组件未找到: ${key}`)
      return () => Promise.resolve({
        default: {
          name: 'ComponentNotFound',
          template: `<div style="padding: 20px; text-align: center;">
            <h3>组件未找到</h3>
            <p>路径: ${viewPath}</p>
            <p>请检查组件文件是否存在</p>
          </div>`
        }
      })
    },

    // 构建完整的路由树（包含Layout）
    buildCompleteRouteTree(routes: FrontendRoute[]): FrontendRoute[] {
      // 添加home路由（首页）- 始终存在
      const homeRoute: FrontendRoute = {
        path: '/home',
        name: 'Home',
        component: () => import('@/views/home/index.vue'),
        meta: { title: '首页', icon: 'home' }
      }

      // 确保至少包含home路由
      const allChildren = [
        homeRoute,
        ...(routes || []),
        // 强制注入用户设置路由
        {
          path: '/user/settings',
          name: 'UserSettings',
          component: () => import('@/views/user/settings/index.vue'),
          meta: { title: '用户设置', icon: '', noCache: true },
          hidden: true
        }
      ]

      return [
        {
          path: '/',
          component: markRaw(BasicLayout),
          redirect: '/home',
          children: allChildren
        }
      ]
    },

    // 过滤有效路由（去除空路径等无效路由）
    filterValidRoutes(routes: FrontendRoute[]): FrontendRoute[] {
      return routes.filter(route => {
        // 必须有路径
        if (!route.path || route.path.trim() === '') {
          return false
        }

        // 如果是目录菜单，必须有component（已经在上面的逻辑中设置了RouterView）
        // 允许没有子菜单的目录菜单显示（会显示一个空页面，但至少菜单会显示）
        if (!route.component) {
          return false
        }

        return true
      })
    },

    // 重置路由
    resetRoutes() {
      this.routes = []
      this.addRoutes = []
      this.sidebarRouters = []
    }
  }
})
