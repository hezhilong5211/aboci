<template>
  <div class="exception-page">
    <h1>500</h1>
    <p>抱歉，服务器出错了</p>
    <a-button type="primary" @click="goHome">返回首页</a-button>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router';

const router = useRouter();

function goHome() {
  router.push('/');
}
</script>

<style scoped>
.exception-page {
  text-align: center;
  padding: 80px 20px;
}
.exception-page h1 {
  font-size: 72px;
  font-weight: 600;
  color: rgb(var(--primary-6));
  margin: 0;
}
.exception-page p {
  color: var(--color-text-2);
  margin: 12px 0 24px;
}
</style>
