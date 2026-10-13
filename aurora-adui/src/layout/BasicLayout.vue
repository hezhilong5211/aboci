<template>
  <a-layout class="layout-container">
    <!-- 顶部导航栏 -->
    <a-layout-header class="layout-header">
      <div class="header-left">
        <!-- 移动端菜单按钮 -->
        <a-button
          v-if="isMobile"
          type="text"
          class="mobile-menu-btn"
          @click="mobileMenuVisible = true"
        >
          <template #icon>
            <icon-menu />
          </template>
        </a-button>
        <div class="logo">
          <img :src="systemLogo" alt="Aboci logo" class="logo-img" v-if="systemLogo" />
          <span class="logo-text">{{ systemTitle }}</span>
        </div>
        <a-menu
          v-if="layoutSettings.topMenu"
          :selected-keys="selectedKeys"
          :open-keys="openKeys"
          mode="horizontal"
          class="top-menu"
          @menu-item-click="handleMenuClick"
          @sub-menu-click="handleSubMenuClick"
        >
          <template v-for="item in menuData" :key="item.key">
            <a-sub-menu v-if="item.children && item.children.length > 0" :key="`sub-${item.key}`">
              <template #icon>
                <IconRenderer :icon="item.icon" />
              </template>
              <template #title>{{ item.title }}</template>
              <a-menu-item v-for="child in item.children" :key="child.key">
                <template #icon>
                  <IconRenderer :icon="child.icon" v-if="child.icon" />
                </template>
                {{ child.title }}
              </a-menu-item>
            </a-sub-menu>
            <a-menu-item v-else :key="item.key">
              <template #icon>
                <IconRenderer :icon="item.icon" />
              </template>
              {{ item.title }}
            </a-menu-item>
          </template>
        </a-menu>
      </div>
      <div class="header-right">
        <a-space :size="16">
          <a-tooltip :content="isFullscreen ? '退出全屏' : '全屏'">
            <a-button type="text" :shape="'circle'" @click="toggleFullscreen">
              <template #icon>
                <icon-fullscreen-exit v-if="isFullscreen" />
                <icon-fullscreen v-else />
              </template>
            </a-button>
          </a-tooltip>
          <a-tooltip content="页面配置">
            <a-button type="text" :shape="'circle'" @click="settingsDrawerVisible = true">
              <template #icon>
                <icon-settings />
              </template>
            </a-button>
          </a-tooltip>
          <a-tooltip :content="isDarkMode ? '切换到亮色模式' : '切换到暗黑模式'">
            <a-button type="text" :shape="'circle'" @click="toggleTheme">
              <template #icon>
                <icon-sun v-if="isDarkMode" />
                <icon-moon v-else />
              </template>
            </a-button>
          </a-tooltip>
          <a-dropdown>
            <a-avatar :size="32" :image-url="userAvatar" :style="{ backgroundColor: userAvatar ? 'transparent' : '#165DFF', cursor: 'pointer' }">
              <template #trigger-icon>
                <icon-user />
              </template>
            </a-avatar>
            <template #content>
              <a-doption @click="handleLogout">
                <a-space>
                  <icon-poweroff />
                  <span>退出登录</span>
                </a-space>
              </a-doption>
            </template>
          </a-dropdown>
        </a-space>
      </div>
    </a-layout-header>

    <a-layout>
      <!-- 左侧侧边栏（桌面端显示，移动端隐藏并使用抽屉） -->
      <a-layout-sider
        v-if="!layoutSettings.topMenu && !isMobile"
        :collapsed="collapsed"
        :width="layoutSettings.menuWidth"
        :collapsed-width="64"
        breakpoint="lg"
        @breakdown="handleBreakdown"
        class="layout-sider"
        :style="{ height: 'calc(100vh - 60px)' }"
      >
        <a-menu
          :selected-keys="selectedKeys"
          :open-keys="openKeys"
          :collapsed="collapsed"
          mode="vertical"
          :style="{ height: 'calc(100% - 48px)' }"
          @menu-item-click="handleMenuClick"
          @sub-menu-click="handleSubMenuClick"
        >
          <template v-for="item in menuData" :key="item.key">
            <a-sub-menu v-if="item.children && item.children.length > 0" :key="`sub-${item.key}`">
              <template #icon>
                <IconRenderer :icon="item.icon" />
              </template>
              <template #title>{{ item.title }}</template>
              <a-menu-item v-for="child in item.children" :key="child.key">
                <template #icon>
                  <IconRenderer :icon="child.icon" v-if="child.icon" />
                </template>
                {{ child.title }}
              </a-menu-item>
            </a-sub-menu>
            <a-menu-item v-else :key="item.key">
              <template #icon>
                <IconRenderer :icon="item.icon" />
              </template>
              {{ item.title }}
            </a-menu-item>
          </template>
        </a-menu>
        <!-- 折叠按钮 -->
        <div class="sider-footer">
          <a-button
            type="text"
            :shape="'circle'"
            @click="toggleCollapse"
            class="collapse-btn"
          >
            <template #icon>
              <icon-menu-fold v-if="!collapsed" />
              <icon-menu-unfold v-else />
            </template>
          </a-button>
        </div>
      </a-layout-sider>

      <!-- 内容区域 -->
      <a-layout-content class="layout-content" :style="{ backgroundColor: dynamicContentBg }">
        <div class="content-wrapper">
          <router-view />
        </div>
      </a-layout-content>
    </a-layout>

    <!-- 页面配置抽屉 -->
    <a-drawer
      v-model:visible="settingsDrawerVisible"
      title="页面配置"
      :width="300"
      placement="right"
    >
      <a-form :model="layoutSettings" layout="vertical">
        <a-form-item label="菜单宽度 (px)">
          <a-input-number
            v-model="layoutSettings.menuWidth"
            :min="180"
            :max="400"
            :step="10"
            style="width: 100%"
          />
        </a-form-item>
        <a-form-item label="菜单位置">
          <a-switch v-model="layoutSettings.topMenu">
            <template #checked>顶部</template>
            <template #unchecked>侧边</template>
          </a-switch>
        </a-form-item>
      </a-form>
    </a-drawer>

    <!-- 移动端菜单抽屉 -->
    <a-drawer
      v-model:visible="mobileMenuVisible"
      title="菜单"
      :width="280"
      placement="left"
      :footer="false"
    >
      <a-menu
        :selected-keys="selectedKeys"
        :open-keys="openKeys"
        mode="vertical"
        @menu-item-click="handleMobileMenuClick"
        @sub-menu-click="handleSubMenuClick"
      >
        <template v-for="item in menuData" :key="item.key">
          <a-sub-menu v-if="item.children && item.children.length > 0" :key="`sub-${item.key}`">
            <template #icon>
              <IconRenderer :icon="item.icon" />
            </template>
            <template #title>{{ item.title }}</template>
            <a-menu-item v-for="child in item.children" :key="child.key">
              <template #icon>
                <IconRenderer :icon="child.icon" v-if="child.icon" />
              </template>
              {{ child.title }}
            </a-menu-item>
          </a-sub-menu>
          <a-menu-item v-else :key="item.key">
            <template #icon>
              <IconRenderer :icon="item.icon" />
            </template>
            {{ item.title }}
          </a-menu-item>
        </template>
      </a-menu>
    </a-drawer>
  </a-layout>
