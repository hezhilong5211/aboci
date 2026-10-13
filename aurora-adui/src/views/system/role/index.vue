<template>
  <div class="role-management">
    <!-- 角色列表 -->
    <a-card class="table-card" title="角色列表">
      <!-- 搜索区域 -->
      <a-row :gutter="16" style="margin-bottom: 16px;">
        <a-col :xs="24" :sm="12" :md="8" :lg="8" :xl="6" style="margin-bottom: 8px;">
          <a-form-item label="搜索" :label-col-style="{ width: '80px' }" :style="{ marginBottom: 0 }">
            <a-input
              v-model="searchForm.keyword"
              placeholder="请输入角色名称或权限字符"
              allow-clear
              @press-enter="handleSearch"
            />
          </a-form-item>
        </a-col>
        <a-col :xs="24" :sm="12" :md="16" :lg="16" :xl="18" style="margin-bottom: 8px;">
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
          <a-button type="primary" @click="handleAdd" v-hasPermi="['system:role:add']">
            <template #icon>
              <icon-plus />
            </template>
            新增角色
          </a-button>
          <a-button
            v-if="selectedRowKeys.length > 0"
            type="primary"
            status="danger"
            @click="handleBatchDelete"
            v-hasPermi="['system:role:remove']"
          >
            <template #icon>
              <icon-delete />
            </template>
            批量删除 ({{ selectedRowKeys.length }})
          </a-button>
        </a-space>
      </div>

      <a-table
        :columns="columns"
        :data="roleList"
        :loading="loading"
        :pagination="pagination"
        :bordered="false"
        :row-selection="rowSelection"
        row-key="roleId"
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
            <!-- 超级管理员角色(roleId=1)不显示编辑、删除按钮 -->
            <a-button v-if="record.roleId !== 1" type="text" size="small" @click="handleEdit(record)" v-hasPermi="['system:role:edit']">
              编辑
            </a-button>
            <a-button
              v-if="record.roleId !== 1"
              type="text"
              size="small"
              status="danger"
              @click="handleDelete(record)"
              v-hasPermi="['system:role:remove']"
            >
              删除
            </a-button>
            <!-- 超级管理员角色显示提示文字 -->
            <span v-if="record.roleId === 1" style="color: #86909c; font-size: 12px;">超级管理员</span>
          </a-space>
        </template>
      </a-table>
    </a-card>

    <!-- 新增/编辑角色弹窗 -->
    <a-modal
      v-model:visible="dialogVisible"
      :title="dialogTitle"
      width="800px"
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
        <a-form-item label="角色名称" field="roleName">
          <a-input
            v-model="formData.roleName"
            placeholder="请输入角色名称"
          />
        </a-form-item>
        <a-form-item label="权限字符" field="roleKey">
          <a-input
            v-model="formData.roleKey"
            placeholder="请输入权限字符"
          />
        </a-form-item>
        <a-form-item label="显示顺序" field="roleSort">
          <a-input-number
            v-model="formData.roleSort"
            :min="0"
            :precision="0"
            placeholder="请输入显示顺序"
            style="width: 100%"
          />
        </a-form-item>
        <a-form-item label="状态" field="status">
          <a-radio-group v-model="formData.status">
            <a-radio value="0">正常</a-radio>
            <a-radio value="1">停用</a-radio>
          </a-radio-group>
        </a-form-item>
        <a-form-item label="菜单权限">
          <div class="menu-permission-container">
            <div class="menu-toolbar">
              <a-space>
                <a-checkbox v-model="isExpandAll" @change="handleExpandAllChange">
                  展开/折叠
                </a-checkbox>
                <a-checkbox v-model="isSelectAll" @change="handleSelectAllChange">
                  全选/全不选
                </a-checkbox>
                <a-checkbox v-model="checkStrictly">
                  父子联动
                </a-checkbox>
              </a-space>
            </div>
            <div class="menu-tree-container">
              <a-spin :loading="menuTreeLoading" tip="加载菜单树...">
                <div v-if="menuTreeData.length === 0 && !menuTreeLoading" class="empty-tree">
                  暂无菜单数据
                </div>
                <a-table
                  v-else-if="menuTreeData.length > 0"
                  :columns="menuTableColumns"
                  :data="menuTreeData"
                  :pagination="false"
                  :bordered="false"
                  row-key="menuId"
                  :default-expand-all-rows="false"
                  v-model:expanded-keys="expandedKeys"
                  v-model:selected-keys="checkedMenuKeys"
                  :row-selection="menuRowSelection"
                  size="small"
                >
                  <template #menuName="{ record }">
                    <span>{{ record.title }}</span>
                  </template>
                </a-table>
              </a-spin>
            </div>
          </div>
        </a-form-item>
        <a-form-item label="备注">
          <a-textarea
            v-model="formData.remark"
            :rows="3"
            placeholder="请输入备注"
          />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed, nextTick, watch } from 'vue';
