<template>
  <div class="cache-container">
    <!-- 缓存列表卡片 -->
    <a-card class="table-card" title="缓存列表">
      <!-- 标签页 -->
      <a-tabs v-model:active-key="activeTab" @change="handleTabChange">
        <a-tab-pane key="cacheNames" title="缓存列表">
          <template #title>缓存列表</template>
        </a-tab-pane>
        <a-tab-pane key="cacheKeys" title="键名列表">
          <template #title>键名列表</template>
        </a-tab-pane>
        <a-tab-pane key="cacheContent" title="缓存内容">
          <template #title>缓存内容</template>
        </a-tab-pane>
      </a-tabs>

      <!-- Tab1: 缓存列表 -->
      <a-table
        v-if="activeTab === 'cacheNames'"
        :loading="loading"
        :data="cacheNames"
        :pagination="false"
        row-key="cacheName"
        @row-click="handleCacheNameClick"
      >
        <template #columns>
          <a-table-column title="序号" :width="80" align="center">
            <template #cell="{ rowIndex }">
              {{ rowIndex + 1 }}
            </template>
          </a-table-column>
          <a-table-column title="缓存名称" data-index="cacheName" align="center" />
          <a-table-column title="备注" data-index="remark" align="center" />
        </template>
      </a-table>

      <!-- Tab2: 键名列表 -->
      <a-table
        v-if="activeTab === 'cacheKeys'"
        :loading="loading"
        :data="cacheKeys"
        :pagination="false"
        row-key="cacheKey"
        @row-click="handleCacheKeyClick"
      >
        <template #columns>
          <a-table-column title="序号" :width="80" align="center">
            <template #cell="{ rowIndex }">
              {{ rowIndex + 1 }}
            </template>
          </a-table-column>
          <a-table-column title="缓存键名" data-index="cacheKey" align="center" ellipsis tooltip />
          <a-table-column title="备注" data-index="remark" align="center" />
        </template>
      </a-table>

      <!-- Tab3: 缓存内容 -->
      <a-table
        v-if="activeTab === 'cacheContent'"
        :loading="loading"
        :data="cacheContentList"
        :pagination="false"
        row-key="cacheKey"
      >
        <template #columns>
          <a-table-column title="缓存名称" data-index="cacheName" align="center" :width="200" />
          <a-table-column title="缓存键名" data-index="cacheKey" align="center" ellipsis tooltip />
          <a-table-column title="缓存内容" data-index="cacheValue" align="center" ellipsis tooltip />
          <a-table-column title="备注" data-index="remark" align="center" :width="150" />
        </template>
      </a-table>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { Message } from '@arco-design/web-vue';
import { 
  getCacheNames as getCacheNamesApi, 
  getCacheKeys as getCacheKeysApi, 
  getCacheValue as getCacheValueApi,
  CacheName,
  CacheKey,
  CacheContent
} from '@/api/monitor/cache';

const loading = ref(false);
const activeTab = ref('cacheNames');

// 当前选中的缓存名称
const selectedCacheName = ref('');

// 数据列表
const cacheNames = ref<CacheName[]>([]);
const cacheKeys = ref<CacheKey[]>([]);
const cacheContentList = ref<CacheContent[]>([]);

// 标签页切换
function handleTabChange(key: string) {
  activeTab.value = key;
  if (key === 'cacheNames') {
    getCacheNames();
  }
}

// 获取缓存列表
function getCacheNames() {
  loading.value = true;
  getCacheNamesApi()
    .then((res: any) => {
      cacheNames.value = res || [];
    })
    .catch((err: any) => {
      // 如果是已处理的认证错误，不再重复显示
      if (err.message !== '__HANDLED__') {
        Message.error('获取缓存列表失败：' + (err.message || err));
      }
    })
    .finally(() => {
      loading.value = false;
    });
}

// 点击缓存名称，获取键名列表
function handleCacheNameClick(record: CacheName) {
  selectedCacheName.value = record.cacheName;
  activeTab.value = 'cacheKeys';
  getCacheKeys(record.cacheName);
}

// 获取键名列表
function getCacheKeys(cacheName: string) {
  loading.value = true;
  getCacheKeysApi(cacheName)
    .then((res: any) => {
      cacheKeys.value = res || [];
    })
    .catch((err: any) => {
      // 如果是已处理的认证错误，不再重复显示
      if (err.message !== '__HANDLED__') {
        Message.error('获取键名列表失败：' + (err.message || err));
      }
    })
    .finally(() => {
      loading.value = false;
    });
}

// 点击键名，获取缓存内容
function handleCacheKeyClick(record: CacheKey) {
  activeTab.value = 'cacheContent';
  getCacheContent(selectedCacheName.value, record.cacheKey);
}

// 获取缓存内容
function getCacheContent(cacheName: string, cacheKey: string) {
  loading.value = true;
  getCacheValueApi(cacheName, cacheKey)
    .then((res: any) => {
      cacheContentList.value = res ? [res] : [];
    })
    .catch((err: any) => {
      // 如果是已处理的认证错误，不再重复显示
      if (err.message !== '__HANDLED__') {
        Message.error('获取缓存内容失败：' + (err.message || err));
      }
    })
    .finally(() => {
      loading.value = false;
    });
}

onMounted(() => {
  getCacheNames();
});
</script>

<style scoped>
.cache-container {
  padding: 0px;
}

/* 表格行可点击样式 */
:deep(.arco-table-tr) {
  cursor: pointer;
}

:deep(.arco-table-tr:hover) {
  background-color: var(--color-fill-2);
}
</style>