</template>

<script setup lang="ts">
import { ref, reactive, watch, computed, onMounted, onUnmounted } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import {
  IconFullscreen,
  IconFullscreenExit,
  IconSettings,
  IconUser,
  IconPoweroff,
  IconMenuFold,
  IconMenuUnfold,
  IconHome,
  IconDashboard,
  IconBarChart,
  IconList,
  IconFile,
  IconInfoCircle,
  IconCheckCircle,
  IconCloseCircle,
  IconUserGroup,
  IconMenu,
  IconSun,
  IconMoon,
} from '@arco-design/web-vue/es/icon';
import * as Icons from '@arco-design/web-vue/es/icon';
import { Message } from '@arco-design/web-vue';
import { usePermissionStore } from '@/stores/permission';
import { resetRouter } from '@/router';
import { getConfigKey } from '@/api/config';
import { useUserStore } from '@/stores/user';
import IconRenderer from '@/components/IconRenderer.vue';
import { listIcons } from '@/api/system';
import { useAppStore } from '@/stores/app';

const router = useRouter();
const route = useRoute();
const permissionStore = usePermissionStore();
const userStore = useUserStore();
const appStore = useAppStore();

// 计算用户头像（需要拼接完整 URL）
const userAvatar = computed(() => {
  const avatar = userStore.getUserAvatar;
  if (!avatar) return '';
  // 如果是相对路径，拼接 /api 前缀以通过代理访问后端
  if (avatar.startsWith('/profile/')) {
    return '/api' + avatar;
  }
  return avatar;
});

