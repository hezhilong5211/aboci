<template>
  <div class="app-container">
    <a-card :bordered="false">
      <a-form :model="query" layout="inline" class="filter-form">
        <a-form-item label="手机号"><a-input v-model="query.phone" allow-clear placeholder="手机号" /></a-form-item>
        <a-form-item label="昵称"><a-input v-model="query.nickName" allow-clear placeholder="昵称" /></a-form-item>
        <a-form-item label="用户编号"><a-input v-model="query.userNo" allow-clear placeholder="用户编号" /></a-form-item>
        <a-form-item label="分销商编号"><a-input v-model="query.partnerNo" allow-clear placeholder="分销商编号" /></a-form-item>
        <a-form-item label="状态">
          <a-select v-model="query.status" allow-clear placeholder="全部" style="width: 130px;">
            <a-option value="ACTIVE">启用</a-option><a-option value="DISABLED">停用</a-option>
          </a-select>
        </a-form-item>
        <a-form-item>
          <a-space><a-button type="primary" @click="search">查询</a-button><a-button @click="reset">重置</a-button></a-space>
        </a-form-item>
      </a-form>
      <a-divider />
      <a-button type="primary" class="add-button" @click="openAdd" v-hasPermi="['distribution:partner:add']">
        <template #icon><icon-plus /></template>添加分销商
      </a-button>
      <a-table :loading="loading" :data="rows" :pagination="pagination" row-key="partnerId"
               :scroll="{ x: 2100 }" @page-change="pageChange" @page-size-change="sizeChange">
        <template #columns>
          <a-table-column title="分销商" :width="180" fixed="left">
            <template #cell="{ record }"><div class="user-cell"><a-avatar :size="32" :image-url="avatar(record.avatarUrl)">{{ (record.nickName || '用')[0] }}</a-avatar><div><div>{{ record.nickName || '-' }}</div><small>{{ record.partnerNo }}</small></div></div></template>
          </a-table-column>
          <a-table-column title="用户编号" data-index="userNo" :width="150" />
          <a-table-column title="手机号" data-index="phoneMasked" :width="130" />
          <a-table-column title="状态" :width="90" align="center"><template #cell="{ record }"><a-tag :color="record.status === 'ACTIVE' ? 'green' : 'gray'">{{ record.status === 'ACTIVE' ? '启用' : '停用' }}</a-tag></template></a-table-column>
          <a-table-column title="二维码访问" data-index="visitCount" :width="100" align="right" />
          <a-table-column title="有效用户" data-index="promotedUserCount" :width="100" align="right" />
          <a-table-column title="付费用户" data-index="paidUserCount" :width="100" align="right" />
          <a-table-column title="订单数" data-index="validOrderCount" :width="90" align="right" />
          <a-table-column title="订单金额" :width="120" align="right"><template #cell="{ record }">¥{{ yuan(record.validOrderAmount) }}</template></a-table-column>
          <a-table-column title="有效佣金" :width="120" align="right"><template #cell="{ record }">¥{{ yuan(record.validCommissionAmount) }}</template></a-table-column>
          <a-table-column title="可提现" :width="110" align="right"><template #cell="{ record }">¥{{ yuan(record.availableAmount) }}</template></a-table-column>
          <a-table-column title="提现中" :width="110" align="right"><template #cell="{ record }">¥{{ yuan(record.lockedAmount) }}</template></a-table-column>
          <a-table-column title="已提现" :width="110" align="right"><template #cell="{ record }">¥{{ yuan(record.withdrawnAmount) }}</template></a-table-column>
          <a-table-column title="待抵扣" :width="110" align="right"><template #cell="{ record }">¥{{ yuan(record.debtAmount) }}</template></a-table-column>
          <a-table-column title="操作" :width="240" fixed="right" align="center">
            <template #cell="{ record }"><a-space>
              <a-button type="text" size="small" @click="openDetail(record)" v-hasPermi="['distribution:partner:query']">详情</a-button>
              <a-button type="text" size="small" @click="openQrcode(record)" v-hasPermi="['distribution:partner:query']">二维码</a-button>
              <a-button type="text" size="small" :status="record.status === 'ACTIVE' ? 'danger' : 'normal'" @click="toggleStatus(record)" v-hasPermi="['distribution:partner:edit']">{{ record.status === 'ACTIVE' ? '停用' : '启用' }}</a-button>
            </a-space></template>
          </a-table-column>
        </template>
      </a-table>
    </a-card>

    <a-modal v-model:visible="addVisible" title="添加分销商" :width="820" @before-ok="submitAdd">
      <a-alert type="info">分销商只能从已有小程序用户中添加，添加后由系统生成随机邀请码。</a-alert>
      <a-input-search v-model="candidateKeyword" class="candidate-search" placeholder="手机号、昵称或用户编号" search-button @search="loadCandidates" />
      <a-table :loading="candidateLoading" :data="candidates" :pagination="candidatePagination" row-key="userId" @page-change="candidatePageChange">
        <template #columns>
          <a-table-column title="选择" :width="70" align="center"><template #cell="{ record }"><a-radio v-model="selectedUserId" :value="record.userId" :disabled="record.alreadyPartner" /></template></a-table-column>
          <a-table-column title="用户编号" data-index="userNo" />
          <a-table-column title="昵称" data-index="nickName" />
          <a-table-column title="手机号" data-index="phoneMasked" />
          <a-table-column title="状态" :width="120"><template #cell="{ record }"><a-tag :color="record.alreadyPartner ? 'gray' : 'green'">{{ record.alreadyPartner ? '已是分销商' : '可添加' }}</a-tag></template></a-table-column>
        </template>
      </a-table>
      <a-form-item label="运营备注" class="remark"><a-textarea v-model="addRemark" :max-length="500" show-word-limit /></a-form-item>
    </a-modal>

    <a-drawer v-model:visible="detailVisible" title="分销商详情" :width="960" :footer="false">
      <a-spin :loading="detailLoading" style="width: 100%;">
        <a-tabs v-if="detail" default-active-key="overview">
          <a-tab-pane key="overview" title="概览">
            <a-descriptions :column="2" bordered>
              <a-descriptions-item label="分销商编号">{{ detail.partnerNo }}</a-descriptions-item><a-descriptions-item label="用户编号">{{ detail.userNo }}</a-descriptions-item>
              <a-descriptions-item label="昵称">{{ detail.nickName || '-' }}</a-descriptions-item><a-descriptions-item label="手机号">{{ detail.phoneMasked }}</a-descriptions-item>
              <a-descriptions-item label="邀请码">{{ detail.inviteCode }}</a-descriptions-item><a-descriptions-item label="状态">{{ detail.status === 'ACTIVE' ? '启用' : '停用' }}</a-descriptions-item>
              <a-descriptions-item label="启用时间">{{ detail.enabledTime || '-' }}</a-descriptions-item><a-descriptions-item label="停用时间">{{ detail.disabledTime || '-' }}</a-descriptions-item>
              <a-descriptions-item label="备注" :span="2">{{ detail.remark || '-' }}</a-descriptions-item>
            </a-descriptions>
          </a-tab-pane>
          <a-tab-pane key="users" title="推广用户"><simple-table :rows="detailUsers" type="users" /></a-tab-pane>
          <a-tab-pane key="orders" title="推广订单"><simple-table :rows="detailOrders" type="orders" /></a-tab-pane>
          <a-tab-pane key="commissions" title="佣金流水"><simple-table :rows="detailCommissions" type="commissions" /></a-tab-pane>
          <a-tab-pane key="withdrawals" title="提现记录"><simple-table :rows="detailWithdrawals" type="withdrawals" /></a-tab-pane>
        </a-tabs>
      </a-spin>
    </a-drawer>

    <a-modal v-model:visible="qrcodeVisible" title="推广二维码" :width="480" :footer="false">
      <a-spin :loading="qrcodeLoading" class="qrcode-wrap">
        <a-result v-if="qrcodeData.status === 'DISABLED'" status="warning" title="该分销商已停用" subtitle="二维码不再产生新绑定和佣金" />
        <img v-if="qrcodeData.imageData" :src="qrcodeData.imageData" class="qrcode-image" alt="推广二维码" />
        <div class="qrcode-stats">访问 {{ qrcodeData.visitCount || 0 }} 次 · 有效绑定 {{ qrcodeData.promotedUserCount || 0 }} 人</div>
        <a-button v-if="qrcodeData.imageData" type="primary" @click="downloadQrcode">下载二维码</a-button>
      </a-spin>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { defineComponent, h, onMounted, reactive, ref } from 'vue';
