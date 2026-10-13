<template>
  <div class="app-container">
    <a-card :bordered="false">
      <a-form :model="query" layout="inline" class="filter-form">
        <a-form-item label="分销商"><a-input v-model="query.partnerKeyword" allow-clear placeholder="编号或昵称" /></a-form-item>
        <a-form-item label="用户手机号"><a-input v-model="query.phone" allow-clear placeholder="手机号" /></a-form-item>
        <a-form-item label="订单号"><a-input v-model="query.orderNo" allow-clear placeholder="订单号" /></a-form-item>
        <a-form-item label="商品类型"><a-select v-model="query.productType" allow-clear placeholder="全部" style="width: 140px;"><a-option value="MEMBERSHIP">会员</a-option><a-option value="BEAN_RECHARGE">加油包</a-option></a-select></a-form-item>
        <a-form-item label="状态"><a-select v-model="query.status" allow-clear placeholder="全部" style="width: 140px;"><a-option v-for="item in statusOptions" :key="item.value" :value="item.value">{{ item.label }}</a-option></a-select></a-form-item>
        <a-form-item label="支付时间"><a-range-picker v-model="timeRange" show-time format="YYYY-MM-DD HH:mm:ss" style="width: 360px;" /></a-form-item>
        <a-form-item><a-space><a-button type="primary" @click="search">查询</a-button><a-button @click="reset">重置</a-button></a-space></a-form-item>
      </a-form>
      <a-divider />
      <a-table :loading="loading" :data="rows" :pagination="pagination" row-key="commissionId" :scroll="{ x: 2200 }" @page-change="pageChange" @page-size-change="sizeChange">
        <template #columns>
          <a-table-column title="订单号" data-index="orderNo" :width="190" fixed="left" ellipsis tooltip />
          <a-table-column title="分销商" :width="160"><template #cell="{ record }"><div>{{ record.partnerName || '-' }}</div><small>{{ record.partnerNo }}</small></template></a-table-column>
          <a-table-column title="订单用户" :width="150"><template #cell="{ record }"><div>{{ record.userNo }}</div><small>{{ record.phoneMasked }}</small></template></a-table-column>
          <a-table-column title="商品" :width="150"><template #cell="{ record }"><div>{{ productText(record.productType) }}</div><small>{{ record.productName || '-' }}</small></template></a-table-column>
          <a-table-column title="实付金额" :width="110" align="right"><template #cell="{ record }">¥{{ yuan(record.orderAmount) }}</template></a-table-column>
          <a-table-column title="比例" :width="90" align="right"><template #cell="{ record }">{{ rate(record.rateBps) }}%</template></a-table-column>
          <a-table-column title="原始佣金" :width="110" align="right"><template #cell="{ record }">¥{{ yuan(record.commissionAmount) }}</template></a-table-column>
          <a-table-column title="可提现" :width="100" align="right"><template #cell="{ record }">¥{{ yuan(record.availableAmount) }}</template></a-table-column>
          <a-table-column title="锁定" :width="100" align="right"><template #cell="{ record }">¥{{ yuan(record.lockedAmount) }}</template></a-table-column>
          <a-table-column title="已提现" :width="100" align="right"><template #cell="{ record }">¥{{ yuan(record.withdrawnAmount) }}</template></a-table-column>
          <a-table-column title="已冲正" :width="100" align="right"><template #cell="{ record }">¥{{ yuan(record.reversedAmount) }}</template></a-table-column>
          <a-table-column title="欠款抵扣" :width="100" align="right"><template #cell="{ record }">¥{{ yuan(record.debtOffsetAmount) }}</template></a-table-column>
          <a-table-column title="状态" :width="100" align="center"><template #cell="{ record }"><a-tag :color="statusColor(record.status)">{{ statusText(record.status) }}</a-tag></template></a-table-column>
          <a-table-column title="支付时间" data-index="payTime" :width="170" />
          <a-table-column title="退款信息" :width="190"><template #cell="{ record }"><span v-if="record.refundNo">{{ record.refundNo }}<br><small>{{ record.refundTime }}</small></span><span v-else>-</span></template></a-table-column>
          <a-table-column title="操作" :width="140" fixed="right" align="center"><template #cell="{ record }"><a-space><a-button type="text" size="small" @click="openDetail(record)" v-hasPermi="['distribution:commission:query']">详情</a-button><a-button type="text" size="small" @click="openAdjust(record)" v-hasPermi="['distribution:commission:adjust']">纠错</a-button></a-space></template></a-table-column>
        </template>
      </a-table>
    </a-card>

    <a-drawer v-model:visible="detailVisible" title="佣金与资金流水" :width="900" :footer="false">
      <a-spin :loading="detailLoading" style="width: 100%;">
        <a-descriptions v-if="detail" :column="2" bordered>
          <a-descriptions-item label="订单号" :span="2">{{ detail.orderNo }}</a-descriptions-item><a-descriptions-item label="商品">{{ detail.productName }}</a-descriptions-item><a-descriptions-item label="订单用户">{{ detail.userNo }} / {{ detail.phoneMasked }}</a-descriptions-item>
          <a-descriptions-item label="实付金额">¥{{ yuan(detail.orderAmount) }}</a-descriptions-item><a-descriptions-item label="比例快照">{{ rate(detail.rateBps) }}%</a-descriptions-item>
          <a-descriptions-item label="原始佣金">¥{{ yuan(detail.commissionAmount) }}</a-descriptions-item><a-descriptions-item label="状态">{{ statusText(detail.status) }}</a-descriptions-item>
          <a-descriptions-item label="可提现">¥{{ yuan(detail.availableAmount) }}</a-descriptions-item><a-descriptions-item label="提现锁定">¥{{ yuan(detail.lockedAmount) }}</a-descriptions-item>
          <a-descriptions-item label="已提现">¥{{ yuan(detail.withdrawnAmount) }}</a-descriptions-item><a-descriptions-item label="已冲正">¥{{ yuan(detail.reversedAmount) }}</a-descriptions-item>
        </a-descriptions>
        <a-divider>不可修改资金流水</a-divider>
        <a-table :data="detail?.transactions || []" :pagination="false" row-key="transactionId">
          <template #columns><a-table-column title="类型" data-index="type" /><a-table-column title="变动(元)" align="right"><template #cell="{ record }">{{ money(record.changeAmount) }}</template></a-table-column><a-table-column title="可提现快照(元)" align="right"><template #cell="{ record }">{{ money(record.availableAfter) }}</template></a-table-column><a-table-column title="锁定快照(元)" align="right"><template #cell="{ record }">{{ money(record.lockedAfter) }}</template></a-table-column><a-table-column title="欠款快照(元)" align="right"><template #cell="{ record }">{{ money(record.debtAfter) }}</template></a-table-column><a-table-column title="说明" data-index="remark" ellipsis tooltip /><a-table-column title="时间" data-index="createTime" :width="170" /></template>
        </a-table>
      </a-spin>
    </a-drawer>

    <a-modal v-model:visible="adjustVisible" title="佣金纠错" :width="520" @before-ok="submitAdjust">
      <a-alert type="warning">只允许在真实订单原始佣金范围内调整；提现中、已提现或已退款佣金不可调整。操作会写入独立资金流水。</a-alert>
      <a-form ref="adjustFormRef" :model="adjustForm" :rules="adjustRules" layout="vertical" class="adjust-form">
        <a-form-item label="订单号"><a-input :model-value="adjustTarget?.orderNo" readonly /></a-form-item>
        <a-form-item label="原始佣金"><a-input :model-value="`¥${yuan(adjustTarget?.commissionAmount)}`" readonly /></a-form-item>
        <a-form-item label="调整后有效佣金（元）" field="effectiveYuan"><a-input-number v-model="adjustForm.effectiveYuan" :min="0" :max="(adjustTarget?.commissionAmount || 0) / 100" :precision="2" style="width: 100%;" /></a-form-item>
        <a-form-item label="调整原因" field="reason"><a-textarea v-model="adjustForm.reason" :max-length="300" show-word-limit /></a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { Message } from '@arco-design/web-vue';
