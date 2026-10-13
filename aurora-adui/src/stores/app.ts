import { defineStore } from 'pinia';
import { RouteRecordRaw } from 'vue-router';

export interface RouteMeta {
  title: string; // 菜单标题（中文）
  icon?: string; // 图标名称
  order?: number; // 排序
  requiresAuth?: boolean; // 是否需要认证
  roles?: string[]; // 角色权限
  hideInMenu?: boolean; // 是否在菜单中隐藏
  activeMenu?: string; // 激活的菜单项（用于高亮）
}

// 扩展路由类型，添加 meta
export interface AppRouteRecordRaw extends Omit<RouteRecordRaw, 'meta' | 'children'> {
  meta?: RouteMeta;
  children?: AppRouteRecordRaw[];
}

// 自定义图标接口
export interface CustomIcon {
  iconId: number;
  iconName: string;
  iconPath: string;
  iconType: string;
}

interface AppState {
  // 应用路由列表
  appRoutes: AppRouteRecordRaw[];
  // 菜单数据
  menuData: any[];
  // 自定义图标缓存
  customIcons: CustomIcon[];
}

export const useAppStore = defineStore('app', {
  state: (): AppState => ({
    appRoutes: [],
    menuData: [],
    customIcons: [],
  }),

  // ... getters unchanged

  actions: {
    // 设置自定义图标列表
    setCustomIcons(icons: CustomIcon[]) {
      this.customIcons = icons;
    },

    // ... other actions

    // 从路由生成菜单数据的内部方法
    _generateMenuFromRoutes(routes: AppRouteRecordRaw[]): any[] {
      // ... unchanged

      return routes
        .filter((route) => {
          // 过滤掉不需要显示在菜单中的路由
          if (route.meta?.hideInMenu) {
            return false;
          }
          // 可以在这里添加权限检查
          // if (route.meta?.roles && !hasPermission(route.meta.roles)) {
          //   return false;
          // }
          return true;
        })
        .map((route) => {
          const menuItem: any = {
            key: route.path || route.name?.toString(),
            path: route.path,
            name: route.name,
            title: route.meta?.title || route.name?.toString() || '',
            icon: route.meta?.icon,
            order: route.meta?.order || 0,
          };

          // 如果有子路由，递归处理
          if (route.children && route.children.length > 0) {
            menuItem.children = this._generateMenuFromRoutes(route.children);
          }

          return menuItem;
        })
        .sort((a, b) => (a.order || 0) - (b.order || 0)); // 按 order 排序
    },

    // 设置应用路由
    setAppRoutes(routes: AppRouteRecordRaw[]) {
      this.appRoutes = routes;
      // 自动生成菜单数据
      this.menuData = this._generateMenuFromRoutes(routes);
    },

    // 手动设置菜单数据
    setMenuData(menuData: any[]) {
      this.menuData = menuData;
    },
  },
});

