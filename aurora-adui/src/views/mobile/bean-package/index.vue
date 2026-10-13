<template>
  <div class="app-container">
    <a-card class="table-card" title="豆子套餐管理">
      <template #extra>
        <a-button type="primary" @click="handleAdd" v-hasPermi="['mobile:beanpackage:add']">
          <template #icon><icon-plus /></template>
          新增套餐
        </a-button>
      </template>

      <!-- 搜索区域 -->
      <a-row :gutter="16" style="margin-bottom: 16px;">
        <a-col :xs="24" :sm="12" :md="6">
          <a-form-item label="状态" :label-col-style="{ width: '60px' }" :style="{ marginBottom: 0 }">
            <a-select v-model="query.status" placeholder="全部状态" allow-clear>
              <a-option value="0">上架</a-option>
              <a-option value="1">下架</a-option>
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
        :scroll="{ x: 1100 }"
        row-key="packageId"
        @page-change="handlePageChange"
        @page-size-change="handlePageSizeChange"
      >
        <template #columns>
          <a-table-column title="套餐名称" data-index="name" :width="160" ellipsis tooltip />
          <a-table-column title="豆数" data-index="beans" align="center" :width="120" />
          <a-table-column title="价格(元)" align="center" :width="120">
            <template #cell="{ record }">{{ record.price }}</template>
          </a-table-column>
          <a-table-column title="微信道具 ID" data-index="virtualProductId" align="center" :width="180" ellipsis tooltip>
            <template #cell="{ record }">{{ record.virtualProductId || '-' }}</template>
          </a-table-column>
          <a-table-column title="排序" data-index="sort" align="center" :width="80" />
          <a-table-column title="状态" align="center" :width="100">
            <template #cell="{ record }">
              <a-tag :color="record.status === '0' ? 'green' : 'gray'">
                {{ record.status === '0' ? '上架' : '下架' }}
              </a-tag>
            </template>
          </a-table-column>
          <a-table-column title="备注" data-index="remark" align="center" ellipsis tooltip />
          <a-table-column title="操作" align="center" :width="140">
            <template #cell="{ record }">
              <a-space>
                <a-button type="text" size="small" @click="handleEdit(record)" v-hasPermi="['mobile:beanpackage:edit']">编辑</a-button>
                <a-button type="text" size="small" status="danger" @click="handleRemove(record)" v-hasPermi="['mobile:beanpackage:remove']">删除</a-button>
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
      :width="620"
      :mask-closable="false"
      @before-ok="submitForm"
      @cancel="formOpen = false"
    >
      <a-form ref="formRef" :model="form" :rules="formRules"
              :label-col-props="{ span: 6 }" :wrapper-col-props="{ span: 18 }">
        <a-form-item label="套餐名称" field="name">
          <a-input v-model="form.name" placeholder="例如：豆子加油包" :max-length="64" allow-clear />
        </a-form-item>
        <a-form-item label="支付方式">
          <a-input model-value="微信虚拟支付·普通道具" readonly />
        </a-form-item>
        <a-form-item label="微信道具 ID" field="virtualProductId">
          <a-input
            v-model="form.virtualProductId"
            placeholder="请输入公众平台道具 ID"
            :max-length="20"
            allow-clear
          />
          <template #extra>仅支持英文字母、数字和下划线，最长 20 位</template>
        </a-form-item>
        <a-form-item label="到账豆数" field="beans">
          <a-input-number v-model="form.beans" placeholder="请输入到账豆数" :min="1" :step="1" style="width: 100%;" />
        </a-form-item>
        <a-form-item label="价格(元)" field="price">
          <a-input-number
            v-model="form.price"
            placeholder="请输入价格"
            :min="0.01"
            :max="10000"
            :precision="2"
            :step="0.01"
            style="width: 100%;"
          />
          <template #extra>价格必须与小程序公众平台道具价格完全一致</template>
        </a-form-item>
        <a-form-item label="排序" field="sort">
          <a-input-number v-model="form.sort" placeholder="数值越小越靠前" :min="0" :step="1" style="width: 100%;" />
        </a-form-item>
        <a-form-item label="状态" field="status">
          <a-switch v-model="form.status" checked-value="0" unchecked-value="1">
            <template #checked>上架</template>
            <template #unchecked>下架</template>
          </a-switch>
        </a-form-item>
        <a-form-item label="备注" field="remark">
          <a-textarea v-model="form.remark" placeholder="请输入备注" :auto-size="{ minRows: 2, maxRows: 4 }" allow-clear />
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
  listBeanPackage,
  addBeanPackage,
  updateBeanPackage,
  deleteBeanPackage,
  BeanRechargePackage
} from '@/api/mobile/beanPackage';

