<template>
  <div class="app-container">
    <a-card class="table-card" title="订单管理">
      <!-- 搜索区域 -->
      <a-row :gutter="16" style="margin-bottom: 16px;">
        <a-col :xs="24" :sm="12" :md="8" :lg="5">
          <a-form-item label="状态" :label-col-style="{ width: '60px' }" :style="{ marginBottom: 0 }">
            <a-select v-model="query.status" placeholder="全部状态" allow-clear>
              <a-option
                v-for="(label, val) in STATUS_MAP"
                :key="val"
                :value="Number(val)"
              >
                {{ label.text }}
              </a-option>
            </a-select>
          </a-form-item>
        </a-col>
        <a-col :xs="24" :sm="12" :md="8" :lg="5">
          <a-form-item label="类型" :label-col-style="{ width: '60px' }" :style="{ marginBottom: 0 }">
            <a-select v-model="query.productType" placeholder="全部类型" allow-clear>
              <a-option
                v-for="(label, val) in PRODUCT_TYPE_MAP"
                :key="val"
                :value="val"
              >
                {{ label.text }}
              </a-option>
            </a-select>
          </a-form-item>
        </a-col>
        <a-col :xs="24" :sm="12" :md="8" :lg="6">
          <a-form-item label="关键词" :label-col-style="{ width: '60px' }" :style="{ marginBottom: 0 }">
            <a-input v-model="query.keyword" placeholder="订单号或用户ID" allow-clear @press-enter="handleQuery" />
          </a-form-item>
        </a-col>
        <a-col :xs="24" :sm="12" :md="8" :lg="8">
          <a-form-item label="下单时间" :label-col-style="{ width: '90px' }" :style="{ marginBottom: 0 }">
            <a-range-picker
              v-model="timeRange"
              show-time
              format="YYYY-MM-DD HH:mm:ss"
              style="width: 100%;"
            />
          </a-form-item>
        </a-col>
        <a-col :xs="24" :sm="24" :md="24" :lg="24" style="margin-top: 8px;">
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
        :scroll="{ x: 2850 }"
        row-key="id"
        @page-change="handlePageChange"
        @page-size-change="handlePageSizeChange"
      >
        <template #columns>
          <a-table-column title="订单号" data-index="orderNo" :width="200" ellipsis tooltip />
          <a-table-column title="类型" align="center" :width="100">
            <template #cell="{ record }">
              <a-tag :color="productTypeColor(record.productType)">
                {{ productTypeText(record.productType) }}
              </a-tag>
            </template>
          </a-table-column>
          <a-table-column title="支付渠道" align="center" :width="130">
            <template #cell="{ record }">
              <a-tag :color="payChannelColor(record.payChannel)">
                {{ payChannelText(record.payChannel) }}
              </a-tag>
            </template>
          </a-table-column>
          <a-table-column title="支付环境" align="center" :width="100">
            <template #cell="{ record }">
              <a-tag :color="envColor(record.env, record.payChannel)">
                {{ envText(record.env, record.payChannel) }}
              </a-tag>
            </template>
          </a-table-column>
          <a-table-column title="商品名" data-index="productName" :width="160" ellipsis tooltip />
          <a-table-column title="微信道具 ID" data-index="virtualProductId" :width="180" ellipsis tooltip>
            <template #cell="{ record }">{{ record.virtualProductId || '-' }}</template>
          </a-table-column>
          <a-table-column title="金额(元)" align="right" :width="110">
            <template #cell="{ record }">
              {{ yuan(record.amount) }}
            </template>
          </a-table-column>
          <a-table-column title="状态" align="center" :width="100">
            <template #cell="{ record }">
              <a-tag :color="statusColor(record.status)">
                {{ statusText(record.status) }}
              </a-tag>
            </template>
          </a-table-column>
          <a-table-column title="发放状态" align="center" :width="110">
            <template #cell="{ record }">
              <a-tag :color="fulfillmentStatusColor(record.fulfillmentStatus)">
                {{ fulfillmentStatusText(record.fulfillmentStatus) }}
              </a-tag>
            </template>
          </a-table-column>
          <a-table-column title="发放失败原因" data-index="fulfillmentError" :width="240" ellipsis tooltip>
            <template #cell="{ record }">{{ record.fulfillmentError || '-' }}</template>
          </a-table-column>
          <a-table-column title="微信发货确认" align="center" :width="130">
            <template #cell="{ record }">
              <a-tag :color="deliveryConfirmedColor(record.deliveryConfirmed, record.payChannel)">
                {{ deliveryConfirmedText(record.deliveryConfirmed, record.payChannel) }}
              </a-tag>
            </template>
          </a-table-column>
          <a-table-column title="微信虚拟订单号" data-index="wxOrderId" :width="220" ellipsis tooltip>
            <template #cell="{ record }">{{ record.wxOrderId || '-' }}</template>
          </a-table-column>
          <a-table-column title="交易号" data-index="transactionId" :width="220" ellipsis tooltip />
          <a-table-column title="支付时间" data-index="payTime" :width="180" align="center" />
          <a-table-column title="下单时间" data-index="createTime" :width="180" align="center" />
          <a-table-column title="操作" align="center" :width="90" fixed="right">
            <template #cell="{ record }">
              <a-button type="text" size="small" @click="handleView(record)" v-hasPermi="['mobile:order:query']">查看</a-button>
            </template>
          </a-table-column>
        </template>
      </a-table>
    </a-card>

    <!-- 订单详情查看弹窗(只读) -->
    <a-modal
      v-model:visible="detailOpen"
      title="订单详情"
      :width="640"
      :footer="false"
      :mask-closable="true"
    >
      <a-spin :loading="detailLoading" style="width: 100%;">
        <a-descriptions
          :column="1"
          bordered
          size="medium"
          :label-style="{ width: '120px' }"
        >
          <a-descriptions-item label="订单号">{{ detail.orderNo || '-' }}</a-descriptions-item>
          <a-descriptions-item label="用户ID">{{ detail.userId ?? '-' }}</a-descriptions-item>
          <a-descriptions-item label="openId">{{ detail.openId || '-' }}</a-descriptions-item>
          <a-descriptions-item label="类型">
            <a-tag :color="productTypeColor(detail.productType)">{{ productTypeText(detail.productType) }}</a-tag>
          </a-descriptions-item>
          <a-descriptions-item label="支付渠道">
            <a-tag :color="payChannelColor(detail.payChannel)">{{ payChannelText(detail.payChannel) }}</a-tag>
          </a-descriptions-item>
          <a-descriptions-item label="支付环境">
            <a-tag :color="envColor(detail.env, detail.payChannel)">
              {{ envText(detail.env, detail.payChannel) }}
            </a-tag>
          </a-descriptions-item>
          <a-descriptions-item label="商品名">{{ detail.productName || '-' }}</a-descriptions-item>
          <a-descriptions-item label="微信道具 ID">{{ detail.virtualProductId || '-' }}</a-descriptions-item>
          <a-descriptions-item label="订单标题">{{ detail.subject || '-' }}</a-descriptions-item>
          <a-descriptions-item label="金额(元)">{{ yuan(detail.amount) }}</a-descriptions-item>
          <a-descriptions-item label="状态">
            <a-tag :color="statusColor(detail.status)">{{ statusText(detail.status) }}</a-tag>
          </a-descriptions-item>
          <a-descriptions-item label="发放状态">
            <a-tag :color="fulfillmentStatusColor(detail.fulfillmentStatus)">
              {{ fulfillmentStatusText(detail.fulfillmentStatus) }}
            </a-tag>
          </a-descriptions-item>
          <a-descriptions-item label="发放失败原因">{{ detail.fulfillmentError || '-' }}</a-descriptions-item>
          <a-descriptions-item label="权益发放时间">{{ detail.fulfilledAt || '-' }}</a-descriptions-item>
          <a-descriptions-item label="微信发货确认">
            <a-tag :color="deliveryConfirmedColor(detail.deliveryConfirmed, detail.payChannel)">
              {{ deliveryConfirmedText(detail.deliveryConfirmed, detail.payChannel) }}
            </a-tag>
          </a-descriptions-item>
          <a-descriptions-item label="微信确认时间">{{ detail.deliveryConfirmedAt || '-' }}</a-descriptions-item>
          <a-descriptions-item label="微信虚拟订单号">{{ detail.wxOrderId || '-' }}</a-descriptions-item>
          <a-descriptions-item label="交易号">{{ detail.transactionId || '-' }}</a-descriptions-item>
          <a-descriptions-item label="支付时间">{{ detail.payTime || '-' }}</a-descriptions-item>
          <a-descriptions-item label="下单时间">{{ detail.createTime || '-' }}</a-descriptions-item>
          <!-- 退款信息:仅退款相关字段有值时展示 -->
          <template v-if="detail.refundNo || detail.refundAmount != null || detail.refundTime">
            <a-descriptions-item label="退款单号">{{ detail.refundNo || '-' }}</a-descriptions-item>
            <a-descriptions-item label="退款金额(元)">{{ yuan(detail.refundAmount) }}</a-descriptions-item>
            <a-descriptions-item label="退款时间">{{ detail.refundTime || '-' }}</a-descriptions-item>
          </template>
        </a-descriptions>
      </a-spin>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue';
