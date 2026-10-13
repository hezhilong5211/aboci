<template>
  <div class="app-container">
    <a-card class="table-card" title="Skill 配置">
      <template #extra>
        <a-button type="primary" @click="handleAdd" v-hasPermi="['skill:add']">
          <template #icon><icon-plus /></template>
          新增 Skill
        </a-button>
      </template>

      <!-- 搜索区域 -->
      <a-row :gutter="16" style="margin-bottom: 16px;">
        <a-col :xs="24" :sm="12" :md="6">
          <a-form-item label="名称" :label-col-style="{ width: '60px' }" :style="{ marginBottom: 0 }">
            <a-input v-model="query.name" placeholder="名称关键词" allow-clear @press-enter="handleQuery" />
          </a-form-item>
        </a-col>
        <a-col :xs="24" :sm="12" :md="6">
          <a-form-item label="状态" :label-col-style="{ width: '60px' }" :style="{ marginBottom: 0 }">
            <a-select v-model="query.status" placeholder="全部状态" allow-clear>
              <a-option value="0">启用</a-option>
              <a-option value="1">停用</a-option>
            </a-select>
          </a-form-item>
        </a-col>
        <a-col :xs="24" :sm="12" :md="6">
          <a-space wrap>
            <a-button type="primary" @click="handleQuery">
              <template #icon><icon-search /></template>
              搜索
            </a-button>
            <a-button @click="resetQuery">
              <template #icon><icon-refresh /></template>
              重置
            </a-button>
          </a-space>
        </a-col>
      </a-row>

      <a-table
        :loading="loading"
        :data="list"
        :pagination="pagination"
        row-key="skillId"
        @page-change="handlePageChange"
        @page-size-change="handlePageSizeChange"
      >
        <template #columns>
          <a-table-column title="图标" align="center" :width="80">
            <template #cell="{ record }">
              <img v-if="record.icon" :src="fullUrl(record.icon)" class="icon-thumb" alt="图标" />
              <span v-else class="icon-empty">无</span>
            </template>
          </a-table-column>
          <a-table-column title="名称" data-index="name" align="center" ellipsis tooltip />
          <a-table-column title="能力范围" align="center" :width="200">
            <template #cell="{ record }">
              <a-space wrap>
                <a-tag v-for="cap in splitCapabilities(record.capabilities)" :key="cap" color="arcoblue">
                  {{ capabilityLabel(cap) }}
                </a-tag>
                <span v-if="!record.capabilities" class="icon-empty">-</span>
              </a-space>
            </template>
          </a-table-column>
          <a-table-column title="排序" data-index="sort" align="center" :width="80" />
          <a-table-column title="启用" align="center" :width="90">
            <template #cell="{ record }">
              <a-switch
                v-hasPermi="['skill:edit']"
                :model-value="record.status"
                checked-value="0"
                unchecked-value="1"
                :loading="statusLoadingId === record.skillId"
                @change="(val: string | number | boolean) => handleToggleStatus(record, val as string)"
              />
            </template>
          </a-table-column>
          <a-table-column title="操作" align="center" :width="140">
            <template #cell="{ record }">
              <a-space>
                <a-button type="text" size="small" @click="handleEdit(record)" v-hasPermi="['skill:edit']">编辑</a-button>
                <a-button type="text" size="small" status="danger" @click="handleRemove(record)" v-hasPermi="['skill:remove']">删除</a-button>
              </a-space>
            </template>
          </a-table-column>
        </template>
      </a-table>
    </a-card>

    <!-- 新增/编辑弹窗 -->
    <a-modal
      v-model:visible="formOpen"
      :title="formTitle"
      :width="820"
      :mask-closable="false"
      :body-style="{ maxHeight: '72vh', overflowY: 'auto', padding: '24px 28px 28px' }"
      @before-ok="submitForm"
      @cancel="formOpen = false"
    >
      <a-form ref="formRef" class="skill-form" layout="vertical" :model="form" :rules="formRules">
        <a-form-item label="Skill 名称" field="name">
          <a-input
            v-model="form.name"
            placeholder="请输入 Skill 名称"
            :max-length="64"
            show-word-limit
            allow-clear
          />
        </a-form-item>
        <a-form-item label="Skill 图标" field="icon">
          <a-upload
            :show-file-list="false"
            :auto-upload="true"
            :custom-request="handleIconUpload"
            accept="image/*"
          >
            <template #upload-button>
              <div class="media-uploader">
                <img v-if="form.icon" :src="fullUrl(form.icon)" class="media-preview" alt="图标" />
                <div v-else class="media-placeholder">
                  <icon-plus />
                  <span>上传</span>
                </div>
              </div>
            </template>
          </a-upload>
          <div class="field-help">建议上传正方形 PNG 或 JPG，列表和小程序会按方形图标展示。</div>
        </a-form-item>
        <a-form-item class="prompt-form-item" label="注入提示词" field="injectPrompt">
          <a-textarea
            v-model="form.injectPrompt"
            class="prompt-textarea"
            placeholder="请输入用户激活该 Skill 后注入给模型的完整提示词"
            :auto-size="{ minRows: 12, maxRows: 20 }"
            :max-length="12000"
            show-word-limit
            allow-clear
          />
          <div class="field-help">这里填写实际发送给大模型的指令，可使用多段文本、规则和输出格式。</div>
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue';
import { Message, Modal } from '@arco-design/web-vue';
import { IconPlus, IconSearch, IconRefresh } from '@arco-design/web-vue/es/icon';
import {
  listSkill,
  addSkill,
  updateSkill,
  removeSkill,
  toggleSkillStatus,
  Skill
} from '@/api/skill';
import { uploadAvatar } from '@/api/system';

