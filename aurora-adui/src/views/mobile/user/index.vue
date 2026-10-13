<template>
  <div class="app-container">
    <a-card class="table-card" title="移动端用户">
      <!-- 搜索区域 -->
      <a-row :gutter="16" style="margin-bottom: 16px;">
        <a-col :xs="24" :sm="12" :md="8" :lg="6">
          <a-form-item label="关键词" :label-col-style="{ width: '60px' }" :style="{ marginBottom: 0 }">
            <a-input v-model="userQuery.keyword" placeholder="昵称/手机号/用户编号" allow-clear @press-enter="handleUserQuery" />
          </a-form-item>
        </a-col>
        <a-col :xs="24" :sm="24" :md="16" :lg="18">
          <a-space wrap>
            <a-button type="primary" @click="handleUserQuery">
              <template #icon><icon-search /></template>
              搜索
            </a-button>
            <a-button @click="resetUserQuery">
              <template #icon><icon-refresh /></template>
              重置
            </a-button>
          </a-space>
        </a-col>
      </a-row>

      <a-table
        :loading="userLoading"
        :data="userList"
        :pagination="userPagination"
        row-key="userId"
        @page-change="handleUserPageChange"
        @page-size-change="handleUserPageSizeChange"
      >
        <template #columns>
          <a-table-column title="头像" align="center" :width="80">
            <template #cell="{ record }">
              <a-avatar :size="40" :image-url="avatarSrc(record.avatarUrl)">
                {{ (record.nickName || 'U').charAt(0) }}
              </a-avatar>
            </template>
          </a-table-column>
          <a-table-column title="用户编号" data-index="userNo" align="center" ellipsis tooltip />
          <a-table-column title="昵称" data-index="nickName" align="center" ellipsis tooltip />
          <a-table-column title="手机号" data-index="phone" align="center" />
          <a-table-column title="会员等级" align="center">
            <template #cell="{ record }">
              <a-select
                v-if="canEditMobileUser"
                :model-value="record.membershipLevelId"
                placeholder="-"
                allow-search
                style="width: 140px;"
                @change="(value: string | number) => handleMembershipChange(record, value)"
              >
                <a-option :value="MEMBERSHIP_NONE_VALUE">无</a-option>
                <a-option v-for="level in membershipLevelOptions" :key="level.levelId" :value="level.levelId">
                  {{ level.name }}
                </a-option>
              </a-select>
              <span v-else>{{ record.membershipLevel || '-' }}</span>
            </template>
          </a-table-column>
          <a-table-column title="豆子余额" data-index="balance" align="center" :width="110" />
          <a-table-column title="状态" align="center" :width="100">
            <template #cell="{ record }">
              <a-switch
                :model-value="record.status === '0'"
                @change="(val:boolean) => handleStatusChange(record, val)"
                v-hasPermi="['mobile:user:edit']"
              />
            </template>
          </a-table-column>
          <a-table-column title="注册时间" align="center" :width="180">
            <template #cell="{ record }">
              <span>{{ parseTime(record.createTime) }}</span>
            </template>
          </a-table-column>
          <a-table-column title="操作" align="center" :width="180">
            <template #cell="{ record }">
              <a-space>
                <a-button type="text" size="small" @click="handleAdjustBean(record)" v-hasPermi="['mobile:bean:adjust']">增减豆</a-button>
                <a-button type="text" size="small" @click="handleViewTransactions(record)" v-hasPermi="['mobile:bean:list']">流水</a-button>
              </a-space>
            </template>
          </a-table-column>
        </template>
      </a-table>
    </a-card>

    <!-- 增减豆弹窗 -->
    <a-modal
      v-model:visible="adjustOpen"
      title="增减豆"
      :width="460"
      @before-ok="submitAdjust"
      @cancel="adjustOpen = false"
    >
      <a-form ref="adjustFormRef" :model="adjustForm" :rules="adjustRules"
              :label-col-props="{ span: 6 }" :wrapper-col-props="{ span: 18 }">
        <a-form-item label="用户">
          <span>{{ adjustTarget?.nickName || adjustTarget?.phone || adjustTarget?.userNo }}</span>
        </a-form-item>
        <a-form-item label="当前余额">
          <span>{{ adjustTarget?.balance ?? 0 }}</span>
        </a-form-item>
        <a-form-item label="变动数量" field="amount">
          <a-input-number v-model="adjustForm.amount" placeholder="正数增加,负数减少" :step="1" style="width: 100%;" />
        </a-form-item>
        <a-form-item label="备注" field="remark">
          <a-textarea v-model="adjustForm.remark" placeholder="选填,记录调整原因" :auto-size="{ minRows: 2, maxRows: 4 }" />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 流水弹窗 -->
    <a-modal
      v-model:visible="txnOpen"
      title="豆子流水"
      :width="760"
      :footer="false"
      @cancel="txnOpen = false"
    >
      <a-table
        :loading="txnLoading"
        :data="txnList"
        :pagination="txnPagination"
        row-key="id"
        @page-change="handleTxnPageChange"
        @page-size-change="handleTxnPageSizeChange"
      >
        <template #columns>
          <a-table-column title="类型" align="center" :width="100">
            <template #cell="{ record }">{{ beanTypeText(record.type) }}</template>
          </a-table-column>
          <a-table-column title="变动值" data-index="changeValue" align="center" :width="100" />
          <a-table-column title="余额快照" data-index="balanceAfter" align="center" :width="100" />
          <a-table-column title="备注" data-index="remark" align="center" ellipsis tooltip />
          <a-table-column title="时间" align="center" :width="180">
            <template #cell="{ record }">{{ parseTime(record.createTime) }}</template>
          </a-table-column>
        </template>
      </a-table>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, reactive, onMounted } from 'vue';
