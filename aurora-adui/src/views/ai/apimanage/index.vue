<template>
  <div class="app-container">
    <!-- 列表 -->
    <a-card class="table-card" title="模型列表">
      <!-- 搜索区域 -->
      <a-row :gutter="16" style="margin-bottom: 16px;">
        <a-col :xs="24" :sm="12" :md="8" :lg="8" :xl="6" style="margin-bottom: 8px;">
          <a-form-item label="模型名称" :label-col-style="{ width: '80px' }" :style="{ marginBottom: 0 }">
            <a-input
              v-model="queryParams.modelName"
              placeholder="请输入模型名称"
              allow-clear
              @press-enter="handleQuery"
            />
          </a-form-item>
        </a-col>
        <a-col :xs="24" :sm="12" :md="16" :lg="16" :xl="18" style="margin-bottom: 8px;">
          <a-space wrap>
            <a-button type="primary" @click="handleQuery">
              <template #icon>
                <icon-search />
              </template>
              搜索
            </a-button>
            <a-button @click="resetQuery">
              <template #icon>
                <icon-refresh />
              </template>
              重置
            </a-button>
          </a-space>
        </a-col>
      </a-row>

      <!-- 操作按钮 -->
      <div style="margin-bottom: 16px;">
        <a-space>
          <a-button type="primary" @click="handleAdd" v-hasPermi="['ai:model:add']">
            <template #icon>
              <icon-plus />
            </template>
            新增
          </a-button>
          <a-button
            type="primary"
            status="danger"
            :disabled="selectedRowKeys.length === 0"
            @click="handleBatchDelete"
            v-hasPermi="['ai:model:remove']"
          >
            <template #icon>
              <icon-delete />
            </template>
            批量删除
          </a-button>
        </a-space>
      </div>

      <!-- 标签页 -->
      <a-tabs v-model:active-key="activeTab" @change="handleTabChange" style="margin-bottom: 16px">
        <a-tab-pane key="all" title="全部模型">
          <template #title>全部模型</template>
        </a-tab-pane>
        <a-tab-pane key="chat" title="对话模型">
          <template #title>对话模型</template>
        </a-tab-pane>
        <a-tab-pane key="image" title="图像模型">
          <template #title>图像模型</template>
        </a-tab-pane>
        <a-tab-pane key="video" title="视频模型">
          <template #title>视频模型</template>
        </a-tab-pane>
      </a-tabs>

      <a-table
        :loading="loading"
        :data="modelList"
        :row-selection="rowSelection"
        :pagination="pagination"
        row-key="id"
        @page-change="handlePageChange"
        @page-size-change="handlePageSizeChange"
        v-model:selected-keys="selectedRowKeys"
      >
        <template #columns>
          <a-table-column title="名称" data-index="modelName" align="center" ellipsis tooltip />
          <a-table-column title="小程序档位" data-index="displayTier" align="center" :width="110" />
          <a-table-column title="部署ID" data-index="modelDeployment" align="center" ellipsis tooltip :width="280" />
          <a-table-column title="Base URL" data-index="baseUrl" align="center" ellipsis tooltip />
          <a-table-column title="状态" data-index="isActive" align="center">
            <template #cell="{ record }">
              <a-switch
                v-model="record.isActive"
                :checked-value="1"
                :unchecked-value="0"
                @change="handleStatusChange(record)"
                v-hasPermi="['ai:model:edit']"
              />
            </template>
          </a-table-column>
          <a-table-column title="默认" align="center" :width="90">
            <template #cell="{ record }">
              <a-switch
                v-if="record.modelType === 'image' || record.modelType === 'chat'"
                :model-value="record.isDefault === 1"
                :disabled="record.isDefault === 1"
                @change="(val) => handleSetDefault(record, val)"
                v-hasPermi="['ai:model:edit']"
              />
              <span v-else style="color:#c9cdd4">-</span>
            </template>
          </a-table-column>
          <a-table-column title="排序" data-index="sortOrder" align="center" />
          <a-table-column title="创建时间" data-index="createTime" align="center" :width="180">
             <template #cell="{ record }">
              <span>{{ parseTime(record.createTime) }}</span>
            </template>
          </a-table-column>
          <a-table-column title="操作" align="center" :width="300" fixed="right">
            <template #cell="{ record }">
              <a-space :size="10">
                <a-button type="text" size="small" @click="handleTest(record)" v-hasPermi="['ai:model:query']">
                  <template #icon><icon-play-circle /></template>
                  测试
                </a-button>
                <a-button type="text" size="small" @click="handleUpdate(record)" v-hasPermi="['ai:model:edit']">
                  <template #icon><icon-edit /></template>
                  编辑
                </a-button>
                <a-button
                  type="text"
                  size="small"
                  status="danger"
                  @click="handleDelete(record)"
                  v-hasPermi="['ai:model:remove']"
                >
                  <template #icon><icon-delete /></template>
                  删除
                </a-button>
              </a-space>
            </template>
          </a-table-column>
        </template>
      </a-table>
    </a-card>

    <!-- Add/Edit Dialog -->
    <a-modal
      v-model:visible="open"
      :title="title"
      :width="760"
      @ok="submitForm"
      @cancel="cancel"
    >
      <!-- 两列栅格布局:短字段两列、URL/密钥/画质画幅等长字段整行;a-col 流式排列对条件字段友好 -->
      <a-form ref="formRef" :model="form" :rules="rules" layout="horizontal" :label-col-props="{span: 8}" :wrapper-col-props="{span: 16}">
        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item label="模型名称" field="modelName">
              <a-input v-model="form.modelName" placeholder="例如: DeepSeek V3" />
            </a-form-item>
          </a-col>
          <a-col :span="12" v-if="form.modelType === 'chat' || form.modelType === 'image'">
            <a-form-item label="小程序档位" field="displayTier" extra="用户仅看到此档位，真实模型名称只在后台显示">
              <a-select v-model="form.displayTier" placeholder="请选择公开档位">
                <a-option value="轻">轻</a-option>
                <a-option value="中">中</a-option>
                <a-option value="高">高</a-option>
              </a-select>
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="提供商" field="provider">
              <a-select v-model="form.provider" placeholder="请选择提供商" @change="handleProviderChange">
                <a-option v-for="dict in filteredProviderOptions" :key="dict.dictValue" :value="dict.dictValue">
                  {{ dict.dictLabel }}
                </a-option>
              </a-select>
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="模型类型" field="modelType">
              <a-select v-model="form.modelType" placeholder="请选择模型类型" @change="handleTypeChange">
                <a-option v-for="dict in filteredTypeOptions" :key="dict.dictValue" :value="dict.dictValue">
                  {{ dict.dictLabel }}
                </a-option>
              </a-select>
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="部署/模型ID" field="modelDeployment">
              <a-input v-model="form.modelDeployment" placeholder="例如: deepseek-chat" />
            </a-form-item>
          </a-col>
          <a-col :span="12" v-if="form.modelType === 'chat'">
            <a-form-item label="接口协议" field="apiProtocol">
              <a-select v-model="form.apiProtocol" placeholder="请选择接口协议" @change="handleApiProtocolChange">
                <a-option value="chat_completions">Chat Completions</a-option>
                <a-option value="responses">Responses</a-option>
              </a-select>
            </a-form-item>
          </a-col>
          <a-col :span="24">
            <a-form-item label="Base URL" field="baseUrl" :label-col-props="{span: 4}" :wrapper-col-props="{span: 20}">
              <a-input v-model="form.baseUrl" placeholder="例如: https://api.deepseek.com" />
            </a-form-item>
          </a-col>
          <a-col :span="24">
            <a-form-item label="API Key" field="apiKey" :label-col-props="{span: 4}" :wrapper-col-props="{span: 20}">
              <a-input-password v-model="form.apiKey" placeholder="sk-..." allow-clear />
            </a-form-item>
          </a-col>
          <!-- SecretKey:仅可灵等双密钥模型需要 -->
          <a-col :span="24" v-if="form.provider === 'kling'">
            <a-form-item label="SecretKey" field="secretKey" :label-col-props="{span: 4}" :wrapper-col-props="{span: 20}">
              <a-input-password v-model="form.secretKey" placeholder="请输入 SecretKey" allow-clear />
            </a-form-item>
          </a-col>
          <a-col :span="24" v-if="form.modelType === 'image' && form.provider === 'luxai'">
            <a-form-item label="生成质量" extra="固定质量用于该模型的全部生成任务；豆价应覆盖此质量的渠道成本。" :label-col-props="{span: 4}" :wrapper-col-props="{span: 20}">
              <a-select v-model="form.imageQuality">
                <a-option v-for="quality in ['auto', 'low', 'medium', 'high', 'xhigh', 'max']" :key="quality" :value="quality">{{ quality }}</a-option>
              </a-select>
            </a-form-item>
          </a-col>
          <a-col :span="24" v-if="form.modelType === 'image'">
            <a-form-item label="参考图能力" :label-col-props="{span: 4}" :wrapper-col-props="{span: 20}">
              <span>{{ ['yunwu', 'luxai'].includes(form.provider) ? '支持有序参考图，最多 9 张' : '仅文生图' }}</span>
            </a-form-item>
          </a-col>
          <!-- 图像模型专属:支持画质/画幅多选 + 默认分辨率 -->
          <a-col :span="24" v-if="form.modelType === 'image'">
            <a-form-item label="支持画质" :label-col-props="{span: 4}" :wrapper-col-props="{span: 20}">
              <a-checkbox-group v-model="resolutionList">
                <a-checkbox v-for="item in RESOLUTION_OPTIONS" :key="item" :value="item">{{ item }}</a-checkbox>
              </a-checkbox-group>
            </a-form-item>
          </a-col>
          <a-col :span="24" v-if="form.modelType === 'image'">
            <a-form-item label="支持画幅比例" :label-col-props="{span: 4}" :wrapper-col-props="{span: 20}">
              <a-checkbox-group v-model="ratioList">
                <a-checkbox v-for="item in ASPECT_RATIO_OPTIONS" :key="item" :value="item">{{ item }}</a-checkbox>
              </a-checkbox-group>
            </a-form-item>
          </a-col>
          <a-col :span="12" v-if="form.modelType === 'image'">
            <a-form-item label="默认分辨率">
              <a-select v-model="form.defaultResolution" placeholder="从已勾选画质中选(可空)" allow-clear>
                <a-option v-for="item in resolutionList" :key="item" :value="item">{{ item }}</a-option>
              </a-select>
            </a-form-item>
          </a-col>
          <!-- 温度/最大Token 仅文本类模型有意义,图像/视频模型隐藏 -->
          <a-col :span="12" v-if="form.modelType !== 'image' && form.modelType !== 'video'">
            <a-form-item label="温度" field="temperature">
              <a-input-number v-model="form.temperature" :precision="1" :step="0.1" :max="2.0" :min="0.0" style="width: 100%" />
            </a-form-item>
          </a-col>
          <a-col :span="12" v-if="form.modelType !== 'image' && form.modelType !== 'video'">
            <a-form-item
              label="最大输出Token"
              field="maxTokens"
              extra="仅限制单次输出；供应商总上下文上限未知。近期原文与早期摘要、已确认需求共同组成对话记忆。"
            >
              <a-input-number v-model="form.maxTokens" :step="100" :min="1" style="width: 100%" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="排序" field="sortOrder">
              <a-input-number v-model="form.sortOrder" :min="0" style="width: 100%" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="状态" field="isActive">
              <a-radio-group v-model="form.isActive">
                <a-radio :value="1">启用</a-radio>
                <a-radio :value="0">禁用</a-radio>
              </a-radio-group>
            </a-form-item>
          </a-col>
          <!-- 计费单价(豆子):按模型类型显隐;文本按每百万token,图片按张,视频按秒 -->
          <!-- 文本类(chat/vision):每百万token输入/输出;DeepSeek 与 Responses 模型可配置缓存命中价 -->
          <a-col :span="12" v-if="form.modelType === 'chat' || form.modelType === 'vision'">
            <a-form-item label="输入(豆/百万token)">
              <a-input-number v-model="form.beanPerMtokenInput" :min="0" :precision="0" :step="1" style="width: 100%" />
            </a-form-item>
          </a-col>
          <a-col :span="12" v-if="showCachedInputPrice">
            <a-form-item label="输入·缓存命中(豆/百万token)">
              <a-input-number v-model="form.beanPerMtokenInputCached" :min="0" :precision="0" :step="1" style="width: 100%" />
            </a-form-item>
          </a-col>
          <a-col :span="12" v-if="form.modelType === 'chat' || form.modelType === 'vision'">
            <a-form-item label="输出(豆/百万token)">
              <a-input-number v-model="form.beanPerMtokenOutput" :min="0" :precision="0" :step="1" style="width: 100%" />
            </a-form-item>
          </a-col>
          <!-- 图片类:独占一整行,按后台启用的分辨率分别配置每张豆子 -->
          <a-col :span="24" v-if="form.modelType === 'image' && resolutionList.length">
            <a-row :gutter="16">
              <a-col :span="8" v-if="resolutionList.includes('1K')">
                <a-form-item label="1K 每张(豆)">
                  <a-input-number v-model="form.beanPerImage1k" :min="0" :precision="0" :step="1" style="width: 100%" />
                </a-form-item>
              </a-col>
              <a-col :span="8" v-if="resolutionList.includes('2K')">
                <a-form-item label="2K 每张(豆)">
                  <a-input-number v-model="form.beanPerImage2k" :min="0" :precision="0" :step="1" style="width: 100%" />
                </a-form-item>
              </a-col>
              <a-col :span="8" v-if="resolutionList.includes('4K')">
                <a-form-item label="4K 每张(豆)">
                  <a-input-number v-model="form.beanPerImage4k" :min="0" :precision="0" :step="1" style="width: 100%" />
                </a-form-item>
              </a-col>
            </a-row>
          </a-col>
          <!-- 视频类:每秒豆子 -->
          <a-col :span="12" v-if="form.modelType === 'video'">
            <a-form-item label="每秒(豆)">
              <a-input-number v-model="form.beanPerSecond" :min="0" :precision="0" :step="1" style="width: 100%" />
            </a-form-item>
          </a-col>
        </a-row>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch, onMounted } from 'vue';
