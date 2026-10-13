<template>
  <div class="list-template">
    <!-- 列表卡片 -->
    <a-card class="table-card" title="列表模版">
      <!-- 搜索和操作行 -->
      <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px;">
        <!-- 左侧搜索 -->
        <a-form :model="searchForm" layout="inline" style="flex: 1;">
          <a-form-item label="搜索关键字" :style="{ marginBottom: 0 }">
            <a-input
              v-model="searchForm.keyword"
              placeholder="请输入搜索关键字"
              allow-clear
              style="width: 250px"
              @press-enter="handleSearch"
            />
          </a-form-item>
          <a-form-item label="状态" :style="{ marginBottom: 0 }">
            <a-select v-model="searchForm.status" placeholder="请选择状态" allow-clear style="width: 120px">
              <a-option value="">全部</a-option>
              <a-option value="0">启用</a-option>
              <a-option value="1">禁用</a-option>
            </a-select>
          </a-form-item>
          <a-form-item :style="{ marginBottom: 0 }">
            <a-space>
              <a-button type="primary" @click="handleSearch">
                <template #icon><icon-search /></template>
                搜索
              </a-button>
              <a-button @click="resetSearch">
                <template #icon><icon-refresh /></template>
                重置
              </a-button>
            </a-space>
          </a-form-item>
        </a-form>

        <!-- 右侧操作 -->
        <a-space>
          <a-button type="primary" @click="handleAdd">
            <template #icon><icon-plus /></template>
            新增
          </a-button>
          <a-button
            type="primary"
            status="danger"
            :disabled="selectedKeys.length === 0"
            @click="handleBatchDelete"
          >
            <template #icon><icon-delete /></template>
            批量删除
          </a-button>
        </a-space>
      </div>

      <!-- 表格 -->
      <a-table
        :columns="columns"
        :data="dataList"
        :row-selection="rowSelection"
        :pagination="pagination"
        :loading="loading"
        row-key="id"
        @page-change="handlePageChange"
        @page-size-change="handlePageSizeChange"
      >
        <template #columns>
          <a-table-column title="编号" data-index="id" :width="80" align="center" />
          <a-table-column title="名称" data-index="name" align="center" ellipsis tooltip />
          <a-table-column title="状态" data-index="status" align="center" :width="100">
            <template #cell="{ record }">
              <a-tag :color="record.status === 0 ? 'green' : 'red'">
                {{ record.status === 0 ? '启用' : '禁用' }}
              </a-tag>
            </template>
          </a-table-column>
          <a-table-column title="创建时间" data-index="createTime" :width="180" align="center">
            <template #cell="{ record }">
              <span>{{ record.createTime }}</span>
            </template>
          </a-table-column>
          <a-table-column title="操作" align="center" :width="200" fixed="right">
            <template #cell="{ record }">
              <a-space>
                <a-button type="text" size="small" @click="handleEdit(record)">
                  <template #icon><icon-edit /></template>
                  编辑
                </a-button>
                <a-button type="text" size="small" status="danger" @click="handleDelete(record)">
                  <template #icon><icon-delete /></template>
                  删除
                </a-button>
              </a-space>
            </template>
          </a-table-column>
        </template>
      </a-table>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue';
import { Message, Modal } from '@arco-design/web-vue';
import { IconPlus, IconSearch, IconRefresh, IconDelete, IconEdit } from '@arco-design/web-vue/es/icon';

interface DataItem {
  id: number;
  name: string;
  status: number;
  createTime: string;
}

const dataList = ref<DataItem[]>([]);
const loading = ref(false);
const selectedKeys = ref<number[]>([]);

const searchForm = reactive({
  keyword: '',
  status: ''
});

const pagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0,
  showTotal: true,
  showPageSize: true,
  pageSizeOptions: ['10', '20', '50', '100']
});

const rowSelection = {
  type: 'checkbox' as const,
  showCheckedAll: true,
  onlyCurrent: false
};

const columns = [
  { title: '编号', dataIndex: 'id', width: 80, align: 'center' },
  { title: '名称', dataIndex: 'name', ellipsis: true },
  { title: '状态', dataIndex: 'status', slotName: 'status', width: 100 },
  { title: '创建时间', dataIndex: 'createTime', width: 180 },
  { title: '操作', slotName: 'operations', width: 200, fixed: 'right' }
];

function handleSearch() {
  pagination.current = 1;
  Message.info('搜索功能（待接入后端接口）');
}

function resetSearch() {
  searchForm.keyword = '';
  searchForm.status = '';
  pagination.current = 1;
  Message.info('重置成功');
}

function handlePageChange(page: number) {
  pagination.current = page;
}

function handlePageSizeChange(pageSize: number) {
  pagination.pageSize = pageSize;
  pagination.current = 1;
}

function handleAdd() {
  Message.info('新增功能（待接入后端接口）');
}

function handleEdit(record: DataItem) {
  Message.info(`编辑功能 - ID: ${record.id}, 名称: ${record.name}`);
}

function handleDelete(record: DataItem) {
  Modal.confirm({
    title: '删除确认',
    content: `确定要删除"${record.name}"吗？`,
    okText: '确定',
    cancelText: '取消',
    onOk: () => {
      Message.success('删除成功（待接入后端接口）');
    }
  });
}

function handleBatchDelete() {
  if (selectedKeys.value.length === 0) {
    Message.warning('请选择要删除的数据');
    return;
  }
  Modal.confirm({
    title: '批量删除确认',
    content: `确定要删除选中的 ${selectedKeys.value.length} 条数据吗？`,
    okText: '确定',
    cancelText: '取消',
    onOk: () => {
      Message.success('批量删除成功（待接入后端接口）');
      selectedKeys.value = [];
    }
  });
}

onMounted(() => {
  dataList.value = [
    { id: 1, name: '示例数据1', status: 0, createTime: '2024-01-01 10:00:00' },
    { id: 2, name: '示例数据2', status: 1, createTime: '2024-01-02 10:00:00' },
    { id: 3, name: '示例数据3', status: 0, createTime: '2024-01-03 10:00:00' },
    { id: 4, name: '示例数据4', status: 0, createTime: '2024-01-04 10:00:00' },
    { id: 5, name: '示例数据5', status: 1, createTime: '2024-01-05 10:00:00' },
    { id: 6, name: '示例数据6', status: 0, createTime: '2024-01-06 10:00:00' },
    { id: 7, name: '示例数据7', status: 0, createTime: '2024-01-07 10:00:00' },
    { id: 8, name: '示例数据8', status: 1, createTime: '2024-01-08 10:00:00' },
    { id: 9, name: '示例数据9', status: 0, createTime: '2024-01-09 10:00:00' },
    { id: 10, name: '示例数据10', status: 0, createTime: '2024-01-10 10:00:00' }
  ];
  pagination.total = 10;
});
</script>

<style scoped>
.list-template {
  padding: 0px;
}
</style>

