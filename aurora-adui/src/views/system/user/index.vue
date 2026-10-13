<template>
  <div class="user-management">
    <!-- 用户列表 -->
    <a-card class="table-card" title="用户列表">
      <!-- 搜索区域 -->
      <a-row :gutter="16" style="margin-bottom: 16px;">
        <a-col :xs="24" :sm="12" :md="8" :lg="6" :xl="6" style="margin-bottom: 8px;">
          <a-form-item label="用户名" :label-col-style="{ width: '80px' }" :style="{ marginBottom: 0 }">
            <a-input
              v-model="searchForm.userName"
              placeholder="请输入用户名"
              allow-clear
              @press-enter="handleSearch"
            />
          </a-form-item>
        </a-col>
        <a-col :xs="24" :sm="12" :md="8" :lg="6" :xl="6" style="margin-bottom: 8px;">
          <a-form-item label="状态" :label-col-style="{ width: '80px' }" :style="{ marginBottom: 0 }">
            <a-select
              v-model="searchForm.status"
              placeholder="请选择状态"
              allow-clear
            >
              <a-option value="">全部</a-option>
              <a-option value="0">正常</a-option>
              <a-option value="1">停用</a-option>
            </a-select>
          </a-form-item>
        </a-col>
        <a-col :xs="24" :sm="24" :md="8" :lg="12" :xl="12" style="margin-bottom: 8px;">
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
        <a-button type="primary" @click="handleAdd" v-hasPermi="['system:user:add']">
          <template #icon>
            <icon-plus />
          </template>
          新增用户
        </a-button>
      </div>

      <a-table
        :columns="columns"
        :data="userList"
        :loading="loading"
        :pagination="pagination"
        :bordered="false"
        :scroll="{ x: 1400 }"
        row-key="userId"
        @page-change="handlePageChange"
        @page-size-change="handlePageSizeChange"
      >
        <template #status="{ record }">
          <a-tag :color="record.status === '0' ? 'green' : 'red'">
            {{ record.status === '0' ? '正常' : '停用' }}
          </a-tag>
        </template>

        <template #operations="{ record }">
          <a-space>
            <!-- 超级管理员(userId=1)不显示编辑、删除、重置密码按钮 -->
            <a-button v-if="record.userId !== 1" type="text" size="small" @click="handleEdit(record)" v-hasPermi="['system:user:edit']">
              编辑
            </a-button>
            <a-button
              v-if="record.userId !== 1"
              type="text"
              size="small"
              status="danger"
              @click="handleDelete(record)"
              v-hasPermi="['system:user:remove']"
            >
              删除
            </a-button>
            <a-button
              v-if="record.userId !== 1"
              type="text"
              size="small"
              @click="handleResetPwd(record)"
              v-hasPermi="['system:user:resetPwd']"
            >
              重置密码
            </a-button>
            <!-- 超级管理员显示提示文字 -->
            <span v-if="record.userId === 1" style="color: #86909c; font-size: 12px;">超级管理员</span>
          </a-space>
        </template>
      </a-table>
    </a-card>

    <!-- 新增/编辑用户弹窗 -->
    <a-modal
      v-model:visible="dialogVisible"
      :title="dialogTitle"
      width="600px"
      @before-ok="handleSubmit"
      @cancel="resetForm"
    >
      <a-form
        ref="formRef"
        :model="formData"
        :rules="formRules"
        layout="horizontal"
        :label-col-props="{ span: 6 }"
        :wrapper-col-props="{ span: 18 }"
      >
        <a-form-item label="用户名" field="userName">
          <a-input
            v-model="formData.userName"
            placeholder="请输入用户名"
            :disabled="!!formData.userId"
          />
        </a-form-item>
        <a-form-item label="昵称" field="nickName">
          <a-input v-model="formData.nickName" placeholder="请输入昵称" />
        </a-form-item>
        <a-form-item label="邮箱" field="email">
          <a-input v-model="formData.email" placeholder="请输入邮箱" />
        </a-form-item>
        <a-form-item label="手机号" field="phonenumber">
          <a-input
            v-model="formData.phonenumber"
            placeholder="请输入手机号"
          />
        </a-form-item>
        <a-form-item
          v-if="!formData.userId"
          label="密码"
          field="password"
        >
          <a-input-password
            v-model="formData.password"
            placeholder="请输入密码"
          />
        </a-form-item>
        <a-form-item label="状态" field="status">
          <a-radio-group v-model="formData.status">
            <a-radio value="0">正常</a-radio>
            <a-radio value="1">停用</a-radio>
          </a-radio-group>
        </a-form-item>
        <a-form-item label="角色" field="roleIds">
          <a-select
            v-model="selectedRoleIds"
            placeholder="请选择角色"
            multiple
            allow-clear
          >
            <a-option
              v-for="item in roleList"
              :key="item.roleId"
              :value="item.roleId"
            >
              {{ item.roleName }}
            </a-option>
          </a-select>
        </a-form-item>
        <a-form-item label="备注" field="remark">
          <a-textarea
            v-model="formData.remark"
            :rows="3"
            placeholder="请输入备注"
          />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 重置密码弹窗 -->
    <a-modal
      v-model:visible="resetPwdVisible"
      title="重置密码"
      width="400px"
      @before-ok="handleResetPwdSubmit"
    >
      <a-form
        ref="resetPwdFormRef"
        :model="resetPwdForm"
        :rules="resetPwdRules"
        layout="vertical"
      >
        <a-form-item label="新密码" field="password">
          <a-input-password
            v-model="resetPwdForm.password"
            placeholder="请输入新密码"
          />
        </a-form-item>
        <a-form-item label="确认密码" field="confirmPassword">
          <a-input-password
            v-model="resetPwdForm.confirmPassword"
            placeholder="请再次输入新密码"
          />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue';
