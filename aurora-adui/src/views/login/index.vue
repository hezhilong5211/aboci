<template>
  <div class="login-container">
    <a-row class="login-row" :gutter="0">
      <a-col :span="24" class="form-col">
        <div class="form-wrapper">
          <!-- 登录标题 -->
          <div class="form-header">
            <h1 class="form-title">登录 aboci管理后台</h1>
            <p class="form-subtitle">欢迎回来，请登录您的账户</p>
          </div>

          <!-- 登录表单 -->
          <a-form
            :model="form"
            :rules="rules"
            @submit="handleSubmit"
            layout="vertical"
            class="login-form"
            auto-label-width
          >
            <a-form-item field="username" hide-label>
              <a-input
                v-model="form.username"
                placeholder="管理员账号"
                size="large"
                class="login-input"
                @input="handleUsernameInput"
              >
                <template #prefix>
                  <icon-user />
                </template>
              </a-input>
            </a-form-item>

            <a-form-item field="password" hide-label>
              <a-input-password
                v-model="form.password"
                placeholder="请输入密码"
                size="large"
                class="login-input"
              >
                <template #prefix>
                  <icon-lock />
                </template>
              </a-input-password>
            </a-form-item>

            <a-form-item field="code" hide-label>
              <div class="captcha-box">
                <a-input
                  v-model="form.code"
                  placeholder="验证码"
                  size="large"
                  class="login-input captcha-input"
                />
                <div class="login-code">
                  <img :src="codeUrl" @click="getCode" class="login-code-img" alt="验证码" />
                </div>
              </div>
            </a-form-item>

            <a-form-item hide-label>
              <div class="form-options">
                <a-checkbox v-model="rememberPassword">记住密码</a-checkbox>
                <a-link>忘记密码</a-link>
              </div>
            </a-form-item>

            <a-form-item hide-label>
              <a-button
                type="primary"
                html-type="submit"
                size="large"
                long
                :loading="loading"
                class="login-button"
              >
                登录
              </a-button>
            </a-form-item>

            <!-- 注册链接 -->
            <div class="form-footer">
              <a-link>注册账号</a-link>
            </div>
          </a-form>
        </div>
      </a-col>
    </a-row>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import { Message } from '@arco-design/web-vue';
import {
  IconUser,
  IconLock,
} from '@arco-design/web-vue/es/icon';
import { login, getCodeImg, getInfo } from '@/api/login';
import { useUserStore } from '@/stores/user';
import { usePermissionStore } from '@/stores/permission';

const router = useRouter();
const route = useRoute();
const userStore = useUserStore();
const permissionStore = usePermissionStore();

// 表单数据
const form = reactive({
  username: '',
  password: '',
  code: '',
  uuid: ''
});

// 表单验证规则
const rules = {
  username: [
    { required: true, message: '请输入用户名' },
  ],
  password: [
    { required: true, message: '请输入密码' },
  ],
  code: [
    { required: true, message: '请输入验证码' },
  ],
};

const loading = ref(false);
const rememberPassword = ref(false);
const codeUrl = ref('');

// 保存密码的 key
const REMEMBER_USERNAME_KEY = 'remember_username';
const REMEMBER_PASSWORD_KEY = 'remember_password';
const REMEMBER_CHECKED_KEY = 'remember_checked';

// 简单的 base64 编码/解码（用于存储密码，虽然不是真正的加密，但比明文好）
const encodePassword = (password: string): string => {
  return btoa(encodeURIComponent(password));
};

const decodePassword = (encoded: string): string => {
  try {
    return decodeURIComponent(atob(encoded));
  } catch (e) {
    return '';
  }
};

// 保存用户名和密码
const saveCredentials = () => {
  if (rememberPassword.value) {
    localStorage.setItem(REMEMBER_USERNAME_KEY, form.username);
    localStorage.setItem(REMEMBER_PASSWORD_KEY, encodePassword(form.password));
    localStorage.setItem(REMEMBER_CHECKED_KEY, 'true');
  } else {
    // 清除保存的密码
    localStorage.removeItem(REMEMBER_USERNAME_KEY);
    localStorage.removeItem(REMEMBER_PASSWORD_KEY);
    localStorage.removeItem(REMEMBER_CHECKED_KEY);
  }
};

