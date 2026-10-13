<template>
  <div class="app-container">
    <a-spin :loading="loading" tip="正在采集服务器状态信息..." :style="{ width: '100%' }">
      <a-row :gutter="16">
        <!-- CPU 状态 -->
        <a-col :span="12" style="margin-bottom: 16px">
          <a-card title="CPU 状态" :bordered="false" class="monitor-card">
            <template #extra>
              <a-tag color="blue">{{ serverInfo?.cpu?.cpuNum }} 核心</a-tag>
            </template>
            <div class="stats-content">
              <a-row>
                <a-col :span="12" class="flex-center">
                  <a-progress
                    type="circle"
                    :percent="serverInfo?.cpu?.used / 100"
                    :color="getProgressColor(serverInfo?.cpu?.used)"
                    :size="120"
                  >
                    <template #text>
                      <div class="progress-text">
                        <div class="val">{{ serverInfo?.cpu?.used }}%</div>
                        <div class="label">使用率</div>
                      </div>
                    </template>
                  </a-progress>
                </a-col>
                <a-col :span="12">
                  <a-statistic title="用户使用率" :value="serverInfo?.cpu?.used" :precision="2" unit="%" />
                  <a-divider style="margin: 8px 0" />
                  <a-statistic title="系统使用率" :value="serverInfo?.cpu?.sys" :precision="2" unit="%" />
                  <a-divider style="margin: 8px 0" />
                  <a-statistic title="空闲率" :value="serverInfo?.cpu?.free" :precision="2" unit="%" />
                </a-col>
              </a-row>
            </div>
          </a-card>
        </a-col>

        <!-- 内存状态 -->
        <a-col :span="12" style="margin-bottom: 16px">
          <a-card title="内存状态" :bordered="false" class="monitor-card">
            <template #extra>
              <a-tag color="green">{{ serverInfo?.mem?.total }} GB 总计</a-tag>
            </template>
            <div class="stats-content">
              <a-row>
                <a-col :span="12" class="flex-center">
                  <a-progress
                    type="circle"
                    :percent="serverInfo?.mem?.usage / 100"
                    :color="getProgressColor(serverInfo?.mem?.usage)"
                    :size="120"
                  >
                    <template #text>
                      <div class="progress-text">
                        <div class="val">{{ serverInfo?.mem?.usage }}%</div>
                        <div class="label">已使用</div>
                      </div>
                    </template>
                  </a-progress>
                </a-col>
                <a-col :span="12">
                  <a-statistic title="已用内存" :value="serverInfo?.mem?.used" :precision="2" unit="GB" />
                  <a-divider style="margin: 8px 0" />
                  <a-statistic title="剩余内存" :value="serverInfo?.mem?.free" :precision="2" unit="GB" />
                  <a-divider style="margin: 8px 0" />
                  <a-statistic title="JVM已用" :value="serverInfo?.jvm?.used" :precision="2" unit="GB" />
                </a-col>
              </a-row>
            </div>
          </a-card>
        </a-col>

        <!-- JVM 信息 -->
        <a-col :span="24" style="margin-bottom: 16px">
          <a-card title="Java 虚拟机信息" :bordered="false">
            <a-descriptions :data="jvmData" :column="2" bordered />
          </a-card>
        </a-col>

        <!-- 服务器信息 -->
        <a-col :span="24" style="margin-bottom: 16px">
          <a-card title="服务器信息" :bordered="false">
            <a-descriptions :data="sysData" :column="2" bordered />
          </a-card>
        </a-col>

        <!-- 磁盘状态 -->
        <a-col :span="24">
          <a-card title="磁盘状态" :bordered="false">
            <a-table :data="serverInfo?.sysFiles" :pagination="false">
              <template #columns>
                <a-table-column title="盘符路径" data-index="dirName" />
                <a-table-column title="文件系统" data-index="sysTypeName" />
                <a-table-column title="磁盘类型" data-index="typeName" />
                <a-table-column title="总大小" data-index="total" />
                <a-table-column title="空闲" data-index="free" />
                <a-table-column title="已用" data-index="used" />
                <a-table-column title="已用百分比">
                  <template #cell="{ record }">
                    <a-progress
                      :percent="record.usage / 100"
                      :color="getProgressColor(record.usage)"
                      style="width: 150px"
                    />
                    <span style="margin-left: 8px">{{ record.usage.toFixed(2) }}%</span>
                  </template>
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
import { ref, onMounted, computed } from 'vue';
import { getServer } from '@/api/monitor/server';

const loading = ref(true);
const serverInfo = ref<any>(null);

const getInfo = () => {
  loading.value = true;
  getServer().then(res => {
    serverInfo.value = res;
    loading.value = false;
  }).catch(() => {
    loading.value = false;
  });
};

const getProgressColor = (percent: number) => {
  if (percent > 90) return '#F53F3F';
  if (percent > 70) return '#FF7D00';
  return '#165DFF';
};

const jvmData = computed(() => {
  if (!serverInfo.value?.jvm) return [];
  const jvm = serverInfo.value.jvm;
  return [
    { label: 'JVM 名称', value: jvm.name },
    { label: 'Java 版本', value: jvm.version },
    { label: '启动时间', value: jvm.startTime },
    { label: '运行时长', value: jvm.runTime },
    { label: '安装路径', value: jvm.home },
    { label: '项目路径', value: serverInfo.value?.sys?.userDir }
  ];
});

const sysData = computed(() => {
  if (!serverInfo.value?.sys) return [];
  const sys = serverInfo.value.sys;
  return [
    { label: '服务器名称', value: sys.computerName },
    { label: '服务器 IP', value: sys.computerIp },
    { label: '操作系统', value: sys.osName },
    { label: '系统架构', value: sys.osArch }
  ];
});

onMounted(() => {
  getInfo();
});
</script>

<style scoped>
.app-container {
  padding: 0px;
}
.flex-center {
  display: flex;
  justify-content: center;
  align-items: center;
  flex-direction: column;
}
.progress-text {
  text-align: center;
}
.progress-text .val {
  font-size: 24px;
  font-weight: bold;
  color: var(--color-text-1);
}
.progress-text .label {
  font-size: 12px;
  color: var(--color-text-3);
}
.mem-sub-label {
  font-size: 12px;
  color: var(--color-text-3);
  margin-top: 4px;
}
:deep(.arco-card).monitor-card {
  min-height: 280px;
}
:deep(.arco-card) {
  border-radius: 8px;
  box-shadow: 0 4px 10px rgba(0, 0, 0, 0.05);
}
</style>