// 页面配置抽屉
const settingsDrawerVisible = ref(false);
const LAYOUT_SETTINGS_KEY = 'aurora-layout-settings';
const layoutSettings = reactive({
  menuWidth: 220, // 默认侧边栏宽度
  topMenu: false, // 是否开启顶部菜单
});

// 侧边栏折叠状态
const collapsed = ref(false);

// 移动端状态
const isMobile = ref(false);
const mobileMenuVisible = ref(false);

// 处理响应式断点切换
const handleBreakdown = (broken: boolean) => {
  isMobile.value = broken;
  if (broken) {
    collapsed.value = true;
  }
};

// 初始化移动端检测
const checkMobile = () => {
  isMobile.value = window.innerWidth < 992;
};

// 暗黑模式状态
const isDarkMode = ref(false);
const THEME_KEY = 'arco-theme';

// 全屏状态
const isFullscreen = ref(false);

// 品牌固定为 Aboci，避免历史 sys_config 配置覆盖页面展示
const systemTitle = 'Aboci';
const systemLogo = '/logo_abopi_mark.png';

// 主题背景配置
const lightBgParam = ref('#EFF0F3');
const darkBgParam = ref('#000000');

// 校验十六进制颜色
const isValidHex = (color: string) => /^#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{3})$/.test(color);

// 动态计算内容底色
const dynamicContentBg = computed(() => {
  if (isDarkMode.value) {
    return isValidHex(darkBgParam.value) ? darkBgParam.value : '#000000';
  } else {
    return isValidHex(lightBgParam.value) ? lightBgParam.value : '#EFF0F3';
  }
});

// 切换主题
const toggleTheme = () => {
  isDarkMode.value = !isDarkMode.value;
  const theme = isDarkMode.value ? 'dark' : 'light';
  document.body.setAttribute('arco-theme', theme);
  localStorage.setItem(THEME_KEY, theme);
};

// 初始化主题
const initTheme = () => {
  const savedTheme = localStorage.getItem(THEME_KEY);
  if (savedTheme) {
    isDarkMode.value = savedTheme === 'dark';
    document.body.setAttribute('arco-theme', savedTheme);
  } else {
    // 默认使用亮色模式
    isDarkMode.value = false;
    document.body.setAttribute('arco-theme', 'light');
  }
};

// 切换折叠状态
const toggleCollapse = () => {
  collapsed.value = !collapsed.value;
};

// 切换全屏
const toggleFullscreen = async () => {
  try {
    if (!document.fullscreenElement) {
      // 进入全屏
      await document.documentElement.requestFullscreen();
      isFullscreen.value = true;
    } else {
      // 退出全屏
      await document.exitFullscreen();
      isFullscreen.value = false;
    }
  } catch (error) {
    Message.error('全屏操作失败');
  }
};

