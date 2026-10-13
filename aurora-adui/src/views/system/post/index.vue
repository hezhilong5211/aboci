<template>
  <div class="post-management">
    <!-- 列表卡片 -->
    <a-card class="table-card" title="岗位列表">
      <!-- 搜索区域 -->
      <a-row :gutter="16" style="margin-bottom: 16px;">
        <a-col :xs="24" :sm="12" :md="8" :lg="6" :xl="6" style="margin-bottom: 8px;">
          <a-form-item label="岗位名称" :label-col-style="{ width: '80px' }" :style="{ marginBottom: 0 }">
            <a-input
              v-model="searchForm.postName"
              placeholder="请输入岗位名称"
              allow-clear
              @press-enter="handleSearch"
            />
          </a-form-item>
        </a-col>
        <a-col :xs="24" :sm="12" :md="8" :lg="6" :xl="6" style="margin-bottom: 8px;">
          <a-form-item label="状态" :label-col-style="{ width: '80px' }" :style="{ marginBottom: 0 }">
            <a-select v-model="searchForm.status" placeholder="请选择状态" allow-clear>
              <a-option value="">全部</a-option>
              <a-option value="0">正常</a-option>
              <a-option value="1">停用</a-option>
            </a-select>
          </a-form-item>
        </a-col>
        <a-col :xs="24" :sm="24" :md="8" :lg="12" :xl="12" style="margin-bottom: 8px;">
          <a-space wrap>
            <a-button type="primary" @click="handleSearch">
              <template #icon><icon-search /></template>
              搜索
            </a-button>
            <a-button @click="resetSearch">
              <template #icon><icon-refresh /></template>
              重置
            </a-button>
          </a-space>
        </a-col>
      </a-row>

      <!-- 操作按钮 -->
      <div style="margin-bottom: 16px;">
        <a-space>
          <a-button type="primary" @click="handleAdd" v-hasPermi="['system:post:add']">
            <template #icon><icon-plus /></template>
            新增
          </a-button>
          <a-button
            type="primary"
            status="danger"
            :disabled="selectedKeys.length === 0"
            @click="handleBatchDelete"
            v-hasPermi="['system:post:remove']"
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
        row-key="postId"
        v-model:selected-keys="selectedKeys"
        @page-change="handlePageChange"
        @page-size-change="handlePageSizeChange"
      >
        <template #columns>
          <a-table-column title="岗位编号" data-index="postId" :width="100" align="center" />
          <a-table-column title="岗位编码" data-index="postCode" :width="150" align="center" />
          <a-table-column title="岗位名称" data-index="postName" align="center" ellipsis tooltip />
          <a-table-column title="显示顺序" data-index="postSort" :width="100" align="center" />
          <a-table-column title="状态" data-index="status" align="center" :width="100">
            <template #cell="{ record }">
              <a-tag :color="record.status === '0' ? 'green' : 'red'">
                {{ record.status === '0' ? '正常' : '停用' }}
              </a-tag>
            </template>
          </a-table-column>
          <a-table-column title="创建时间" data-index="createTime" :width="180" align="center">
            <template #cell="{ record }">
              <span>{{ formatTime(record.createTime) }}</span>
            </template>
          </a-table-column>
          <a-table-column title="操作" align="center" :width="160" fixed="right">
            <template #cell="{ record }">
              <a-space>
                <a-button type="text" size="small" @click="handleEdit(record)" v-hasPermi="['system:post:edit']">
                  编辑
                </a-button>
                <a-button type="text" size="small" status="danger" @click="handleDelete(record)" v-hasPermi="['system:post:remove']">
                  删除
                </a-button>
              </a-space>
            </template>
          </a-table-column>
        </template>
      </a-table>
    </a-card>

    <!-- 新增/编辑弹窗 -->
    <a-modal
      v-model:visible="dialogVisible"
      :title="dialogTitle"
      width="500px"
      @before-ok="handleSubmit"
      @cancel="handleCancel"
    >
      <a-form
        ref="formRef"
        :model="formData"
        :rules="formRules"
        layout="horizontal"
        :label-col-props="{ span: 6 }"
        :wrapper-col-props="{ span: 18 }"
      >
        <a-form-item label="岗位名称" field="postName">
          <a-input v-model="formData.postName" placeholder="请输入岗位名称" />
        </a-form-item>
        <a-form-item label="岗位编码" field="postCode">
          <a-input v-model="formData.postCode" placeholder="请输入岗位编码" />
        </a-form-item>
        <a-form-item label="显示顺序" field="postSort">
          <a-input-number v-model="formData.postSort" :min="0" placeholder="请输入显示顺序" style="width: 100%" />
        </a-form-item>
        <a-form-item label="状态" field="status">
          <a-radio-group v-model="formData.status">
            <a-radio value="0">正常</a-radio>
            <a-radio value="1">停用</a-radio>
          </a-radio-group>
        </a-form-item>
        <a-form-item label="备注" field="remark">
          <a-textarea v-model="formData.remark" :max-length="500" placeholder="请输入备注" :auto-size="{ minRows: 3 }" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue';
import { Message, Modal } from '@arco-design/web-vue';
import { IconPlus, IconSearch, IconRefresh, IconDelete } from '@arco-design/web-vue/es/icon';
import { listPost, getPost, addPost, updatePost, deletePost, deletePostBatch } from '@/api/deptPost';

