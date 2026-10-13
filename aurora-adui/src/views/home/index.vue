<template>
  <div class="home-container">
    <!-- 顶部:4 张 KPI 卡 -->
    <a-row :gutter="16" class="kpi-row">
      <a-col :span="6">
        <a-card class="kpi-card">
          <a-statistic
            :value="overview.userTotal"
            :precision="0"
            :value-style="{ color: '#165DFF', fontSize: '26px', fontWeight: 600 }"
          >
            <template #title>
              <a-space>
                <icon-user-group />
                <span>用户总数</span>
              </a-space>
            </template>
          </a-statistic>
          <div class="kpi-sub">
            今日新增 <span class="kpi-up">+{{ overview.userToday }}</span>
          </div>
        </a-card>
      </a-col>
      <a-col :span="6">
        <a-card class="kpi-card">
          <a-statistic
            :value="overview.genToday"
            :precision="0"
            :value-style="{ color: '#722ED1', fontSize: '26px', fontWeight: 600 }"
          >
            <template #title>
              <a-space>
                <icon-robot />
                <span>今日 AI 产出</span>
              </a-space>
            </template>
          </a-statistic>
          <div class="kpi-sub">
            较昨日
            <span :class="genRatio.up ? 'kpi-up' : 'kpi-down'">
              {{ genRatio.text }}
              <icon-arrow-rise v-if="genRatio.up" :size="12" />
              <icon-arrow-fall v-else :size="12" />
            </span>
          </div>
        </a-card>
      </a-col>
      <a-col :span="6">
        <a-card class="kpi-card">
          <a-statistic
            :value="overview.incomeToday"
            :precision="2"
            :value-style="{ color: '#FF7D00', fontSize: '26px', fontWeight: 600 }"
          >
            <template #title>
              <a-space>
                <icon-dashboard />
                <span>今日收入</span>
              </a-space>
            </template>
            <template #prefix>
              <span style="font-size: 16px;">¥</span>
            </template>
          </a-statistic>
          <div class="kpi-sub">
            累计收入 <span class="kpi-strong">¥{{ formatMoney(overview.incomeTotal) }}</span>
          </div>
        </a-card>
      </a-col>
      <a-col :span="6">
        <a-card class="kpi-card">
          <a-statistic
            :value="overview.memberActive"
            :precision="0"
            :value-style="{ color: '#00B42A', fontSize: '26px', fontWeight: 600 }"
          >
            <template #title>
              <a-space>
                <icon-trophy />
                <span>有效会员</span>
              </a-space>
            </template>
          </a-statistic>
          <div class="kpi-sub">
            今日活跃创作 <span class="kpi-strong">{{ overview.activeCreatorsToday }}</span> 人
          </div>
        </a-card>
      </a-col>
    </a-row>

    <!-- 中部:左 近30天产出+收入双轴折线 / 右 豆子经济环形 -->
    <a-row :gutter="16" class="mid-row">
      <a-col :span="16">
        <a-card class="chart-card">
          <template #title>
            <a-space>
              <icon-bar-chart />
              <span>近30天产出量与收入</span>
            </a-space>
          </template>
          <a-spin :loading="loading" style="width: 100%;">
            <div v-show="overview.trend.length > 0" ref="trendContainer" class="trend-container"></div>
            <a-empty v-if="!loading && overview.trend.length === 0" description="暂无数据" class="empty-block" />
          </a-spin>
        </a-card>
      </a-col>
      <a-col :span="8">
        <a-card class="chart-card">
          <template #title>
            <a-space>
              <icon-storage />
              <span>豆子经济</span>
            </a-space>
          </template>
          <a-spin :loading="loading" style="width: 100%;">
            <div v-show="hasBean" ref="beanContainer" class="bean-container"></div>
            <a-empty v-if="!loading && !hasBean" description="暂无数据" class="empty-block" />
          </a-spin>
        </a-card>
      </a-col>
    </a-row>

    <!-- 下部:左 模型/类型占比双饼 / 右 最近订单+最近产出 -->
    <a-row :gutter="16" class="bottom-row">
      <a-col :span="16">
        <a-card class="chart-card">
          <template #title>
            <a-space>
              <icon-apps />
              <span>产出分布</span>
            </a-space>
          </template>
          <a-spin :loading="loading" style="width: 100%;">
            <a-row :gutter="16">
              <a-col :span="12">
                <div class="pie-title">模型产出占比</div>
                <div v-show="overview.modelDist.length > 0" ref="modelContainer" class="dist-container"></div>
                <a-empty v-if="!loading && overview.modelDist.length === 0" description="暂无数据" class="empty-block-sm" />
              </a-col>
              <a-col :span="12">
                <div class="pie-title">产出类型占比</div>
                <div v-show="overview.typeDist.length > 0" ref="typeContainer" class="dist-container"></div>
                <a-empty v-if="!loading && overview.typeDist.length === 0" description="暂无数据" class="empty-block-sm" />
              </a-col>
            </a-row>
          </a-spin>
        </a-card>
      </a-col>
      <a-col :span="8">
        <a-card class="list-card">
          <a-tabs default-active-key="orders">
            <a-tab-pane key="orders" title="最近订单">
              <a-table
                :columns="orderColumns"
                :data="overview.recentOrders"
                :pagination="false"
                :bordered="false"
                size="small"
                :scroll="{ y: 280 }"
              >
                <template #amount="{ record }">
                  <span style="color: #FF7D00;">¥{{ formatMoney(record.amount) }}</span>
                </template>
                <template #empty>
                  <a-empty description="暂无数据" />
                </template>
              </a-table>
            </a-tab-pane>
            <a-tab-pane key="gens" title="最近产出">
              <a-table
                :columns="genColumns"
                :data="overview.recentGens"
                :pagination="false"
                :bordered="false"
                size="small"
                :scroll="{ y: 280 }"
              >
                <template #beanCost="{ record }">
                  <span style="color: #F53F3F;">-{{ record.beanCost }}</span>
                </template>
                <template #empty>
                  <a-empty description="暂无数据" />
                </template>
              </a-table>
            </a-tab-pane>
          </a-tabs>
        </a-card>
      </a-col>
    </a-row>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onBeforeUnmount, nextTick } from 'vue';
