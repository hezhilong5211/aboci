import { createRouter, createWebHistory } from 'vue-router';
import type { RouteRecordRaw } from 'vue-router';
import { markRaw } from 'vue';
import { usePermissionStore } from '@/stores/permission';
import { useUserStore } from '@/stores/user';
import BasicLayout from '@/layout/BasicLayout.vue';

// 白名单路由（不需要登录即可访问）
const whiteList = ['/login', '/404'];

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/login',
      name: 'Login',
      component: () => import('@/views/login/index.vue')
    },
    {
      path: '/404',
      name: 'NotFound',
      component: () => import('@/views/developing/index.vue')
    }
    // 注意：通配符路由将在动态路由添加后再添加
  ]
});

/**
 * 初始化路由数据到 Store（保留兼容性，但实际不使用）
 */
export function setupRouterStore() {
  // 这个函数在新版本中不再需要，但保留以兼容现有代码
}

// 路由守卫
router.beforeEach(async (to, from, next) => {
  const token = localStorage.getItem('token');
  const permissionStore = usePermissionStore();
  const userStore = useUserStore();
  
  // 白名单路由直接放行
  if (whiteList.includes(to.path)) {
    next();
    return;
  }
  
  // 没有token，跳转到登录页
  if (!token) {
    permissionStore.clearPermissions();
    userStore.setUserInfo(null);
    next('/login');
    return;
  }
  
  // 有token，检查是否已生成动态路由
  if (permissionStore.addRoutes.length === 0) {
    try {
      // 生成动态路由
      await permissionStore.generateRoutes();
      
      // 将动态路由添加到路由器（即使routes为空，也要添加home路由）
      const completeRoutes = permissionStore.addRoutes;
      
      // 如果路由生成失败（返回空数组），可能是401错误，检查token
      if (!completeRoutes || completeRoutes.length === 0) {
        // 检查token是否还存在（可能被清除）
        const currentToken = localStorage.getItem('token');
        if (!currentToken) {
          // token已被清除，跳转到登录页
          next('/login');
          return;
        }
      }
        
      // 先移除通配符路由（如果存在）
      try {
        router.removeRoute('NotFound');
      } catch (e) {
        // 忽略错误
      }
      
      // 添加动态路由
      if (completeRoutes && completeRoutes.length > 0) {
        completeRoutes.forEach((route) => {
          router.addRoute(route as RouteRecordRaw);
        })
      } else {
        // 即使没有动态路由，也至少添加home路由
        router.addRoute({
          path: '/',
          component: markRaw(BasicLayout),
          redirect: '/home',
          children: [
            {
              path: 'home',
              name: 'Home',
              component: () => import('@/views/home/index.vue'),
              meta: { title: '首页' }
            }
          ]
        })
      }
      
      // 添加404路由（必须在最后）
      router.addRoute({
        path: '/:pathMatch(.*)*',
        name: 'NotFound',
        redirect: '/404'
      })
      
      // 重新导航到目标路由（确保新路由已添加）
      // 如果访问的是根路径，重定向到home
      if (to.path === '/') {
        next({ path: '/home', replace: true });
      } else {
        // 重新导航到目标路由（确保新路由已添加）
        next({ ...to, replace: true });
      }
    } catch (error: any) {
      console.error('路由生成失败:', error);
      
      // 检查是否是401未授权错误
      const is401Error = error?.response?.status === 401 || 
                        error?.message?.includes('401') ||
                        error?.message?.includes('登录已过期') ||
                        error?.message?.includes('服务已重启') ||
                        error?.message?.includes('认证失败');
      
      if (is401Error) {
        // 清除token并跳转到登录页
        localStorage.removeItem('token');
        permissionStore.clearPermissions();
        userStore.setUserInfo(null);
        // 直接跳转到登录页，使用 replace 避免历史记录
        next({ path: '/login', replace: true });
        return;
      }
      
      // 其他错误，跳转到404页面
      next('/404');
    }
  } else {
    // 路由已生成，直接放行
    // 如果访问的是根路径，重定向到home
    if (to.path === '/') {
      next('/home');
    } else {
      next();
    }
  }
});

// 重置路由（用于退出登录）
export function resetRouter() {
  const permissionStore = usePermissionStore();
  // 移除所有动态路由
  permissionStore.addRoutes.forEach((route) => {
    if (route.name) {
      router.removeRoute(route.name);
    }
  });
  // 重置store中的路由
  permissionStore.resetRoutes();
}

export default router;