import { Message, Modal } from '@arco-design/web-vue';
import {
  IconPlus,
  IconSearch,
  IconRefresh,
} from '@arco-design/web-vue/es/icon';
import {
  listUser,
  getUser,
  addUser,
  updateUser,
  deleteUser,
  resetUserPwd,
  listRole,
  getUserRoleIds,
} from '@/api/system';
import { getConfigKey } from '@/api/config';

interface User {
  userId?: number;
  userName: string;
  nickName?: string;
  email?: string;
  phonenumber?: string;
  status: string;
  password?: string;
  remark?: string;
  createTime?: string;
}

interface Role {
  roleId: number;
  roleName: string;
}

const userList = ref<User[]>([]);
const roleList = ref<Role[]>([]); // 角色列表
const loading = ref(false);
const dialogVisible = ref(false);
const dialogTitle = ref('新增用户');
const formRef = ref();
const resetPwdVisible = ref(false);
const resetPwdFormRef = ref();

const initPassword = ref('');

const searchForm = reactive({
  userName: '',
  status: '',
});

// 注意：a-form 的 :model 必须是 reactive 对象；若用 ref 会在模板解包后变成普通对象，
// 触发 Arco 内部 `toRefs() expects a reactive object but received a plain one` 警告
const formData = reactive<User>({
  userName: '',
  nickName: '',
  email: '',
  phonenumber: '',
  status: '0',
  password: '',
  remark: '',
});

/** 新增/编辑弹窗表单的默认值，用于每次打开前或取消后完整重置 */
const defaultFormData = (): User => ({
  userName: '',
  nickName: '',
  email: '',
  phonenumber: '',
  status: '0',
  password: '',
  remark: '',
});

const selectedRoleIds = ref<number[]>([]);

const resetPwdForm = reactive({
  userId: 0,
  password: '',
  confirmPassword: '',
});