import { Message, Modal } from '@arco-design/web-vue';
import {
  IconPlus,
  IconSearch,
  IconRefresh,
  IconDelete,
} from '@arco-design/web-vue/es/icon';
import {
  listRole,
  getRole,
  addRole,
  updateRole,
  deleteRole,
  getRoleMenuIds,
  listMenuTree,
} from '@/api/system';
import { handleTree } from '@/utils/tree';

interface Role {
  roleId?: number;
  roleName: string;
  roleKey: string;
  roleSort: number;
  status: string;
  remark?: string;
  createTime?: string;
}

interface Menu {
  menuId: number;
  menuName: string;
  parentId: number;
  children?: Menu[];
}

const roleList = ref<Role[]>([]);
const loading = ref(false);
const dialogVisible = ref(false);
const dialogTitle = ref('新增角色');
const formRef = ref();
const selectedRowKeys = ref<number[]>([]);

const searchForm = reactive({
  keyword: '',
});

const formData = ref<Role>({
  roleName: '',
  roleKey: '',
  roleSort: 0,
  status: '0',
  remark: '',
});

const menuTreeData = ref<any[]>([]);
const menuTreeLoading = ref(false); // 菜单树加载状态
const checkedMenuKeys = ref<(string | number)[]>([]);
const expandedKeys = ref<(string | number)[]>([]);
const isExpandAll = ref(true); // 默认展开
const isSelectAll = ref(false); // 全选状态
const checkStrictly = ref(true); // 默认不联动

// 菜单表格列定义
const menuTableColumns = [
  {
    title: '菜单名称',
    dataIndex: 'title',
    slotName: 'menuName',
    ellipsis: true,
  },
];

// 菜单表格行选择配置
const menuRowSelection = reactive({
  type: 'checkbox' as const,
  showCheckedAll: true,
  onlyCurrent: false,
  checkStrictly: true, 
});

// 监听 checkStrictly 变化
watch(() => checkStrictly.value, (val) => {
  // Arco Design 的 checkStrictly: true 表示父子不联动
  // 我们的 UI 复选框是 "父子联动"，所以 val 为 true 时，应该设置 checkStrictly 为 false
  menuRowSelection.checkStrictly = !val;
  // 切换模式时，为了避免选择状态混乱，不清除已选，但需要注意 Arco 内部行为
});

// 监听 checkedMenuKeys 变化，更新全选状态
watch(() => checkedMenuKeys.value, (val) => {
  const allKeys = getAllMenuKeys(menuTreeData.value);
  isSelectAll.value = allKeys.length > 0 && val.length === allKeys.length;
});

// 监听 expandedKeys 变化，更新展开状态
watch(() => expandedKeys.value, (val) => {
  const allKeys = getAllMenuKeys(menuTreeData.value);
  // 当且仅当所有节点都在 expandedKeys 中时，isExpandAll 为 true
  isExpandAll.value = allKeys.length > 0 && val.length === allKeys.length;
});

// 获取所有菜单ID
const getAllMenuKeys = (nodes: any[]): (string | number)[] => {
  const keys: (string | number)[] = [];
  nodes.forEach((node) => {
    if (node.menuId) {
      keys.push(node.menuId);
    }
    if (node.children && node.children.length > 0) {
      keys.push(...getAllMenuKeys(node.children));
    }
  });
  return keys;
};

// 全选/全不选变化
const handleSelectAllChange = (checked: boolean) => {
  if (checked) {
    checkedMenuKeys.value = getAllMenuKeys(menuTreeData.value);
  } else {
    checkedMenuKeys.value = [];
  }
};

