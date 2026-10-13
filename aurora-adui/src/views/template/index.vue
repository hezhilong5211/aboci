<template>
  <div class="app-container">
    <a-card class="table-card" title="案例/模板">
      <template #extra>
        <a-space>
          <a-button @click="openCategoryManager" v-hasPermi="['template:category:list']">
            行业分类
          </a-button>
          <a-button @click="handleOpenFeatured" v-hasPermi="['template:edit']">
            <template #icon><icon-star /></template>
            本周精选
          </a-button>
          <a-button type="primary" @click="handleAdd" v-hasPermi="['template:add']">
            <template #icon><icon-plus /></template>
            新增模板
          </a-button>
        </a-space>
      </template>

      <!-- 搜索区域 -->
      <div class="filter-toolbar">
        <a-form-item class="filter-item" label="分类" :label-col-style="{ width: '60px' }">
          <a-select v-model="query.category" placeholder="全部分类" allow-clear>
            <a-option value="image">图片</a-option>
            <a-option value="video">视频</a-option>
          </a-select>
        </a-form-item>
        <a-form-item class="filter-item" label="行业" :label-col-style="{ width: '60px' }">
          <a-select v-model="query.industryCategoryId" placeholder="全部行业" allow-clear>
            <a-option v-for="item in categories" :key="item.categoryId" :value="item.categoryId!">
              {{ item.name }}{{ item.status === '1' ? '（已停用）' : '' }}
            </a-option>
          </a-select>
        </a-form-item>
        <a-form-item class="filter-item" label="状态" :label-col-style="{ width: '60px' }">
          <a-select v-model="query.status" placeholder="全部状态" allow-clear>
            <a-option value="0">上架</a-option>
            <a-option value="1">下架</a-option>
          </a-select>
        </a-form-item>
        <a-space class="filter-actions">
          <a-button type="primary" @click="handleQuery">
            <template #icon><icon-search /></template>
            搜索
          </a-button>
          <a-button @click="resetQuery">
            <template #icon><icon-refresh /></template>
            重置
          </a-button>
        </a-space>
      </div>

      <a-table
        :loading="loading"
        :data="list"
        :pagination="pagination"
        row-key="templateId"
        @page-change="handlePageChange"
        @page-size-change="handlePageSizeChange"
      >
        <template #columns>
          <a-table-column title="封面" align="center" :width="90">
            <template #cell="{ record }">
              <img v-if="record.coverUrl" :src="fullUrl(record.coverUrl)" class="cover-thumb" alt="封面" />
              <span v-else class="cover-empty">无</span>
            </template>
          </a-table-column>
          <a-table-column title="标题" data-index="title" align="center" ellipsis tooltip />
          <a-table-column title="分类" align="center" :width="90">
            <template #cell="{ record }">
              <a-tag :color="record.category === 'video' ? 'arcoblue' : 'green'">
                {{ record.category === 'video' ? '视频' : '图片' }}
              </a-tag>
            </template>
          </a-table-column>
          <a-table-column title="行业" data-index="industryCategoryName" align="center" :width="100" />
          <a-table-column title="使用次数" data-index="useCount" align="center" :width="100" />
          <a-table-column title="排序" data-index="sort" align="center" :width="80" />
          <a-table-column title="上架" align="center" :width="90">
            <template #cell="{ record }">
              <a-switch
                :model-value="record.status"
                checked-value="0"
                unchecked-value="1"
                :loading="statusLoadingId === record.templateId"
                @change="(val: string | number | boolean) => handleToggleStatus(record, val as string)"
                v-hasPermi="['template:edit']"
              />
            </template>
          </a-table-column>
          <a-table-column title="操作" align="center" :width="140">
            <template #cell="{ record }">
              <a-space>
                <a-button type="text" size="small" @click="handleEdit(record)" v-hasPermi="['template:edit']">编辑</a-button>
                <a-button type="text" size="small" status="danger" @click="handleRemove(record)" v-hasPermi="['template:remove']">删除</a-button>
              </a-space>
            </template>
          </a-table-column>
        </template>
      </a-table>
    </a-card>

    <!-- 新增/编辑弹窗(表单较长,弹窗体可滚动) -->
    <a-modal
      v-model:visible="formOpen"
      :title="formTitle"
      :width="680"
      :mask-closable="false"
      :body-style="{ maxHeight: '68vh', overflowY: 'auto' }"
      @before-ok="submitForm"
      @cancel="formOpen = false"
    >
      <a-form ref="formRef" :model="form" :rules="formRules"
              :label-col-props="{ span: 6 }" :wrapper-col-props="{ span: 18 }">
        <a-form-item label="分类" field="category">
          <a-radio-group v-model="form.category" type="button">
            <a-radio value="image">图片</a-radio>
            <a-radio value="video">视频</a-radio>
          </a-radio-group>
        </a-form-item>
        <a-form-item label="行业分类" field="industryCategoryId">
          <a-select v-model="form.industryCategoryId" placeholder="请选择行业分类">
            <a-option
              v-for="item in categories"
              :key="item.categoryId"
              :value="item.categoryId!"
              :disabled="item.status === '1' && item.categoryId !== form.industryCategoryId"
            >
              {{ item.name }}{{ item.status === '1' ? '（已停用）' : '' }}
            </a-option>
          </a-select>
        </a-form-item>
        <a-form-item label="标题" field="title">
          <a-input v-model="form.title" placeholder="请输入标题" allow-clear />
        </a-form-item>
        <a-form-item label="封面" field="coverUrl">
          <a-upload
            :show-file-list="false"
            :auto-upload="true"
            :custom-request="handleCoverUpload"
            accept="image/*"
          >
            <template #upload-button>
              <div class="media-uploader">
                <img v-if="form.coverUrl" :src="fullUrl(form.coverUrl)" class="media-preview" alt="封面" />
                <div v-else class="media-placeholder">
                  <icon-plus />
                  <span>上传</span>
                </div>
              </div>
            </template>
          </a-upload>
        </a-form-item>
        <a-form-item label="提示词" field="prompt">
          <a-textarea v-model="form.prompt" placeholder="请输入提示词" :auto-size="{ minRows: 3, maxRows: 6 }" />
        </a-form-item>

        <!-- 参数预设:按分类条件渲染,双排布局(每行两项)更密集 -->
        <a-divider orientation="left" :margin="12">参数预设</a-divider>
        <template v-if="form.category === 'image'">
          <a-row :gutter="16">
            <a-col :span="12">
              <a-form-item label="模式" :label-col-props="{ span: 6 }" :wrapper-col-props="{ span: 18 }">
                <a-select v-model="imageParams.mode" placeholder="请选择模式">
                  <a-option value="text2img">文生图</a-option>
                  <a-option value="img2img">图生图</a-option>
                </a-select>
              </a-form-item>
            </a-col>
            <a-col :span="12">
              <a-form-item label="画幅比例" :label-col-props="{ span: 6 }" :wrapper-col-props="{ span: 18 }">
                <a-select v-model="imageParams.aspectRatio" placeholder="请选择画幅比例">
                  <a-option v-for="ratio in aspectRatioOptions" :key="ratio" :value="ratio">{{ ratio }}</a-option>
                </a-select>
              </a-form-item>
            </a-col>
          </a-row>
          <!-- 图生图参考图:仅图片分类 + 图生图模式时显示,1~4 张 -->
          <a-form-item v-if="imageParams.mode === 'img2img'" label="参考图">
            <div class="media-list">
              <div v-for="(url, idx) in referenceImages" :key="idx" class="media-item">
                <img :src="fullUrl(url)" class="media-preview" alt="参考图" />
                <icon-close-circle-fill class="media-remove" @click="removeReferenceImage(idx)" />
              </div>
              <a-upload
                v-if="referenceImages.length < 4"
                :show-file-list="false"
                :auto-upload="true"
                :custom-request="handleReferenceImageUpload"
                accept="image/*"
              >
                <template #upload-button>
                  <div class="media-uploader">
                    <div class="media-placeholder">
                      <icon-plus />
                      <span>上传</span>
                    </div>
                  </div>
                </template>
              </a-upload>
            </div>
          </a-form-item>
        </template>
        <template v-else>
          <a-row :gutter="16">
            <a-col :span="12">
              <a-form-item label="模式" :label-col-props="{ span: 6 }" :wrapper-col-props="{ span: 18 }">
                <a-select v-model="videoParams.mode" placeholder="请选择模式">
                  <a-option value="text2video">文生视频</a-option>
                  <a-option value="img2video">图生视频</a-option>
                  <a-option value="keyframe">首尾帧</a-option>
                </a-select>
              </a-form-item>
            </a-col>
            <a-col :span="12">
              <a-form-item label="画幅" :label-col-props="{ span: 6 }" :wrapper-col-props="{ span: 18 }">
                <a-select v-model="videoParams.aspectRatio" placeholder="请选择画幅">
                  <a-option v-for="ratio in aspectRatioOptions" :key="ratio" :value="ratio">{{ ratio }}</a-option>
                </a-select>
              </a-form-item>
            </a-col>
            <a-col :span="12">
              <a-form-item label="生成模式" :label-col-props="{ span: 6 }" :wrapper-col-props="{ span: 18 }">
                <a-select v-model="videoParams.genMode" placeholder="请选择生成模式">
                  <a-option value="标准">标准</a-option>
                  <a-option value="专业">专业</a-option>
                </a-select>
              </a-form-item>
            </a-col>
            <a-col :span="12">
              <a-form-item label="时长" :label-col-props="{ span: 6 }" :wrapper-col-props="{ span: 18 }">
                <a-select v-model="videoParams.duration" placeholder="请选择时长">
                  <a-option value="5s">5s</a-option>
                  <a-option value="10s">10s</a-option>
                </a-select>
              </a-form-item>
            </a-col>
          </a-row>
        </template>

        <a-form-item label="排序" field="sort">
          <a-input-number v-model="form.sort" placeholder="数值越小越靠前" :min="0" :step="1" style="width: 100%;" />
        </a-form-item>
        <a-form-item label="上架" field="status">
          <a-switch v-model="form.status" checked-value="0" unchecked-value="1">
            <template #checked>上架</template>
            <template #unchecked>下架</template>
          </a-switch>
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 本周精选管理:从上架的图片案例中最多勾选 5 个,整体替换 -->
    <a-modal
      v-model:visible="featuredOpen"
      title="本周精选"
      :width="640"
      :mask-closable="false"
      :ok-loading="featuredSaving"
      :body-style="{ maxHeight: '60vh', overflowY: 'auto' }"
      @before-ok="submitFeatured"
      @cancel="featuredOpen = false"
    >
      <a-spin :loading="featuredLoading" style="width: 100%;">
        <div class="featured-tip">
          最多选择 5 个上架图片案例(已选 {{ featuredSelected.length }}/5)
        </div>
        <a-empty v-if="!featuredLoading && featuredList.length === 0" description="暂无可选的上架图片案例" />
        <a-checkbox-group v-else v-model="featuredSelected" class="featured-grid">
          <a-checkbox
            v-for="item in featuredList"
            :key="item.templateId"
            :value="item.templateId"
            :disabled="featuredSelected.length >= 5 && !featuredSelected.includes(item.templateId!)"
            class="featured-card"
          >
            <div class="featured-card-body">
              <img v-if="item.coverUrl" :src="fullUrl(item.coverUrl)" class="featured-cover" alt="封面" />
              <span v-else class="featured-cover-empty">无封面</span>
              <span class="featured-title">{{ item.title }}</span>
            </div>
          </a-checkbox>
        </a-checkbox-group>
      </a-spin>
    </a-modal>

    <a-modal
      v-model:visible="categoryManagerOpen"
      title="行业分类"
      :width="680"
      :footer="false"
    >
      <div class="category-toolbar">
        <a-button type="primary" @click="openCategoryForm()" v-hasPermi="['template:category:add']">
          <template #icon><icon-plus /></template>
          新增分类
        </a-button>
      </div>
      <a-table :loading="categoryLoading" :data="categories" :pagination="false" row-key="categoryId">
        <template #columns>
          <a-table-column title="名称" data-index="name" />
          <a-table-column title="排序" data-index="sort" align="center" :width="90" />
          <a-table-column title="状态" align="center" :width="100">
            <template #cell="{ record }">
              <a-tag :color="record.status === '0' ? 'green' : 'gray'">
                {{ record.status === '0' ? '启用' : '停用' }}
              </a-tag>
            </template>
          </a-table-column>
          <a-table-column title="操作" align="center" :width="150">
            <template #cell="{ record }">
              <a-space>
                <a-button type="text" size="small" @click="openCategoryForm(record)" v-hasPermi="['template:category:edit']">编辑</a-button>
                <a-button type="text" size="small" status="danger" @click="deleteCategory(record)" v-hasPermi="['template:category:remove']">删除</a-button>
              </a-space>
            </template>
          </a-table-column>
        </template>
      </a-table>
    </a-modal>

    <a-modal
      v-model:visible="categoryFormOpen"
      :title="categoryForm.categoryId ? '编辑行业分类' : '新增行业分类'"
      :mask-closable="false"
      @before-ok="submitCategoryForm"
    >
      <a-form ref="categoryFormRef" :model="categoryForm" :rules="categoryFormRules">
        <a-form-item label="名称" field="name">
          <a-input v-model="categoryForm.name" :max-length="32" placeholder="例如：美发、美甲" />
        </a-form-item>
        <a-form-item label="排序" field="sort">
          <a-input-number v-model="categoryForm.sort" :min="0" style="width: 100%;" />
        </a-form-item>
        <a-form-item label="状态" field="status">
          <a-switch v-model="categoryForm.status" checked-value="0" unchecked-value="1">
            <template #checked>启用</template>
            <template #unchecked>停用</template>
          </a-switch>
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue';
import { Message, Modal } from '@arco-design/web-vue';
import {
  IconPlus,
  IconSearch,
  IconRefresh,
  IconStar,
  IconCloseCircleFill
} from '@arco-design/web-vue/es/icon';
import {
  listTemplate,
  addTemplate,
  updateTemplate,
  removeTemplate,
  toggleTemplateStatus,
  getDefaultImageRatios,
  setTemplateFeatured,
  listTemplateCategory,
  addTemplateCategory,
  updateTemplateCategory,
  removeTemplateCategory,
  Template,
  TemplateCategory
} from '@/api/template';
import { uploadAvatar } from '@/api/system';

