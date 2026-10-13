<template>
  <div class="app-container">
    <!-- 审核日志列表(只读) -->
    <a-card class="table-card" title="内容审核日志">
      <!-- 搜索区域 -->
      <a-row :gutter="16" style="margin-bottom: 16px;">
        <a-col :xs="24" :sm="12" :md="8" :lg="6" :xl="4" style="margin-bottom: 8px;">
          <a-form-item label="对象类型" :label-col-style="{ width: '80px' }" :style="{ marginBottom: 0 }">
            <a-select v-model="searchForm.objectType" placeholder="全部" allow-clear>
              <a-option v-for="item in OBJECT_TYPE_OPTIONS" :key="item.value" :value="item.value">
                {{ item.label }}
              </a-option>
            </a-select>
          </a-form-item>
        </a-col>
        <a-col :xs="24" :sm="12" :md="8" :lg="6" :xl="4" style="margin-bottom: 8px;">
          <a-form-item label="命中建议" :label-col-style="{ width: '80px' }" :style="{ marginBottom: 0 }">
            <a-select v-model="searchForm.suggest" placeholder="全部" allow-clear>
              <a-option v-for="item in SUGGEST_OPTIONS" :key="item.value" :value="item.value">
                {{ item.label }}
              </a-option>
            </a-select>
          </a-form-item>
        </a-col>
        <a-col :xs="24" :sm="12" :md="8" :lg="6" :xl="4" style="margin-bottom: 8px;">
          <a-form-item label="处置结果" :label-col-style="{ width: '80px' }" :style="{ marginBottom: 0 }">
            <a-select v-model="searchForm.result" placeholder="全部" allow-clear>
              <a-option v-for="item in RESULT_OPTIONS" :key="item.value" :value="item.value">
                {{ item.label }}
              </a-option>
            </a-select>
          </a-form-item>
        </a-col>
        <a-col :xs="24" :sm="12" :md="8" :lg="6" :xl="4" style="margin-bottom: 8px;">
          <a-form-item label="用户ID" :label-col-style="{ width: '80px' }" :style="{ marginBottom: 0 }">
            <a-input
              v-model="searchForm.userId"
              placeholder="请输入用户ID"
              allow-clear
              @press-enter="handleSearch"
            />
          </a-form-item>
        </a-col>
        <a-col :xs="24" :sm="24" :md="12" :lg="8" :xl="6" style="margin-bottom: 8px;">
          <a-form-item label="送审时间" :label-col-style="{ width: '80px' }" :style="{ marginBottom: 0 }">
            <a-range-picker
              v-model="dateRange"
              show-time
              format="YYYY-MM-DD HH:mm:ss"
              style="width: 100%"
              @change="handleDateChange"
            />
          </a-form-item>
        </a-col>
        <a-col :xs="24" :sm="24" :md="12" :lg="24" :xl="2" style="margin-bottom: 8px;">
          <a-space wrap>
            <a-button type="primary" @click="handleSearch">
              <template #icon>
                <icon-search />
              </template>
              查询
            </a-button>
            <a-button @click="resetSearch">
              <template #icon>
                <icon-refresh />
              </template>
              重置
            </a-button>
          </a-space>
        </a-col>
      </a-row>

      <a-table
        :columns="columns"
        :data="recordList"
        :loading="loading"
        :pagination="pagination"
        :bordered="false"
        row-key="id"
        :scroll="{ x: 1600 }"
        @page-change="handlePageChange"
        @page-size-change="handlePageSizeChange"
      >
        <template #suggest="{ record }">
          <a-tag v-if="record.suggest" :color="getSuggestColor(record.suggest)">
            {{ record.suggest }}
          </a-tag>
          <span v-else>-</span>
        </template>
      </a-table>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue';
import { Message } from '@arco-design/web-vue';
import { IconSearch, IconRefresh } from '@arco-design/web-vue/es/icon';
import { listAuditRecord, type AuditRecord, type AuditRecordQuery } from '@/api/audit';

const recordList = ref<AuditRecord[]>([]);
const loading = ref(false);
// 时间范围控件本地值(含时分秒);change 时拆成 beginTime/endTime 传参
const dateRange = ref<string[]>([]);