// 加载保存的用户名和密码
const loadCredentials = () => {
  const savedUsername = localStorage.getItem(REMEMBER_USERNAME_KEY);
  const savedPassword = localStorage.getItem(REMEMBER_PASSWORD_KEY);
  const savedChecked = localStorage.getItem(REMEMBER_CHECKED_KEY);
  
  if (savedUsername) {
    form.username = savedUsername;
  }
  
  if (savedPassword) {
    form.password = decodePassword(savedPassword);
  }
  
  if (savedChecked === 'true') {
    rememberPassword.value = true;
  }
};

// 获取验证码（返回 Promise，便于提交前同步等待 uuid 就绪）
const getCode = (): Promise<void> => {
  return getCodeImg()
    .then((res: any) => {
      codeUrl.value = 'data:image/gif;base64,' + res.img;
      form.uuid = res.uuid;
    })
    .catch(() => {
      Message.error('获取验证码失败');
    });
};

// 处理用户名输入（移除空格）
const handleUsernameInput = () => {
  form.username = form.username.replace(/\s+/g, '');
};

// 处理密码输入（移除空格）
const handlePasswordInput = () => {
  form.password = form.password.replace(/\s+/g, '');
};

// 处理验证码输入（移除空格）
const handleCodeInput = () => {
  form.code = form.code.replace(/\s+/g, '');
};

// 登录提交
const handleSubmit = async ({ errors }: any) => {
  if (errors) {
    return;
  }

  // 在提交前再次确保所有输入都没有空格
  form.username = form.username.trim().replace(/\s+/g, '');
  form.password = form.password.trim().replace(/\s+/g, '');
  form.code = form.code.trim().replace(/\s+/g, '');

  if (!form.username || !form.password || !form.code) {
    Message.warning('请输入完整信息');
    return;
  }

  loading.value = true;
  let tokenIssued = false;

  try {
    // 兜底：uuid 为空（如首次挂载 getCode 尚未返回）时，先同步取一次，避免后端报"验证码uuid不能为空"或误判已失效
    if (!form.uuid) {
      await getCode();
      if (!form.uuid) {
        Message.error('验证码加载失败，请刷新后重试');
        return;
      }
    }
    const res: any = await login(form.username, form.password, form.code, form.uuid);
    localStorage.setItem('token', res.token);
    tokenIssued = true;
    // 新账号登录后先清空上一账号的按钮权限；getInfo 失败时也必须默认隐藏，而不是沿用旧权限。
    permissionStore.clearPermissions();
    userStore.setUserInfo(null);
    // 保存或清除密码
    saveCredentials();

    // getInfo 必须成功后才算登录完成，避免接口失败时带着半初始化会话进入后台。
    const userInfo = await getInfo();
    if (!userInfo?.user) {
      Message.error('登录状态初始化失败，请重新登录');
      throw new Error('登录状态初始化失败');
    }
    userStore.setUserInfo(userInfo.user);
    permissionStore.setPermissions(userInfo?.permissions || []);

    Message.success('登录成功');

    // 跳转到之前访问的页面，如果没有则跳转到首页
    const redirect = (route.query.redirect as string) || '/home';
    router.push(redirect);
  } catch (error: any) {
    if (tokenIssued) {
      localStorage.removeItem('token');
      permissionStore.clearPermissions();
      userStore.setUserInfo(null);
    }
    // 登录失败统一刷新验证码，并清空输入，避免用户二次提交旧 uuid 命中"已失效"
    await getCode();
    form.code = '';
    // 错误消息已经在 request 拦截器中显示了
  } finally {
    loading.value = false;
  }
};

onMounted(() => {
  getCode();
  // 加载保存的用户名和密码
  loadCredentials();
});
</script>

<style scoped lang="less">
.login-container {
  width: 100%;
  height: 100vh;
  overflow: hidden;
}

.login-row {
  height: 100%;
}

// 登录表单区域
.form-col {
  height: 100%;
  background: #ffffff;
  display: flex;
  align-items: center;
  justify-content: center;
}