// 后端返回相对路径(如 /profile/...)时拼接 /api 前缀以可预览;已是完整 URL 则原样返回
function fullUrl(url?: string): string {
  if (!url) return '';
  if (url.startsWith('/')) return '/api' + url;
  return url;
}

// 能力范围:取值 -> 中文标签(列表标签展示用)
const capabilityLabelMap: Record<string, string> = {
  text: '文本',
  vision: '视觉',
  image: '生图',
  video: '生视频'
};
function capabilityLabel(cap: string): string {
  return capabilityLabelMap[cap] || cap;
}
// 逗号分隔字符串 -> 数组(过滤空项)
function splitCapabilities(capabilities?: string): string[] {
  if (!capabilities) return [];
  return capabilities.split(',').filter((c) => c.trim());
}

// ---- 列表 ----
const loading = ref(false);
const list = ref<Skill[]>([]);
const query = reactive({
  name: undefined as string | undefined,
  status: undefined as string | undefined
});
const pagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0,
  showTotal: true,
  showPageSize: true,
  pageSizeOptions: ['10', '20', '50']
});

function getList() {
  loading.value = true;
  listSkill({
    name: query.name,
    status: query.status,
    pageNum: pagination.current,
    pageSize: pagination.pageSize
  }).then((res: any) => {
    list.value = res.records;
    pagination.total = res.total;
  }).finally(() => {
    loading.value = false;
  });
}

function handleQuery() {
  pagination.current = 1;
  getList();
}

function resetQuery() {
  query.name = undefined;
  query.status = undefined;
  handleQuery();
}

function handlePageChange(current: number) {
  pagination.current = current;
  getList();
}

function handlePageSizeChange(pageSize: number) {
  pagination.pageSize = pageSize;
  pagination.current = 1;
  getList();
}

// ---- 启停快捷切换 ----
const statusLoadingId = ref<number | undefined>(undefined);
function handleToggleStatus(record: Skill, val: string) {
  statusLoadingId.value = record.skillId;
  toggleSkillStatus(record.skillId!, val)
    .then(() => {
      record.status = val;
      Message.success(val === '0' ? '已启用' : '已停用');
    })
    .finally(() => {
      statusLoadingId.value = undefined;
    });
}

