<template>
  <div class="icon-picker">
    <!-- 搜索框 -->
    <a-input
      v-model="searchText"
      placeholder="搜索图标（支持中英文）"
      allow-clear
      style="margin-bottom: 12px"
    >
      <template #prefix>
        <icon-search />
      </template>
      <template #suffix>
        <span class="icon-count">共 {{ filteredIcons.length }} 个图标</span>
      </template>
    </a-input>

    <!-- 分类Tab -->
    <a-radio-group v-model="activeCategory" type="button" size="small" style="margin-bottom: 12px; width: 100%;">
      <a-radio v-for="cat in categories" :key="cat.key" :value="cat.key">
        {{ cat.label }}
      </a-radio>
    </a-radio-group>

    <!-- 自定义分类：上传区域 -->
    <div v-if="activeCategory === 'custom'" class="upload-section" v-hasPermi="['system:icon:add']">
      <a-upload
        :auto-upload="false"
        :show-file-list="false"
        accept=".png,.svg"
        @change="handleFileChange"
      >
        <template #upload-button>
          <a-button type="outline" size="small">
            <template #icon><icon-plus /></template>
            上传图标 (PNG/SVG)
          </a-button>
        </template>
      </a-upload>
      <a-input
        v-model="customIconName"
        placeholder="图标名称（留空自动生成）"
        size="small"
        style="width: 160px; margin-left: 8px;"
      />
      <a-button 
        type="primary" 
        size="small" 
        :loading="uploading"
        :disabled="!selectedFile"
        style="margin-left: 8px;"
        @click="handleUpload"
      >
        确认上传
      </a-button>
    </div>

    <!-- 图标列表 -->
    <div class="icon-list">
      <!-- 自定义图标 -->
      <template v-if="activeCategory === 'custom' || activeCategory === 'all'">
        <div
          v-for="icon in customIconsFiltered"
          :key="'custom-' + icon.iconId"
          class="icon-item custom-icon"
          :class="{ active: selectedIcon === 'custom:' + icon.iconName }"
          @click="handleSelect('custom:' + icon.iconName)"
        >
          <img :src="getIconUrl(icon.iconPath)" :alt="icon.iconName" class="custom-icon-img" />
          <span class="icon-name">{{ icon.iconName }}</span>
          <a-button 
            v-if="activeCategory === 'custom'"
            type="text" 
            size="mini" 
            status="danger" 
            class="delete-btn"
            @click.stop="handleDeleteIcon(icon)"
            v-hasPermi="['system:icon:remove']"
          >
            <icon-delete />
          </a-button>
        </div>
      </template>

      <!-- Arco Design 图标 -->
      <div
        v-for="icon in filteredArcoIcons"
        :key="icon.name"
        class="icon-item"
        :class="{ active: selectedIcon === icon.name }"
        @click="handleSelect(icon.name)"
      >
        <component :is="icon.component" :size="24" />
        <span class="icon-name">{{ icon.name }}</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, markRaw } from 'vue';
import { Message, Modal } from '@arco-design/web-vue';
import { IconSearch, IconPlus, IconDelete } from '@arco-design/web-vue/es/icon';
import * as ArcoIcons from '@arco-design/web-vue/es/icon';
import { uploadIcon, listIcons, deleteIcon } from '@/api/system';

interface IconItem {
  name: string;
  component: any;
  category: string;
}

interface CustomIcon {
  iconId: number;
  iconName: string;
  iconPath: string;
  iconType: string;
}

// 图标分类定义
const categories = [
  { key: 'all', label: '全部' },
  { key: 'direction', label: '方向' },
  { key: 'hint', label: '提示' },
  { key: 'media', label: '媒体' },
  { key: 'file', label: '文件' },
  { key: 'edit', label: '编辑' },
  { key: 'brand', label: '品牌' },
  { key: 'common', label: '通用' },
  { key: 'custom', label: '自定义' },
];