import { Message, Modal } from '@arco-design/web-vue';
import { IconSearch, IconRefresh } from '@arco-design/web-vue/es/icon';
import {
  listMobileUser,
  editMobileUserStatus,
  editMobileUserMembership,
  listMobileUserMembershipLevelOptions,
  adjustBean,
  listBeanTransactions,
  MobileUser,
  BeanTransaction,
  MembershipLevelOption
} from '@/api/mobile';
import { usePermissionStore } from '@/stores/permission';

const permissionStore = usePermissionStore();
const canEditMobileUser = computed(() => permissionStore.hasAnyPermission('mobile:user:edit'));
const MEMBERSHIP_NONE_VALUE = '__NONE__';
const membershipLevelOptions = ref<MembershipLevelOption[]>([]);

// 头像 URL 容错:历史数据可能存成含旧 IP 的完整 URL,剥离域名只留 /profile 相对路径,
// 拼 /api 走 vite 代理到当前后端,避免电脑局域网 IP 变化导致头像失效
function avatarSrc(url?: string): string {
  if (!url) return '';
  const i = url.indexOf('/profile/');
  if (i >= 0) return '/api' + url.slice(i);
  if (url.startsWith('/')) return '/api' + url;
  return url;
}

const userLoading = ref(false);
const userList = ref<MobileUser[]>([]);
const userQuery = reactive({
  keyword: undefined as string | undefined
});
const userPagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0,
  showTotal: true,
  showPageSize: true,
  pageSizeOptions: ['10', '20', '50']
});

function getUserList() {
  userLoading.value = true;
  listMobileUser({
    keyword: userQuery.keyword,
    pageNum: userPagination.current,
    pageSize: userPagination.pageSize
  }).then((res: any) => {
    userList.value = res.records;
    userPagination.total = res.total;
  }).finally(() => {
    userLoading.value = false;
  });
}

function handleUserQuery() {
  userPagination.current = 1;
  getUserList();
}

function resetUserQuery() {
  userQuery.keyword = undefined;
  handleUserQuery();
}

function handleUserPageChange(current: number) {
  userPagination.current = current;
  getUserList();
}

function handleUserPageSizeChange(pageSize: number) {
  userPagination.pageSize = pageSize;
  userPagination.current = 1;
  getUserList();
}