// ---- 新增/编辑弹窗 ----
const formOpen = ref(false);
const formRef = ref();
const formTitle = ref('新增 Skill');
const form = reactive<Skill>({
  skillId: undefined,
  name: '',
  icon: '',
  injectPrompt: ''
});

const formRules = {
  name: [{ required: true, message: '请输入 Skill 名称' }],
  injectPrompt: [{ required: true, message: '请输入注入提示词' }]
};

// 重置弹窗到新增初始态
function resetForm() {
  form.skillId = undefined;
  form.name = '';
  form.icon = '';
  form.injectPrompt = '';
}

function handleAdd() {
  resetForm();
  formTitle.value = '新增 Skill';
  formOpen.value = true;
}

function handleEdit(record: Skill) {
  resetForm();
  form.skillId = record.skillId;
  form.name = record.name;
  form.icon = record.icon;
  form.injectPrompt = record.injectPrompt;
  formTitle.value = '编辑 Skill';
  formOpen.value = true;
}

// 图标单图上传
async function handleIconUpload(option: any) {
  try {
    const file = option.fileItem.file;
    const fileUrl = await uploadAvatar(file);
    form.icon = fileUrl as unknown as string;
    Message.success('图标上传成功');
    option.onSuccess(fileUrl);
  } catch (error: any) {
    Message.error(error?.message || '图标上传失败');
    option.onError(error);
  }
}

function submitForm(done: (closed: boolean) => void) {
  formRef.value.validate((errors: any) => {
    if (errors) {
      done(false);
      return;
    }
    const payload: Skill = { ...form };
    const req = form.skillId ? updateSkill(payload) : addSkill(payload);
    req.then(() => {
      Message.success(form.skillId ? '修改成功' : '新增成功');
      getList();
      done(true);
    }).catch(() => done(false));
  });
}

function handleRemove(record: Skill) {
  Modal.confirm({
    title: '删除确认',
    content: `确认要删除 Skill "${record.name}" 吗?`,
    onOk: () => {
      removeSkill(record.skillId!).then(() => {
        Message.success('删除成功');
        getList();
      });
    }
  });
}

onMounted(() => {
  getList();
});
</script>

<style scoped>
.app-container {
  padding: 0px;
}

.icon-thumb {
  width: 40px;
  height: 40px;
  object-fit: cover;
  border-radius: 6px;
}

.icon-empty {
  color: var(--color-text-3);
  font-size: 12px;
}

.skill-form :deep(.arco-form-item) {
  margin-bottom: 24px;
}

.skill-form :deep(.arco-form-item-label-col) {
  padding-bottom: 9px;
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text-1);
  line-height: 1.4;
}

.skill-form :deep(.arco-form-item-wrapper-col) {
  width: 100%;
}

.prompt-form-item {
  margin-bottom: 0 !important;
}

.prompt-form-item :deep(.arco-form-item-content-flex) {
  flex-direction: column;
  align-items: stretch;
}

.prompt-textarea {
  width: 100%;
}

.prompt-textarea :deep(textarea) {
  line-height: 1.7;
}

.field-help {
  width: 100%;
  margin-top: 8px;
  color: var(--color-text-3);
  font-size: 12px;
  line-height: 1.6;
}

.media-uploader {
  width: 96px;
  height: 96px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px dashed var(--color-neutral-3);
  border-radius: 6px;
  cursor: pointer;
  transition: border-color 0.2s;
  background: var(--color-fill-1);
}

.media-uploader:hover {
  border-color: rgb(var(--primary-6));
}

.media-preview {
  width: 100%;
  height: 100%;
  object-fit: cover;
  border-radius: 6px;
}

.media-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: var(--color-text-3);
  font-size: 12px;
}
</style>