import { Message, Modal } from '@arco-design/web-vue';
import { IconSearch, IconRefresh, IconPlus, IconDelete, IconEdit, IconPlayCircle } from '@arco-design/web-vue/es/icon';
import { listSysAiModel, getSysAiModel, addSysAiModel, updateSysAiModel, delSysAiModel, testSysAiModel, setDefaultSysAiModel, SysAiModel } from '@/api/ai';
import { getDicts } from '@/api/dict';

const loading = ref(true);
const selectedRowKeys = ref<number[]>([]);
const modelList = ref<SysAiModel[]>([]);
const title = ref("");
const open = ref(false);
const formRef = ref();

// 字典数据
const providerOptions = ref<any[]>([]);
const typeOptions = ref<any[]>([]);

// 允许显示的模型类型(其余隐藏)
const ALLOWED_TYPES = ['chat', 'image', 'video'];
// 厂商按模型类型映射
const PROVIDER_BY_TYPE: Record<string, string[]> = {
  chat: ['deepseek', 'openai', 'aliyun', 'yunwu', 'luxai'],
  image: ['yunwu', 'gptsapi', 'luxai'],
  video: ['kling']
};
// 图像模型可选画质/画幅全集
const RESOLUTION_OPTIONS = ['1K', '2K', '4K'];
const ASPECT_RATIO_OPTIONS = ['1:1', '16:9', '9:16', '3:2', '2:3', '4:3', '3:4'];