// 展开/折叠变化
const handleExpandAllChange = (checked: boolean) => {
  if (checked) {
    // 展开全部
    expandedKeys.value = getAllMenuKeys(menuTreeData.value);
  } else {
    // 折叠全部
    expandedKeys.value = [];
  }
};

const formRules = {
  roleName: [{ required: true, message: '请输入角色名称' }],
  roleKey: [{ required: true, message: '请输入权限字符' }],
  status: [{ required: true, message: '请选择状态' }],
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
    title: '角色编号',
    dataIndex: 'roleId',
    width: 100,
  },
  {
    title: '角色名称',
    dataIndex: 'roleName',
    ellipsis: true,
  },
  {
    title: '权限字符',
    dataIndex: 'roleKey',
    ellipsis: true,
  },
  {
    title: '显示顺序',
    dataIndex: 'roleSort',
    width: 120,
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
    width: 150,
    fixed: 'right',
  },
];

// 行选择配置
const rowSelection = {
  type: 'checkbox' as const,
  showCheckedAll: true,
  onlyCurrent: false,
};

// 加载角色列表
const loadRoleList = async () => {
  loading.value = true;
  try {
    const params: any = {
      pageNum: pagination.current,
      pageSize: pagination.pageSize,
    };
    
    // 如果有关键词，同时搜索角色名称和权限字符
    if (searchForm.keyword && searchForm.keyword.trim()) {
      params.roleName = searchForm.keyword.trim();
      params.roleKey = searchForm.keyword.trim();
    }

    const res = await listRole(params);
    
    // 处理分页响应
    if (res && res.records) {
      roleList.value = res.records || [];
      pagination.total = res.total || 0;
    } else if (Array.isArray(res)) {
      roleList.value = res;
      pagination.total = res.length;
    } else {
      roleList.value = [];
      pagination.total = 0;
    }
  } catch (error: any) {
    const errorMsg =
      error?.response?.data?.msg || error?.message || '加载角色列表失败';
    Message.error(errorMsg);
    if (error?.response?.status === 403 || error?.response?.status === 401) {
      roleList.value = [];
    }
  } finally {
    loading.value = false;
  }
};

// 加载菜单树
const loadMenuTree = async () => {
  menuTreeLoading.value = true;
  try {
    const res = await listMenuTree();
    
    // 处理响应数据：可能是数组，也可能是包装在对象中
    let menus: any[] = [];
    if (Array.isArray(res)) {
      menus = res;
    } else if (res && Array.isArray(res.data)) {
      menus = res.data;
    } else if (res && Array.isArray(res.records)) {
      menus = res.records;
    } else {
      menus = [];
    }
    
    if (menus.length === 0) {
      menuTreeData.value = [];
      return;
    }
    
    // 判断数据是否已经是树形结构（检查是否有children字段）
    const isTreeStructure = menus.some((menu: any) => menu.children && Array.isArray(menu.children));
    
    let treeData: Menu[];
    if (isTreeStructure) {
      // 如果已经是树形结构，直接使用
      treeData = menus as Menu[];
    } else {
      // 如果是扁平数组，转换为树形结构
      treeData = handleTree(
        menus,
      'menuId',
      'parentId',
      'children',
      0
    ) as Menu[];
    }
    
    // 转换为表格需要的树形数据格式
    const convertToTreeData = (nodes: Menu[]): any[] => {
      if (!nodes || nodes.length === 0) {
        return [];
      }
      return nodes.map((node) => {
        // 确保 menuId 和 menuName 存在
        if (!node.menuId || !node.menuName) {
          return null;
        }
        
        const treeNode: any = {
          menuId: node.menuId,
          title: node.menuName,
        };
        
        // 检查并转换子节点
        if (node.children && Array.isArray(node.children) && node.children.length > 0) {
          treeNode.children = convertToTreeData(node.children);
        }
        return treeNode;
      }).filter((node) => node !== null); // 过滤掉无效节点
    };
    
    const convertedData = convertToTreeData(treeData);
    menuTreeData.value = convertedData;
    
    // 默认展开所有节点
    if (isExpandAll.value && menuTreeData.value.length > 0) {
      // 先设置展开状态，然后调用切换函数
      const collectKeys = (nodes: any[]): (string | number)[] => {
        const keys: (string | number)[] = [];
        nodes.forEach((node) => {
          if (node.menuId) {
            keys.push(node.menuId);
          }
          if (node.children && node.children.length > 0) {
            keys.push(...collectKeys(node.children));
          }
        });
        return keys;
      };
      expandedKeys.value = collectKeys(menuTreeData.value);
    }
  } catch (error: any) {
    Message.error('加载菜单树失败: ' + (error?.message || '未知错误'));
    menuTreeData.value = [];
  } finally {
    menuTreeLoading.value = false;
  }
};