// 状态开关:开=正常(0)、关=停用(1)
function handleStatusChange(record: MobileUser, val: boolean) {
  const newStatus = val ? '0' : '1';
  const text = val ? '启用' : '停用';
  Modal.confirm({
    title: '确认操作',
    content: `确认要${text}用户 "${record.nickName || record.phone || record.userId}" 吗?`,
    onOk: () => {
      editMobileUserStatus(record.userId!, newStatus).then(() => {
        Message.success(`${text}成功`);
        record.status = newStatus;
      });
    }
  });
}

function getMembershipLevelOptions() {
  if (!canEditMobileUser.value) return Promise.resolve();
  return listMobileUserMembershipLevelOptions().then((res: any) => {
    membershipLevelOptions.value = Array.isArray(res) ? res : [];
  });
}

function handleMembershipChange(record: MobileUser, value: string | number) {
  const levelId = value === MEMBERSHIP_NONE_VALUE ? null : Number(value);
  const targetName = levelId == null
    ? '无'
    : membershipLevelOptions.value.find(item => item.levelId === levelId)?.name || '所选等级';
  Modal.confirm({
    title: '确认调整会员等级',
    content: `确认将用户 "${record.nickName || record.phone || record.userId}" 调整为“${targetName}”吗？本操作不补发豆子。`,
    onOk: () => editMobileUserMembership(record.userId!, levelId).then(() => {
      Message.success('会员等级调整成功');
      getUserList();
    })
  });
}

function parseTime(time: string | undefined) {
  if (!time) return '';
  return time.replace('T', ' ');
}

// ---- 增减豆 ----
const adjustOpen = ref(false);
const adjustFormRef = ref();
const adjustTarget = ref<MobileUser>();
const adjustForm = reactive({
  amount: undefined as number | undefined,
  remark: ''
});
const adjustRules = {
  amount: [
    { required: true, message: '请输入变动数量' },
    {
      validator: (value: number | undefined, cb: (msg?: string) => void) => {
        if (value === 0) {
          cb('数量不能为 0');
        } else {
          cb();
        }
      }
    }
  ]
};

function handleAdjustBean(record: MobileUser) {
  adjustTarget.value = record;
  adjustForm.amount = undefined;
  adjustForm.remark = '';
  adjustOpen.value = true;
}

function submitAdjust(done: (closed: boolean) => void) {
  adjustFormRef.value.validate((errors: any) => {
    if (errors) {
      done(false);
      return;
    }
    adjustBean({
      userId: adjustTarget.value!.userId!,
      amount: adjustForm.amount!,
      remark: adjustForm.remark
    }).then(() => {
      Message.success('调整成功');
      getUserList();
      done(true);
    }).catch(() => done(false));
  });
}

// ---- 豆子流水 ----
const txnOpen = ref(false);
const txnLoading = ref(false);
const txnList = ref<BeanTransaction[]>([]);
const txnTarget = ref<MobileUser>();
const txnPagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0,
  showTotal: true,
  showPageSize: true,
  pageSizeOptions: ['10', '20', '50']
});

const beanTypeMap: Record<string, string> = {
  GIFT: '赠送',
  ISSUE: '发放',
  RECHARGE: '充值',
  DEDUCT: '扣费',
  REFUND: '退还',
  ADJUST: '后台调整'
};

function beanTypeText(type: string | undefined) {
  return (type && beanTypeMap[type]) || type || '';
}

function getTxnList() {
  if (!txnTarget.value?.userId) return;
  txnLoading.value = true;
  listBeanTransactions({
    userId: txnTarget.value.userId,
    pageNum: txnPagination.current,
    pageSize: txnPagination.pageSize
  }).then((res: any) => {
    txnList.value = res.records;
    txnPagination.total = res.total;
  }).finally(() => {
    txnLoading.value = false;
  });
}

function handleViewTransactions(record: MobileUser) {
  txnTarget.value = record;
  txnPagination.current = 1;
  txnOpen.value = true;
  getTxnList();
}

function handleTxnPageChange(current: number) {
  txnPagination.current = current;
  getTxnList();
}

function handleTxnPageSizeChange(pageSize: number) {
  txnPagination.pageSize = pageSize;
  txnPagination.current = 1;
  getTxnList();
}

onMounted(() => {
  getUserList();
  void getMembershipLevelOptions();
});
</script>

<style scoped>
.app-container {
  padding: 0px;
}
</style>
