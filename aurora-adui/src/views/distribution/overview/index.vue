<template>
  <div class="app-container">
    <a-spin :loading="loading" style="width: 100%;">
      <a-row :gutter="16">
        <a-col v-for="item in metrics" :key="item.key" :xs="24" :sm="12" :md="6" class="metric-col">
          <a-card :bordered="false" class="metric-card">
            <a-statistic :title="item.title" :value="item.value" :precision="item.money ? 2 : 0">
              <template v-if="item.money" #prefix>¥</template>
            </a-statistic>
            <div class="metric-note">{{ item.note }}</div>
          </a-card>
        </a-col>
      </a-row>

      <a-row :gutter="16" class="content-row">
        <a-col :xs="24" :lg="12">
          <a-card title="最近提现申请" :bordered="false">
            <a-table :data="overview.recentWithdrawals || []" :pagination="false" row-key="withdrawalId">
              <template #columns>
                <a-table-column title="提现单号" data-index="withdrawalNo" ellipsis tooltip />
                <a-table-column title="分销商" data-index="partnerName" :width="120" ellipsis tooltip />
                <a-table-column title="金额" :width="100" align="right">
                  <template #cell="{ record }">¥{{ yuan(record.applyAmount) }}</template>
                </a-table-column>
                <a-table-column title="状态" :width="100" align="center">
                  <template #cell="{ record }"><a-tag :color="withdrawColor(record.status)">{{ withdrawText(record.status) }}</a-tag></template>
                </a-table-column>
              </template>
            </a-table>
          </a-card>
        </a-col>
        <a-col :xs="24" :lg="12">
          <a-card title="分销商业绩排行" :bordered="false">
            <a-table :data="overview.ranking || []" :pagination="false" row-key="partnerId">
              <template #columns>
                <a-table-column title="分销商" data-index="nickName" ellipsis tooltip />
                <a-table-column title="有效用户" data-index="promotedUserCount" :width="90" align="right" />
                <a-table-column title="订单金额" :width="110" align="right">
                  <template #cell="{ record }">¥{{ yuan(record.validOrderAmount) }}</template>
                </a-table-column>
                <a-table-column title="有效佣金" :width="110" align="right">
                  <template #cell="{ record }">¥{{ yuan(record.validCommissionAmount) }}</template>
                </a-table-column>
              </template>
            </a-table>
          </a-card>
        </a-col>
      </a-row>
    </a-spin>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { Message } from '@arco-design/web-vue';
import { getOverview, OverviewData } from '@/api/distribution';

const loading = ref(false);
const overview = reactive<OverviewData>({
  partnerCount: 0,
  promotedUserCount: 0,
  paidUserCount: 0,
  validOrderCount: 0,
  validOrderAmount: 0,
  validCommissionAmount: 0,
  unwithdrawnAmount: 0,
  withdrawnAmount: 0,
  debtAmount: 0,
  recentWithdrawals: [],
  ranking: []
});

const metrics = computed(() => [
  { key: 'partner', title: '分销商', value: overview.partnerCount, note: '后台已添加的分销商', money: false },
  { key: 'user', title: '有效推广用户', value: overview.promotedUserCount, note: '永久绑定去重用户', money: false },
  { key: 'paidUser', title: '付费用户', value: overview.paidUserCount, note: '绑定后产生有效订单', money: false },
  { key: 'order', title: '有效订单', value: overview.validOrderCount, note: `金额 ¥${yuan(overview.validOrderAmount)}`, money: false },
  { key: 'commission', title: '有效佣金', value: overview.validCommissionAmount / 100, note: '已扣除退款冲正', money: true },
  { key: 'unwithdrawn', title: '未提现', value: overview.unwithdrawnAmount / 100, note: '可提现与提现中合计', money: true },
  { key: 'withdrawn', title: '已提现', value: overview.withdrawnAmount / 100, note: '后台已确认打款', money: true },
  { key: 'debt', title: '待抵扣佣金', value: overview.debtAmount / 100, note: '后续佣金优先抵扣', money: true }
]);

function yuan(value?: number) {
  return ((value || 0) / 100).toFixed(2);
}

function withdrawText(status: string) {
  return ({ SUBMITTED: '待处理', NEEDS_REVIEW: '需复核', PAID: '已打款', REJECTED: '已驳回' } as Record<string, string>)[status] || status;
}

function withdrawColor(status: string) {
  return ({ SUBMITTED: 'orange', NEEDS_REVIEW: 'red', PAID: 'green', REJECTED: 'gray' } as Record<string, string>)[status] || 'gray';
}

async function load() {
  loading.value = true;
  try {
    Object.assign(overview, await getOverview());
  } catch (error: any) {
    if (error?.message !== '__HANDLED__') Message.error(error?.message || '分销概览加载失败');
  } finally {
    loading.value = false;
  }
}

onMounted(load);
</script>

<style scoped>
.metric-col { margin-bottom: 16px; }
.metric-card { min-height: 128px; }
.metric-note { margin-top: 10px; color: var(--color-text-3); font-size: 12px; }
.content-row > :deep(.arco-col) { margin-bottom: 16px; }
</style>
