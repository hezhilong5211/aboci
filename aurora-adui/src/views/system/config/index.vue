<template>
  <div class="app-container">
    <!-- 列表卡片 -->
    <a-card class="table-card" title="参数设置列表">
      <!-- 搜索区域 -->
      <a-row :gutter="16" style="margin-bottom: 16px;">
        <a-col :xs="24" :sm="12" :md="8" :lg="6" :xl="6" style="margin-bottom: 8px;">
          <a-form-item label="参数名称" :label-col-style="{ width: '80px' }" :style="{ marginBottom: 0 }">
            <a-input
              v-model="queryParams.configName"
              placeholder="请输入参数名称"
              allow-clear
              @press-enter="handleQuery"
            />
          </a-form-item>
        </a-col>
        <a-col :xs="24" :sm="12" :md="8" :lg="6" :xl="6" style="margin-bottom: 8px;">
          <a-form-item label="参数键名" :label-col-style="{ width: '80px' }" :style="{ marginBottom: 0 }">
            <a-input
              v-model="queryParams.configKey"
              placeholder="请输入参数键名"
              allow-clear
              @press-enter="handleQuery"
            />
          </a-form-item>
        </a-col>
        <a-col :xs="24" :sm="12" :md="8" :lg="6" :xl="6" style="margin-bottom: 8px;">
          <a-form-item label="系统内置" :label-col-style="{ width: '80px' }" :style="{ marginBottom: 0 }">
            <a-select v-model="queryParams.configType" placeholder="系统内置" allow-clear>
              <a-option value="Y">是</a-option>
              <a-option value="N">否</a-option>
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
          <a-button type="primary" @click="handleAdd" v-hasPermi="['system:config:add']">
            <template #icon><icon-plus /></template>
            新增
          </a-button>
          <a-button
            type="primary"
            status="danger"
            :disabled="selectedRowKeys.length === 0"
            @click="handleBatchDelete"
            v-hasPermi="['system:config:remove']"
          >
            <template #icon><icon-delete /></template>
            批量删除
          </a-button>
        </a-space>
      </div>

      <a-table
        :loading="loading"
        :data="configList"
        :row-selection="rowSelection"
        :pagination="pagination"
        row-key="configId"
        @page-change="handlePageChange"
        @page-size-change="handlePageSizeChange"
        v-model:selected-keys="selectedRowKeys"
      >
        <template #columns>
          <a-table-column title="参数主键" data-index="configId" align="center" :width="100" />
          <a-table-column title="参数名称" data-index="configName" align="center" ellipsis tooltip />
          <a-table-column title="参数键名" data-index="configKey" align="center" ellipsis tooltip />
          <a-table-column title="参数键值" data-index="configValue" align="center" ellipsis tooltip />
          <a-table-column title="系统内置" data-index="configType" align="center" :width="100">
            <template #cell="{ record }">
              <a-tag :color="record.configType === 'Y' ? 'green' : 'red'">
                {{ record.configType === 'Y' ? '是' : '否' }}
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
                <a-button type="text" size="small" @click="handleUpdate(record)" v-hasPermi="['system:config:edit']">
                  <template #icon><icon-edit /></template>
                  编辑
                </a-button>
                <a-button
                  type="text"
                  size="small"
                  status="danger"
                  @click="handleDelete(record)"
                  v-hasPermi="['system:config:remove']"
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

    <!-- 新增/编辑参数弹窗 -->
    <a-modal
      v-model:visible="open"
      :title="title"
      @before-ok="submitForm"
      @cancel="reset"
    >
      <a-form
        ref="formRef"
        :model="form"
        :rules="rules"
        layout="horizontal"
        :label-col-props="{ span: 6 }"
        :wrapper-col-props="{ span: 18 }"
      >
        <a-form-item label="参数名称" field="configName">
          <a-input v-model="form.configName" placeholder="请输入参数名称" />
        </a-form-item>
        <a-form-item label="参数键名" field="configKey">
          <a-input v-model="form.configKey" placeholder="请输入参数键名" />
        </a-form-item>
        <a-form-item label="参数键值" field="configValue">
          <a-input v-model="form.configValue" placeholder="请输入参数键值" />
        </a-form-item>
        <a-form-item label="系统内置" field="configType">
          <a-radio-group v-model="form.configType">
            <a-radio value="Y">是</a-radio>
            <a-radio value="N">否</a-radio>
          </a-radio-group>
        </a-form-item>
        <a-form-item label="备注">
          <a-textarea
            v-model="form.remark"
            :rows="3"
            placeholder="请输入备注"
          />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue';
import { Message, Modal } from '@arco-design/web-vue';
import { IconPlus, IconSearch, IconRefresh, IconDelete, IconEdit } from '@arco-design/web-vue/es/icon';
import { listConfig, getConfig, addConfig, updateConfig, deleteConfig } from '@/api/system';

interface SysConfig {
  configId?: number;
  configName: string;
  configKey: string;
  configValue: string;
  configType: string;
  remark?: string;
  createTime?: string;
}

const configList = ref<SysConfig[]>([]);
const loading = ref(false);
const selectedRowKeys = ref<number[]>([]);
const open = ref(false);
const title = ref("");
const formRef = ref();

const queryParams = reactive({
  configName: '',
  configKey: '',
  configType: ''
});

const form = ref<SysConfig>({
  configName: '',
  configKey: '',
  configValue: '',
  configType: 'N',
  remark: ''
});

const rules = {
  configName: [{ required: true, message: "参数名称不能为空" }],
  configKey: [{ required: true, message: "参数键名不能为空" }],
  configValue: [{ required: true, message: "参数键值不能为空" }]
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
  showCheckedAll: true
};

function getList() {
  loading.value = true;
  listConfig({
    pageNum: pagination.current,
    pageSize: pagination.pageSize,
    ...queryParams
  }).then((response: any) => {
    configList.value = response.records;
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
  queryParams.configName = '';
  queryParams.configKey = '';
  queryParams.configType = '';
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
    configId: undefined,
    configName: '',
    configKey: '',
    configValue: '',
    configType: 'N',
    remark: ''
  };
}

function handleAdd() {
  reset();
  open.value = true;
  title.value = "添加参数设置";
}

function handleUpdate(row: SysConfig) {
  reset();
  const configId = row.configId!;
  getConfig(configId).then((response: any) => {
    form.value = response;
    open.value = true;
    title.value = "修改参数设置";
  });
}

function submitForm() {
  formRef.value.validate((errors: any) => {
    if (!errors) {
      if (form.value.configId) {
        updateConfig(form.value).then(() => {
          Message.success("修改成功");
          open.value = false;
          getList();
        });
      } else {
        addConfig(form.value).then(() => {
          Message.success("新增成功");
          open.value = false;
          getList();
        });
      }
    }
  });
}

function handleDelete(row: SysConfig) {
  const configId = row.configId!;
  Modal.confirm({
    title: '确认删除',
    content: `确定要删除参数"${row.configName}"吗？`,
    onOk: () => {
      deleteConfig(configId).then(() => {
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
    content: `确定要删除选中的 ${selectedRowKeys.value.length} 个参数吗？`,
    onOk: () => {
      deleteConfig(selectedRowKeys.value).then(() => {
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

onMounted(() => {
  getList();
});
</script>

<style scoped>
.app-container {
  padding: 0px;
}
</style>