import { DualAxes, Pie } from '@antv/g2plot';
import {
  IconUserGroup,
  IconRobot,
  IconDashboard,
  IconTrophy,
  IconBarChart,
  IconStorage,
  IconApps,
  IconArrowRise,
  IconArrowFall,
} from '@arco-design/web-vue/es/icon';
import { getDashboardOverview, type DashboardVO } from '@/api/dashboard';

// 看板聚合数据(默认空,接口返回后覆盖)
const overview = reactive<DashboardVO>({
  userTotal: 0,
  userToday: 0,
  genTotal: 0,
  genToday: 0,
  genYesterday: 0,
  activeCreatorsToday: 0,
  incomeTotal: 0,
  incomeToday: 0,
  memberActive: 0,
  trend: [],
  bean: { recharge: 0, consume: 0, grant: 0 },
  modelDist: [],
  typeDist: [],
  recentOrders: [],
  recentGens: [],
});

const loading = ref(true);

const trendContainer = ref<HTMLDivElement>();
const beanContainer = ref<HTMLDivElement>();
const modelContainer = ref<HTMLDivElement>();
const typeContainer = ref<HTMLDivElement>();

let trendChart: DualAxes | null = null;
let beanChart: Pie | null = null;
let modelChart: Pie | null = null;
let typeChart: Pie | null = null;

// 豆子经济是否有数据
const hasBean = computed(
  () => overview.bean.recharge + overview.bean.consume + overview.bean.grant > 0
);

// 今日产出较昨日环比(箭头 + 百分比)
const genRatio = computed(() => {
  const today = overview.genToday;
  const yesterday = overview.genYesterday;
  if (yesterday === 0) {
    // 昨日为0:今日有产出记为新增,否则持平
    return today > 0 ? { up: true, text: '新增' } : { up: true, text: '0%' };
  }
  const rate = ((today - yesterday) / yesterday) * 100;
  return { up: rate >= 0, text: `${Math.abs(rate).toFixed(1)}%` };
});

// 金额千分位格式化(元,保留2位)
const formatMoney = (val: number) => {
  const n = Number(val) || 0;
  return n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
};

// 最近订单列
const orderColumns = [
  { title: '订单号', dataIndex: 'orderNo', ellipsis: true, tooltip: true },
  { title: '商品', dataIndex: 'productName', ellipsis: true, tooltip: true },
  { title: '金额', dataIndex: 'amount', slotName: 'amount', width: 90 },
  { title: '状态', dataIndex: 'statusText', width: 80 },
];

// 最近产出列
const genColumns = [
  { title: '用户', dataIndex: 'userId', width: 80 },
  { title: '类型', dataIndex: 'taskType', ellipsis: true, tooltip: true },
  { title: '状态', dataIndex: 'statusText', width: 70 },
  { title: '扣豆', dataIndex: 'beanCost', slotName: 'beanCost', width: 70 },
];