// 分类关键词映射
const categoryKeywords: Record<string, string[]> = {
  direction: ['Arrow', 'Caret', 'Double', 'Up', 'Down', 'Left', 'Right', 'Expand', 'Shrink', 'Swap', 'Rotate', 'Sync', 'Undo', 'Redo', 'Forward', 'Backward', 'Sort'],
  hint: ['Check', 'Close', 'Exclamation', 'Info', 'Question', 'Warning', 'Stop', 'Minus', 'Plus', 'Loading'],
  media: ['Video', 'Audio', 'Image', 'Camera', 'Play', 'Pause', 'Sound', 'Mute', 'Volume', 'Music', 'Mic', 'Record', 'Live', 'Fullscreen'],
  file: ['File', 'Folder', 'Document', 'Archive', 'Drive', 'Cloud', 'Download', 'Upload', 'Import', 'Export', 'Attachment', 'Storage'],
  edit: ['Edit', 'Delete', 'Copy', 'Paste', 'Cut', 'Save', 'Undo', 'Redo', 'Bold', 'Italic', 'Underline', 'Strike', 'Align', 'Font', 'Highlight', 'Eraser', 'Brush', 'Palette', 'Filter', 'Zoom', 'Crop', 'Scissor'],
  brand: ['Github', 'Google', 'Facebook', 'Twitter', 'Weibo', 'Wechat', 'Qq', 'Alipay', 'Tiktok', 'Apple', 'Android', 'Windows', 'Linux', 'Chrome', 'Firefox', 'Safari', 'Codepen', 'Dribbble', 'Behance', 'Figma', 'Sketch', 'Adobe', 'Bytedance', 'Lark', 'Xigua', 'Slack', 'Gitlab', 'Reddit', 'Youtube', 'Instagram', 'Linkedin', 'Pinterest', 'Discord', 'Telegram', 'Whatsapp', 'Snapchat', 'Twitch', 'Medium', 'Amazon', 'Microsoft'],
};

// 获取图标分类
const getIconCategory = (name: string): string => {
  const upperName = name;
  for (const [category, keywords] of Object.entries(categoryKeywords)) {
    if (keywords.some(keyword => upperName.includes(keyword))) {
      return category;
    }
  }
  return 'common';
};

// Arco Design 图标
const allArcoIcons = ref<IconItem[]>([]);
const activeCategory = ref('all');

// 自定义图标
const customIcons = ref<CustomIcon[]>([]);
const customIconName = ref('');
const selectedFile = ref<File | null>(null);
const uploading = ref(false);

// 加载自定义图标
const loadCustomIcons = async () => {
  try {
    const res = await listIcons();
    customIcons.value = Array.isArray(res) ? res : [];
  } catch (error) {
    console.error('加载自定义图标失败:', error);
  }
};

// 获取图标URL
const getIconUrl = (path: string): string => {
  // 后端返回的是相对路径，需要拼接完整URL
  return `/api${path}`;
};

onMounted(() => {
  // 动态获取所有 Arco Design 图标
  const iconEntries = Object.entries(ArcoIcons).filter(([key, value]) => {
    return key.startsWith('Icon') && 
           key !== 'Icon' && 
           (typeof value === 'function' || (typeof value === 'object' && value !== null));
  });

  allArcoIcons.value = iconEntries.map(([key, component]) => {
    const name = key.replace('Icon', '');
    return {
      name,
      component: markRaw(component as any),
      category: getIconCategory(name),
    };
  }).sort((a, b) => a.name.localeCompare(b.name));

  console.log(`[IconPicker] 已加载 ${allArcoIcons.value.length} 个 Arco 图标`);

  // 加载自定义图标
  loadCustomIcons();
});

const props = defineProps<{
  modelValue?: string;
}>();

const emit = defineEmits<{
  'update:modelValue': [value: string];
  'select': [iconName: string];
}>();

const searchText = ref('');
const selectedIcon = ref(props.modelValue || '');

// 过滤 Arco 图标
const filteredArcoIcons = computed(() => {
  // 自定义分类不显示 Arco 图标
  if (activeCategory.value === 'custom') {
    return [];
  }

  let icons = allArcoIcons.value;

  // 按分类过滤
  if (activeCategory.value !== 'all') {
    icons = icons.filter(icon => icon.category === activeCategory.value);
  }

  // 按搜索关键词过滤
  if (searchText.value) {
    const keyword = searchText.value.toLowerCase();
    icons = icons.filter(icon => icon.name.toLowerCase().includes(keyword));
  }

  return icons;
});

