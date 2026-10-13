<template>
  <div class="app-container">
    <a-card class="table-card" title="底部导航配置">
      <template #extra>
        <span class="card-tip">拖动右侧卡片可调整顺序；名称、图标和启用状态改完自动保存。至少保留一项启用</span>
      </template>

      <a-spin :loading="loading" style="width: 100%;">
        <a-row :gutter="16">
          <!-- 左侧:底部导航样式预览(未选中态 + 选中态,按当前顺序实时反映) -->
          <a-col :xs="24" :md="8">
            <div class="preview-block">
              <div class="preview-label">未选中态</div>
              <div class="tabbar-bar">
                <div class="tabbar-item" v-for="t in enabledTabbarList" :key="'n-' + t.tabKey">
                  <img v-if="t.iconNormal" :src="fullUrl(t.iconNormal)" class="tabbar-icon" alt="" />
                  <div v-else class="tabbar-icon tabbar-icon--empty"></div>
                  <span class="tabbar-name">{{ t.tabName || t.tabKey }}</span>
                </div>
              </div>
            </div>
            <div class="preview-block">
              <div class="preview-label">选中态</div>
              <div class="tabbar-bar">
                <div class="tabbar-item tabbar-item--active" v-for="t in enabledTabbarList" :key="'a-' + t.tabKey">
                  <img v-if="t.iconActive" :src="fullUrl(t.iconActive)" class="tabbar-icon" alt="" />
                  <div v-else class="tabbar-icon tabbar-icon--empty"></div>
                  <span class="tabbar-name">{{ t.tabName || t.tabKey }}</span>
                </div>
              </div>
            </div>
          </a-col>

          <!-- 右侧:可拖拽排序的 tab 列表(每个 tab 一行,整行拖动调顺序) -->
          <a-col :xs="24" :md="16">
            <div class="sort-tip" v-hasPermi="['mobile:tabbar:edit']"><icon-drag-dot-vertical /> 拖动卡片调整底部导航顺序</div>
            <div class="tab-list">
              <div
                v-for="(item, index) in tabbarList"
                :key="item.tabKey"
                class="tab-row"
                :class="{ 'tab-row--disabled': item.visible === 0, 'tab-row--dragging': dragIndex === index, 'tab-row--over': overIndex === index && dragIndex !== index }"
                @dragover.prevent="onDragOver(index)"
                @drop.prevent="onDrop(index)"
              >
                <span
                  class="drag-handle"
                  title="按住拖动排序"
                  draggable="true"
                  @dragstart="onDragStart(index)"
                  @dragend="onDragEnd"
                  v-hasPermi="['mobile:tabbar:edit']"
                ><icon-drag-dot-vertical /></span>
                <span class="seq-no">{{ index + 1 }}</span>

                <div class="row-main">
                  <div class="row-line">
                    <a-tag size="small" color="arcoblue">{{ item.tabKey }}</a-tag>
                    <a-input
                      v-model="item.tabName"
                      size="small"
                      placeholder="Tab 名称"
                      allow-clear
                      class="name-input"
                      @blur="autoSave(item)"
                      v-hasPermi="['mobile:tabbar:edit']"
                    />
                  </div>
                  <span class="page-name">关联页面:{{ pageName(item.pagePath) }}</span>
                </div>

                <div class="row-icons">
                  <a-upload
                    :show-file-list="false"
                    :auto-upload="true"
                    :custom-request="(option: any) => handleIconUpload(option, index, 'iconNormal')"
                    accept="image/*"
                    v-hasPermi="['mobile:tabbar:edit']"
                  >
                    <template #upload-button>
                      <div class="icon-uploader" title="未选中态图标">
                        <img v-if="item.iconNormal" :src="fullUrl(item.iconNormal)" class="icon-preview" alt="未选中图标" />
                        <div v-else class="icon-placeholder"><icon-plus /><span>未选</span></div>
                      </div>
                    </template>
                  </a-upload>
                  <a-upload
                    :show-file-list="false"
                    :auto-upload="true"
                    :custom-request="(option: any) => handleIconUpload(option, index, 'iconActive')"
                    accept="image/*"
                    v-hasPermi="['mobile:tabbar:edit']"
                  >
                    <template #upload-button>
                      <div class="icon-uploader" title="选中态图标">
                        <img v-if="item.iconActive" :src="fullUrl(item.iconActive)" class="icon-preview" alt="选中图标" />
                        <div v-else class="icon-placeholder"><icon-plus /><span>选中</span></div>
                      </div>
                    </template>
                  </a-upload>
                </div>

                <div class="row-enabled" v-hasPermi="['mobile:tabbar:edit']">
                  <span>启用</span>
                  <a-switch
                    :model-value="item.visible === 1"
                    @change="(value: boolean) => handleVisibleChange(item, value)"
                  />
                </div>
              </div>
            </div>
          </a-col>
        </a-row>
      </a-spin>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, onMounted } from 'vue';