// 监听全屏状态变化
const handleFullscreenChange = () => {
  isFullscreen.value = !!document.fullscreenElement;
};

// 图标映射（用于兼容一些简写或特殊映射）
const iconMap: Record<string, any> = {
  'system': IconSettings,
  'Setting': IconSettings,
  'Settings': IconSettings,
  'user': IconUser,
  'peoples': IconUserGroup,
  'home': IconHome,
};

// 获取图标组件
const getIcon = (iconName?: string) => {
  if (!iconName) return IconMenu;
  
  // 如果是自定义图标，直接返回名称字符串
  if (iconName.startsWith('custom:')) {
    return iconName;
  }
  
  // 1. 先尝试在映射表中查找
  if (iconMap[iconName]) {
    return iconMap[iconName];
  }
  
  // 2. 尝试将名称转换为首字符大写并查找（如 'menu' -> 'IconMenu'）
  let processedName = iconName;
  
  // 如果是 k-case 格式（如 icon-apps），转换为 PascalCase (IconApps)
  if (processedName.includes('-')) {
    processedName = processedName.split('-').map(part => part.charAt(0).toUpperCase() + part.slice(1)).join('');
  }
  
  // 确保以 Icon 开头
  if (!processedName.startsWith('Icon')) {
    processedName = 'Icon' + processedName.charAt(0).toUpperCase() + processedName.slice(1);
  }
  
  // 3. 从全量图标库中查找
  return (Icons as any)[processedName] || IconMenu;
};

// 将路由数据转换为菜单数据
const transformRoutesToMenu = (routes: any[]): any[] => {
  return routes.map(route => {
    const menuItem: any = {
      key: route.path || route.name || `menu-${Math.random()}`,
      title: route.meta?.title || route.name || '未命名',
      path: route.path,
      icon: getIcon(route.meta?.icon),
    };

    if (route.children && route.children.length > 0) {
      menuItem.children = transformRoutesToMenu(route.children);
    }

    return menuItem;
  });
};

// 固定的主页菜单（默认菜单，不依赖后端数据）
const defaultHomeMenu = {
  key: 'home',
  title: '主页',
  path: '/home',
  icon: IconHome,
};

// 从 permission store 获取动态菜单，并在最前面添加固定的主页菜单
const menuData = computed(() => {
  const routes = permissionStore.sidebarRouters || [];
  const dynamicMenus = routes.length > 0 ? transformRoutesToMenu(routes) : [];
  
  // 主页菜单始终排在第一位
  return [defaultHomeMenu, ...dynamicMenus];
});

// 根据路径查找菜单key
function findMenuKeyByPath(path: string, menus: any[]): string {
  for (const item of menus) {
    if (item.path === path) {
      return item.key;
    }
    // 检查子菜单
    if (item.children && item.children.length > 0) {
      for (const child of item.children) {
        if (child.path === path) {
          return child.key;
        }
      }
    }
  }
  // 默认返回首页
  return 'home';
}

// 根据路径查找需要展开的菜单
function findOpenKeysByPath(path: string, menus: any[]): string[] {
  for (const item of menus) {
    if (item.children && item.children.length > 0) {
      for (const child of item.children) {
        if (child.path === path) {
          return [item.key];
        }
      }
    }
  }
  // 如果访问首页，不展开任何菜单
  if (path === '/home') {
    return [];
  }
  return [];
}

// 响应式的选中菜单key
const selectedKeys = ref<string[]>([findMenuKeyByPath(route.path, menuData.value)]);

// 响应式的展开菜单keys
const openKeys = ref<string[]>(findOpenKeysByPath(route.path, menuData.value));

