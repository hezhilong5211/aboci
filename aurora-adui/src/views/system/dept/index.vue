<template>
  <div class="dept-management">
    <!-- 列表卡片 -->
    <a-card class="table-card" title="部门列表">
      <!-- 搜索区域 -->
      <a-row :gutter="16" style="margin-bottom: 16px;">
        <a-col :xs="24" :sm="12" :md="8" :lg="6" :xl="6" style="margin-bottom: 8px;">
          <a-form-item label="部门名称" :label-col-style="{ width: '80px' }" :style="{ marginBottom: 0 }">
            <a-input
              v-model="searchForm.deptName"
              placeholder="请输入部门名称"
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
            <a-button @click="toggleExpandAll">
              {{ isExpandAll ? '折叠' : '展开' }}
            </a-button>
          </a-space>
        </a-col>
      </a-row>

      <!-- 操作按钮 -->
      <div style="margin-bottom: 16px;">
        <a-button type="primary" @click="handleAdd()" v-hasPermi="['system:dept:add']">
          <template #icon><icon-plus /></template>
          新增
        </a-button>
      </div>

      <!-- 树形表格 -->
      <a-table
        :columns="columns"
        :data="filteredData"
        :loading="loading"
        :pagination="false"
        row-key="deptId"
        :default-expand-all-rows="true"
        v-model:expanded-keys="expandedKeys"
      >
        <template #columns>
          <a-table-column title="部门名称" data-index="deptName" :width="260" />
          <a-table-column title="排序" data-index="orderNum" :width="80" align="center" />
          <a-table-column title="负责人" data-index="leader" :width="120" align="center">
            <template #cell="{ record }">
              <span>{{ record.leader || '-' }}</span>
            </template>
          </a-table-column>
          <a-table-column title="联系电话" data-index="phone" :width="130" align="center">
            <template #cell="{ record }">
              <span>{{ record.phone || '-' }}</span>
            </template>
          </a-table-column>
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
          <a-table-column title="操作" align="center" :width="200" fixed="right">
            <template #cell="{ record }">
              <a-space>
                <a-button type="text" size="small" @click="handleAdd(record)" v-hasPermi="['system:dept:add']">
                  新增
                </a-button>
                <a-button type="text" size="small" @click="handleEdit(record)" v-hasPermi="['system:dept:edit']">
                  编辑
                </a-button>
                <a-button type="text" size="small" status="danger" @click="handleDelete(record)" v-hasPermi="['system:dept:remove']">
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
      width="600px"
      @before-ok="handleSubmit"
      @cancel="handleCancel"
    >
      <a-form
        ref="formRef"
        :model="formData"
        :rules="formRules"
        layout="horizontal"
        :label-col-props="{ span: 5 }"
        :wrapper-col-props="{ span: 19 }"
      >
        <a-form-item label="上级部门" field="parentId">
          <a-tree-select
            v-model="formData.parentId"
            :data="deptTreeOptions"
            :field-names="{ key: 'deptId', title: 'deptName', children: 'children' }"
            placeholder="请选择上级部门"
            allow-clear
            :disabled="formData.deptId === 100"
          />
        </a-form-item>
        <a-form-item label="部门名称" field="deptName">
          <a-input v-model="formData.deptName" placeholder="请输入部门名称" />
        </a-form-item>
        <a-form-item label="显示顺序" field="orderNum">
          <a-input-number v-model="formData.orderNum" :min="0" placeholder="请输入显示顺序" style="width: 100%" />
        </a-form-item>
        <a-form-item label="负责人" field="leader">
          <a-input v-model="formData.leader" placeholder="请输入负责人" />
        </a-form-item>
        <a-form-item label="联系电话" field="phone">
          <a-input v-model="formData.phone" placeholder="请输入联系电话" />
        </a-form-item>
        <a-form-item label="邮箱" field="email">
          <a-input v-model="formData.email" placeholder="请输入邮箱" />
        </a-form-item>
        <a-form-item label="状态" field="status">
          <a-radio-group v-model="formData.status">
            <a-radio value="0">正常</a-radio>
            <a-radio value="1">停用</a-radio>
          </a-radio-group>
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue';
import { Message, Modal } from '@arco-design/web-vue';
import { IconPlus, IconSearch, IconRefresh } from '@arco-design/web-vue/es/icon';
import { listDept, getDept, addDept, updateDept, deleteDept } from '@/api/deptPost';

interface Dept {
  deptId?: number;
  parentId: number;
  deptName: string;
  orderNum: number;
  leader?: string;
  phone?: string;
  email?: string;
  status: string;
  ancestors?: string;
  createTime?: string;
  children?: Dept[];
}

const dataList = ref<Dept[]>([]);
const loading = ref(false);
const dialogVisible = ref(false);
const dialogTitle = ref('新增部门');
const formRef = ref();
const expandedKeys = ref<number[]>([]);
const isExpandAll = ref(true);