import { Message } from '@arco-design/web-vue';
import { IconPlus, IconDragDotVertical } from '@arco-design/web-vue/es/icon';
import { listMobileTabbar, updateMobileTabbar, SysMobileTabbar } from '@/api/mobile';
import { uploadAvatar } from '@/api/system';

const loading = ref(false);
const tabbarList = ref<SysMobileTabbar[]>([]);
const enabledTabbarList = computed(() => tabbarList.value.filter(item => item.visible === 1));
// 拖拽排序状态:正在拖动的行 / 当前悬停目标行
const dragIndex = ref(-1);
const overIndex = ref(-1);

// page_path → 页面名(只读展示;tab 固定关联自己的页面,不可改)
const PAGE_NAME: Record<string, string> = {
  '/pages/index/index': '首页',
  '/pages/create/index': '生成',
  '/pages/cases/index': '案例',
  '/pages/user/index': '我的',
};
function pageName(path?: string): string {
  return (path && PAGE_NAME[path]) || path || '';
}

// 后端返回相对路径(如 /profile/...)时拼接 /api 前缀以可预览;已是完整 URL 则原样返回
function fullUrl(url?: string): string {
  if (!url) return '';
  if (url.startsWith('/')) return '/api' + url;
  return url;
}

function getTabbarList() {
  loading.value = true;
  listMobileTabbar()
    .then((res: any) => {
      tabbarList.value = Array.isArray(res) ? res : res?.records || [];
    })
    .finally(() => {
      loading.value = false;
    });
}

// icon 上传:复用后台图片上传接口,成功后回填可访问 URL 并自动保存
async function handleIconUpload(option: any, index: number, field: 'iconNormal' | 'iconActive') {
  try {
    const file = option.fileItem.file;
    const fileUrl = await uploadAvatar(file);
    tabbarList.value[index][field] = fileUrl as unknown as string;
    option.onSuccess(fileUrl);
    autoSave(tabbarList.value[index]);
  } catch (error: any) {
    Message.error(error?.message || '图标上传失败');
    option.onError(error);
  }
}

// 自动保存单个 tab(名称失焦 / 图标上传后即时保存,无需保存按钮)
function autoSave(item: SysMobileTabbar) {
  updateMobileTabbar(item)
    .then(() => {
      Message.success('已保存');
    })
    .catch((e: any) => {
      Message.error(e?.message || '保存失败');
  });
}

function handleVisibleChange(item: SysMobileTabbar, enabled: boolean) {
  if (!enabled && enabledTabbarList.value.length <= 1) {
    Message.warning('底部导航至少保留一项启用');
    return;
  }
  const visible = enabled ? 1 : 0;
  updateMobileTabbar({ ...item, visible })
    .then(() => {
      item.visible = visible;
      Message.success(enabled ? '已启用' : '已停用');
    })
    .catch((e: any) => {
      Message.error(e?.message || '启用状态保存失败');
      getTabbarList();
    });
}