// 监听路由变化，同步更新菜单选中状态
watch(
  () => route.path,
  (newPath) => {
    selectedKeys.value = [findMenuKeyByPath(newPath, menuData.value)];
    // 查找需要展开的父菜单
    const newOpenKeys = findOpenKeysByPath(newPath, menuData.value);
    // 合并现有的展开菜单，避免关闭其他已展开的菜单
    openKeys.value = [...new Set([...openKeys.value, ...newOpenKeys])];
  },
  { immediate: true }
);

// 监听菜单数据变化
watch(
  () => menuData.value,
  () => {
    selectedKeys.value = [findMenuKeyByPath(route.path, menuData.value)];
    openKeys.value = findOpenKeysByPath(route.path, menuData.value);
  }
);

// 菜单项点击处理
const handleMenuClick = (key: string) => {
  const menus = menuData.value;
  // 查找对应的菜单项
  for (const item of menus) {
    if (item.key === key && item.path) {
      selectedKeys.value = [key];
      router.push(item.path);
      return;
    }
    // 检查子菜单
    if (item.children && item.children.length > 0) {
      for (const child of item.children) {
        if (child.key === key && child.path) {
          selectedKeys.value = [key];
          // 确保父菜单展开，并保留其他已展开的菜单
          if (!openKeys.value.includes(item.key)) {
            openKeys.value = [...openKeys.value, item.key];
          }
          router.push(child.path);
          return;
        }
      }
    }
  }
};

// 移动端菜单点击处理（导航后关闭抽屉）
const handleMobileMenuClick = (key: string) => {
  handleMenuClick(key);
  mobileMenuVisible.value = false;
};

// 子菜单展开/收起处理
const handleSubMenuClick = (_key: string, openKeysList: string[]) => {
  // 检查当前选中的菜单项是否属于某个子菜单
  const currentSelectedKey = selectedKeys.value[0];
  const parentKeyToKeep: string[] = [];
  const menus = menuData.value;
  
  if (currentSelectedKey) {
    // 查找当前选中的菜单项属于哪个父菜单
    for (const item of menus) {
      if (item.children && item.children.length > 0) {
        const childKey = item.children.find((child: any) => child.key === currentSelectedKey);
        if (childKey) {
          // 如果选中的是子菜单项，强制保持父菜单展开
          parentKeyToKeep.push(item.key);
        }
      }
    }
  }
  
  // 合并：确保父菜单在展开列表中
  const finalOpenKeys = [...new Set([...openKeysList, ...parentKeyToKeep])];
  openKeys.value = finalOpenKeys;
};

// 退出登录
const handleLogout = () => {
  Message.info('退出登录');
  localStorage.removeItem('token');
  permissionStore.clearPermissions();
  userStore.setUserInfo(null);
  // 重置路由
  resetRouter();
  router.push('/login');
};

// 组件挂载时检查路由和初始化主题
onMounted(async () => {
  // 加载自定义图标数据
  try {
    const icons = await listIcons() as any;
    if (icons) {
      appStore.setCustomIcons(icons);
    }
  } catch (e) {
    console.error('加载自定义图标失败:', e);
  }

  // 如果路由未生成，尝试生成
  if (permissionStore.addRoutes.length === 0) {
    permissionStore.generateRoutes();
  }
  // 初始化主题
  initTheme();
  // 初始化布局配置
  const savedLayout = localStorage.getItem(LAYOUT_SETTINGS_KEY);
  if (savedLayout) {
    try {
      Object.assign(layoutSettings, JSON.parse(savedLayout));
    } catch (e) {
      console.error('加载布局配置失败:', e);
    }
  }
  // 初始化全屏状态
  isFullscreen.value = !!document.fullscreenElement;
  
  // 初始化用户信息（确保页面刷新后也能正确显示头像）
  if (!userStore.userInfo || !permissionStore.permissionsReady) {
    try {
      const { getInfo } = await import('@/api/login');
      const userInfo = await getInfo();
      if (userInfo && userInfo.user) {
        userStore.setUserInfo(userInfo.user);
      }
      // 刷新后同步响应式按钮权限；角色权限变更后所有 v-hasPermi 会立即重算。
      permissionStore.setPermissions(userInfo?.permissions || []);
    } catch (error) {
      console.error('获取用户信息失败:', error);
    }
  }
  
  // 获取主题底色配置
  getConfigKey('sys.theme.light.bg').then((res: any) => {
    if (res) lightBgParam.value = res;
  });
  getConfigKey('sys.theme.dark.bg').then((res: any) => {
    if (res) darkBgParam.value = res;
  });

  // 监听全屏状态变化
  document.addEventListener('fullscreenchange', handleFullscreenChange);
  
  // 初始化移动端检测并监听窗口大小变化
  checkMobile();
  window.addEventListener('resize', checkMobile);
});

