<template>
  <div class="developing-container">
    <a-empty
      :description="subtitle || '该功能正在开发中'"
    >
      <template #image>
        <icon-tool :size="80" />
      </template>
      <template #footer>
        <a-space>
          <a-button type="primary" @click="goHome">返回首页</a-button>
          <a-button @click="goBack">返回上一页</a-button>
        </a-space>
      </template>
    </a-empty>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router';
import { IconTool } from '@arco-design/web-vue/es/icon';

const router = useRouter();

// 可以通过 props 自定义提示文字
defineProps<{
  subtitle?: string;
}>();

const goHome = () => {
  router.push('/home');
};

const goBack = () => {
  router.back();
};
</script>

<style scoped>
.developing-container {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 60vh;
  width: 100%;
}
</style>