// 搜索条件(下拉枚举值固定,无字典依赖)
const searchForm = reactive<Omit<AuditRecordQuery, 'pageNum' | 'pageSize'>>({
  objectType: undefined,
  suggest: undefined,
  result: undefined,
  userId: undefined,
  beginTime: undefined,
  endTime: undefined
});

// 对象类型下拉
const OBJECT_TYPE_OPTIONS = [
  { value: 'text', label: '文本' },
  { value: 'media', label: '图片/视频' }
];

// 命中建议下拉
const SUGGEST_OPTIONS = [
  { value: 'pass', label: 'pass(通过)' },
  { value: 'review', label: 'review(复审)' },
  { value: 'risky', label: 'risky(风险)' },
  { value: 'error', label: 'error(异常)' }
];

// 处置结果下拉
const RESULT_OPTIONS = [
  { value: 'PASS', label: 'PASS(放行)' },
  { value: 'REJECT', label: 'REJECT(拦截)' },
  { value: 'ERROR', label: 'ERROR(异常)' }
];

// 命中建议着色映射
const SUGGEST_COLOR_MAP: Record<string, string> = {
  pass: 'green',
  review: 'orange',
  risky: 'red',
  error: 'gray'
};

function getSuggestColor(suggest: string) {
  return SUGGEST_COLOR_MAP[suggest] || 'gray';
}

// 分页配置
const pagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0,
  showTotal: true,
  showPageSize: true,
  pageSizeOptions: [10, 20, 50, 100]
});

// 表格列定义
const columns = [
  { title: 'ID', dataIndex: 'id', width: 80 },
  { title: '用户ID', dataIndex: 'userId', width: 100 },
  { title: '对象类型', dataIndex: 'objectType', width: 100 },
  { title: '业务类型', dataIndex: 'bizType', width: 120 },
  { title: '命中建议', dataIndex: 'suggest', slotName: 'suggest', width: 110 },
  { title: '标签', dataIndex: 'label', width: 120, ellipsis: true, tooltip: true },
  { title: '处置', dataIndex: 'result', width: 100 },
  { title: '原因', dataIndex: 'reason', width: 180, ellipsis: true, tooltip: true },
  { title: '送审摘要', dataIndex: 'contentSnippet', ellipsis: true, tooltip: true, minWidth: 260 },
  { title: '时间', dataIndex: 'createTime', width: 180 }
];

// 加载审核日志列表
const loadRecordList = async () => {
  loading.value = true;
  try {
    const params: AuditRecordQuery = {
      pageNum: pagination.current,
      pageSize: pagination.pageSize,
      ...searchForm
    };
    // 移除空值,避免向后端传空串
    Object.keys(params).forEach((key) => {
      const value = params[key as keyof AuditRecordQuery];
      if (value === '' || value === null || value === undefined) {
        delete params[key as keyof AuditRecordQuery];
      }
    });

    const res: any = await listAuditRecord(params);
    recordList.value = res?.records || [];
    pagination.total = res?.total || 0;
  } catch (error) {
    Message.error('加载审核日志失败');
    recordList.value = [];
    pagination.total = 0;
  } finally {
    loading.value = false;
  }
};

// 时间范围变化:拆成 beginTime/endTime(格式 YYYY-MM-DD HH:mm:ss)
const handleDateChange = (dates: string[] | undefined) => {
  if (dates && dates.length === 2) {
    searchForm.beginTime = dates[0];
    searchForm.endTime = dates[1];
  } else {
    searchForm.beginTime = undefined;
    searchForm.endTime = undefined;
  }
};

// 查询
const handleSearch = () => {
  pagination.current = 1;
  loadRecordList();
};

// 重置
const resetSearch = () => {
  searchForm.objectType = undefined;
  searchForm.suggest = undefined;
  searchForm.result = undefined;
  searchForm.userId = undefined;
  searchForm.beginTime = undefined;
  searchForm.endTime = undefined;
  dateRange.value = [];
  pagination.current = 1;
  loadRecordList();
};

// 分页变化
const handlePageChange = (page: number) => {
  pagination.current = page;
  loadRecordList();
};

const handlePageSizeChange = (pageSize: number) => {
  pagination.pageSize = pageSize;
  pagination.current = 1;
  loadRecordList();
};

onMounted(() => {
  loadRecordList();
});
</script>

<style scoped>
.app-container {
  padding: 0px;
}
</style>
