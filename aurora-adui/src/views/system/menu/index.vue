<template>
  <div class="menu-management">
    <!-- 菜单列表 -->
    <a-card class="table-card">
      <template #title>
        <a-space>
          <span>菜单列表</span>
        </a-space>
      </template>
      <template #extra>
        <a-button type="primary" @click="() => handleAdd()" v-hasPermi="['system:menu:add']">
          <template #icon>
            <icon-plus />
          </template>
          新增菜单
        </a-button>
      </template>

      <a-table
        :columns="columns"
        :data="menuList"
        :loading="loading"
        :pagination="false"
        :bordered="false"
        row-key="menuId"
        :default-expand-all-rows="false"
        :expanded-row-keys="expandedKeys"
        @expand="handleExpand"
      >
        <template #expand-icon="{ record, expanded }">
          <!-- 按钮类型不显示展开图标，只有目录和菜单类型且有子节点时才显示 -->
          <span
            v-if="
              record.menuType !== 'F' &&
              record.children &&
              record.children.length > 0
            "
          >
            <icon-right
              :style="{
                transform: expanded ? 'rotate(90deg)' : 'rotate(0deg)',
                transition: 'transform 0.2s',
                cursor: 'pointer',
              }"
            />
          </span>
        </template>

        <template #menuName="{ record }">
          <span>{{ record.menuName }}</span>
        </template>

        <template #menuType="{ record }">
          <a-tag v-if="record.menuType === 'M'" color="blue" size="small">目录</a-tag>
          <a-tag v-else-if="record.menuType === 'C'" color="green" size="small">菜单</a-tag>
          <a-tag v-else-if="record.menuType === 'F'" color="gray" size="small">按钮</a-tag>
        </template>


        <template #icon="{ record }">
          <IconRenderer 
            v-if="record.icon && record.icon !== '#'"
            :icon="record.icon"
            :size="16"
          />
          <span v-else>-</span>
        </template>

        <template #visible="{ record }">
          <a-tag :color="record.visible === '0' ? 'green' : 'gray'">
            {{ record.visible === '0' ? '显示' : '隐藏' }}
          </a-tag>
        </template>

        <template #operations="{ record }">
          <a-space>
            <a-button type="text" size="small" @click="handleAdd(record)" v-hasPermi="['system:menu:add']">
              新增
            </a-button>
            <a-button type="text" size="small" @click="handleEdit(record)" v-hasPermi="['system:menu:edit']">
              编辑
            </a-button>
            <a-button type="text" size="small" status="danger" @click="handleDelete(record)" v-hasPermi="['system:menu:remove']">
              删除
            </a-button>
          </a-space>
        </template>
      </a-table>
    </a-card>

    <!-- 新增/编辑菜单弹窗 -->
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
        :label-col-props="{ span: 6 }"
        :wrapper-col-props="{ span: 18 }"
      >
        <a-form-item label="上级菜单" field="parentId">
          <a-tree-select
            v-model="formData.parentId"
            placeholder="请选择上级菜单"
            :data="parentMenuTreeData"
            :allow-search="true"
            :allow-clear="true"
            :default-expand-all="true"
            :dropdown-style="{ maxHeight: '400px' }"
            style="width: 100%"
          />
        </a-form-item>

        <a-form-item label="菜单类型" field="menuType">
          <a-radio-group v-model="formData.menuType">
            <a-radio value="M">目录</a-radio>
            <a-radio value="C">菜单</a-radio>
            <a-radio value="F">按钮</a-radio>
          </a-radio-group>
        </a-form-item>

        <a-form-item label="菜单名称" field="menuName">
          <a-input v-model="formData.menuName" placeholder="请输入菜单名称" />
        </a-form-item>

        <a-form-item label="显示顺序" field="orderNum">
          <a-input-number v-model="formData.orderNum" :min="0" />
        </a-form-item>

        <a-form-item
          v-if="formData.menuType !== 'F'"
          label="路由地址"
          field="path"
        >
          <a-input v-model="formData.path" placeholder="请输入路由地址" />
        </a-form-item>

        <a-form-item
          v-if="formData.menuType === 'C'"
          label="组件路径"
          field="component"
        >
          <a-input
            v-model="formData.component"
            placeholder="请输入组件路径，如：system/user/index"
          />
        </a-form-item>

        <a-form-item
          v-if="formData.menuType !== 'M'"
          label="权限标识"
          field="perms"
        >
          <a-input v-model="formData.perms" placeholder="请输入权限标识" />
        </a-form-item>

        <a-form-item
          v-if="formData.menuType !== 'F'"
          label="菜单图标"
          field="icon"
        >
          <a-space>
            <a-input
              v-model="formData.icon"
              placeholder="请输入图标名称或点击选择"
              style="width: 200px"
            >
              <template #prefix>
                <IconRenderer 
                  v-if="formData.icon" 
                  :icon="formData.icon"
                  :size="16"
                />
              </template>
            </a-input>
            <a-button @click="showIconPicker = true">选择图标</a-button>
          </a-space>
        </a-form-item>

        <a-form-item
          v-if="formData.menuType !== 'F'"
          label="是否外链"
          field="isFrame"
        >
          <a-radio-group v-model="formData.isFrame">
            <a-radio :value="0">是</a-radio>
            <a-radio :value="1">否</a-radio>
          </a-radio-group>
        </a-form-item>

        <a-form-item
          v-if="formData.menuType === 'C'"
          label="是否缓存"
          field="isCache"
        >
          <a-radio-group v-model="formData.isCache">
            <a-radio :value="0">缓存</a-radio>
            <a-radio :value="1">不缓存</a-radio>
          </a-radio-group>
        </a-form-item>

        <a-form-item label="显示状态" field="visible">
          <a-radio-group v-model="formData.visible">
            <a-radio value="0">显示</a-radio>
            <a-radio value="1">隐藏</a-radio>
          </a-radio-group>
        </a-form-item>

        <a-form-item label="菜单状态" field="status">
          <a-radio-group v-model="formData.status">
            <a-radio value="0">正常</a-radio>
            <a-radio value="1">停用</a-radio>
          </a-radio-group>
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

    <!-- 图标选择器弹窗 -->
    <a-modal
      v-model:visible="showIconPicker"
      title="选择图标"
      width="600px"
      :footer="false"
    >
      <IconPicker
        v-model="formData.icon"
        @select="handleIconSelect"
      />
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue';
import { Message, Modal } from '@arco-design/web-vue';
import {
  IconPlus,
  IconMenu,
  IconSettings,
  IconUser,
  IconRight,
  IconRefresh,
  IconHome,
  IconDashboard,
  IconBarChart,
  IconFile,
  IconMessage,
  IconNotification,
  IconSearch,
  IconClose,
  IconCloseCircle,
  IconCheckCircle,
  IconInfoCircle,
  IconArrowUp,
  IconArrowDown,
  IconArrowLeft,
  IconThunderbolt,
  IconFire,
  IconList,
  IconPoweroff,
  IconFullscreen,
  IconMenuFold,
  IconMenuUnfold,
  IconCopy,
} from '@arco-design/web-vue/es/icon';
import * as ArcoIcons from '@arco-design/web-vue/es/icon';
import {
  listMenu,
  getMenu,
  addMenu,
  updateMenu,
  deleteMenu,
  listMenuTree,
} from '@/api/system';
import { handleTree } from '@/utils/tree';
import IconPicker from '@/components/IconPicker.vue';
import IconRenderer from '@/components/IconRenderer.vue';