const formRules = {
  userName: [
    { required: true, message: '请输入用户名' },
    { minLength: 2, maxLength: 30, message: '用户名长度在 2-30 个字符之间' },
  ],
  status: [{ required: true, message: '请选择状态' }],
  // 新增用户时密码必填；编辑用户时 a-form-item 被 v-if 隐藏，规则自动失效
  password: [
    {
      required: true,
      validator: (value: string, callback: (error?: string) => void) => {
        if (formData.userId) {
          callback();
          return;
        }
        if (!value) {
          callback('请输入密码');
          return;
        }
        if (value.length < 6 || value.length > 20) {
          callback('密码长度在 6-20 个字符之间');
          return;
        }
        callback();
      },
    },
  ],
  email: [
    {
      validator: (value: string, callback: (error?: string) => void) => {
        if (!value) {
          callback();
          return;
        }
        const reg = /^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/;
        if (!reg.test(value)) {
          callback('邮箱格式不正确');
          return;
        }
        callback();
      },
    },
  ],
  phonenumber: [
    {
      validator: (value: string, callback: (error?: string) => void) => {
        if (!value) {
          callback();
          return;
        }
        const reg = /^1[3-9]\d{9}$/;
        if (!reg.test(value)) {
          callback('手机号格式不正确');
          return;
        }
        callback();
      },
    },
  ],
};

const resetPwdRules = {
  password: [{ required: true, message: '请输入新密码' }],
  confirmPassword: [
    { required: true, message: '请再次输入新密码' },
    {
      validator: (value: string, callback: (error?: string) => void) => {
        if (value !== resetPwdForm.password) {
          callback('两次输入的密码不一致');
        } else {
          callback();
        }
      },
    },
  ],
};

// 分页配置
const pagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0,
  showTotal: true,
  showPageSize: true,
  pageSizeOptions: ['10', '20', '50', '100'],
});

// 表格列定义
const columns = [
  {
    title: '用户ID',
    dataIndex: 'userId',
    width: 80,
  },
  {
    title: '用户名',
    dataIndex: 'userName',
    width: 140,
    ellipsis: true,
    tooltip: true,
  },
  {
    title: '昵称',
    dataIndex: 'nickName',
    width: 140,
    ellipsis: true,
    tooltip: true,
  },
  {
    title: '邮箱',
    dataIndex: 'email',
    width: 220,
    ellipsis: true,
    tooltip: true,
  },
  {
    title: '手机号',
    dataIndex: 'phonenumber',
    width: 140,
    ellipsis: true,
    tooltip: true,
  },
  {
    title: '状态',
    dataIndex: 'status',
    slotName: 'status',
    width: 100,
  },
  {
    title: '创建时间',
    dataIndex: 'createTime',
    width: 180,
  },
  {
    title: '操作',
    slotName: 'operations',
    width: 250,
    fixed: 'right',
  },
];

// 加载用户列表
const loadUserList = async () => {
  loading.value = true;
  try {
    const params = {
      pageNum: pagination.current,
      pageSize: pagination.pageSize,
      ...searchForm,
    };
    // 移除空值
    Object.keys(params).forEach((key) => {
      if (params[key] === '' || params[key] === null || params[key] === undefined) {
        delete params[key];
      }
    });

    const res = await listUser(params);
    
    // 处理分页响应
    if (res && res.records) {
      userList.value = res.records || [];
      pagination.total = res.total || 0;
    } else if (Array.isArray(res)) {
      userList.value = res;
      pagination.total = res.length;
    } else {
      userList.value = [];
      pagination.total = 0;
    }
  } catch (error: any) {
    console.error('加载用户列表失败:', error);
    const errorMsg =
      error?.response?.data?.msg || error?.message || '加载用户列表失败';
    Message.error(errorMsg);
    if (error?.response?.status === 403 || error?.response?.status === 401) {
      userList.value = [];
    }
  } finally {
    loading.value = false;
  }
};

// 加载角色列表
const loadRoles = async () => {
  try {
    const res = await listRole({ pageSize: 1000, status: '0' }); // 获取所有可用角色
    if (res && res.records) {
      roleList.value = res.records;
    } else if (Array.isArray(res)) {
      roleList.value = res;
    } else {
      roleList.value = [];
    }
  } catch (error) {
    console.error('加载角色列表失败', error);
  }
};

// 搜索
const handleSearch = () => {
  pagination.current = 1;
  loadUserList();
};

// 重置搜索
const resetSearch = () => {
  searchForm.userName = '';
  searchForm.status = '';
  pagination.current = 1;
  loadUserList();
};

// 分页变化
const handlePageChange = (page: number) => {
  pagination.current = page;
  loadUserList();
};

const handlePageSizeChange = (pageSize: number) => {
  pagination.pageSize = pageSize;
  pagination.current = 1;
  loadUserList();
};

