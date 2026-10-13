<template>
  <div class="app-container">
    <a-card class="table-card" title="会员列表">
      <template #extra>
        <a-button type="primary" @click="handleGrant" v-hasPermi="['membership:user:grant']">
          <template #icon><icon-plus /></template>
          新增会员
        </a-button>
      </template>

      <!-- 搜索区域 -->
      <a-row :gutter="16" style="margin-bottom: 16px;">
        <a-col :xs="24" :sm="12" :md="8" :lg="6">
          <a-form-item label="关键词" :label-col-style="{ width: '60px' }" :style="{ marginBottom: 0 }">
            <a-input v-model="query.keyword" placeholder="昵称/手机号" allow-clear @press-enter="handleQuery" />
          </a-form-item>
        </a-col>
        <a-col :xs="24" :sm="12" :md="8" :lg="6">
          <a-form-item label="等级" :label-col-style="{ width: '60px' }" :style="{ marginBottom: 0 }">
            <a-select v-model="query.levelId" placeholder="全部等级" allow-clear>
              <a-option v-for="lv in levelOptions" :key="lv.levelId" :value="lv.levelId!">{{ lv.name }}</a-option>
            </a-select>
          </a-form-item>
        </a-col>
        <a-col :xs="24" :sm="24" :md="8" :lg="12">
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
        row-key="id"
        @page-change="handlePageChange"
        @page-size-change="handlePageSizeChange"
      >
        <template #columns>
          <a-table-column title="用户" data-index="userNo" align="center" ellipsis tooltip />
          <a-table-column title="昵称" data-index="nickName" align="center" ellipsis tooltip />
          <a-table-column title="手机号" data-index="phone" align="center" />
          <a-table-column title="当前等级" data-index="currentLevelName" align="center" />
          <a-table-column title="到期时间" align="center" :width="180">
            <template #cell="{ record }">{{ parseTime(record.currentExpireTime) }}</template>
          </a-table-column>
        </template>
      </a-table>
    </a-card>

    <!-- 新增会员弹窗:选用户 + 选等级，会员时长继承所选等级配置 -->
    <a-modal
      v-model:visible="grantOpen"
      title="新增会员"
      :width="760"
      @before-ok="submitGrant"
      @cancel="grantOpen = false"
    >
      <!-- 移动端用户搜索 + 单选表 -->
      <a-space style="margin-bottom: 12px;">
        <a-input
          v-model="muQuery.keyword"
          placeholder="按昵称/手机号搜索"
          allow-clear
          style="width: 240px;"
          @press-enter="handleMuQuery"
        />
        <a-button type="primary" @click="handleMuQuery">
          <template #icon><icon-search /></template>
          搜索
        </a-button>
      </a-space>

      <a-table
        :loading="muLoading"
        :data="muList"
        :pagination="muPagination"
        row-key="userId"
        :row-selection="rowSelection"
        v-model:selectedKeys="selectedUserKeys"
        :scroll="{ y: 240 }"
        @page-change="handleMuPageChange"
        @page-size-change="handleMuPageSizeChange"
      >
        <template #columns>
          <a-table-column title="昵称" data-index="nickName" align="center" ellipsis tooltip />
          <a-table-column title="手机号" data-index="phone" align="center" />
          <a-table-column title="用户编号" data-index="userNo" align="center" ellipsis tooltip />
        </template>
      </a-table>

      <a-form ref="grantFormRef" :model="grantForm" :rules="grantRules"
              :label-col-props="{ span: 5 }" :wrapper-col-props="{ span: 19 }" style="margin-top: 16px;">
        <a-form-item label="会员等级" field="levelId">
          <a-select v-model="grantForm.levelId" placeholder="请选择会员等级" allow-clear>
            <a-option v-for="lv in levelOptions" :key="lv.levelId" :value="lv.levelId!">{{ lv.name }}</a-option>
          </a-select>
        </a-form-item>
        <a-form-item label="会员时长">
          <a-input :model-value="selectedGrantDurationText" readonly />
          <template #extra>后台开通按所选会员等级当前配置发放</template>
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue';
import { Message } from '@arco-design/web-vue';
import { IconPlus, IconSearch, IconRefresh } from '@arco-design/web-vue/es/icon';
import {
  listMembershipUser,
  listMembershipLevel,
  grantMembership,
  MembershipUser,
  MembershipLevel
} from '@/api/membership';
import { listMobileUser, MobileUser } from '@/api/mobile';