interface Menu {
  menuId?: number;
  menuName: string;
  parentId: number;
  orderNum: number;
  path?: string;
  component?: string;
  menuType: string;
  visible: string;
  status: string;
  perms?: string;
  icon?: string;
  isFrame?: number;
  isCache?: number;
  remark?: string;
  children?: Menu[];
}

const menuList = ref<Menu[]>([]);
const loading = ref(false);
const expandedKeys = ref<number[]>([]);

const dialogVisible = ref(false);
const dialogTitle = ref('新增菜单');
const formRef = ref();
const showIconPicker = ref(false);

const formData = ref<Menu>({
  menuName: '',
  parentId: 0,
  orderNum: 0,
  path: '',
  component: '',
  menuType: 'C',
  visible: '0',
  status: '0',
  perms: '',
  icon: '',
  isFrame: 1,
  isCache: 0,
  remark: '',
});

const formRules = {
  menuName: [{ required: true, message: '请输入菜单名称' }],
  orderNum: [{ required: true, message: '请输入显示顺序' }],
  menuType: [{ required: true, message: '请选择菜单类型' }],
};

const menuTreeData = ref<Menu[]>([]);

// 表格列定义
const columns = [
  {
    title: '菜单名称',
    dataIndex: 'menuName',
    slotName: 'menuName',
    width: 200,
    ellipsis: true,
  },
  {
    title: '类型',
    dataIndex: 'menuType',
    slotName: 'menuType',
    width: 80,
  },
  {
    title: '图标',
    dataIndex: 'icon',
    slotName: 'icon',
    width: 100,
  },
  {
    title: '排序',
    dataIndex: 'orderNum',
    width: 80,
  },
  {
    title: '权限标识',
    dataIndex: 'perms',
    ellipsis: true,
  },
  {
    title: '组件路径',
    dataIndex: 'component',
    ellipsis: true,
  },
  {
    title: '显隐',
    dataIndex: 'visible',
    slotName: 'visible',
    width: 100,
  },
  {
    title: '操作',
    slotName: 'operations',
    width: 200,
    fixed: 'right',
  },
];

