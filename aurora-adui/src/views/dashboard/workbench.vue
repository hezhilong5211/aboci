<template>
  <div class="dashboard-container">
    <!-- 欢迎标题 -->
    <a-typography-title :heading="5" class="welcome-title">
      欢迎回来! 管理员
    </a-typography-title>

    <!-- 指标卡片 -->
    <a-row :gutter="16" class="metrics-row">
      <a-col :span="6">
        <a-card class="metric-card">
          <a-statistic
            :value="37305000"
            :precision="0"
            :value-style="{ color: '#165DFF', fontSize: '24px', fontWeight: 600 }"
          >
            <template #title>
              <a-space>
                <icon-file />
                <span>线上总内容</span>
              </a-space>
            </template>
            <template #suffix>
              <span style="font-size: 14px; color: var(--color-text-2)">个</span>
            </template>
            <template #prefix>
              <a-tag color="green" class="trend-tag">
                <icon-arrow-up />
              </a-tag>
            </template>
          </a-statistic>
        </a-card>
      </a-col>
      <a-col :span="6">
        <a-card class="metric-card">
          <a-statistic
            :value="8874"
            :precision="0"
            :value-style="{ color: '#165DFF', fontSize: '24px', fontWeight: 600 }"
          >
            <template #title>
              <a-space>
                <icon-message />
                <span>今日新增评论</span>
              </a-space>
            </template>
            <template #suffix>
              <span style="font-size: 14px; color: var(--color-text-2)">个</span>
            </template>
            <template #prefix>
              <a-tag color="green" class="trend-tag">
                <icon-arrow-up />
              </a-tag>
            </template>
          </a-statistic>
        </a-card>
      </a-col>
      <a-col :span="6">
        <a-card class="metric-card">
          <a-statistic
            :value="368"
            :precision="0"
            :value-style="{ color: '#165DFF', fontSize: '24px', fontWeight: 600 }"
          >
            <template #title>
              <a-space>
                <icon-file />
                <span>投放中内容</span>
              </a-space>
            </template>
            <template #suffix>
              <span style="font-size: 14px; color: var(--color-text-2)">个</span>
            </template>
            <template #prefix>
              <a-tag color="green" class="trend-tag">
                <icon-arrow-up />
              </a-tag>
            </template>
          </a-statistic>
        </a-card>
      </a-col>
      <a-col :span="6">
        <a-card class="metric-card">
          <a-statistic
            :value="20.8"
            :precision="1"
            :value-style="{ color: '#165DFF', fontSize: '24px', fontWeight: 600 }"
          >
            <template #title>
              <a-space>
                <icon-bar-chart />
                <span>较昨天新增</span>
              </a-space>
            </template>
            <template #suffix>
              <span style="font-size: 14px; color: var(--color-text-2)">%</span>
            </template>
            <template #prefix>
              <a-tag color="green" class="trend-tag">
                <icon-arrow-up />
              </a-tag>
            </template>
          </a-statistic>
        </a-card>
      </a-col>
    </a-row>

    <!-- 图表和快捷操作 -->
    <a-row :gutter="16" class="content-row">
      <a-col :span="16">
        <a-card>
          <template #title>
            <a-space>
              <icon-bar-chart />
              <span>内容数据</span>
            </a-space>
          </template>
          <template #extra>
            <a-link>查看更多</a-link>
          </template>
          <div class="chart-container">
            <a-empty description="图表数据加载中..." />
          </div>
        </a-card>
      </a-col>
      <a-col :span="8">
        <a-card>
          <template #title>
            <a-space>
              <icon-thunderbolt />
              <span>快捷操作</span>
            </a-space>
          </template>
          <template #extra>
            <a-link>管理</a-link>
          </template>
          <a-grid :cols="2" :col-gap="12" :row-gap="12">
            <a-grid-item>
              <a-card :bordered="false" hoverable class="quick-action-card">
                <a-space direction="vertical" align="center" :size="8">
                  <icon-file :size="24" />
                  <span>内容管理</span>
                </a-space>
              </a-card>
            </a-grid-item>
            <a-grid-item>
              <a-card :bordered="false" hoverable class="quick-action-card">
                <a-space direction="vertical" align="center" :size="8">
                  <icon-bar-chart :size="24" />
                  <span>内容分析</span>
                </a-space>
              </a-card>
            </a-grid-item>
            <a-grid-item>
              <a-card :bordered="false" hoverable class="quick-action-card">
                <a-space direction="vertical" align="center" :size="8">
                  <icon-settings :size="24" />
                  <span>高级管理</span>
                </a-space>
              </a-card>
            </a-grid-item>
            <a-grid-item>
              <a-card :bordered="false" hoverable class="quick-action-card">
                <a-space direction="vertical" align="center" :size="8">
                  <icon-upload :size="24" />
                  <span>线上推广</span>
                </a-space>
              </a-card>
            </a-grid-item>
            <a-grid-item :span="2">
              <a-card :bordered="false" hoverable class="quick-action-card">
                <a-space direction="vertical" align="center" :size="8">
                  <icon-fire :size="24" />
                  <span>内容投放</span>
                </a-space>
              </a-card>
            </a-grid-item>
          </a-grid>
        </a-card>
      </a-col>
    </a-row>

    <!-- 最近访问和公告 -->
    <a-row :gutter="16" class="content-row">
      <a-col :span="8">
        <a-card>
          <template #title>
            <a-space>
              <icon-clock-circle />
              <span>最近访问</span>
            </a-space>
          </template>
          <a-list :bordered="false">
            <a-list-item>
              <a-space>
                <icon-file />
                <span>内容管理</span>
              </a-space>
            </a-list-item>
            <a-list-item>
              <a-space>
                <icon-bar-chart />
                <span>内容分析</span>
              </a-space>
            </a-list-item>
            <a-list-item>
              <a-space>
                <icon-settings />
                <span>高级管理</span>
              </a-space>
            </a-list-item>
          </a-list>
        </a-card>
      </a-col>
      <a-col :span="16">
        <a-card>
          <template #title>
            <a-space>
              <icon-notification />
              <span>公告</span>
            </a-space>
          </template>
          <template #extra>
            <a-link>查看更多</a-link>
          </template>
          <a-list :bordered="false">
            <a-list-item>
              <a-space>
                <a-tag color="red">活动</a-tag>
                <span>内容最新优惠活动</span>
              </a-space>
            </a-list-item>
          </a-list>
        </a-card>
      </a-col>
    </a-row>
  </div>
</template>

<script setup lang="ts">
import {
  IconFile,
  IconMessage,
  IconBarChart,
  IconArrowUp,
  IconThunderbolt,
  IconSettings,
  IconUpload,
  IconFire,
  IconClockCircle,
  IconNotification,
} from '@arco-design/web-vue/es/icon';
</script>

<style scoped>
.dashboard-container {
  width: 100%;
}

.welcome-title {
  margin-bottom: 24px;
}

.metrics-row {
  margin-bottom: 24px;
}

.metric-card {
  height: 100%;
}

.trend-tag {
  margin-right: 8px;
}

.content-row {
  margin-bottom: 24px;
}

.chart-container {
  height: 300px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.quick-action-card {
  text-align: center;
  cursor: pointer;
  transition: all 0.3s;
}

.quick-action-card:hover {
  transform: translateY(-2px);
}
</style>