interface Post {
  postId?: number;
  postCode: string;
  postName: string;
  postSort: number;
  status: string;
  remark?: string;
  createTime?: string;
}

const dataList = ref<Post[]>([]);
const loading = ref(false);
const selectedKeys = ref<number[]>([]);
const dialogVisible = ref(false);
const dialogTitle = ref('新增岗位');
const formRef = ref();

const searchForm = reactive({
  postName: '',
  status: ''
});

const formData = ref<Post>({
  postCode: '',
  postName: '',
  postSort: 0,
  status: '0',
  remark: ''
});

const formRules = {
  postName: [{ required: true, message: '请输入岗位名称' }],
  postCode: [{ required: true, message: '请输入岗位编码' }],
  postSort: [{ required: true, message: '请输入显示顺序' }]
};

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
  { title: '岗位编号', dataIndex: 'postId', width: 100, align: 'center' },
  { title: '岗位编码', dataIndex: 'postCode', width: 150, align: 'center' },
  { title: '岗位名称', dataIndex: 'postName', ellipsis: true },
  { title: '显示顺序', dataIndex: 'postSort', width: 100, align: 'center' },
  { title: '状态', dataIndex: 'status', slotName: 'status', width: 100 },
  { title: '创建时间', dataIndex: 'createTime', width: 180 },
  { title: '操作', slotName: 'operations', width: 160, fixed: 'right' }
];

// 格式化时间
function formatTime(time: string | undefined) {
  if (!time) return '';
  return time.replace('T', ' ');
}

// 加载列表数据
async function loadList() {
  loading.value = true;
  try {
    const params = {
      pageNum: pagination.current,
      pageSize: pagination.pageSize,
      postName: searchForm.postName || undefined,
      status: searchForm.status || undefined
    };
    const res: any = await listPost(params);
    // 响应拦截器已解包，res 就是 data 对象
    dataList.value = res.records || [];
    pagination.total = res.total || 0;
  } catch (error) {
    console.error('加载岗位列表失败:', error);
  } finally {
    loading.value = false;
  }
}

// 搜索
function handleSearch() {
  pagination.current = 1;
  loadList();
}

// 重置搜索
function resetSearch() {
  searchForm.postName = '';
  searchForm.status = '';
  pagination.current = 1;
  loadList();
}

// 分页变化
function handlePageChange(page: number) {
  pagination.current = page;
  loadList();
}

function handlePageSizeChange(pageSize: number) {
  pagination.pageSize = pageSize;
  pagination.current = 1;
  loadList();
}

// 新增
function handleAdd() {
  dialogTitle.value = '新增岗位';
  formData.value = {
    postCode: '',
    postName: '',
    postSort: 0,
    status: '0',
    remark: ''
  };
  dialogVisible.value = true;
}

// 编辑
async function handleEdit(record: Post) {
  dialogTitle.value = '编辑岗位';
  try {
    const res: any = await getPost(record.postId!);
    formData.value = { ...res };
    dialogVisible.value = true;
  } catch (error) {
    Message.error('获取岗位详情失败');
  }
}

// 提交表单
async function handleSubmit(done: (closed: boolean) => void) {
  if (!formRef.value) {
    done(false);
    return;
  }

  const errors = await formRef.value.validate();
  if (errors) {
    done(false);
    return;
  }

  try {
    let res: any;
    if (formData.value.postId) {
      res = await updatePost(formData.value);
    } else {
      res = await addPost(formData.value);
    }
    
    // 响应拦截器已解包，成功时直接返回 data
    Message.success('操作成功');
    loadList();
    done(true);
  } catch (error: any) {
    Message.error(error?.response?.data?.msg || '操作失败');
    done(false);
  }
}

// 取消
function handleCancel() {
  formRef.value?.resetFields();
}

// 删除
function handleDelete(record: Post) {
  Modal.confirm({
    title: '删除确认',
    content: `确定要删除岗位"${record.postName}"吗？`,
    okText: '确定',
    cancelText: '取消',
    onOk: async () => {
      try {
        const res: any = await deletePost(record.postId!);
        // 响应拦截器已解包，成功时直接返回 data
        Message.success('删除成功');
        loadList();
      } catch (error: any) {
        Message.error(error?.response?.data?.msg || '删除失败');
      }
    }
  });
}

// 批量删除
function handleBatchDelete() {
  if (selectedKeys.value.length === 0) {
    Message.warning('请选择要删除的岗位');
    return;
  }
  Modal.confirm({
    title: '批量删除确认',
    content: `确定要删除选中的 ${selectedKeys.value.length} 个岗位吗？`,
    okText: '确定',
    cancelText: '取消',
    onOk: async () => {
      try {
        const res: any = await deletePostBatch(selectedKeys.value);
        // 响应拦截器已解包，成功时直接返回 data
        Message.success('删除成功');
        selectedKeys.value = [];
        loadList();
      } catch (error: any) {
        Message.error(error?.response?.data?.msg || '删除失败');
      }
    }
  });
}

onMounted(() => {
  loadList();
});
</script>

<style scoped>
.post-management {
  padding: 0px;
}
</style>
