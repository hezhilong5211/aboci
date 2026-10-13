<template>
  <div class="app-container">
    <!-- 列表卡片 -->
    <a-card class="table-card" title="字典配置列表">
      <!-- 搜索区域 -->
      <a-row :gutter="16" style="margin-bottom: 16px;">
        <a-col :xs="24" :sm="12" :md="8" :lg="6" :xl="6" style="margin-bottom: 8px;">
          <a-form-item label="字典名称" :label-col-style="{ width: '80px' }" :style="{ marginBottom: 0 }">
            <a-input
              v-model="queryParams.dictName"
              placeholder="请输入字典名称"
              allow-clear
              @press-enter="handleQuery"
            />
          </a-form-item>
        </a-col>
        <a-col :xs="24" :sm="12" :md="8" :lg="6" :xl="6" style="margin-bottom: 8px;">
          <a-form-item label="字典类型" :label-col-style="{ width: '80px' }" :style="{ marginBottom: 0 }">
            <a-input
              v-model="queryParams.dictType"
              placeholder="请输入字典类型"
              allow-clear
              @press-enter="handleQuery"
            />
          </a-form-item>
        </a-col>
        <a-col :xs="24" :sm="12" :md="8" :lg="6" :xl="6" style="margin-bottom: 8px;">
          <a-form-item label="状态" :label-col-style="{ width: '80px' }" :style="{ marginBottom: 0 }">
            <a-select v-model="queryParams.status" placeholder="状态" allow-clear>
              <a-option value="0">正常</a-option>
              <a-option value="1">停用</a-option>
            </a-select>
          </a-form-item>
        </a-col>
        <a-col :xs="24" :sm="12" :md="24" :lg="6" :xl="6" style="margin-bottom: 8px;">
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

      <!-- 操作按钮 -->
      <div style="margin-bottom: 16px;">
        <a-space>
          <a-button type="primary" @click="handleAdd" v-hasPermi="['system:dict:add']">
            <template #icon><icon-plus /></template>
            新增
          </a-button>
          <a-button
            type="primary"
            status="danger"
            :disabled="selectedRowKeys.length === 0"
            @click="handleBatchDelete"
            v-hasPermi="['system:dict:remove']"
          >
            <template #icon><icon-delete /></template>
            批量删除
          </a-button>
        </a-space>
      </div>

      <a-table
        :loading="loading"
        :data="typeList"
        :row-selection="rowSelection"
        :pagination="pagination"
        row-key="dictId"
        @page-change="handlePageChange"
        @page-size-change="handlePageSizeChange"
        v-model:selected-keys="selectedRowKeys"
      >
        <template #columns>
          <a-table-column title="字典编号" data-index="dictId" align="center" :width="100" />
          <a-table-column title="字典名称" data-index="dictName" align="center" ellipsis tooltip />
          <a-table-column title="字典类型" data-index="dictType" align="center" ellipsis tooltip />
          <a-table-column title="状态" data-index="status" align="center" :width="80">
            <template #cell="{ record }">
              <a-tag :color="record.status === '0' ? 'green' : 'red'">
                {{ record.status === '0' ? '正常' : '停用' }}
              </a-tag>
            </template>
          </a-table-column>
          <a-table-column title="备注" data-index="remark" align="center" ellipsis tooltip />
          <a-table-column title="创建时间" data-index="createTime" align="center" :width="180">
            <template #cell="{ record }">
              <span>{{ parseTime(record.createTime) }}</span>
            </template>
          </a-table-column>
          <a-table-column title="操作" align="center" :width="250" fixed="right">
            <template #cell="{ record }">
              <a-space>
                <a-button type="text" size="small" @click="handleData(record)">
                  <template #icon><icon-list /></template>
                  数据
                </a-button>
                <a-button type="text" size="small" @click="handleUpdate(record)" v-hasPermi="['system:dict:edit']">
                  <template #icon><icon-edit /></template>
                  编辑
                </a-button>
                <a-button type="text" size="small" status="danger" @click="handleDelete(record)" v-hasPermi="['system:dict:remove']">
                  <template #icon><icon-delete /></template>
                  删除
                </a-button>
              </a-space>
            </template>
          </a-table-column>
        </template>
      </a-table>
    </a-card>

    <!-- 表单弹窗 -->
    <a-modal
      v-model:visible="open"
      :title="title"
      @ok="submitForm"
      @cancel="cancel"
    >
      <a-form ref="formRef" :model="form" :rules="rules" layout="horizontal" :label-col-props="{span: 6}" :wrapper-col-props="{span: 18}">
        <a-form-item label="字典名称" field="dictName">
          <a-input v-model="form.dictName" placeholder="请输入字典名称" />
        </a-form-item>
        <a-form-item label="字典类型" field="dictType">
          <a-input v-model="form.dictType" placeholder="请输入字典类型" />
        </a-form-item>
        <a-form-item label="状态" field="status">
          <a-radio-group v-model="form.status">
            <a-radio value="0">正常</a-radio>
            <a-radio value="1">停用</a-radio>
          </a-radio-group>
        </a-form-item>
        <a-form-item label="备注" field="remark">
          <a-textarea v-model="form.remark" placeholder="请输入内容" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue';
