<template>
  <div class="app-container">
    <!-- 当前生效 -->
    <a-card class="table-card" title="当前生效" style="margin-bottom: 16px;">
      <template #extra>
        <a-button type="primary" :loading="saving" @click="handleSave" v-hasPermi="['globalprompt:edit']">
          保存并生效
        </a-button>
      </template>
      <a-spin :loading="activeLoading" style="width: 100%;">
        <a-textarea
          v-model="activeContent"
          placeholder="请输入全局提示词内容"
          :auto-size="{ minRows: 8, maxRows: 20 }"
        />
      </a-spin>
    </a-card>

    <!-- 历史版本 -->
    <a-card class="table-card" title="历史版本">
      <a-table
        :loading="historyLoading"
        :data="historyList"
        :pagination="pagination"
        row-key="id"
        @page-change="handlePageChange"
        @page-size-change="handlePageSizeChange"
      >
        <template #columns>
          <a-table-column title="内容摘要" align="left" ellipsis tooltip>
            <template #cell="{ record }">
              {{ summarize(record.content) }}
            </template>
          </a-table-column>
          <a-table-column title="创建时间" data-index="createTime" align="center" :width="180" />
          <a-table-column title="是否生效" align="center" :width="100">
            <template #cell="{ record }">
              <a-tag :color="record.isActive === '1' ? 'green' : 'gray'">
                {{ record.isActive === '1' ? '生效中' : '历史' }}
              </a-tag>
            </template>
          </a-table-column>
          <a-table-column title="操作" align="center" :width="160">
            <template #cell="{ record }">
              <a-space>
                <a-button
                  type="text"
                  size="small"
                  :disabled="record.isActive === '1'"
                  @click="handleActivate(record)"
                  v-hasPermi="['globalprompt:edit']"
                >
                  启用
                </a-button>
                <a-button type="text" size="small" @click="handleView(record)">查看</a-button>
              </a-space>
            </template>
          </a-table-column>
        </template>
      </a-table>
    </a-card>

    <!-- 查看全文弹窗 -->
    <a-modal
      v-model:visible="viewOpen"
      title="提示词全文"
      :width="640"
      :footer="false"
      :body-style="{ maxHeight: '68vh', overflowY: 'auto' }"
    >
      <pre class="view-content">{{ viewContent }}</pre>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue';
import { Message, Modal } from '@arco-design/web-vue';
import {
  getActiveGlobalPrompt,
  listGlobalPrompt,
  saveGlobalPrompt,
  activateGlobalPrompt,
  GlobalPrompt
} from '@/api/globalPrompt';

// ---- 当前生效 ----
const activeLoading = ref(false);
const activeContent = ref('');

function getActive() {
  activeLoading.value = true;
  getActiveGlobalPrompt()
    .then((res: any) => {
      activeContent.value = res?.content || '';
    })
    .finally(() => {
      activeLoading.value = false;
    });
}

// ---- 历史版本 ----
const historyLoading = ref(false);
const historyList = ref<GlobalPrompt[]>([]);
const pagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0,
  showTotal: true,
  showPageSize: true,
  pageSizeOptions: ['10', '20', '50']
});

function getHistory() {
  historyLoading.value = true;
  listGlobalPrompt({
    pageNum: pagination.current,
    pageSize: pagination.pageSize
  }).then((res: any) => {
    historyList.value = res.records;
    pagination.total = res.total;
  }).finally(() => {
    historyLoading.value = false;
  });
}

function handlePageChange(current: number) {
  pagination.current = current;
  getHistory();
}

function handlePageSizeChange(pageSize: number) {
  pagination.pageSize = pageSize;
  pagination.current = 1;
  getHistory();
}

// 内容摘要(列表展示用,截断长文)
function summarize(content?: string): string {
  if (!content) return '';
  const text = content.replace(/\s+/g, ' ').trim();
  return text.length > 60 ? text.slice(0, 60) + '…' : text;
}

// ---- 保存并生效 ----
const saving = ref(false);
function handleSave() {
  if (!activeContent.value.trim()) {
    Message.warning('请输入全局提示词内容');
    return;
  }
  saving.value = true;
  saveGlobalPrompt({ content: activeContent.value })
    .then(() => {
      Message.success('已保存并生效');
      getActive();
      pagination.current = 1;
      getHistory();
    })
    .finally(() => {
      saving.value = false;
    });
}

// ---- 启用历史版本 ----
function handleActivate(record: GlobalPrompt) {
  Modal.confirm({
    title: '启用确认',
    content: '确认要启用该历史版本吗?当前生效版本将被替换。',
    onOk: () => {
      activateGlobalPrompt(record.id!).then(() => {
        Message.success('已启用');
        getActive();
        getHistory();
      });
    }
  });
}

// ---- 查看全文 ----
const viewOpen = ref(false);
const viewContent = ref('');
function handleView(record: GlobalPrompt) {
  viewContent.value = record.content || '';
  viewOpen.value = true;
}

onMounted(() => {
  getActive();
  getHistory();
});
</script>

<style scoped>
.app-container {
  padding: 0px;
}

.view-content {
  white-space: pre-wrap;
  word-break: break-word;
  margin: 0;
  font-family: inherit;
  font-size: 14px;
  line-height: 1.6;
  color: var(--color-text-1);
}
</style>