import { Message } from '@arco-design/web-vue';
import { IconSearch, IconRefresh } from '@arco-design/web-vue/es/icon';
import { listPayOrder, getPayOrder, PayOrder } from '@/api/mobile/payOrder';

// 状态映射:中文文案 + a-tag 着色
const STATUS_MAP: Record<number, { text: string; color: string }> = {
  0: { text: '待支付', color: 'orange' },
  1: { text: '已支付', color: 'green' },
  2: { text: '已退款', color: 'red' },
  3: { text: '已关闭', color: 'gray' },
  4: { text: '已支付待退款', color: 'red' }
};
// 商品类型映射:中文文案 + a-tag 着色
const PRODUCT_TYPE_MAP: Record<string, { text: string; color: string }> = {
  MEMBERSHIP: { text: '会员', color: 'arcoblue' },
  BEAN_RECHARGE: { text: '豆子充值', color: 'purple' }
};
// 支付渠道兼容历史命名，虚拟支付订单不提供后台手工改成功入口。
const PAY_CHANNEL_MAP: Record<string, { text: string; color: string }> = {
  WXPAY: { text: '微信支付', color: 'green' },
  WX_PAY: { text: '微信支付', color: 'green' },
  WECHAT_PAY: { text: '微信支付', color: 'green' },
  JSAPI: { text: '微信支付', color: 'green' },
  VIRTUAL: { text: '微信虚拟支付', color: 'arcoblue' },
  WX_VIRTUAL: { text: '微信虚拟支付', color: 'arcoblue' },
  WECHAT_VIRTUAL: { text: '微信虚拟支付', color: 'arcoblue' },
  WECHAT_VIRTUAL_PAY: { text: '微信虚拟支付', color: 'arcoblue' },
  VIRTUAL_PAY: { text: '微信虚拟支付', color: 'arcoblue' }
};
const FULFILLMENT_STATUS_MAP: Record<number, { text: string; color: string }> = {
  0: { text: '待发放', color: 'orange' },
  1: { text: '已发放', color: 'green' },
  2: { text: '发放失败', color: 'red' }
};
const ENV_MAP: Record<number, { text: string; color: string }> = {
  0: { text: '现网', color: 'blue' },
  1: { text: '沙箱', color: 'orange' }
};
const DELIVERY_CONFIRMED_MAP: Record<number, { text: string; color: string }> = {
  0: { text: '待确认', color: 'orange' },
  1: { text: '已确认', color: 'green' }
};
const VIRTUAL_PAY_CHANNELS = new Set([
  'VIRTUAL', 'WX_VIRTUAL', 'WECHAT_VIRTUAL', 'WECHAT_VIRTUAL_PAY', 'VIRTUAL_PAY'
]);