// 上级菜单树形选择数据（使用 Arco Design 要求的 key/title 字段）
interface TreeNode {
  key: number;
  title: string;
  children?: TreeNode[];
}

const parentMenuTreeData = computed((): TreeNode[] => {
  // 将菜单数据转换为 TreeSelect 需要的格式
  const convertToTreeNode = (menus: Menu[]): TreeNode[] => {
    return menus
      .filter(menu => menu.menuType === 'M' || menu.menuType === 'C')
      .map(menu => ({
        key: menu.menuId || 0,
        title: menu.menuName,
        children: menu.children ? convertToTreeNode(menu.children) : []
      }));
  };

  // 根节点：主类目
  const root: TreeNode = {
    key: 0,
    title: '主类目',
    children: convertToTreeNode(menuList.value)
  };
  
  return [root];
});



// 获取一级菜单的ID（用于默认展开）
const getFirstLevelMenuIds = (tree: Menu[]): number[] => {
  const ids: number[] = [];
  tree.forEach((menu) => {
    if (menu.menuId && (!menu.parentId || menu.parentId === 0)) {
      ids.push(menu.menuId);
    }
  });
  return ids;
};

// 处理展开事件
const handleExpand = (rowKey: number, expanded: boolean) => {
  if (expanded) {
    if (!expandedKeys.value.includes(rowKey)) {
      expandedKeys.value.push(rowKey);
    }
  } else {
    const index = expandedKeys.value.indexOf(rowKey);
    if (index > -1) {
      expandedKeys.value.splice(index, 1);
    }
  }
};

// 加载菜单列表
const loadMenuList = async () => {
  loading.value = true;
  try {
    const res = await listMenu();
    const flatMenus = Array.isArray(res) ? res : [];

    // 使用 handleTree 将扁平数据转换为树形结构
    const treeData = handleTree(
      flatMenus,
      'menuId',
      'parentId',
      'children',
      0
    ) as Menu[];

    menuList.value = treeData;

    // 同时更新菜单树数据（用于下拉选择）
    menuTreeData.value = Array.isArray(res) ? res : [];

    // 只展开一级菜单
    expandedKeys.value = getFirstLevelMenuIds(treeData);
  } catch (error: any) {
    console.error('加载菜单列表失败:', error);
    const errorMsg =
      error?.response?.data?.msg || error?.message || '加载菜单列表失败';
    Message.error(errorMsg);
    if (error?.response?.status === 403 || error?.response?.status === 401) {
      menuList.value = [];
    }
  } finally {
    loading.value = false;
  }
};

// 加载菜单树（用于下拉选择）
const loadMenuTree = async () => {
  try {
    const res = await listMenuTree();
    menuTreeData.value = Array.isArray(res) ? res : [];
  } catch (error: any) {
    console.error('加载菜单树失败:', error);
    const errorMsg =
      error?.response?.data?.msg || error?.message || '加载菜单树失败';
    Message.error(errorMsg);
  }
};

// 新增菜单
const handleAdd = (parent?: Menu) => {
  dialogTitle.value = '新增菜单';
  formData.value = {
    menuName: '',
    parentId: parent?.menuId || 0,
    orderNum: 0,
    path: '',
    component: '',
    menuType: 'C',
    visible: '0',
    status: '0',
    perms: '',
    icon: '',
    isFrame: 1,
    isCache: 0,
    remark: '',
  };
  dialogVisible.value = true;
};