// 搜索
const handleSearch = () => {
  pagination.current = 1;
  loadRoleList();
};

// 重置搜索
const resetSearch = () => {
  searchForm.keyword = '';
  pagination.current = 1;
  loadRoleList();
};

// 分页变化
const handlePageChange = (page: number) => {
  pagination.current = page;
  loadRoleList();
};

const handlePageSizeChange = (pageSize: number) => {
  pagination.pageSize = pageSize;
  pagination.current = 1;
  loadRoleList();
};

// 新增角色
const handleAdd = () => {
  dialogTitle.value = '新增角色';
  formData.value = {
    roleName: '',
    roleKey: '',
    roleSort: 0,
    status: '0',
    remark: '',
  };
  checkedMenuKeys.value = [];
  isSelectAll.value = false;
  // 确保菜单树已加载
  if (menuTreeData.value.length === 0) {
    loadMenuTree().then(() => {
      if (isExpandAll.value) {
        handleExpandAllChange(true);
      }
    });
  } else {
    if (isExpandAll.value) {
      handleExpandAllChange(true);
    }
  }
  dialogVisible.value = true;
};

// 编辑角色
const handleEdit = async (row: Role) => {
  dialogTitle.value = '编辑角色';
  // 先重置表单和选中状态
  checkedMenuKeys.value = [];
  isSelectAll.value = false;
  
  try {
    // 1. 获取角色详情
    const res: any = await getRole(row.roleId!);
    formData.value = { ...res };
    
    // 2. 确保菜单树已加载
    if (menuTreeData.value.length === 0) {
      await loadMenuTree();
    }
    
    // 3. 获取角色的菜单ID列表
    const menuIds: any = await getRoleMenuIds(row.roleId!);
    
    // 4. 设置选中状态
    // 注意：如果是父子联动模式(checkStrictly=false)，后端返回的数据可能包含父节点ID
    // 如果父节点被选中，Arco 会自动选中所有子节点，这可能导致未完全选中的子节点也被选中
    // 这里的策略是：先设置为不联动，设置完 keys 后，再根据用户设置恢复联动状态
    menuRowSelection.checkStrictly = true; 
    
    // 确保 ID 是数字类型
    const validMenuIds = (Array.isArray(menuIds) ? menuIds : []).map(id => Number(id));
    checkedMenuKeys.value = validMenuIds;
    
    // 5. 恢复用户的联动设置 (nextTick 确保 UI 更新后再切换模式)
    nextTick(() => {
       // 如果 checkStrictly.value 为 true (表示开启父子联动)，则 menuRowSelection.checkStrictly 应为 false
       menuRowSelection.checkStrictly = !checkStrictly.value;
    });
    
    // 6. 展开所有节点
    if (isExpandAll.value) {
      handleExpandAllChange(true);
    }
    
    // 7. 打开弹窗
    dialogVisible.value = true;
  } catch (error: any) {
    console.error('获取角色详情失败:', error);
    const errorMsg = error?.response?.data?.msg || error?.message || '获取角色详情失败';
    Message.error(errorMsg);
  }
};

// 删除角色
const handleDelete = (row: Role) => {
  Modal.confirm({
    title: '删除确认',
    content: `确定要删除角色"${row.roleName}"吗？删除后无法恢复。`,
    okText: '确定删除',
    cancelText: '取消',
    onOk: async () => {
      try {
        await deleteRole(row.roleId!);
        Message.success('删除成功');
        loadRoleList();
      } catch (error: any) {
        const errorMsg =
          error?.response?.data?.msg || error?.message || '删除失败';
        Message.error(errorMsg);
      }
    },
  });
};