// 图像模型画质/画幅本地数组(控件绑数组,提交/回显时与逗号分隔字符串互转)
const resolutionList = ref<string[]>([]);
const ratioList = ref<string[]>([]);

// 模型类型下拉:仅保留 ALLOWED_TYPES
const filteredTypeOptions = computed(() =>
  typeOptions.value.filter(dict => ALLOWED_TYPES.includes(dict.dictValue))
);

// 厂商下拉:按当前模型类型过滤;类型为空或未映射时用三类并集兜底
const filteredProviderOptions = computed(() => {
  const type = form.value.modelType;
  const allowed = PROVIDER_BY_TYPE[type] || Object.values(PROVIDER_BY_TYPE).flat();
  const options = providerOptions.value.some(dict => dict.dictValue === 'luxai') ? providerOptions.value : [...providerOptions.value, { dictValue: 'luxai', dictLabel: 'Luxai' }];
  return options.filter(dict => allowed.includes(dict.dictValue));
});

// 标签页配置
interface TabItem {
  key: string;
  title: string;
  modelType?: string;
}

const tabs: TabItem[] = [
  { key: 'all', title: '全部模型', modelType: undefined },
  { key: 'chat', title: '对话模型', modelType: 'chat' },
  { key: 'image', title: '图像模型', modelType: 'image' },
  { key: 'video', title: '视频模型', modelType: 'video' }
];

