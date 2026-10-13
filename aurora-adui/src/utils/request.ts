import axios, { AxiosInstance, AxiosResponse, AxiosError, InternalAxiosRequestConfig } from 'axios';
import { Message } from '@arco-design/web-vue';
import { usePermissionStore } from '@/stores/permission';
import { useUserStore } from '@/stores/user';

/** 同步清理当前浏览器会话，确保鉴权失败后敏感按钮立即隐藏。 */
function clearAuthSession() {
  localStorage.removeItem('token');
  usePermissionStore().clearPermissions();
  useUserStore().setUserInfo(null);
}

/**
 * 后端统一响应格式
 */
export interface Result<T = any> {
  code: number;
  message?: string;
  msg?: string;
  data: T;
}

/**
 * 创建 Axios 实例
 */
const service: AxiosInstance = axios.create({
  baseURL: '/api',
  timeout: 60000,
  // 不在这里设置默认的 Content-Type，让浏览器根据数据类型自动设置
});

/**
 * 请求拦截器
 */
service.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    if (!config.headers) {
      config.headers = {} as any;
    }

    // 如果是 FormData(文件上传),不改 Content-Type(让浏览器自动设 multipart),
    // 但仍需注入 token —— 后端上传端点(如 /system/user/uploadAvatar)走 authenticated() 鉴权,
    // 之前这里把 uploadAvatar 排除掉不加 token,导致上传必 401「认证失败」。
    if (config.data instanceof FormData) {
      const token = localStorage.getItem('token');
      if (token) {
        config.headers.Authorization = `Bearer ${token}`;
      }
      return config;
    }

    // 登录和验证码接口不需要 token，优先判断
    // 使用更精确的匹配，避免 /login_tokens 等被误判
    const url = config.url || '';
    const isLoginOrCaptcha = url === '/login' ||
      url.startsWith('/login?') ||
      url.includes('/captchaImage');

    // 如果是登录或验证码接口，明确不添加 token
    if (isLoginOrCaptcha) {
      // 确保不发送 Authorization header
      delete (config.headers as any).Authorization;
      return config;
    }

    // 检查是否需要设置 token
    // 从 headers 中获取 isToken 标志，如果为 false 则不添加 token
    const headers = config.headers || {};
    const isToken = (headers as any).isToken !== false; // 默认为 true，除非明确设置为 false

    // 从 localStorage 读取 token
    const token = localStorage.getItem('token');

    // 只有在需要 token 时才添加
    if (token && isToken) {
      config.headers.Authorization = `Bearer ${token}`;
    } else {
      // 如果不需要 token，确保不发送
      delete (config.headers as any).Authorization;
    }

    return config;
  },
  (error: AxiosError) => {
    console.error('Request error:', error);
    return Promise.reject(error);
  }
);

/**
 * 响应拦截器
 */
service.interceptors.response.use(
  (response: AxiosResponse<Result>) => {
    const res = response.data;

    // 如果 code 不是 200，说明有错误
    if (res.code !== 200) {
      const errorMsg = res.message || res.msg || '系统错误';
      if (res.code === 401) {
        clearAuthSession();
        Message.error(errorMsg);
        if (window.location.pathname !== '/login') {
          setTimeout(() => {
            window.location.href = '/login';
          }, 300);
        }
        return Promise.reject(new Error('__HANDLED__'));
      }
      Message.error(errorMsg);
      return Promise.reject(new Error(errorMsg));
    }

    // 直接返回 data
    return res.data;
  },
  (error: AxiosError<Result>) => {
    let errorMessage = '网络请求失败';

    // 检查是否是登录或验证码接口
    const url = error.config?.url || '';
    const isLoginOrCaptcha = url.includes('/login') || url.includes('/captchaImage');

    if (error.response) {
      // 服务器返回了错误状态码
      const status = error.response.status;
      const data = error.response.data;

      if (status === 401) {
        // 401 可能是 token 过期、无效，或者服务重启导致 token 缓存丢失
        const msg = data?.msg || data?.message || '登录已过期，请重新登录';
        errorMessage = msg;

        // 如果是登录或验证码接口，不清除 token 也不跳转（这些接口本身就是用来登录的）
        if (!isLoginOrCaptcha) {
          clearAuthSession();
          // 显示错误消息后跳转到登录页
          Message.error(errorMessage);
          // 延迟跳转，让用户看到错误信息
          setTimeout(() => {
            window.location.href = '/login';
          }, 1000);
          // 返回一个已处理的 reject，避免上层重复显示错误
          return Promise.reject(new Error('__HANDLED__'));
        } else {
          // 登录接口的 401 错误，只显示错误消息，不跳转
          Message.error(errorMessage);
        }
      } else if (status === 403) {
        // 403 可能是权限不足，也可能是 token 无效（服务重启）
        const msg = data?.msg || data?.message || '没有权限，请联系管理员授权';
        const isAuthFailed = msg.includes('认证失败') ||
          msg.includes('无法访问系统资源') ||
          msg.includes('服务已重启') ||
          msg.includes('登录已过期');

        // 如果是登录或验证码接口，不处理认证失败（这些接口不应该需要认证）
        if (isLoginOrCaptcha) {
          // 登录/验证码接口的 403，可能是后端配置问题，只显示错误
          errorMessage = msg;
          Message.error(errorMessage);
        } else if (isAuthFailed) {
          errorMessage = msg;
          clearAuthSession();
          Message.error(errorMessage);
          // 立即跳转到登录页
          window.location.href = '/login';
        } else {
          errorMessage = msg;
          Message.error(errorMessage);
        }
      } else if (status === 404) {
        errorMessage = '请求的资源不存在';
        Message.error(errorMessage);
      } else if (status >= 500) {
        errorMessage = data?.msg || data?.message || '服务器错误';
        Message.error(errorMessage);
      } else if (data && (data.msg || data.message)) {
        errorMessage = data.msg || data.message || `请求失败 (${status})`;
        Message.error(errorMessage);
      }
    } else if (error.request) {
      // 请求已发出，但没有收到响应
      errorMessage = '网络连接失败，请检查网络';
      Message.error(errorMessage);
    } else {
      // 其他错误
      errorMessage = error.message || '请求失败';
      Message.error(errorMessage);
    }

    return Promise.reject(error);
  }
);

export default service;