import { Message, Modal, Table, TableColumn } from '@arco-design/web-vue';
import { IconPlus } from '@arco-design/web-vue/es/icon';
import { addPartner, getPartner, getPartnerQrcode, listCommissions, listPartnerCandidates, listPartnerOrders, listPartners, listPartnerUsers, listWithdrawals, PartnerRow, updatePartnerStatus } from '@/api/distribution';

const SimpleTable = defineComponent({
  props: { rows: { type: Array, default: () => [] }, type: { type: String, required: true } },
  setup(props) {
    return () => h(Table, { data: props.rows, pagination: false, scroll: { x: 800 } }, {
      columns: () => props.type === 'users' ? [
        h(TableColumn, { title: '用户编号', dataIndex: 'userNo' }), h(TableColumn, { title: '手机号', dataIndex: 'phoneMasked' }),
        h(TableColumn, { title: '绑定时间', dataIndex: 'bindTime' }), h(TableColumn, { title: '订单数', dataIndex: 'orderCount' }),
        h(TableColumn, { title: '订单金额(元)', align: 'right' }, { cell: ({ record }: any) => `¥${yuan(record.orderAmount)}` }),
        h(TableColumn, { title: '佣金(元)', align: 'right' }, { cell: ({ record }: any) => `¥${yuan(record.commissionAmount)}` })
      ] : props.type === 'orders' || props.type === 'commissions' ? [
        h(TableColumn, { title: '订单号', dataIndex: 'orderNo' }), h(TableColumn, { title: '商品', dataIndex: 'productName' }),
        h(TableColumn, { title: '订单金额(元)', align: 'right' }, { cell: ({ record }: any) => `¥${yuan(record.orderAmount)}` }),
        h(TableColumn, { title: '佣金(元)', align: 'right' }, { cell: ({ record }: any) => `¥${yuan(record.commissionAmount)}` }),
        h(TableColumn, { title: '状态', dataIndex: 'status' }), h(TableColumn, { title: '支付时间', dataIndex: 'payTime' })
      ] : [
        h(TableColumn, { title: '提现单号', dataIndex: 'withdrawalNo' }),
        h(TableColumn, { title: '金额(元)', align: 'right' }, { cell: ({ record }: any) => `¥${yuan(record.applyAmount)}` }),
        h(TableColumn, { title: '方式', dataIndex: 'methodType' }), h(TableColumn, { title: '状态', dataIndex: 'status' }),
        h(TableColumn, { title: '申请时间', dataIndex: 'applyTime' })
      ]
    });
  }
});