function statusText(status?: number): string {
  return status != null && STATUS_MAP[status] ? STATUS_MAP[status].text : '-';
}
function statusColor(status?: number): string {
  return status != null && STATUS_MAP[status] ? STATUS_MAP[status].color : 'gray';
}
function productTypeText(type?: string): string {
  return type && PRODUCT_TYPE_MAP[type] ? PRODUCT_TYPE_MAP[type].text : '-';
}
function productTypeColor(type?: string): string {
  return type && PRODUCT_TYPE_MAP[type] ? PRODUCT_TYPE_MAP[type].color : 'gray';
}
function payChannelText(channel?: string): string {
  return channel && PAY_CHANNEL_MAP[channel] ? PAY_CHANNEL_MAP[channel].text : (channel || '-');
}
function payChannelColor(channel?: string): string {
  return channel && PAY_CHANNEL_MAP[channel] ? PAY_CHANNEL_MAP[channel].color : 'gray';
}
function fulfillmentStatusText(status?: number): string {
  return status != null && FULFILLMENT_STATUS_MAP[status]
    ? FULFILLMENT_STATUS_MAP[status].text
    : (status != null ? String(status) : '-');
}
function fulfillmentStatusColor(status?: number): string {
  return status != null && FULFILLMENT_STATUS_MAP[status] ? FULFILLMENT_STATUS_MAP[status].color : 'gray';
}
function isVirtualPayChannel(channel?: string): boolean {
  return Boolean(channel && VIRTUAL_PAY_CHANNELS.has(channel));
}
function envText(env?: number, channel?: string): string {
  if (!isVirtualPayChannel(channel)) return '不适用';
  return env != null && ENV_MAP[env] ? ENV_MAP[env].text : '-';
}
function envColor(env?: number, channel?: string): string {
  if (!isVirtualPayChannel(channel)) return 'gray';
  return env != null && ENV_MAP[env] ? ENV_MAP[env].color : 'gray';
}
function deliveryConfirmedText(status?: number, channel?: string): string {
  if (!isVirtualPayChannel(channel)) return '不适用';
  return status != null && DELIVERY_CONFIRMED_MAP[status]
    ? DELIVERY_CONFIRMED_MAP[status].text
    : (status != null ? String(status) : '-');
}
function deliveryConfirmedColor(status?: number, channel?: string): string {
  if (!isVirtualPayChannel(channel)) return 'gray';
  return status != null && DELIVERY_CONFIRMED_MAP[status] ? DELIVERY_CONFIRMED_MAP[status].color : 'gray';
}
// 金额由分转元展示,保留两位小数
function yuan(amount?: number): string {
  if (amount == null) return '-';
  return (amount / 100).toFixed(2);
}