// 监听布局配置变化并保存
watch(layoutSettings, (val) => {
  localStorage.setItem(LAYOUT_SETTINGS_KEY, JSON.stringify(val));
}, { deep: true });

// 组件卸载时移除监听器
onUnmounted(() => {
  document.removeEventListener('fullscreenchange', handleFullscreenChange);
  window.removeEventListener('resize', checkMobile);
});
</script>

<style scoped>
.layout-container {
  height: 100vh;
}

.layout-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  height: 60px;
  background: var(--color-bg-1);
  border-bottom: 1px solid var(--color-border);
}

.header-left {
  display: flex;
  align-items: center;
  flex: 1;
  overflow: hidden;
}

.mobile-menu-btn {
  margin-right: 12px;
  font-size: 18px;
}

.top-menu {
  flex: 1;
  margin-left: 20px;
  background-color: transparent;
}

.logo {
  display: flex;
  align-items: center;
  font-size: 18px;
  font-weight: 600;
  color: var(--color-text-1);
}

.logo-img {
  width: auto;
  height: 28px;
  max-width: 80px;
  object-fit: contain;
}

.logo-text {
  margin-left: 8px;
}

.header-right {
  display: flex;
  align-items: center;
}

.layout-sider {
  background: var(--color-bg-1);
  border-right: 1px solid var(--color-border);
  position: relative;
}
.sider-footer {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-top: 1px solid var(--color-border);
  background: var(--color-bg-1);
}

.collapse-btn {
  width: 32px;
  height: 32px;
}

.layout-content {
  overflow: auto;
  height: calc(100vh - 60px);
}

.content-wrapper {
  padding: 24px;
  min-height: 100%;
}
</style>

<style>
/* 暗黑模式下的背景颜色 */
body[arco-theme='dark'] .layout-container {
  background-color: #000000;
}


body[arco-theme='dark'] .layout-header {
  background-color: #000000;
}

body[arco-theme='dark'] .layout-sider {
  background-color: #000000;
}

/* 统一图标大小并增加平滑过渡 */
.arco-menu-icon {
  transition: all 0.2s;
}

.arco-menu-icon .arco-icon {
  font-size: 18px !important;
}

/* 微调展开态图标位置，对齐折叠态中心 */
.arco-layout-sider:not(.arco-layout-sider-collapsed) .arco-menu-icon {
  margin-left: 3px !important;
}

/* 折叠菜单布局 */
.arco-menu-collapsed {
  width: 100% !important;
}

.arco-menu-collapsed .arco-menu-item,
.arco-menu-collapsed .arco-menu-pop-header {
  width: 40px !important;
  height: 40px !important;
  margin: 4px auto !important;
  padding: 0 !important;
  display: flex !important;
  align-items: center !important;
  justify-content: center !important;
  border-radius: 4px !important;
}

.arco-menu-collapsed .arco-menu-icon {
  margin: 0 !important;
}

.arco-menu-collapsed .arco-menu-title,
.arco-menu-collapsed .arco-menu-icon-suffix {
  display: none !important;
  width: 0 !important;
}
</style>