// ===== 拖拽排序 =====
function onDragStart(index: number) {
  dragIndex.value = index;
}
function onDragOver(index: number) {
  overIndex.value = index;
}
function onDragEnd() {
  dragIndex.value = -1;
  overIndex.value = -1;
}
function onDrop(targetIndex: number) {
  const from = dragIndex.value;
  dragIndex.value = -1;
  overIndex.value = -1;
  if (from < 0 || from === targetIndex) return;
  const list = tabbarList.value.slice();
  const [moved] = list.splice(from, 1);
  list.splice(targetIndex, 0, moved);
  tabbarList.value = list;
  persistOrder();
}

// 拖完按新位置重设 sort,并保存顺序有变化的 tab(逐个走现有更新接口)
function persistOrder() {
  const updates: Promise<any>[] = [];
  tabbarList.value.forEach((item, i) => {
    if (item.sort !== i) {
      item.sort = i;
      updates.push(updateMobileTabbar(item));
    }
  });
  if (!updates.length) return;
  Promise.all(updates)
    .then(() => {
      Message.success('顺序已保存');
    })
    .catch((e: any) => {
      Message.error(e?.message || '顺序保存失败');
      getTabbarList();
    });
}

onMounted(() => {
  getTabbarList();
});
</script>

<style scoped>
.app-container {
  padding: 0px;
}

.card-tip {
  color: var(--color-text-3);
  font-size: 12px;
}

/* 左侧:底部导航预览 */
.preview-block {
  margin-bottom: 16px;
}

.preview-label {
  font-size: 13px;
  color: var(--color-text-2);
  margin-bottom: 6px;
}

.tabbar-bar {
  display: flex;
  justify-content: space-around;
  align-items: flex-end;
  padding: 8px 4px;
  background: var(--color-bg-2);
  border: 1px solid var(--color-neutral-3);
  border-radius: 8px;
}

.tabbar-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  color: var(--color-text-3);
}

.tabbar-item--active {
  color: rgb(var(--primary-6));
}

.tabbar-icon {
  width: 26px;
  height: 26px;
  object-fit: contain;
}

.tabbar-icon--empty {
  border: 1px dashed var(--color-neutral-3);
  border-radius: 4px;
}

.tabbar-name {
  font-size: 11px;
}

/* 右侧:可拖拽排序列表 */
.sort-tip {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-bottom: 8px;
  color: var(--color-text-3);
  font-size: 12px;
}

.tab-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.tab-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  border: 1px solid var(--color-neutral-3);
  border-radius: 8px;
  background: var(--color-bg-2);
  transition: border-color 0.15s, box-shadow 0.15s, opacity 0.15s;
}

.tab-row--dragging {
  opacity: 0.5;
  cursor: grabbing;
}

.tab-row--over {
  border-color: rgb(var(--primary-6));
  box-shadow: 0 0 0 2px rgba(var(--primary-6), 0.2);
}

.tab-row--disabled {
  opacity: 0.62;
}

.row-enabled {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--color-text-2);
  font-size: 13px;
}

.drag-handle {
  color: var(--color-text-3);
  font-size: 18px;
  cursor: grab;
}

.seq-no {
  width: 20px;
  height: 20px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: rgb(var(--primary-6));
  color: #fff;
  font-size: 12px;
}

.row-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.row-line {
  display: flex;
  align-items: center;
  gap: 6px;
}

.name-input {
  max-width: 160px;
}

.page-name {
  color: var(--color-text-3);
  font-size: 12px;
}

.row-icons {
  display: flex;
  gap: 10px;
  flex-shrink: 0;
}

.icon-uploader {
  width: 52px;
  height: 52px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px dashed var(--color-neutral-3);
  border-radius: 6px;
  cursor: pointer;
  transition: border-color 0.2s;
  background: var(--color-fill-1);
}

.icon-uploader:hover {
  border-color: rgb(var(--primary-6));
}

.icon-preview {
  width: 100%;
  height: 100%;
  object-fit: contain;
  border-radius: 6px;
}

.icon-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: var(--color-text-3);
  font-size: 11px;
}
</style>