// ---- 列表 ----
const loading = ref(false);
const list = ref<PayOrder[]>([]);
// 下单时间区间(a-range-picker 双向绑定,提交时转 beginTime/endTime)
const timeRange = ref<string[]>([]);
const query = reactive({
  status: undefined as number | undefined,
  productType: undefined as string | undefined,
  keyword: undefined as string | undefined
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
  listPayOrder({
    status: query.status,
    productType: query.productType,
    keyword: query.keyword,
    beginTime: timeRange.value?.[0] || undefined,
    endTime: timeRange.value?.[1] || undefined,
    pageNum: pagination.current,
    pageSize: pagination.pageSize
  }).then((res: any) => {
    list.value = res.records || [];
    pagination.total = res.total || 0;
  }).finally(() => {
    loading.value = false;
  });
}

function handleQuery() {
  pagination.current = 1;
  getList();
}

function resetQuery() {
  query.status = undefined;
  query.productType = undefined;
  query.keyword = undefined;
  timeRange.value = [];
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

// ---- 详情查看弹窗(只读) ----
const detailOpen = ref(false);
const detailLoading = ref(false);
const detail = ref<PayOrder>({});

function handleView(record: PayOrder) {
  if (record.id == null) {
    Message.error('缺少订单ID,无法查看详情');
    return;
  }
  detailOpen.value = true;
  detailLoading.value = true;
  detail.value = {};
  getPayOrder(record.id).then((res: any) => {
    detail.value = res || {};
  }).finally(() => {
    detailLoading.value = false;
  });
}

onMounted(() => {
  getList();
});
</script>

<style scoped>
.app-container {
  padding: 0px;
}
</style>