.form-wrapper {
  width: 100%;
  max-width: 400px;
  padding: 40px;
  box-sizing: border-box;
  overflow-y: auto;
}

.form-header {
  margin-bottom: 48px;

  .form-title {
    font-size: 28px;
    font-weight: 600;
    color: #1d2129;
    margin: 0 0 8px 0;
    line-height: 1.4;
  }

  .form-subtitle {
    font-size: 16px;
    color: #86909c;
    margin: 0;
    line-height: 1.5;
  }
}

.login-form {
  :deep(.arco-form-item) {
    margin-bottom: 20px;

    &:last-of-type {
      margin-bottom: 0;
    }
  }

  .login-input {
    height: 48px;
    font-size: 16px;
    background-color: #f7f8fa;
    border: 1px solid #e5e6eb;
    border-radius: 6px;

    &:hover {
      background-color: #f2f3f5;
      border-color: #c9cdd4;
    }

    &:focus {
      background-color: #ffffff;
      border-color: #165dff;
    }

    :deep(.arco-input-prefix) {
      color: #86909c;
      margin-right: 12px;
    }
  }

  .form-options {
    display: flex;
    justify-content: space-between;
    align-items: center;
    width: 100%;

    :deep(.arco-checkbox-label) {
      font-size: 14px;
      color: #1d2129;
    }

    :deep(.arco-link) {
      font-size: 14px;
      color: #165dff;
    }
  }

  .login-button {
    height: 48px;
    font-size: 16px;
    font-weight: 500;
    border-radius: 6px;
    margin-top: 8px;
  }

  .captcha-box {
    display: flex;
    justify-content: space-between;
    width: 100%;
    gap: 12px;
  }

  .captcha-input {
    flex: 1;
  }

  .login-code {
    width: 120px;
    height: 48px;
    flex-shrink: 0;
  }

  .login-code-img {
    width: 100%;
    height: 100%;
    cursor: pointer;
    border-radius: 6px;
    border: 1px solid var(--color-border);
    object-fit: cover;
  }

  .form-footer {
    text-align: center;
    margin-top: 24px;

    :deep(.arco-link) {
      font-size: 14px;
      color: #165dff;
    }
  }
}

// 响应式设计 - 适配不同设备
// 超大屏幕 (1920px+)
@media (min-width: 1920px) {
  .form-wrapper {
    max-width: 480px;
    padding: 60px;
  }

  .form-title {
    font-size: 32px;
  }
}

// 中等屏幕 (1200px - 1439px)
@media (min-width: 1200px) and (max-width: 1439px) {
  .form-wrapper {
    max-width: 380px;
    padding: 40px;
  }
}

// 平板横屏 (992px - 1199px)
@media (min-width: 992px) and (max-width: 1199px) {
  .form-wrapper {
    max-width: 400px;
    padding: 30px;
  }

  .form-title {
    font-size: 24px;
  }
}

// 平板竖屏 (768px - 991px)
@media (min-width: 768px) and (max-width: 991px) {
  .form-wrapper {
    max-width: 500px;
    padding: 30px;
  }
}

// 手机横屏 (576px - 767px)
@media (min-width: 576px) and (max-width: 767px) {
  .form-wrapper {
    max-width: 100%;
    padding: 30px 20px;
  }

  .form-title {
    font-size: 22px;
  }
}

// 手机竖屏 (< 576px)
@media (max-width: 575px) {
  .form-wrapper {
    max-width: 100%;
    padding: 20px;
  }

  .form-header {
    margin-bottom: 32px;
  }

  .form-title {
    font-size: 20px;
  }

  .form-subtitle {
    font-size: 14px;
  }

  .login-input {
    height: 44px;
    font-size: 14px;
  }

  .login-button {
    height: 44px;
    font-size: 14px;
  }
}

// 超小屏幕 (< 375px) - iPhone SE 等
@media (max-width: 374px) {
  .form-wrapper {
    padding: 15px;
  }

  .form-title {
    font-size: 18px;
  }

  .form-subtitle {
    font-size: 12px;
  }
}
</style>