const loading = ref(false); const rows = ref<PartnerRow[]>([]);
const query = reactive({ phone: '', nickName: '', userNo: '', partnerNo: '', status: '' });
const pagination = reactive({ current: 1, pageSize: 10, total: 0, showTotal: true, showPageSize: true, pageSizeOptions: ['10', '20', '50'] });
const addVisible = ref(false); const candidateLoading = ref(false); const candidates = ref<any[]>([]); const candidateKeyword = ref(''); const selectedUserId = ref<number>(); const addRemark = ref('');
const candidatePagination = reactive({ current: 1, pageSize: 10, total: 0 });
const detailVisible = ref(false); const detailLoading = ref(false); const detail = ref<PartnerRow>(); const detailUsers = ref<any[]>([]); const detailOrders = ref<any[]>([]); const detailCommissions = ref<any[]>([]); const detailWithdrawals = ref<any[]>([]);
const qrcodeVisible = ref(false); const qrcodeLoading = ref(false); const qrcodeData = reactive<any>({});

function yuan(value?: number) { return ((value || 0) / 100).toFixed(2); }
function avatar(url?: string) { if (!url) return ''; const i = url.indexOf('/profile/'); return i >= 0 ? '/api' + url.slice(i) : url; }
async function load() { loading.value = true; try { const res = await listPartners({ ...query, pageNum: pagination.current, pageSize: pagination.pageSize }); rows.value = res.records || []; pagination.total = res.total || 0; } finally { loading.value = false; } }
function search() { pagination.current = 1; load(); }
function reset() { Object.assign(query, { phone: '', nickName: '', userNo: '', partnerNo: '', status: '' }); search(); }
function pageChange(page: number) { pagination.current = page; load(); }
function sizeChange(size: number) { pagination.pageSize = size; pagination.current = 1; load(); }
function openAdd() { selectedUserId.value = undefined; addRemark.value = ''; candidateKeyword.value = ''; candidatePagination.current = 1; addVisible.value = true; loadCandidates(); }
async function loadCandidates() { candidateLoading.value = true; try { const res = await listPartnerCandidates({ keyword: candidateKeyword.value, pageNum: candidatePagination.current, pageSize: candidatePagination.pageSize }); candidates.value = res.records || []; candidatePagination.total = res.total || 0; } finally { candidateLoading.value = false; } }
function candidatePageChange(page: number) { candidatePagination.current = page; loadCandidates(); }
async function submitAdd(done: (closed: boolean) => void) { if (!selectedUserId.value) { Message.warning('请选择可添加的小程序用户'); done(false); return; } try { await addPartner({ userId: selectedUserId.value, remark: addRemark.value }); Message.success('分销商添加成功'); done(true); load(); } catch { done(false); } }
function toggleStatus(record: PartnerRow) { const next = record.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE'; Modal.confirm({ title: next === 'ACTIVE' ? '确认启用' : '确认停用', content: next === 'ACTIVE' ? '重新启用后只计算本次启用之后支付的新订单。' : '永久绑定和余额会保留，停用期间新订单不产生佣金。', onOk: async () => { await updatePartnerStatus(record.partnerId, { status: next, remark: record.remark }); Message.success(next === 'ACTIVE' ? '已启用' : '已停用'); load(); } }); }
async function openDetail(record: PartnerRow) { detailVisible.value = true; detailLoading.value = true; try { const [info, users, orders, commissions, withdrawals] = await Promise.all([getPartner(record.partnerId), listPartnerUsers(record.partnerId, { pageNum: 1, pageSize: 50 }), listPartnerOrders(record.partnerId, { pageNum: 1, pageSize: 50 }), listCommissions({ partnerId: record.partnerId, pageNum: 1, pageSize: 50 }), listWithdrawals({ partnerId: record.partnerId, pageNum: 1, pageSize: 50 })]); detail.value = info; detailUsers.value = users.records || []; detailOrders.value = orders.records || []; detailCommissions.value = commissions.records || []; detailWithdrawals.value = withdrawals.records || []; } finally { detailLoading.value = false; } }
async function openQrcode(record: PartnerRow) { qrcodeVisible.value = true; qrcodeLoading.value = true; Object.keys(qrcodeData).forEach(key => delete qrcodeData[key]); try { Object.assign(qrcodeData, await getPartnerQrcode(record.partnerId)); } finally { qrcodeLoading.value = false; } }
function downloadQrcode() { const link = document.createElement('a'); link.href = qrcodeData.imageData; link.download = `${detail.value?.partnerNo || 'distribution'}-qrcode.png`; link.click(); }
onMounted(load);
</script>

<style scoped>
.filter-form { row-gap: 8px; }.add-button { margin-bottom: 16px; }.user-cell { display: flex; gap: 10px; align-items: center; }.user-cell small { color: var(--color-text-3); }.candidate-search { margin: 16px 0; }.remark { margin-top: 16px; }.qrcode-wrap { display: flex; min-height: 320px; flex-direction: column; align-items: center; gap: 14px; }.qrcode-image { width: 260px; height: 260px; object-fit: contain; }.qrcode-stats { color: var(--color-text-2); }
</style>