// ---- 列表 ----
const loading = ref(false);
const list = ref<BeanRechargePackage[]>([]);
const query = reactive({
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
  listBeanPackage({
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

// ---- 新增/编辑弹窗 ----
const formOpen = ref(false);
const formRef = ref();
const formTitle = ref('新增套餐');
const form = reactive<BeanRechargePackage>({
  packageId: undefined,
  name: '',
  beans: undefined,
  price: undefined,
  virtualProductId: '',
  sort: 0,
  status: '0',
  remark: ''
});

const formRules = {
  name: [{ required: true, message: '请输入套餐名称' }],
  virtualProductId: [
    {
      validator: (value: string | undefined, callback: (error?: string) => void) => {
        if (form.status === '0' && !value) {
          callback('上架套餐必须填写微信道具 ID');
          return;
        }
        if (value && !/^[A-Za-z0-9_]{1,20}$/.test(value)) {
          callback('道具 ID 仅支持英文字母、数字和下划线，最长 20 位');
          return;
        }
        callback();
      }
    }
  ],
  beans: [{ required: true, message: '请输入到账豆数' }],
  price: [
    { required: true, message: '请输入价格' },
    {
      validator: (value: number | undefined, callback: (error?: string) => void) => {
        if (value != null && (value < 0.01 || value > 10000)) {
          callback('价格必须在 0.01 至 10000.00 元之间');
          return;
        }
        if (value != null && Math.abs(value * 100 - Math.round(value * 100)) > 1e-8) {
          callback('价格最多保留两位小数');
          return;
        }
        callback();
      }
    }
  ],
  sort: [{ required: true, message: '请输入排序' }]
};

// 重置弹窗到新增初始态
function resetForm() {
  form.packageId = undefined;
  form.name = '';
  form.beans = undefined;
  form.price = undefined;
  form.virtualProductId = '';
  form.sort = 0;
  form.status = '0';
  form.remark = '';
}

function handleAdd() {
  resetForm();
  formTitle.value = '新增套餐';
  formOpen.value = true;
}

function handleEdit(record: BeanRechargePackage) {
  resetForm();
  form.packageId = record.packageId;
  form.name = record.name ?? '';
  form.beans = record.beans;
  form.price = record.price;
  form.virtualProductId = record.virtualProductId ?? '';
  form.sort = record.sort ?? 0;
  form.status = record.status || '0';
  form.remark = record.remark || '';
  formTitle.value = '编辑套餐';
  formOpen.value = true;
}

function submitForm(done: (closed: boolean) => void) {
  formRef.value.validate((errors: any) => {
    if (errors) {
      done(false);
      return;
    }
    const payload: BeanRechargePackage = {
      packageId: form.packageId,
      name: form.name,
      beans: form.beans,
      price: form.price,
      virtualProductId: form.virtualProductId,
      sort: form.sort,
      status: form.status,
      remark: form.remark
    };
    const req = form.packageId ? updateBeanPackage(payload) : addBeanPackage(payload);
    req.then(() => {
      Message.success(form.packageId ? '修改成功' : '新增成功');
      getList();
      done(true);
    }).catch(() => done(false));
  });
}

function handleRemove(record: BeanRechargePackage) {
  Modal.confirm({
    title: '删除确认',
    content: `确认要删除套餐 "${record.name || `${record.beans} 豆`}" 吗?`,
    onOk: () => {
      deleteBeanPackage(record.packageId!).then(() => {
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
</style>