const activeTab = ref('all');

const queryParams = reactive({
  pageNum: 1,
  pageSize: 10,
  modelName: undefined as string | undefined,
  modelType: undefined as string | undefined
});

const form = ref<SysAiModel>({
  modelName: '',
  displayTier: '中',
  apiKey: '',
  imageQuality: 'auto',
  baseUrl: 'https://api.openai.com',
  modelDeployment: '',
  temperature: 0.7,
  maxTokens: 2000,
  provider: 'openai',
  modelType: 'chat',
  apiProtocol: 'chat_completions',
  isActive: 1,
  sortOrder: 0,
  supportedResolutions: '',
  supportedAspectRatios: '',
  defaultResolution: '',
  beanPerMtokenInput: undefined,
  beanPerMtokenInputCached: undefined,
  beanPerMtokenOutput: undefined,
  beanPerImage: undefined,
  beanPerImage1k: undefined,
  beanPerImage2k: undefined,
  beanPerImage4k: undefined,
  beanPerSecond: undefined
});

const rules = {
  modelName: [{ required: true, message: "请输入模型名称" }],
  displayTier: [{ required: true, message: "请选择小程序档位" }],
  apiKey: [{ required: true, message: "请输入API Key" }],
  baseUrl: [{ required: true, message: "请输入Base URL" }],
  modelDeployment: [{ required: true, message: "请输入部署ID" }]
};

const pagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0,
  showTotal: true,
  showPageSize: true,
  pageSizeOptions: ['10', '20', '50']
});

const rowSelection = {
  type: 'checkbox' as const,
  showCheckedAll: true
};

// 厂商 → 默认 Base URL 映射(仅这几家联动预填)
const providerBaseUrlMap: Record<string, string> = {
  luxai: 'https://api.openlux.ai',
  yunwu: 'https://api.yunwu.ai',
  gptsapi: 'https://api.gptsapi.net',
  kling: 'https://api.klingai.com'
};

const showCachedInputPrice = computed(() =>
  (form.value.modelType === 'chat' || form.value.modelType === 'vision')
  && (form.value.provider === 'deepseek'
    || (form.value.modelType === 'chat' && form.value.apiProtocol === 'responses'))
);

// 按模型类型/厂商清空不匹配的计费字段(只保留当前界面会显示的项)
// 文本(chat/vision):保留 input/output;DeepSeek 或 Responses 协议保留缓存命中档
// 图片:保留统一兜底价与三个分辨率单价;视频:仅保留 beanPerSecond;互不匹配项一律置空
function clearMismatchedBillingFields(type: string, provider: string) {
  const isText = type === 'chat' || type === 'vision';
  if (!isText) {
    form.value.beanPerMtokenInput = undefined;
    form.value.beanPerMtokenInputCached = undefined;
    form.value.beanPerMtokenOutput = undefined;
  } else if (provider !== 'deepseek'
    && (type !== 'chat' || form.value.apiProtocol !== 'responses')) {
    // 非 DeepSeek 且非 Responses 模型不保留缓存命中档
    form.value.beanPerMtokenInputCached = undefined;
  }
  if (type !== 'image') {
    form.value.beanPerImage = undefined;
    form.value.beanPerImage1k = undefined;
    form.value.beanPerImage2k = undefined;
    form.value.beanPerImage4k = undefined;
  }
  if (type !== 'video') {
    form.value.beanPerSecond = undefined;
  }
}