function parseTime(time: string | undefined) {
  if (!time) return '';
  return time.replace('T', ' ');
}

// 等级下拉选项(筛选 + 开通共用)
const levelOptions = ref<MembershipLevel[]>([]);
function getLevelOptions() {
  listMembershipLevel().then((res: any) => {
    levelOptions.value = Array.isArray(res) ? res : res?.records || [];
  });
}

// ---- 会员列表 ----
const loading = ref(false);
const list = ref<MembershipUser[]>([]);
const query = reactive({
  keyword: undefined as string | undefined,
  levelId: undefined as number | undefined
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
  listMembershipUser({
    keyword: query.keyword,
    levelId: query.levelId,
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
  query.keyword = undefined;
  query.levelId = undefined;
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

// ---- 新增会员弹窗 ----
const grantOpen = ref(false);
const grantFormRef = ref();
const grantForm = reactive({
  levelId: undefined as number | undefined
});
const grantRules = {
  levelId: [{ required: true, message: '请选择会员等级' }]
};

// 弹窗内移动端用户单选
const muLoading = ref(false);
const muList = ref<MobileUser[]>([]);
const muQuery = reactive({
  keyword: undefined as string | undefined
});
const muPagination = reactive({
  current: 1,
  pageSize: 5,
  total: 0,
  showTotal: true,
  showPageSize: false
});
const selectedUserKeys = ref<number[]>([]);
const rowSelection = {
  type: 'radio' as const,
  showCheckedAll: false
};

function getMuList() {
  muLoading.value = true;
  listMobileUser({
    keyword: muQuery.keyword,
    pageNum: muPagination.current,
    pageSize: muPagination.pageSize
  }).then((res: any) => {
    muList.value = res.records;
    muPagination.total = res.total;
  }).finally(() => {
    muLoading.value = false;
  });
}

function handleMuQuery() {
  muPagination.current = 1;
  getMuList();
}

function handleMuPageChange(current: number) {
  muPagination.current = current;
  getMuList();
}

function handleMuPageSizeChange(pageSize: number) {
  muPagination.pageSize = pageSize;
  muPagination.current = 1;
  getMuList();
}

// 当前选中的用户 id
const selectedUserId = computed<number | undefined>(() => selectedUserKeys.value[0]);
const selectedGrantLevel = computed(() =>
  levelOptions.value.find((level) => level.levelId === grantForm.levelId)
);
const selectedGrantDurationText = computed(() =>
  selectedGrantLevel.value?.durationDays
    ? `${selectedGrantLevel.value.durationDays} 天`
    : '请选择会员等级'
);

function handleGrant() {
  grantForm.levelId = undefined;
  selectedUserKeys.value = [];
  muQuery.keyword = undefined;
  muPagination.current = 1;
  grantOpen.value = true;
  getMuList();
}

function submitGrant(done: (closed: boolean) => void) {
  if (selectedUserId.value === undefined) {
    Message.warning('请先选择一个用户');
    done(false);
    return;
  }
  grantFormRef.value.validate((errors: any) => {
    if (errors) {
      done(false);
      return;
    }
    grantMembership({
      userId: selectedUserId.value!,
      levelId: grantForm.levelId!
    }).then(() => {
      Message.success('开通成功');
      handleQuery();
      done(true);
    }).catch(() => done(false));
  });
}

onMounted(() => {
  getLevelOptions();
  getList();
});
</script>

<style scoped>
.app-container {
  padding: 0px;
}
</style>