const searchForm = reactive({
  deptName: '',
  status: ''
});

const formData = ref<Dept>({
  parentId: 0,
  deptName: '',
  orderNum: 0,
  leader: '',
  phone: '',
  email: '',
  status: '0'
});

const formRules = {
  parentId: [{ required: true, message: '请选择上级部门' }],
  deptName: [{ required: true, message: '请输入部门名称' }],
  orderNum: [{ required: true, message: '请输入显示顺序' }]
};

const columns = [
  { title: '部门名称', dataIndex: 'deptName', width: 260 },
  { title: '排序', dataIndex: 'orderNum', width: 80, align: 'center' },
  { title: '负责人', dataIndex: 'leader', width: 120, align: 'center' },
  { title: '联系电话', dataIndex: 'phone', width: 130, align: 'center' },
  { title: '状态', dataIndex: 'status', slotName: 'status', width: 100 },
  { title: '创建时间', dataIndex: 'createTime', width: 180 },
  { title: '操作', slotName: 'operations', width: 200, fixed: 'right' }
];

// 部门树选项（用于选择上级部门）
const deptTreeOptions = computed(() => {
  // 添加一个顶级节点
  return [
    {
      deptId: 0,
      deptName: '顶级部门',
      children: dataList.value
    }
  ];
});

// 过滤后的数据
const filteredData = computed(() => {
  if (!searchForm.deptName && !searchForm.status) {
    return dataList.value;
  }
  
  // 过滤逻辑
  function filterTree(nodes: Dept[]): Dept[] {
    return nodes.filter(node => {
      const nameMatch = !searchForm.deptName || node.deptName.includes(searchForm.deptName);
      const statusMatch = !searchForm.status || node.status === searchForm.status;
      
      if (node.children && node.children.length > 0) {
        const filteredChildren = filterTree(node.children);
        if (filteredChildren.length > 0) {
          node.children = filteredChildren;
          return true;
        }
      }
      
      return nameMatch && statusMatch;
    });
  }
  
  // 深拷贝后过滤
  const clonedData = JSON.parse(JSON.stringify(dataList.value));
  return filterTree(clonedData);
});

// 格式化时间
function formatTime(time: string | undefined) {
  if (!time) return '';
  return time.replace('T', ' ');
}

// 获取所有部门ID（用于展开/折叠）
function getAllDeptIds(nodes: Dept[]): number[] {
  const ids: number[] = [];
  nodes.forEach(node => {
    if (node.deptId) {
      ids.push(node.deptId);
    }
    if (node.children && node.children.length > 0) {
      ids.push(...getAllDeptIds(node.children));
    }
  });
  return ids;
}

// 切换展开/折叠
function toggleExpandAll() {
  if (isExpandAll.value) {
    expandedKeys.value = [];
  } else {
    expandedKeys.value = getAllDeptIds(dataList.value);
  }
  isExpandAll.value = !isExpandAll.value;
}

// 加载列表数据
async function loadList() {
  loading.value = true;
  try {
    const res: any = await listDept();
    dataList.value = res || [];
    // 默认展开所有
    if (isExpandAll.value) {
      expandedKeys.value = getAllDeptIds(dataList.value);
    }
  } catch (error) {
    console.error('加载部门列表失败:', error);
  } finally {
    loading.value = false;
  }
}

// 搜索
function handleSearch() {
  // 前端过滤，无需重新请求
}

// 重置搜索
function resetSearch() {
  searchForm.deptName = '';
  searchForm.status = '';
}

// 新增
function handleAdd(parent?: Dept) {
  dialogTitle.value = '新增部门';
  formData.value = {
    parentId: parent?.deptId || 0,
    deptName: '',
    orderNum: 0,
    leader: '',
    phone: '',
    email: '',
    status: '0'
  };
  dialogVisible.value = true;
}

// 编辑
async function handleEdit(record: Dept) {
  dialogTitle.value = '编辑部门';
  try {
    const res: any = await getDept(record.deptId!);
    formData.value = { ...res };
    dialogVisible.value = true;
  } catch (error) {
    Message.error('获取部门详情失败');
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
    if (formData.value.deptId) {
      res = await updateDept(formData.value);
    } else {
      res = await addDept(formData.value);
    }
    
    // 响应拦截器已解包，成功时直接返回 data（无 code 字段）
    // 如果进入 catch，说明失败
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
function handleDelete(record: Dept) {
  Modal.confirm({
    title: '删除确认',
    content: `确定要删除部门"${record.deptName}"吗？`,
    okText: '确定',
    cancelText: '取消',
    onOk: async () => {
      try {
        const res: any = await deleteDept(record.deptId!);
        // 响应拦截器已解包，成功时直接返回 data
        Message.success('删除成功');
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
.dept-management {
  padding: 0px;
}
</style>
