<template>
  <router-view />
</template>

<script setup lang="ts">
// App 根组件
</script>

<style>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

#app {
  width: 100%;
  height: 100vh;
  overflow: hidden;
}

/* 暗黑模式背景颜色 */
body[arco-theme='dark'] {
  background-color: #000000;
}

body[arco-theme='dark'] #app {
  background-color: #000000;
}

/* 表格操作列的 text 按钮紧凑化 */
.arco-table .arco-btn-text.arco-btn-size-small {
  min-width: auto;      /* 取消最小宽度限制 */
  height: auto;         /* 高度自适应内容 */
  line-height: 1.5;     /* 适当行高 */
  padding: 2px 8px;     /* 保持适当内边距 */
}
</style>

