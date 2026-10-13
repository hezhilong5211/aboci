<template>
  <!-- Arco Design 组件图标 -->
  <component
    v-if="resolvedComponent"
    :is="resolvedComponent"
    :size="size"
    :style="customStyle"
    class="arco-icon"
  />
  
  <!-- 自定义图片图标 -->
  <img
    v-else-if="customIconUrl"
    :src="customIconUrl"
    :alt="String(icon)"
    :style="imgStyle"
    class="custom-icon-img arco-icon"
  />
  
  <!-- 默认图标 -->
  <icon-menu v-else :size="size" :style="customStyle" class="arco-icon" />
</template>

<script setup lang="ts">
import { computed } from 'vue';
import * as Icons from '@arco-design/web-vue/es/icon';
import {
  IconSettings,
  IconUser,
  IconUserGroup,
  IconHome,
  IconMenu,
} from '@arco-design/web-vue/es/icon';
import { useAppStore } from '@/stores/app';

const props = withDefaults(defineProps<{
  icon?: string | object;
  size?: number;
  color?: string;
}>(), {
  size: 16,
});

const appStore = useAppStore();

// 图标映射
const iconMap: Record<string, any> = {
  'system': IconSettings,
  'Setting': IconSettings,
  'Settings': IconSettings,
  'user': IconUser,
  'peoples': IconUserGroup,
  'home': IconHome,
  'Menu': IconMenu,
};

// 样式
const customStyle = computed(() => ({
  color: props.color
}));

const imgStyle = computed(() => ({
  width: '1em',
  height: '1em',
  objectFit: 'contain' as const,
  verticalAlign: '-0.15em',
  ...customStyle.value
}));

// 判断是否是自定义图标字符串
const isCustomString = computed(() => {
  return typeof props.icon === 'string' && props.icon.startsWith('custom:');
});

// 解析自定义图标URL
const customIconUrl = computed(() => {
  if (isCustomString.value) {
    const name = (props.icon as string).replace('custom:', '');
    // 从 Store 中查找对应的路径
    const iconDef = appStore.customIcons.find(i => i.iconName === name);
    if (iconDef) {
       // 后端存储的是相对路径 /profile/icons/... 需要转为 /api/profile/...
       return '/api' + iconDef.iconPath;
    }
    // 如果 Store 中还没加载，或者找不到，暂时无法显示
    return '';
  }
  return '';
});

// 解析 Arco 组件
const resolvedComponent = computed(() => {
  if (!props.icon) return null;
  
  // 如果已经是组件对象
  if (typeof props.icon === 'object') {
    return props.icon;
  }
  
  // 如果是字符串且不是自定义图标
  if (typeof props.icon === 'string' && !isCustomString.value) {
    const iconName = props.icon;
    
    // 1. 查映射表
    if (iconMap[iconName]) {
      return iconMap[iconName];
    }
    
    // 2. 尝试从全量库查找
    let processedName = iconName;
    if (processedName.includes('-')) {
      processedName = processedName.split('-').map(part => part.charAt(0).toUpperCase() + part.slice(1)).join('');
    }
    if (!processedName.startsWith('Icon')) {
      processedName = 'Icon' + processedName.charAt(0).toUpperCase() + processedName.slice(1);
    }
    
    const component = (Icons as any)[processedName];
    if (component) return component;
  }
  
  return null;
});
</script>

<style scoped>
.custom-icon-img {
  display: inline-block;
}
.arco-icon {
  display: inline-block;
}
</style>