// 后端返回相对路径(如 /profile/...)时拼接 /api 前缀以可预览;已是完整 URL 则原样返回
function fullUrl(url?: string): string {
  if (!url) return '';
  if (url.startsWith('/')) return '/api' + url;
  return url;
}

// ---- 列表 ----
const loading = ref(false);
const list = ref<Template[]>([]);
const query = reactive({
  category: undefined as string | undefined,
  industryCategoryId: undefined as number | undefined,
  status: undefined as string | undefined,
  keyword: undefined as string | undefined
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
  listTemplate({
    category: query.category,
    industryCategoryId: query.industryCategoryId,
    status: query.status,
    keyword: query.keyword,
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
  query.category = undefined;
  query.industryCategoryId = undefined;
  query.status = undefined;
  query.keyword = undefined;
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

// ---- 上下架快捷切换 ----
const statusLoadingId = ref<number | undefined>(undefined);
function handleToggleStatus(record: Template, val: string) {
  statusLoadingId.value = record.templateId;
  toggleTemplateStatus(record.templateId!, val)
    .then(() => {
      record.status = val;
      Message.success(val === '0' ? '已上架' : '已下架');
    })
    .finally(() => {
      statusLoadingId.value = undefined;
    });
}

// ---- 新增/编辑弹窗 ----
const formOpen = ref(false);
const formRef = ref();
const formTitle = ref('新增模板');
const form = reactive<Template>({
  templateId: undefined,
  category: 'image',
  industryCategoryId: undefined,
  title: '',
  coverUrl: '',
  prompt: '',
  sort: 0,
  status: '0'
});
// 参数预设:按分类分别维护,提交时按当前分类组装
const imageParams = reactive({
  mode: 'text2img',
  aspectRatio: '1:1'
});
const videoParams = reactive({
  mode: 'text2video',
  aspectRatio: '16:9',
  genMode: '标准',
  duration: '5s'
});
// 图生图参考图:上传得到的 /profile URL 数组(1~4 张)
const referenceImages = ref<string[]>([]);
// 画幅比例可选项:抽屉打开时从默认图像模型动态拉取,失败/空时降级为默认集合
const DEFAULT_ASPECT_RATIOS = ['1:1', '3:4', '4:3', '16:9', '9:16'];
const aspectRatioOptions = ref<string[]>([...DEFAULT_ASPECT_RATIOS]);

// 加载默认图像模型支持的画幅比例,解析逗号分隔串为数组;失败或空时降级为默认集合
function loadAspectRatioOptions() {
  getDefaultImageRatios()
    .then((data) => {
      const raw = data?.supportedAspectRatios;
      const parsed = raw
        ? raw.split(',').map((s) => s.trim()).filter((s) => s)
        : [];
      aspectRatioOptions.value = parsed.length ? parsed : [...DEFAULT_ASPECT_RATIOS];
    })
    .catch(() => {
      aspectRatioOptions.value = [...DEFAULT_ASPECT_RATIOS];
    });
}

const formRules = {
  category: [{ required: true, message: '请选择分类' }],
  industryCategoryId: [{ required: true, message: '请选择行业分类' }],
  title: [{ required: true, message: '请输入标题' }],
  prompt: [{ required: true, message: '请输入提示词' }],
  sort: [{ required: true, message: '请输入排序' }]
};

// 重置弹窗到新增初始态
function resetForm() {
  form.templateId = undefined;
  form.category = 'image';
  form.industryCategoryId = categories.value.find((item) => item.status === '0')?.categoryId;
  form.title = '';
  form.coverUrl = '';
  form.prompt = '';
  form.sort = 0;
  form.status = '0';
  imageParams.mode = 'text2img';
  imageParams.aspectRatio = '1:1';
  videoParams.mode = 'text2video';
  videoParams.aspectRatio = '16:9';
  videoParams.genMode = '标准';
  videoParams.duration = '5s';
  referenceImages.value = [];
}

function handleAdd() {
  resetForm();
  loadAspectRatioOptions();
  formTitle.value = '新增模板';
  formOpen.value = true;
}

function handleEdit(record: Template) {
  resetForm();
  loadAspectRatioOptions();
  form.templateId = record.templateId;
  form.category = record.category || 'image';
  form.industryCategoryId = record.industryCategoryId;
  form.title = record.title;
  form.coverUrl = record.coverUrl;
  form.prompt = record.prompt;
  form.sort = record.sort ?? 0;
  form.status = record.status || '0';
  // 回填参数预设
  if (record.params) {
    try {
      const p = JSON.parse(record.params);
      if (form.category === 'image') {
        imageParams.mode = p.mode ?? imageParams.mode;
        imageParams.aspectRatio = p.aspectRatio ?? imageParams.aspectRatio;
      } else {
        videoParams.mode = p.mode ?? videoParams.mode;
        videoParams.aspectRatio = p.aspectRatio ?? videoParams.aspectRatio;
        videoParams.genMode = p.genMode ?? videoParams.genMode;
        videoParams.duration = p.duration ?? videoParams.duration;
      }
    } catch {
      // 历史脏数据解析失败时保留默认值
    }
  }
  // 回填图生图参考图(仅图片分类有意义)
  if (record.referenceImages) {
    try {
      const refs = JSON.parse(record.referenceImages);
      referenceImages.value = Array.isArray(refs) ? refs : [];
    } catch {
      // 历史脏数据解析失败时置空
    }
  }
  formTitle.value = '编辑模板';
  formOpen.value = true;
}

// 封面单图上传
async function handleCoverUpload(option: any) {
  try {
    const file = option.fileItem.file;
    const fileUrl = await uploadAvatar(file);
    form.coverUrl = fileUrl as unknown as string;
    Message.success('封面上传成功');
    option.onSuccess(fileUrl);
  } catch (error: any) {
    Message.error(error?.message || '封面上传失败');
    option.onError(error);
  }
}

// 参考图上传(追加,最多 4 张),复用头像上传得到 /profile URL
async function handleReferenceImageUpload(option: any) {
  try {
    const file = option.fileItem.file;
    const fileUrl = await uploadAvatar(file);
    referenceImages.value.push(fileUrl as unknown as string);
    Message.success('参考图上传成功');
    option.onSuccess(fileUrl);
  } catch (error: any) {
    Message.error(error?.message || '参考图上传失败');
    option.onError(error);
  }
}

function removeReferenceImage(idx: number) {
  referenceImages.value.splice(idx, 1);
}

function submitForm(done: (closed: boolean) => void) {
  formRef.value.validate((errors: any) => {
    if (errors) {
      done(false);
      return;
    }
    // 按当前分类组装 params,序列化为 JSON 字符串提交(图片参数不含 count)
    const params = form.category === 'image'
      ? { mode: imageParams.mode, aspectRatio: imageParams.aspectRatio }
      : {
          mode: videoParams.mode,
          aspectRatio: videoParams.aspectRatio,
          genMode: videoParams.genMode,
          duration: videoParams.duration
        };
    // 参考图仅图生图模式有意义,其余情况置空
    const refs = form.category === 'image' && imageParams.mode === 'img2img'
      ? referenceImages.value
      : [];
    // 效果媒体与标签录入已下线,提交统一置空
    const payload: Template = {
      ...form,
      params: JSON.stringify(params),
      resultMedia: '',
      tags: '',
      referenceImages: JSON.stringify(refs)
    };
    const req = form.templateId ? updateTemplate(payload) : addTemplate(payload);
    req.then(() => {
      Message.success(form.templateId ? '修改成功' : '新增成功');
      getList();
      done(true);
    }).catch(() => done(false));
  });
}

// ---- 本周精选管理 ----
const featuredOpen = ref(false);
const featuredLoading = ref(false);
const featuredSaving = ref(false);
// 可选案例:上架的图片案例
const featuredList = ref<Template[]>([]);
// 已勾选的模板 id 集合(默认回填当前已是精选的)
const featuredSelected = ref<number[]>([]);

function handleOpenFeatured() {
  featuredOpen.value = true;
  featuredSelected.value = [];
  featuredLoading.value = true;
  listTemplate({
    category: 'image',
    status: '0',
    pageNum: 1,
    pageSize: 100
  }).then((res: any) => {
    featuredList.value = res.records || [];
    // 默认勾选当前已是精选的
    featuredSelected.value = featuredList.value
      .filter((item) => item.isFeatured === 1)
      .map((item) => item.templateId!)
      .filter((id) => id !== undefined && id !== null);
  }).finally(() => {
    featuredLoading.value = false;
  });
}

function submitFeatured(done: (closed: boolean) => void) {
  if (featuredSelected.value.length > 5) {
    Message.warning('本周精选最多选择 5 个');
    done(false);
    return;
  }
  featuredSaving.value = true;
  setTemplateFeatured(featuredSelected.value)
    .then(() => {
      Message.success('精选已保存');
      getList();
      done(true);
    })
    .catch(() => done(false))
    .finally(() => {
      featuredSaving.value = false;
    });
}

function handleRemove(record: Template) {
  Modal.confirm({
    title: '删除确认',
    content: `确认要删除模板 "${record.title}" 吗?`,
    onOk: () => {
      removeTemplate(record.templateId!).then(() => {
        Message.success('删除成功');
        getList();
      });
    }
  });
}

// ---- 行业分类管理 ----
const categories = ref<TemplateCategory[]>([]);
const categoryLoading = ref(false);
const categoryManagerOpen = ref(false);
const categoryFormOpen = ref(false);
const categoryFormRef = ref();
const categoryForm = reactive<TemplateCategory>({
  categoryId: undefined,
  name: '',
  sort: 0,
  status: '0'
});
const categoryFormRules = {
  name: [{ required: true, message: '请输入分类名称' }],
  sort: [{ required: true, message: '请输入排序' }]
};

async function loadCategories() {
  categoryLoading.value = true;
  try {
    const data = await listTemplateCategory();
    categories.value = Array.isArray(data) ? data : [];
  } finally {
    categoryLoading.value = false;
  }
}

async function openCategoryManager() {
  categoryManagerOpen.value = true;
  await loadCategories();
}

function openCategoryForm(record?: TemplateCategory) {
  categoryForm.categoryId = record?.categoryId;
  categoryForm.name = record?.name || '';
  categoryForm.sort = record?.sort ?? 0;
  categoryForm.status = record?.status || '0';
  categoryFormOpen.value = true;
}

function submitCategoryForm(done: (closed: boolean) => void) {
  categoryFormRef.value.validate((errors: any) => {
    if (errors) {
      done(false);
      return;
    }
    const request = categoryForm.categoryId
      ? updateTemplateCategory({ ...categoryForm })
      : addTemplateCategory({ ...categoryForm });
    request.then(async () => {
      Message.success(categoryForm.categoryId ? '分类已修改' : '分类已新增');
      await loadCategories();
      getList();
      done(true);
    }).catch(() => done(false));
  });
}

function deleteCategory(record: TemplateCategory) {
  Modal.confirm({
    title: '删除确认',
    content: `确认删除行业分类“${record.name}”吗？`,
    onOk: () => removeTemplateCategory(record.categoryId!).then(async () => {
      Message.success('分类已删除');
      await loadCategories();
      getList();
    })
  });
}

onMounted(async () => {
  await loadCategories();
  getList();
});
</script>

<style scoped>
.app-container {
  padding: 0px;
}

.filter-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px 16px;
  margin-bottom: 16px;
}

.filter-item {
  flex: 0 1 260px;
  width: 260px;
  margin-bottom: 0;
}

.filter-actions {
  flex: 0 0 auto;
}

@media (max-width: 768px) {
  .filter-item {
    flex: 1 1 220px;
    width: auto;
  }
}

.cover-thumb {
  width: 48px;
  height: 48px;
  object-fit: cover;
  border-radius: 4px;
}

.cover-empty {
  color: var(--color-text-3);
  font-size: 12px;
}

.category-toolbar {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 12px;
}

.media-uploader {
  width: 80px;
  height: 80px;
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

.media-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.media-item {
  position: relative;
  width: 80px;
  height: 80px;
}

.media-item .media-preview {
  width: 80px;
  height: 80px;
}

.media-remove {
  position: absolute;
  top: -8px;
  right: -8px;
  font-size: 18px;
  color: var(--color-text-3);
  background: var(--color-bg-2);
  border-radius: 50%;
  cursor: pointer;
}

/* 本周精选对话框 */
.featured-tip {
  margin-bottom: 12px;
  color: var(--color-text-3);
  font-size: 13px;
}

.featured-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
}

.featured-card {
  width: 100%;
  margin: 0;
  padding: 8px;
  border: 1px solid var(--color-neutral-3);
  border-radius: 6px;
}

.featured-card-body {
  display: flex;
  align-items: center;
  gap: 8px;
  overflow: hidden;
}

.featured-cover {
  width: 40px;
  height: 40px;
  object-fit: cover;
  border-radius: 4px;
  flex-shrink: 0;
}

.featured-cover-empty {
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  color: var(--color-text-3);
  background: var(--color-fill-1);
  border-radius: 4px;
  flex-shrink: 0;
}

.featured-title {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
