<template>
  <div class="operlog-management">
    <!-- 操作日志列表 -->
    <a-card class="table-card" title="操作日志列表">
      <!-- 搜索区域 -->
      <a-row :gutter="16" style="margin-bottom: 16px;">
        <a-col :xs="24" :sm="12" :md="8" :lg="6" :xl="6" style="margin-bottom: 8px;">
          <a-form-item label="操作人员" :label-col-style="{ width: '80px' }" :style="{ marginBottom: 0 }">
            <a-input
              v-model="searchForm.operName"
              placeholder="请输入操作人员"
              allow-clear
              @press-enter="handleSearch"
            />
          </a-form-item>
        </a-col>
        <a-col :xs="24" :sm="12" :md="8" :lg="8" :xl="8" style="margin-bottom: 8px;">
          <a-form-item label="操作时间" :label-col-style="{ width: '80px' }" :style="{ marginBottom: 0 }">
            <a-range-picker
              v-model="dateRange"
              @change="handleDateChange"
            />
          </a-form-item>
        </a-col>
        <a-col :xs="24" :sm="24" :md="8" :lg="10" :xl="10" style="margin-bottom: 8px;">
          <a-space wrap>
            <a-button type="primary" @click="handleSearch">
              <template #icon>
                <icon-search />
              </template>
              搜索
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

      <!-- 操作按钮 -->
      <div style="margin-bottom: 16px;">
        <a-space>
          <a-button
            type="primary"
            status="danger"
            :disabled="selectedKeys.length === 0"
            @click="handleBatchDelete"
            v-hasPermi="['monitor:operlog:remove']"
          >
            <template #icon>
              <icon-delete />
            </template>
            删除
          </a-button>
          <a-button status="warning" @click="handleClean" v-hasPermi="['monitor:operlog:clean']">
            <template #icon>
              <icon-delete />
            </template>
            清空
          </a-button>
          <a-button type="primary" @click="handleExport" v-hasPermi="['monitor:operlog:export']">
            <template #icon>
              <icon-download />
            </template>
            导出
          </a-button>
        </a-space>
      </div>

      <a-table
        :columns="columns"
        :data="operlogList"
        :loading="loading"
        :pagination="pagination"
        :bordered="false"
        :row-selection="rowSelection"
        row-key="operId"
        @page-change="handlePageChange"
        @page-size-change="handlePageSizeChange"
        @selection-change="handleSelectionChange"
      >
        <template #businessType="{ record }">
          <a-tag :color="getBusinessTypeColor(record.businessType)">
            {{ getBusinessTypeName(record.businessType) }}
          </a-tag>
        </template>

        <template #status="{ record }">
          <a-tag :color="record.status === 0 ? 'green' : 'red'">
            {{ record.status === 0 ? '成功' : '失败' }}
          </a-tag>
        </template>

        <template #costTime="{ record }">
          <span>{{ record.costTime }} ms</span>
        </template>

        <template #operations="{ record }">
          <a-button
            type="text"
            size="small"
            status="danger"
            @click="handleDelete(record)"
            v-hasPermi="['monitor:operlog:remove']"
          >
            删除
          </a-button>
        </template>
      </a-table>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue';
import { Message, Modal } from '@arco-design/web-vue';
import type { TableRowSelection } from '@arco-design/web-vue';
import {
  listOperLog,
  deleteOperLog,
  cleanOperLog,
  exportOperLog,
  type OperLog,
  type OperLogQuery,
} from '@/api/monitor/operlog';

const operlogList = ref<OperLog[]>([]);
const loading = ref(false);
const selectedKeys = ref<number[]>([]);
const dateRange = ref<string[]>([]);

const searchForm = reactive<OperLogQuery>({
  operName: '',
  beginTime: '',
  endTime: ''
});

// 分页配置
const pagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0,
  showTotal: true,
  showPageSize: true,
  pageSizeOptions: [10, 20, 50, 100],
});

// 表格行选择配置
const rowSelection = computed<TableRowSelection>(() => ({
  type: 'checkbox',
  showCheckedAll: true,
  onlyCurrent: false,
}));

// 表格列定义
const columns = [
  {
    title: '日志编号',
    dataIndex: 'operId',
    width: 100,
  },
  {
    title: '系统模块',
    dataIndex: 'title',
    ellipsis: true,
    width: 150,
  },
  {
    title: '操作类型',
    dataIndex: 'businessType',
    slotName: 'businessType',
    width: 100,
  },
  {
    title: '操作人员',
    dataIndex: 'operName',
    width: 120,
  },
  {
    title: 'IP地址',
    dataIndex: 'operIp',
    width: 140,
  },
  {
    title: 'IP信息',
    dataIndex: 'operLocation',
    ellipsis: true,
    width: 150,
  },
  {
    title: '操作状态',
    dataIndex: 'status',
    slotName: 'status',
    width: 100,
  },
  {
    title: '操作日期',
    dataIndex: 'operTime',
    width: 180,
  },
  {
    title: '耗时',
    dataIndex: 'costTime',
    slotName: 'costTime',
    width: 100,
  },
  {
    title: '操作',
    slotName: 'operations',
    width: 80,
    fixed: 'right',
  },
];