// 过滤自定义图标
const customIconsFiltered = computed(() => {
  if (searchText.value) {
    const keyword = searchText.value.toLowerCase();
    return customIcons.value.filter(icon => icon.iconName.toLowerCase().includes(keyword));
  }
  return customIcons.value;
});

// 计算总图标数
const filteredIcons = computed(() => {
  if (activeCategory.value === 'custom') {
    return customIconsFiltered.value;
  }
  if (activeCategory.value === 'all') {
    return [...customIconsFiltered.value, ...filteredArcoIcons.value];
  }
  return filteredArcoIcons.value;
});

const handleSelect = (iconName: string) => {
  selectedIcon.value = iconName;
  emit('update:modelValue', iconName);
  emit('select', iconName);
};

// 文件选择
const handleFileChange = (fileList: any) => {
  if (fileList && fileList.length > 0) {
    selectedFile.value = fileList[0].file;
    Message.info(`已选择文件: ${fileList[0].name}`);
  }
};

// 上传图标
const handleUpload = async () => {
  if (!selectedFile.value) {
    Message.warning('请先选择图标文件');
    return;
  }

  uploading.value = true;
  try {
    await uploadIcon(selectedFile.value, customIconName.value || undefined);
    Message.success('图标上传成功');
    selectedFile.value = null;
    customIconName.value = '';
    await loadCustomIcons();
  } catch (error: any) {
    Message.error(error?.message || '上传失败');
  } finally {
    uploading.value = false;
  }
};

// 删除图标
const handleDeleteIcon = (icon: CustomIcon) => {
  Modal.confirm({
    title: '删除确认',
    content: `确定要删除图标 "${icon.iconName}" 吗？`,
    onOk: async () => {
      try {
        await deleteIcon(icon.iconId);
        Message.success('删除成功');
        await loadCustomIcons();
      } catch (error: any) {
        Message.error(error?.message || '删除失败');
      }
    },
  });
};
</script>

<style scoped>
.icon-picker {
  width: 100%;
}

.icon-count {
  font-size: 12px;
  color: var(--color-text-3);
}

.upload-section {
  display: flex;
  align-items: center;
  margin-bottom: 12px;
  padding: 12px;
  background: var(--color-fill-1);
  border-radius: 6px;
}

.icon-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(90px, 1fr));
  gap: 10px;
  max-height: 400px;
  overflow-y: auto;
  padding: 8px;
}

.icon-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 10px 6px;
  border: 1px solid var(--color-border-2);
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s;
  background: var(--color-bg-1);
  position: relative;
}

.icon-item:hover {
  border-color: var(--color-primary-light-3);
  background: var(--color-primary-light-1);
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

.icon-item.active {
  border-color: var(--color-primary);
  background: var(--color-primary-light-1);
}

/* 自定义图标特殊样式 */
.custom-icon {
  border-color: var(--color-success-light-3);
}

.custom-icon:hover {
  border-color: var(--color-success);
}

.custom-icon-img {
  width: 24px;
  height: 24px;
  object-fit: contain;
}

.delete-btn {
  position: absolute;
  top: 2px;
  right: 2px;
  opacity: 0;
  transition: opacity 0.2s;
}

.icon-item:hover .delete-btn {
  opacity: 1;
}

.icon-name {
  margin-top: 6px;
  font-size: 11px;
  color: var(--color-text-2);
  text-align: center;
  word-break: break-all;
  line-height: 1.2;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.icon-item.active .icon-name {
  color: var(--color-primary);
  font-weight: 500;
}

/* 自定义滚动条 */
.icon-list::-webkit-scrollbar {
  width: 6px;
}

.icon-list::-webkit-scrollbar-thumb {
  background: var(--color-border-3);
  border-radius: 3px;
}

.icon-list::-webkit-scrollbar-thumb:hover {
  background: var(--color-border-4);
}
</style>
