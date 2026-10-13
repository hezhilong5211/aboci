import { createApp } from 'vue';
import { createPinia } from 'pinia';
import ArcoVue from '@arco-design/web-vue';
import '@arco-design/web-vue/dist/arco.css';
import router from './router';
import App from './App.vue';
import { setupDirectives } from './directives/permission';

// 初始化主题（在应用启动时立即应用，避免闪烁）
const initTheme = () => {
  const THEME_KEY = 'arco-theme';
  const savedTheme = localStorage.getItem(THEME_KEY);
  const theme = savedTheme || 'light';
  document.body.setAttribute('arco-theme', theme);
};

// 在应用创建前初始化主题
initTheme();

// 创建应用实例
const app = createApp(App);

// 使用 Pinia
app.use(createPinia());

// 使用 Vue Router
app.use(router);

// 使用 Arco Design
app.use(ArcoVue);

// 注册自定义指令(v-hasPermi 按钮级权限)
setupDirectives(app);

// 挂载应用
app.mount('#app');