// 编辑菜单
const handleEdit = async (row: Menu) => {
  dialogTitle.value = '编辑菜单';
  try {
    const res = await getMenu(row.menuId!);
    formData.value = { ...(res as unknown as Menu) };
    dialogVisible.value = true;
  } catch (error) {
    Message.error('获取菜单详情失败');
  }
};

// 删除菜单
const handleDelete = (row: Menu) => {
  // 检查是否有子菜单
  if (row.children && row.children.length > 0) {
    Message.warning('该菜单存在子菜单，请先删除子菜单');
    return;
  }

  Modal.confirm({
    title: '删除确认',
    content: `确定要删除菜单"${row.menuName}"吗？删除后无法恢复。`,
    onOk: async () => {
      try {
        await deleteMenu(row.menuId!);
        Message.success('删除成功');
        loadMenuList();
        loadMenuTree();
      } catch (error: any) {
        console.error('删除菜单失败:', error);
        const errorMsg =
          error?.response?.data?.msg || error?.message || '删除失败';
        Message.error(errorMsg);
      }
    },
  });
};

// 提交表单
const handleSubmit = async () => {
  if (!formRef.value) {
    Message.error('表单引用不存在');
    return false;
  }

  // 确保必填字段有默认值
  if (formData.value.orderNum === null || formData.value.orderNum === undefined) {
    formData.value.orderNum = 0;
  }
  
  console.log('开始表单验证，当前表单数据:', JSON.stringify(formData.value, null, 2));
  
  // 手动验证必填字段
  if (!formData.value.menuName || formData.value.menuName.trim() === '') {
    Message.error('请输入菜单名称');
    return false;
  }
  
  if (formData.value.orderNum === null || formData.value.orderNum === undefined) {
    Message.error('请输入显示顺序');
    return false;
  }
  
  if (!formData.value.menuType) {
    Message.error('请选择菜单类型');
    return false;
  }
  
  // 尝试使用 Arco Design 的 validate 方法（如果可用）
  try {
    const valid = await formRef.value.validate().catch(() => {
      // 如果 validate 抛出错误或返回 undefined，我们手动验证已经完成
      return true; // 手动验证已通过，继续执行
    });
    
    console.log('表单验证结果:', valid);
    
    // 如果 validate 返回 false，说明有验证错误
    if (valid === false) {
      console.error('表单验证失败');
      Message.warning('请检查表单输入，确保必填项已填写');
      return false;
    }
  } catch (error) {
    console.warn('表单验证方法调用异常，但手动验证已通过:', error);
    // 手动验证已通过，继续执行
  }
  
  console.log('表单验证通过，准备提交数据');
  
  try {

    // 准备提交数据，确保所有字段都正确传递
    const submitData = {
      ...formData.value,
      // 确保 icon 字段被正确传递（即使是空字符串也要传递）
      icon: formData.value.icon || '',
    };

    console.log('提交菜单数据:', JSON.stringify(submitData, null, 2)); // 调试日志

    let response;
    if (formData.value.menuId) {
      console.log('更新菜单，menuId:', formData.value.menuId);
      response = await updateMenu(submitData);
      console.log('更新菜单响应:', response);
      Message.success('修改成功');
    } else {
      console.log('新增菜单');
      response = await addMenu(submitData);
      console.log('新增菜单响应:', response);
      Message.success('新增成功');
    }
    
    dialogVisible.value = false;
    await loadMenuList();
    await loadMenuTree();
    return true;
  } catch (error: any) {
    console.error('保存菜单失败，完整错误信息:', error);
    console.error('错误响应:', error?.response);
    console.error('错误数据:', error?.response?.data);
    
    let errorMsg = '操作失败';
    if (error?.response?.data) {
      errorMsg = error.response.data.msg || error.response.data.message || errorMsg;
    } else if (error?.message) {
      errorMsg = error.message;
    }
    
    Message.error(errorMsg);
    return false;
  }
};

// 重置表单
const resetForm = () => {
  formRef.value?.resetFields();
};

// 处理图标选择
const handleIconSelect = (iconName: string) => {
  formData.value.icon = iconName;
  showIconPicker.value = false;
};

onMounted(() => {
  loadMenuList();
  loadMenuTree();
});
</script>

<style scoped>
.menu-management {
  padding: 0px;
  height: 100%;
  display: flex;
  flex-direction: column;
}

.table-card {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.table-card :deep(.arco-card-body) {
  flex: 1;
  overflow: auto;
  padding: 16px;
}
</style>