// 离开 Responses 协议后立即清掉其缓存输入价，避免隐藏字段形成脏配置。
function handleApiProtocolChange(protocol: string) {
  if (protocol !== 'responses' && form.value.provider !== 'deepseek') {
    form.value.beanPerMtokenInputCached = undefined;
  }
}

// 选择厂商时自动预填 Base URL(未在映射内的厂商不覆盖用户已填值)
function handleProviderChange(provider: string) {
  const baseUrl = providerBaseUrlMap[provider];
  if (baseUrl) {
    form.value.baseUrl = baseUrl;
  }
  // 厂商变化时清理不匹配的计费字段
  clearMismatchedBillingFields(form.value.modelType, provider);
}

// 切换模型类型:当前厂商若不在新类型允许的厂商列表中则清空,让用户重选
function handleTypeChange(type: string) {
  const allowed = PROVIDER_BY_TYPE[type];
  if (allowed && form.value.provider && !allowed.includes(form.value.provider)) {
    form.value.provider = '';
  }
  if (type !== 'chat') {
    form.value.apiProtocol = 'chat_completions';
  }
  // 切换类型后清理不匹配的计费字段,避免界面残留旧单价
  clearMismatchedBillingFields(type, form.value.provider);
}

// 画质允许集变化时,若默认分辨率已不在其中则清空,保证默认值始终在允许集内
watch(resolutionList, (list) => {
  if (form.value.defaultResolution && !list.includes(form.value.defaultResolution)) {
    form.value.defaultResolution = '';
  }
});

// 标签页切换处理
function handleTabChange(key: string) {
  activeTab.value = key;
  const tab = tabs.find(t => t.key === key);
  queryParams.modelType = tab?.modelType;
  handleQuery();
}

function getList() {
  loading.value = true;
  listSysAiModel({
    ...queryParams,
    pageNum: pagination.current,
    pageSize: pagination.pageSize
  }).then((response: any) => {
    modelList.value = response.records;
    pagination.total = response.total;
    loading.value = false;
  }).catch(() => {
    loading.value = false;
  });
}

function handleQuery() {
  pagination.current = 1;
  getList();
}