import { adjustCommission, CommissionRow, getCommission, listCommissions } from '@/api/distribution';

const statusOptions = [{ value: 'AVAILABLE', label: '可提现' }, { value: 'PARTIAL', label: '部分分配' }, { value: 'LOCKED', label: '提现锁定' }, { value: 'WITHDRAWN', label: '已提现' }, { value: 'REVERSED', label: '已冲正' }];
const loading = ref(false); const rows = ref<CommissionRow[]>([]); const timeRange = ref<string[]>([]);
const query = reactive({ partnerKeyword: '', phone: '', orderNo: '', productType: '', status: '' });
const pagination = reactive({ current: 1, pageSize: 10, total: 0, showTotal: true, showPageSize: true, pageSizeOptions: ['10', '20', '50'] });
const detailVisible = ref(false); const detailLoading = ref(false); const detail = ref<CommissionRow>();
const adjustVisible = ref(false); const adjustFormRef = ref(); const adjustTarget = ref<CommissionRow>(); const adjustForm = reactive({ effectiveYuan: 0, reason: '' });
const adjustRules = { effectiveYuan: [{ required: true, message: '请输入调整后有效佣金' }], reason: [{ required: true, message: '请输入调整原因' }] };
function yuan(value?: number) { return ((value || 0) / 100).toFixed(2); } function money(value?: number) { const amount = (value || 0) / 100; return `${amount < 0 ? '-' : ''}¥${Math.abs(amount).toFixed(2)}`; } function rate(value?: number) { return ((value || 0) / 100).toFixed(2); }
function productText(type: string) { return type === 'MEMBERSHIP' ? '会员' : type === 'BEAN_RECHARGE' ? '加油包' : type; }
function statusText(status: string) { return ({ AVAILABLE: '可提现', PARTIAL: '部分分配', LOCKED: '提现锁定', WITHDRAWN: '已提现', REVERSED: '已冲正' } as Record<string, string>)[status] || status; }
function statusColor(status: string) { return ({ AVAILABLE: 'green', PARTIAL: 'blue', LOCKED: 'orange', WITHDRAWN: 'purple', REVERSED: 'red' } as Record<string, string>)[status] || 'gray'; }
async function load() { loading.value = true; try { const res = await listCommissions({ ...query, payStart: timeRange.value?.[0], payEnd: timeRange.value?.[1], pageNum: pagination.current, pageSize: pagination.pageSize }); rows.value = res.records || []; pagination.total = res.total || 0; } finally { loading.value = false; } }
function search() { pagination.current = 1; load(); } function reset() { Object.assign(query, { partnerKeyword: '', phone: '', orderNo: '', productType: '', status: '' }); timeRange.value = []; search(); }
function pageChange(page: number) { pagination.current = page; load(); } function sizeChange(size: number) { pagination.pageSize = size; pagination.current = 1; load(); }
async function openDetail(record: CommissionRow) { detailVisible.value = true; detailLoading.value = true; try { detail.value = await getCommission(record.commissionId); } finally { detailLoading.value = false; } }
function openAdjust(record: CommissionRow) { adjustTarget.value = record; adjustForm.effectiveYuan = (record.commissionAmount - record.reversedAmount) / 100; adjustForm.reason = ''; adjustVisible.value = true; }
async function submitAdjust(done: (closed: boolean) => void) { const errors = await adjustFormRef.value.validate(); if (errors) { done(false); return; } try { await adjustCommission(adjustTarget.value!.commissionId, { effectiveAmount: Math.floor(adjustForm.effectiveYuan * 100), reason: adjustForm.reason }); Message.success('佣金调整成功'); done(true); load(); } catch { done(false); } }
onMounted(load);
</script>

<style scoped>
.filter-form { row-gap: 8px; } small { color: var(--color-text-3); } .adjust-form { margin-top: 16px; }
</style>
