<template>
  <div class="app-container">
    <a-card :bordered="false">
      <a-form :model="query" layout="inline" class="filter-form">
        <a-form-item label="提现单号"><a-input v-model="query.withdrawalNo" allow-clear /></a-form-item>
        <a-form-item label="分销商"><a-input v-model="query.partnerKeyword" allow-clear placeholder="编号或昵称" /></a-form-item>
        <a-form-item label="手机号"><a-input v-model="query.phone" allow-clear /></a-form-item>
        <a-form-item label="收款方式"><a-select v-model="query.methodType" allow-clear placeholder="全部" style="width: 150px;"><a-option value="WECHAT_QR">微信收款码</a-option><a-option value="ALIPAY_QR">支付宝收款码</a-option><a-option value="BANK_CARD">银行卡</a-option></a-select></a-form-item>
        <a-form-item label="状态"><a-select v-model="query.status" allow-clear placeholder="全部" style="width: 140px;"><a-option value="SUBMITTED">待处理</a-option><a-option value="NEEDS_REVIEW">需复核</a-option><a-option value="PAID">已打款</a-option><a-option value="REJECTED">已驳回</a-option></a-select></a-form-item>
        <a-form-item label="申请时间"><a-range-picker v-model="timeRange" show-time format="YYYY-MM-DD HH:mm:ss" style="width: 360px;" /></a-form-item>
        <a-form-item><a-space><a-button type="primary" @click="search">查询</a-button><a-button @click="reset">重置</a-button></a-space></a-form-item>
      </a-form>
      <a-divider />
      <a-alert v-if="rows.some(item => item.status === 'NEEDS_REVIEW')" type="warning" class="review-alert">存在受退款影响的提现单。需复核状态禁止打款，只能驳回并让分销商重新申请。</a-alert>
      <a-table :loading="loading" :data="rows" :pagination="pagination" row-key="withdrawalId" :scroll="{ x: 1900 }" @page-change="pageChange" @page-size-change="sizeChange">
        <template #columns>
          <a-table-column title="提现单号" data-index="withdrawalNo" :width="210" fixed="left" />
          <a-table-column title="分销商" :width="170"><template #cell="{ record }"><div>{{ record.partnerName || '-' }}</div><small>{{ record.partnerNo }} · {{ record.phoneMasked }}</small></template></a-table-column>
          <a-table-column title="申请金额" :width="120" align="right"><template #cell="{ record }"><strong>¥{{ yuan(record.applyAmount) }}</strong></template></a-table-column>
          <a-table-column title="收款方式" :width="130"><template #cell="{ record }">{{ methodText(record.methodType) }}</template></a-table-column>
          <a-table-column title="收款人" data-index="payeeName" :width="120" />
          <a-table-column title="收款资料" :width="180"><template #cell="{ record }">
            <template v-if="record.methodType === 'BANK_CARD'">
              <a-link v-if="canViewSensitive" @click="openPaymentProfile(record)">银行卡</a-link><span v-else>银行卡</span>
            </template>
            <template v-else-if="record.receiptAvailable">
              <img v-if="canViewSensitive && receiptThumbnails[record.withdrawalId]" class="receipt-thumbnail" :src="receiptThumbnails[record.withdrawalId]" alt="收款码缩略图" @click="openPaymentProfile(record)" />
              <a-link v-else-if="canViewSensitive" @click="openPaymentProfile(record)">收款码</a-link><span v-else>收款码已上传</span>
            </template>
            <span v-else>未上传</span>
          </template></a-table-column>
          <a-table-column title="状态" :width="110" align="center"><template #cell="{ record }"><a-tag :color="statusColor(record.status)">{{ statusText(record.status) }}</a-tag></template></a-table-column>
          <a-table-column title="打款流水" data-index="paymentReferenceMasked" :width="150"><template #cell="{ record }">{{ record.paymentReferenceMasked || '-' }}</template></a-table-column>
          <a-table-column title="申请时间" data-index="applyTime" :width="170" />
          <a-table-column title="处理时间" :width="170"><template #cell="{ record }">{{ record.paidTime || record.auditTime || '-' }}</template></a-table-column>
          <a-table-column title="操作" :width="260" fixed="right" align="center"><template #cell="{ record }"><a-space>
            <a-button type="text" size="small" @click="openDetail(record)" v-hasPermi="['distribution:withdrawal:query']">详情</a-button>
            <a-button type="text" size="small" @click="openSensitive(record)" v-hasPermi="['distribution:withdrawal:sensitive']">完整资料</a-button>
            <a-button v-if="record.status === 'SUBMITTED' || record.status === 'NEEDS_REVIEW'" type="text" size="small" status="danger" @click="openReject(record)" v-hasPermi="['distribution:withdrawal:audit']">驳回</a-button>
            <a-button v-if="record.status === 'SUBMITTED'" type="text" size="small" @click="openPaid(record)" v-hasPermi="['distribution:withdrawal:pay']">确认打款</a-button>
          </a-space></template></a-table-column>
        </template>
      </a-table>
    </a-card>

    <a-drawer v-model:visible="detailVisible" title="提现详情" :width="850" :footer="false">
      <a-spin :loading="detailLoading" style="width: 100%;">
        <a-descriptions v-if="detail" :column="2" bordered>
          <a-descriptions-item label="提现单号" :span="2">{{ detail.withdrawalNo }}</a-descriptions-item><a-descriptions-item label="分销商">{{ detail.partnerName }}（{{ detail.partnerNo }}）</a-descriptions-item><a-descriptions-item label="申请金额">¥{{ yuan(detail.applyAmount) }}</a-descriptions-item>
          <a-descriptions-item label="收款方式">{{ methodText(detail.methodType) }}</a-descriptions-item><a-descriptions-item label="收款人">{{ detail.payeeName }}</a-descriptions-item>
          <a-descriptions-item label="默认脱敏资料" :span="2">{{ detail.methodType === 'BANK_CARD' ? `${detail.bankName} ${detail.bankAccountMasked}` : '私有收款码，仅通过独立权限查看' }}</a-descriptions-item>
          <a-descriptions-item label="状态">{{ statusText(detail.status) }}</a-descriptions-item><a-descriptions-item label="申请时间">{{ detail.applyTime }}</a-descriptions-item>
          <a-descriptions-item label="驳回原因" :span="2">{{ detail.rejectReason || '-' }}</a-descriptions-item>
          <a-descriptions-item label="打款流水">{{ detail.paymentReferenceMasked || '-' }}</a-descriptions-item><a-descriptions-item label="付款凭证">{{ detail.paymentProofAvailable ? '已私有留档' : '-' }}</a-descriptions-item>
        </a-descriptions>
        <a-divider>资金流水</a-divider>
        <a-table :data="detail?.transactions || []" :pagination="false"><template #columns><a-table-column title="类型" data-index="type" /><a-table-column title="变动(元)" align="right"><template #cell="{ record }">{{ money(record.changeAmount) }}</template></a-table-column><a-table-column title="说明" data-index="remark" /><a-table-column title="时间" data-index="createTime" :width="170" /></template></a-table>
      </a-spin>
    </a-drawer>

    <a-modal v-model:visible="paymentProfileVisible" title="打款资料" :width="620" :footer="false">
      <a-spin :loading="paymentProfileLoading" style="width: 100%;">
        <a-alert type="warning">以下内容属于敏感资料，仅限本次打款使用，访问行为已记录审计日志。</a-alert>
        <a-descriptions v-if="paymentProfile" :column="1" bordered class="sensitive-content">
          <a-descriptions-item label="收款方式">{{ methodText(paymentProfile.methodType) }}</a-descriptions-item>
          <a-descriptions-item label="收款人">{{ paymentProfile.payeeName }}</a-descriptions-item>
          <a-descriptions-item v-if="paymentProfile.bankAccount" label="银行卡">{{ paymentProfile.bankName }} {{ paymentProfile.bankAccount }}</a-descriptions-item>
        </a-descriptions>
        <div v-if="paymentProfile?.receiptImageData" class="payment-profile-image"><img :src="paymentProfile.receiptImageData" alt="收款码" /></div>
      </a-spin>
    </a-modal>

    <a-modal v-model:visible="sensitiveVisible" title="完整收款资料" :width="620" :footer="false">
      <a-alert type="warning">以下内容属于敏感资料，仅限本次打款核验使用，访问行为已记录审计日志。</a-alert>
      <a-descriptions v-if="sensitive" :column="1" bordered class="sensitive-content"><a-descriptions-item label="收款方式">{{ methodText(sensitive.methodType) }}</a-descriptions-item><a-descriptions-item label="收款人">{{ sensitive.payeeName }}</a-descriptions-item><a-descriptions-item v-if="sensitive.bankAccount" label="银行卡">{{ sensitive.bankName }} {{ sensitive.bankAccount }}</a-descriptions-item></a-descriptions>
      <div class="image-grid"><div v-if="sensitive?.receiptImageData"><div class="image-title">收款码</div><img :src="sensitive.receiptImageData" alt="收款码" /></div><div v-if="sensitive?.paymentProofImageData"><div class="image-title">付款凭证</div><img :src="sensitive.paymentProofImageData" alt="付款凭证" /></div></div>
    </a-modal>

    <a-modal v-model:visible="rejectVisible" title="驳回提现" :width="500" @before-ok="submitReject"><a-form ref="rejectFormRef" :model="rejectForm" :rules="rejectRules" layout="vertical"><a-form-item label="驳回原因" field="reason"><a-textarea v-model="rejectForm.reason" :max-length="500" show-word-limit /></a-form-item></a-form></a-modal>

    <a-modal v-model:visible="paidVisible" title="确认线下打款" :width="560" ok-text="确认已打款" @before-ok="submitPaid">
      <a-alert type="warning">确认后提现单不可修改。打款流水号和付款凭证用于线下留档，可按实际情况选填。</a-alert>
      <a-form ref="paidFormRef" :model="paidForm" :rules="paidRules" layout="vertical" class="paid-form">
        <a-form-item label="打款流水号（选填）" field="paymentReference"><a-input v-model="paidForm.paymentReference" :max-length="128" /></a-form-item>
        <a-form-item label="付款凭证（选填）"><a-space><a-upload :show-file-list="false" :limit="1" accept="image/*" :custom-request="captureProof"><a-button>选择付款凭证</a-button></a-upload><span v-if="proofFileName">{{ proofFileName }}</span><a-button v-if="proofFileName" type="text" status="danger" @click="clearProof">移除</a-button></a-space></a-form-item>
        <a-form-item field="confirmed"><a-checkbox v-model="paidForm.confirmed">我确认已完成线下打款，金额为 ¥{{ yuan(paidTarget?.applyAmount) }}</a-checkbox></a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { Message } from '@arco-design/web-vue';