// 批量删除
const handleBatchDelete = () => {
  if (selectedRowKeys.value.length === 0) {
    Message.warning('请选择要删除的角色');
    return;
  }
  
  Modal.confirm({
    title: '批量删除确认',
    content: `确定要删除选中的 ${selectedRowKeys.value.length} 个角色吗？删除后无法恢复。`,
    okText: '确定删除',
    cancelText: '取消',
    onOk: async () => {
      try {
        await deleteRole(selectedRowKeys.value);
        Message.success('删除成功');
        selectedRowKeys.value = [];
        loadRoleList();
      } catch (error: any) {
        const errorMsg =
          error?.response?.data?.msg || error?.message || '删除失败';
        Message.error(errorMsg);
      }
    },
  });
};


// 提交表单
const handleSubmit = async (done: (closed: boolean) => void) => {
  if (!formRef.value) {
    done(false);
    return;
  }

  try {
    const errors = await formRef.value.validate();
    if (errors) {
      // 验证不通过
      done(false);
      return;
    }

    // 选中的节点 key 转 number
    const checkedNums = checkedMenuKeys.value
      .map(key => Number(key))
      .filter(id => !isNaN(id));
    // 父子联动模式下,半选的父目录不在 checkedMenuKeys 里;若只提交选中叶子,
    // sys_role_menu 会缺父目录(M)/父菜单(C),登录后菜单树无根、只剩首页。
    // 故按 menuTreeData 回溯,补全每个选中节点的全部祖先菜单 id。
    const parentMap = new Map<number, number>();
    const buildParentMap = (nodes: any[], parentId: number | null) => {
      for (const n of nodes) {
        if (parentId != null) parentMap.set(Number(n.menuId), parentId);
        if (n.children && n.children.length) buildParentMap(n.children, Number(n.menuId));
      }
    };
    buildParentMap(menuTreeData.value, null);
    const fullMenuIds = new Set<number>(checkedNums);
    for (const id of checkedNums) {
      let p = parentMap.get(id);
      while (p != null && !fullMenuIds.has(p)) {
        fullMenuIds.add(p);
        p = parentMap.get(p);
      }
    }
    const menuIds = Array.from(fullMenuIds);
    
    const submitData = {
      role: {
        ...formData.value,
      },
      menuIds: menuIds,
    };
    
    if (formData.value.roleId) {
      await updateRole(submitData);
      Message.success('修改成功');
    } else {
      await addRole(submitData);
      Message.success('新增成功');
    }
    
    loadRoleList();
    done(true); // 关闭弹窗
  } catch (error: any) {
    console.error('提交失败:', error);
    const errorMsg =
      error?.response?.data?.msg || error?.response?.data?.message || error?.message || '操作失败';
    Message.error(errorMsg);
    done(false); // 保持弹窗打开
  }
};


// 重置表单
const resetForm = () => {
  formRef.value?.resetFields();
  checkedMenuKeys.value = [];
};

onMounted(() => {
  loadRoleList();
  // 预加载菜单树，确保弹窗打开时数据已准备好
  loadMenuTree();
});
</script>

<style scoped>
.role-management {
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

.menu-permission-container {
  border: 1px solid var(--color-border-2);
  border-radius: 4px;
  padding: 12px;
  background: var(--color-bg-1);
}

.menu-toolbar {
  margin-bottom: 12px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--color-border-2);
}

.menu-tree-container {
  width: 100%;
  max-height: 300px;
  overflow-y: auto;
  border: 1px solid var(--color-border-2);
  border-radius: 4px;
  padding: 8px;
  background: var(--color-bg-1);
}

.menu-tree-container :deep(.arco-spin) {
  width: 100%;
}

.menu-tree-container :deep(.arco-table) {
  font-size: 13px;
}

.menu-tree-container :deep(.arco-table-tr) {
  height: 32px;
}

.menu-tree-container :deep(.arco-table-th),
.menu-tree-container :deep(.arco-table-td) {
  padding: 4px 8px;
  height: 32px;
  line-height: 24px;
}

.menu-tree-container :deep(.arco-table-body) {
  max-height: 280px;
  overflow-y: auto;
}

.empty-tree {
  height: 200px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--color-text-3);
}
</style>