import { Message, Modal } from '@arco-design/web-vue';
import { IconSearch, IconRefresh, IconPlus, IconDelete, IconEdit, IconList } from '@arco-design/web-vue/es/icon';
import { listType, getType, addType, updateType, delType, SysDictType } from '@/api/dict';

const loading = ref(true);
const selectedRowKeys = ref<number[]>([]);
const typeList = ref<SysDictType[]>([]);
const title = ref("");
const open = ref(false);
const formRef = ref();

const queryParams = reactive({
  dictName: undefined,
  dictType: undefined,
  status: undefined
});

const form = ref<SysDictType>({
  dictName: '',
  dictType: '',
  status: '0',
  remark: ''
});

const rules = {
  dictName: [{ required: true, message: "字典名称不能为空" }],
  dictType: [{ required: true, message: "字典类型不能为空" }]
};

const pagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0,
  showTotal: true,
  showPageSize: true,
});

const rowSelection = {
  type: 'checkbox' as const,
  showCheckedAll: true
};

function getList() {
  loading.value = true;
  listType({
    pageNum: pagination.current,
    pageSize: pagination.pageSize,
    ...queryParams
  }).then((response: any) => {
    typeList.value = response.records;
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
  queryParams.dictName = undefined;
  queryParams.dictType = undefined;
  queryParams.status = undefined;
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
    dictId: undefined,
    dictName: '',
    dictType: '',
    status: '0',
    remark: ''
  };
}

function handleAdd() {
  reset();
  open.value = true;
  title.value = "添加字典类型";
}

function handleData(row: SysDictType) {
  Message.info(`正在跳转到 "${row.dictName}" 的数据管理... (功能开发中)`);
}

function handleUpdate(row: SysDictType) {
  reset();
  const dictId = row.dictId!;
  getType(dictId).then((response: any) => {
    form.value = response;
    open.value = true;
    title.value = "修改字典类型";
  });
}

function submitForm() {
  formRef.value.validate((errors: any) => {
    if (!errors) {
      if (form.value.dictId) {
        updateType(form.value).then(() => {
          Message.success("修改成功");
          open.value = false;
          getList();
        });
      } else {
        addType(form.value).then(() => {
          Message.success("新增成功");
          open.value = false;
          getList();
        });
      }
    }
  });
}

function handleDelete(row: SysDictType) {
  const dictId = row.dictId!;
  Modal.confirm({
    title: '系统提示',
    content: `是否确认删除字典编号为"${dictId}"的数据项?`,
    onOk: () => {
      delType(dictId).then(() => {
        Message.success("删除成功");
        getList();
      });
    }
  });
}

function handleBatchDelete() {
  if (selectedRowKeys.value.length === 0) return;
  Modal.confirm({
    title: '系统提示',
    content: `是否确认删除选中的数据项?`,
    onOk: () => {
      delType(selectedRowKeys.value).then(() => {
        Message.success("删除成功");
        selectedRowKeys.value = [];
        getList();
      });
    }
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
});
</script>

<style scoped>
.app-container {
  padding: 0px;
}
</style>