// 加载操作日志列表
const loadOperlogList = async () => {
  loading.value = true;
  try {
    const params: OperLogQuery = {
      pageNum: pagination.current,
      pageSize: pagination.pageSize,
      ...searchForm,
    };
    // 移除空值
    Object.keys(params).forEach((key) => {
      const value = params[key as keyof OperLogQuery];
      if (value === '' || value === null || value === undefined) {
        delete params[key as keyof OperLogQuery];
      }
    });

    const res = await listOperLog(params);

    // 处理分页响应
    if (res && res.records) {
      operlogList.value = res.records || [];
      pagination.total = res.total || 0;
    } else if (Array.isArray(res)) {
      operlogList.value = res;
      pagination.total = res.length;
    } else {
      operlogList.value = [];
      pagination.total = 0;
    }
  } catch (error: any) {
    console.error('加载操作日志失败:', error);
    Message.error('加载操作日志失败');
    operlogList.value = [];
  } finally {
    loading.value = false;
  }
};

// 日期范围变化
const handleDateChange = (dates: string[] | undefined) => {
  if (dates && dates.length === 2) {
    searchForm.beginTime = dates[0];
    searchForm.endTime = dates[1];
  } else {
    searchForm.beginTime = '';
    searchForm.endTime = '';
  }
};

// 搜索
const handleSearch = () => {
  pagination.current = 1;
  loadOperlogList();
};

// 重置搜索
const resetSearch = () => {
  searchForm.operName = '';
  searchForm.beginTime = '';
  searchForm.endTime = '';
  dateRange.value = [];
  pagination.current = 1;
  loadOperlogList();
};

// 分页变化
const handlePageChange = (page: number) => {
  pagination.current = page;
  loadOperlogList();
};

const handlePageSizeChange = (pageSize: number) => {
  pagination.pageSize = pageSize;
  pagination.current = 1;
  loadOperlogList();
};

// 选择变化
const handleSelectionChange = (keys: (string | number)[]) => {
  selectedKeys.value = keys as number[];
};

// 删除单条日志
const handleDelete = (row: OperLog) => {
  Modal.confirm({
    title: '删除确认',
    content: `确定要删除该操作日志吗？`,
    okText: '确定删除',
    cancelText: '取消',
    onOk: async () => {
      try {
        await deleteOperLog(row.operId);
        Message.success('删除成功');
        loadOperlogList();
      } catch (error: any) {
        Message.error('删除失败');
      }
    },
  });
};

// 批量删除
const handleBatchDelete = () => {
  if (selectedKeys.value.length === 0) {
    Message.warning('请选择要删除的日志');
    return;
  }
  Modal.confirm({
    title: '批量删除确认',
    content: `确定要删除选中的 ${selectedKeys.value.length} 条日志吗？`,
    okText: '确定删除',
    cancelText: '取消',
    onOk: async () => {
      try {
        await deleteOperLog(selectedKeys.value);
        Message.success('删除成功');
        selectedKeys.value = [];
        loadOperlogList();
      } catch (error: any) {
        Message.error('删除失败');
      }
    },
  });
};

// 清空日志
const handleClean = () => {
  Modal.confirm({
    title: '清空确认',
    content: '确定要清空所有操作日志吗？此操作不可恢复！',
    okText: '确定清空',
    cancelText: '取消',
    onOk: async () => {
      try {
        await cleanOperLog();
        Message.success('清空成功');
        loadOperlogList();
      } catch (error: any) {
        Message.error('清空失败');
      }
    },
  });
};

// 导出日志
const handleExport = async () => {
  try {
    const res = await exportOperLog(searchForm);
    // 创建下载链接
    const blob = new Blob([res as any], {
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    });
    const url = window.URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `操作日志_${Date.now()}.xlsx`;
    link.click();
    window.URL.revokeObjectURL(url);
    Message.success('导出成功');
  } catch (error: any) {
    Message.error('导出失败');
  }
};

// 获取业务类型名称
const getBusinessTypeName = (type: number) => {
  const typeMap: Record<number, string> = {
    0: '其它',
    1: '新增',
    2: '修改',
    3: '删除',
    4: '授权',
    5: '导出',
    6: '导入',
    7: '强退',
    8: '生成代码',
    9: '清空数据',
  };
  return typeMap[type] || '其它';
};

// 获取业务类型颜色
const getBusinessTypeColor = (type: number) => {
  const colorMap: Record<number, string> = {
    0: 'gray',
    1: 'green',
    2: 'blue',
    3: 'red',
    4: 'orange',
    5: 'purple',
    6: 'cyan',
    7: 'magenta',
    8: 'gold',
    9: 'lime',
  };
  return colorMap[type] || 'gray';
};

onMounted(() => {
  loadOperlogList();
});
</script>

<style scoped>
.operlog-management {
  padding: 0px;
  height: 100%;
  display: flex;
  flex-direction: column;
}

.search-card {
  flex-shrink: 0;
}

.table-card {
  flex: 1;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.table-card :deep(.arco-card-body) {
  flex: 1;
  overflow: auto;
  padding: 16px;
}
</style>