import { getWithdrawal, getWithdrawalPaymentProfile, getWithdrawalReceiptThumbnails, getWithdrawalSensitive, listWithdrawals, markWithdrawalPaid, rejectWithdrawal, WithdrawalPaymentProfile, WithdrawalRow, WithdrawalSensitive } from '@/api/distribution';
import { usePermissionStore } from '@/stores/permission';

const loading = ref(false); const rows = ref<WithdrawalRow[]>([]); const timeRange = ref<string[]>([]);
const permissionStore = usePermissionStore(); const canViewSensitive = computed(() => permissionStore.hasAnyPermission('distribution:withdrawal:sensitive'));
const receiptThumbnails = reactive<Record<number, string>>({});
const query = reactive({ withdrawalNo: '', partnerKeyword: '', phone: '', methodType: '', status: '' });
const pagination = reactive({ current: 1, pageSize: 10, total: 0, showTotal: true, showPageSize: true, pageSizeOptions: ['10', '20', '50'] });
const detailVisible = ref(false); const detailLoading = ref(false); const detail = ref<WithdrawalRow>();
const sensitiveVisible = ref(false); const sensitive = ref<WithdrawalSensitive>();
const paymentProfileVisible = ref(false); const paymentProfileLoading = ref(false); const paymentProfile = ref<WithdrawalPaymentProfile>();
const rejectVisible = ref(false); const rejectTarget = ref<WithdrawalRow>(); const rejectFormRef = ref(); const rejectForm = reactive({ reason: '' }); const rejectRules = { reason: [{ required: true, message: '请输入驳回原因' }] };
const paidVisible = ref(false); const paidTarget = ref<WithdrawalRow>(); const paidFormRef = ref(); const proofFile = ref<File>(); const proofFileName = ref(''); const paidForm = reactive({ paymentReference: '', confirmed: false });
const paidRules = { confirmed: [{ validator: (value: boolean, cb: (msg?: string) => void) => cb(value ? undefined : '请再次确认已完成线下打款') }] };
function yuan(value?: number) { return ((value || 0) / 100).toFixed(2); }
function money(value?: number) { const amount = (value || 0) / 100; return `${amount < 0 ? '-' : ''}¥${Math.abs(amount).toFixed(2)}`; }
function methodText(type: string) { return ({ WECHAT_QR: '微信收款码', ALIPAY_QR: '支付宝收款码', BANK_CARD: '银行卡' } as Record<string, string>)[type] || type; }
function statusText(status: string) { return ({ SUBMITTED: '待处理', NEEDS_REVIEW: '需复核', PAID: '已打款', REJECTED: '已驳回' } as Record<string, string>)[status] || status; }
function statusColor(status: string) { return ({ SUBMITTED: 'orange', NEEDS_REVIEW: 'red', PAID: 'green', REJECTED: 'gray' } as Record<string, string>)[status] || 'gray'; }
async function load() { loading.value = true; try { const res = await listWithdrawals({ ...query, applyStart: timeRange.value?.[0], applyEnd: timeRange.value?.[1], pageNum: pagination.current, pageSize: pagination.pageSize }); rows.value = res.records || []; pagination.total = res.total || 0; loadReceiptThumbnails(rows.value); } finally { loading.value = false; } }
async function loadReceiptThumbnails(records: WithdrawalRow[]) { Object.keys(receiptThumbnails).forEach(key => delete receiptThumbnails[Number(key)]); if (!canViewSensitive.value) return; const ids = records.filter(item => item.methodType !== 'BANK_CARD' && item.receiptAvailable).map(item => item.withdrawalId); if (!ids.length) return; try { const result = await getWithdrawalReceiptThumbnails(ids); const activeIds = new Set(rows.value.map(item => item.withdrawalId)); result.forEach(item => { if (activeIds.has(item.withdrawalId)) receiptThumbnails[item.withdrawalId] = item.imageData; }); } catch { /* 缩略图失败不影响提现列表操作。 */ } }
function search() { pagination.current = 1; load(); } function reset() { Object.assign(query, { withdrawalNo: '', partnerKeyword: '', phone: '', methodType: '', status: '' }); timeRange.value = []; search(); }
function pageChange(page: number) { pagination.current = page; load(); } function sizeChange(size: number) { pagination.pageSize = size; pagination.current = 1; load(); }
async function openDetail(record: WithdrawalRow) { detailVisible.value = true; detailLoading.value = true; try { detail.value = await getWithdrawal(record.withdrawalId); } finally { detailLoading.value = false; } }
async function openSensitive(record: WithdrawalRow) { sensitive.value = await getWithdrawalSensitive(record.withdrawalId); sensitiveVisible.value = true; }
async function openPaymentProfile(record: WithdrawalRow) { paymentProfile.value = undefined; paymentProfileVisible.value = true; paymentProfileLoading.value = true; try { paymentProfile.value = await getWithdrawalPaymentProfile(record.withdrawalId); } finally { paymentProfileLoading.value = false; } }
function openReject(record: WithdrawalRow) { rejectTarget.value = record; rejectForm.reason = ''; rejectVisible.value = true; }
async function submitReject(done: (closed: boolean) => void) { const errors = await rejectFormRef.value.validate(); if (errors) { done(false); return; } try { await rejectWithdrawal(rejectTarget.value!.withdrawalId, rejectForm.reason); Message.success('提现已驳回'); done(true); load(); } catch { done(false); } }
function openPaid(record: WithdrawalRow) { paidTarget.value = record; paidForm.paymentReference = ''; paidForm.confirmed = false; clearProof(); paidVisible.value = true; }
function captureProof(option: any) { proofFile.value = option.fileItem.file; proofFileName.value = option.fileItem.name || '已选择'; option.onSuccess(); return { abort() {} }; }
function clearProof() { proofFile.value = undefined; proofFileName.value = ''; }
async function submitPaid(done: (closed: boolean) => void) { const errors = await paidFormRef.value.validate(); if (errors) { done(false); return; } try { await markWithdrawalPaid(paidTarget.value!.withdrawalId, paidForm.paymentReference, proofFile.value); Message.success('已确认线下打款'); done(true); load(); } catch { done(false); } }
onMounted(load);
</script>

<style scoped>
.filter-form { row-gap: 8px; }.review-alert { margin-bottom: 16px; } small { color: var(--color-text-3); }.receipt-thumbnail { display: block; width: 48px; height: 48px; object-fit: cover; border: 1px solid var(--color-border-2); border-radius: 6px; cursor: pointer; }.sensitive-content,.paid-form { margin-top: 16px; }.image-grid { display: grid; grid-template-columns: repeat(auto-fit,minmax(220px,1fr)); gap: 16px; margin-top: 16px; }.image-grid img,.payment-profile-image img { width: 100%; max-height: 420px; object-fit: contain; border: 1px solid var(--color-border-2); border-radius: 8px; }.payment-profile-image { margin-top: 16px; }.image-title { margin-bottom: 8px; font-weight: 600; }
</style>