// 新增用户
const handleAdd = () => {
  dialogTitle.value = '新增用户';
  // 用默认值覆盖式重置，保证每次打开弹窗都是干净状态
  Object.assign(formData, defaultFormData(), { password: initPassword.value });
  selectedRoleIds.value = [];
  // 清理上一次可能残留的校验提示
  formRef.value?.clearValidate?.();
  dialogVisible.value = true;
};

// 编辑用户
const handleEdit = async (row: User) => {
  dialogTitle.value = '编辑用户';
  try {
    const res = (await getUser(row.userId!)) as unknown as User;
    // 先恢复默认值再合并后端返回的字段，避免上次数据残留
    Object.assign(formData, defaultFormData(), res);

    const roleIds = await getUserRoleIds(row.userId!);
    selectedRoleIds.value = Array.isArray(roleIds) ? roleIds.map(Number) : [];

    formRef.value?.clearValidate?.();
    dialogVisible.value = true;
  } catch (error) {
    Message.error('获取用户详情失败');
  }
};

// 删除用户
const handleDelete = (row: User) => {
  Modal.confirm({
    title: '删除确认',
    content: `确定要删除用户"${row.userName}"吗？删除后无法恢复。`,
    okText: '确定删除',
    cancelText: '取消',
    onOk: async () => {
      try {
        await deleteUser(row.userId!);
        Message.success('删除成功');
        loadUserList();
      } catch (error: any) {
        console.error('删除用户失败:', error);
        const errorMsg =
          error?.response?.data?.msg || error?.message || '删除失败';
        Message.error(errorMsg);
      }
    },
  });
};

// 重置密码
const handleResetPwd = (row: User) => {
  Object.assign(resetPwdForm, {
    userId: row.userId!,
    password: '',
    confirmPassword: '',
  });
  resetPwdFormRef.value?.clearValidate?.();
  resetPwdVisible.value = true;
};

// 提交重置密码（统一采用 before-ok 的 done 回调，避免与 v-model 双源驱动冲突）
const handleResetPwdSubmit = async (done: (closed: boolean) => void) => {
  if (!resetPwdFormRef.value) {
    done(false);
    return;
  }

  // Arco validate() 成功返回 undefined，失败返回错误对象，统一命名为 errors 避免歧义
  const errors = await resetPwdFormRef.value.validate();
  if (errors) {
    done(false);
    return;
  }

  try {
    await resetUserPwd({
      userId: resetPwdForm.userId,
      password: resetPwdForm.password,
    });
    Message.success('重置密码成功');
    done(true);
  } catch (error: any) {
    const errorMsg =
      error?.response?.data?.msg || error?.message || '重置密码失败';
    Message.error(errorMsg);
    done(false);
  }
};

// 提交表单
const handleSubmit = async (done: (closed: boolean) => void) => {
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
    const submitData = {
      user: { ...formData },
      roleIds: selectedRoleIds.value,
    };

    if (formData.userId) {
      await updateUser(submitData);
      Message.success('修改成功');
    } else {
      if (!formData.password) {
        Message.error('新增用户时密码不能为空');
        done(false);
        return;
      }
      await addUser(submitData);
      Message.success('新增成功');
    }
    // 关闭弹窗统一交给 done(true)，避免与 v-model:visible 双源驱动造成弹窗卡死
    loadUserList();
    done(true);
  } catch (error: any) {
    const errorMsg =
      error?.response?.data?.msg || error?.response?.data?.message || error?.message || '操作失败';
    Message.error(errorMsg);
    done(false);
  }
};

// 重置表单：除了调用 Arco 的 resetFields，再显式还原 formData，
// 避免某些字段因未绑定 field 属性而残留上一次的输入
const resetForm = () => {
  formRef.value?.resetFields();
  selectedRoleIds.value = [];
  Object.assign(formData, defaultFormData());
};

onMounted(() => {
  loadUserList();
  loadRoles(); // 加载角色列表
  // 获取初始密码配置
  getConfigKey('sys.user.initPassword').then((res: any) => {
    if (res) initPassword.value = res;
  });
});
</script>

<style scoped>
.user-management {
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