function resetQuery() {
  queryParams.modelName = undefined;
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

function reset() {
  form.value = {
    id: undefined,
    modelName: '',
    displayTier: '中',
    apiKey: '',
  imageQuality: 'auto',
    baseUrl: 'https://api.openai.com',
    modelDeployment: '',
    temperature: 0.7,
    maxTokens: 2000,
    provider: 'openai',
    modelType: 'chat',
    apiProtocol: 'chat_completions',
    isActive: 1,
    sortOrder: 0,
    supportedResolutions: '',
    supportedAspectRatios: '',
    defaultResolution: '',
    beanPerMtokenInput: undefined,
    beanPerMtokenInputCached: undefined,
    beanPerMtokenOutput: undefined,
    beanPerImage: undefined,
    beanPerImage1k: undefined,
    beanPerImage2k: undefined,
    beanPerImage4k: undefined,
    beanPerSecond: undefined
  };
  // 清空图像模型画质/画幅本地数组
  resolutionList.value = [];
  ratioList.value = [];
}

function handleAdd() {
  reset();
  open.value = true;
  title.value = "新增 AI 模型";
}

function handleUpdate(row: SysAiModel) {
  reset();
  const id = row.id!;
  getSysAiModel(id).then((response: any) => {
    form.value = { ...response, displayTier: response.displayTier || '中', imageQuality: response.imageQuality || 'auto' };
    // 回显图像模型画质/画幅:逗号分隔字符串 → 数组(空串→空数组)
    resolutionList.value = response.supportedResolutions ? response.supportedResolutions.split(',') : [];
    ratioList.value = response.supportedAspectRatios ? response.supportedAspectRatios.split(',') : [];
    open.value = true;
    title.value = "编辑 AI 模型";
  });
}

function submitForm() {
  // 图像模型:画质/画幅数组 → 逗号分隔字符串;非图像模型置空(不限制)
  if (form.value.modelType === 'image') {
    form.value.supportedResolutions = resolutionList.value.join(',');
    form.value.supportedAspectRatios = ratioList.value.join(',');
    const missingPrice = resolutionList.value.find((item) => {
      const key = `beanPerImage${item.toLowerCase()}` as 'beanPerImage1k' | 'beanPerImage2k' | 'beanPerImage4k';
      return form.value[key] === undefined || form.value[key] === null || form.value[key] === '';
    });
    if (missingPrice) {
      Message.warning(`请配置 ${missingPrice} 每张消耗豆子`);
      return;
    }
  } else {
    form.value.supportedResolutions = '';
    form.value.supportedAspectRatios = '';
    form.value.defaultResolution = '';
  }
  // 计费字段:按模型类型只保留匹配项,其余置空(避免切换类型残留旧单价被提交)
  clearMismatchedBillingFields(form.value.modelType, form.value.provider);
  formRef.value.validate((errors: any) => {
    if (!errors) {
      if (form.value.id) {
        updateSysAiModel(form.value).then(() => {
          Message.success("修改成功");
          open.value = false;
          getList();
        });
      } else {
        addSysAiModel(form.value).then(() => {
          Message.success("新增成功");
          open.value = false;
          getList();
        });
      }
    }
  });
}

function handleDelete(row: SysAiModel) {
  const id = row.id!;
  Modal.confirm({
    title: '确认删除',
    content: `确定要删除模型 "${row.modelName}" 吗？`,
    onOk: () => {
      delSysAiModel(id).then(() => {
        Message.success("删除成功");
        getList();
      });
    }
  });
}

function handleBatchDelete() {
  if (selectedRowKeys.value.length === 0) return;
  Modal.confirm({
    title: '确认删除',
    content: `确定要删除这些模型吗？`,
    onOk: () => {
      delSysAiModel(selectedRowKeys.value).then(() => {
           Message.success("删除成功");
        selectedRowKeys.value = [];
        getList();
      });
    }
  });
}

function handleStatusChange(row: SysAiModel) {
  const text = row.isActive === 1 ? "启用" : "禁用";
  Modal.confirm({
    title: '确认操作',
    content: `确认要${text}模型 "${row.modelName}" 吗？`,
    onOk: () => {
      updateSysAiModel(row).then(() => {
        Message.success(`${text}成功`);
      }).catch(() => {
          row.isActive = row.isActive === 1 ? 0 : 1;
      });
    },
    onCancel: () => {
      row.isActive = row.isActive === 1 ? 0 : 1;
    }
  });
}

// 同模型类型只保留一个默认，已有任务仍使用创建时选择的模型。
function handleSetDefault(row: SysAiModel, val: boolean | string | number) {
  if (!val) {
    return; // 关闭动作忽略:默认靠开启另一个来切换
  }
  Modal.confirm({
    title: '设为默认',
    content: `确认把「${row.modelName}」设为默认${row.modelType === 'chat' ? '对话' : '出图'}模型吗？未指定模型的新任务将使用它。`,
    onOk: () => {
      setDefaultSysAiModel(row.id as number).then(() => {
        Message.success('已设为默认');
        getList();
      }).catch(() => {
        Message.error('设置默认失败');
        getList();
      });
    },
    onCancel: () => {
      getList(); // 取消则回滚开关视图
    }
  });
}

function handleTest(row: SysAiModel) {
  const msg = Message.loading({ content: '正在测试连接...', duration: 0 });
  testSysAiModel(row).then((res: any) => {
    msg.close();
    Modal.success({
      title: '连接成功',
      content: res.msg || res.data || '模型对接成功✅'
    });
  }).catch((err: any) => {
    msg.close();
    Modal.error({
      title: '连接失败',
      content: err.msg || err.message || '部署失败，请检查填写内容'
    });
  });
}

function parseTime(time: string | undefined) {
  if (!time) return '';
  return time.replace('T', ' ');
}

function cancel() {
  open.value = false;
  reset();
}

onMounted(() => {
  getList();
  // 加载配置字典
  getDicts('sys_ai_provider').then(res => {
    providerOptions.value = res as any;
  });
  getDicts('sys_ai_model_type').then(res => {
    typeOptions.value = res as any;
  });
});
</script>

<style scoped>
.app-container {
  padding: 0px;
}
</style>