// 渲染近30天双轴折线(左轴产出量 / 右轴收入)
const renderTrend = () => {
  if (!trendContainer.value || overview.trend.length === 0) return;
  if (trendChart) {
    trendChart.destroy();
    trendChart = null;
  }
  const data = overview.trend.map((t) => ({
    date: t.date,
    genCount: Number(t.genCount) || 0,
    income: Number(t.income) || 0,
  }));
  trendChart = new DualAxes(trendContainer.value, {
    data: [data, data],
    xField: 'date',
    yField: ['genCount', 'income'],
    geometryOptions: [
      {
        geometry: 'line',
        smooth: true,
        color: '#165DFF',
        point: { size: 3, shape: 'circle' },
      },
      {
        geometry: 'line',
        smooth: true,
        color: '#FF7D00',
        point: { size: 3, shape: 'circle' },
      },
    ],
    legend: {
      position: 'top',
      items: [
        { name: '产出量', value: 'genCount', marker: { symbol: 'circle', style: { fill: '#165DFF' } } },
        { name: '收入(元)', value: 'income', marker: { symbol: 'circle', style: { fill: '#FF7D00' } } },
      ],
    },
    tooltip: {
      formatter: (datum: any) => {
        if (datum.income !== undefined && datum.genCount === undefined) {
          return { name: '收入(元)', value: `¥${formatMoney(datum.income)}` };
        }
        return { name: '产出量', value: datum.genCount };
      },
    },
    animation: false,
  });
  trendChart.render();
};

// 渲染豆子经济环形图
const renderBean = () => {
  if (!beanContainer.value || !hasBean.value) return;
  if (beanChart) {
    beanChart.destroy();
    beanChart = null;
  }
  const data = [
    { type: '累计充值', value: overview.bean.recharge },
    { type: '累计消耗', value: overview.bean.consume },
    { type: '累计赠送', value: overview.bean.grant },
  ];
  beanChart = new Pie(beanContainer.value, {
    data,
    angleField: 'value',
    colorField: 'type',
    radius: 0.8,
    innerRadius: 0.6,
    label: {
      type: 'inner',
      offset: '-50%',
      content: '{percentage}',
      style: { fill: '#fff', fontSize: 12, textAlign: 'center' },
    },
    color: ['#165DFF', '#F53F3F', '#00B42A'],
    legend: { position: 'bottom' },
    statistic: {
      title: { content: '豆子', style: { fontSize: '13px', color: '#86909c' } },
      content: {
        content: String(overview.bean.recharge + overview.bean.consume + overview.bean.grant),
        style: { fontSize: '18px', fontWeight: '600', color: '#1d2129' },
      },
    },
    animation: false,
  });
  beanChart.render();
};

// 渲染占比小饼(通用)
const renderDistPie = (
  container: HTMLDivElement | undefined,
  current: Pie | null,
  data: { type: string; value: number }[]
): Pie | null => {
  if (!container || data.length === 0) return null;
  if (current) current.destroy();
  const chart = new Pie(container, {
    data,
    angleField: 'value',
    colorField: 'type',
    radius: 0.85,
    innerRadius: 0.5,
    label: {
      type: 'spider',
      content: '{name} {percentage}',
      style: { fontSize: 11 },
    },
    legend: false,
    color: ['#165DFF', '#00D6B9', '#FF7D00', '#F53F3F', '#722ED1', '#14C9C9'],
    animation: false,
  });
  chart.render();
  return chart;
};

// 渲染全部图表
const renderCharts = () => {
  nextTick(() => {
    renderTrend();
    renderBean();
    modelChart = renderDistPie(
      modelContainer.value,
      modelChart,
      overview.modelDist.map((m) => ({ type: m.name, value: Number(m.count) || 0 }))
    );
    typeChart = renderDistPie(
      typeContainer.value,
      typeChart,
      overview.typeDist.map((t) => ({ type: t.type, value: Number(t.count) || 0 }))
    );
  });
};

const loadData = async () => {
  loading.value = true;
  try {
    const data = await getDashboardOverview();
    Object.assign(overview, data);
    renderCharts();
  } catch (e) {
    // 错误提示已由 request 响应拦截器统一处理,此处不重复弹窗
  } finally {
    loading.value = false;
  }
};

onMounted(() => {
  loadData();
});

onBeforeUnmount(() => {
  trendChart?.destroy();
  beanChart?.destroy();
  modelChart?.destroy();
  typeChart?.destroy();
});
</script>

<style scoped>
.home-container {
  width: 100%;
}

.kpi-row {
  margin-bottom: 16px;
}

.kpi-card {
  height: 100%;
}

.kpi-sub {
  margin-top: 8px;
  font-size: 13px;
  color: var(--color-text-3);
}

.kpi-up {
  color: #00b42a;
  font-weight: 600;
}

.kpi-down {
  color: #f53f3f;
  font-weight: 600;
}

.kpi-strong {
  color: var(--color-text-1);
  font-weight: 600;
}

.mid-row {
  margin-bottom: 16px;
}

.bottom-row {
  margin-bottom: 16px;
}

.chart-card {
  min-height: 360px;
}

.trend-container {
  height: 300px;
  width: 100%;
}

.bean-container {
  height: 300px;
  width: 100%;
}

.pie-title {
  text-align: center;
  font-size: 13px;
  color: var(--color-text-2);
  margin-bottom: 4px;
}

.dist-container {
  height: 280px;
  width: 100%;
}

.empty-block {
  padding: 100px 0;
}

.empty-block-sm {
  padding: 90px 0;
}

.list-card {
  min-height: 360px;
}
</style>
