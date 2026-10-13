import request from '@/utils/request'

// 用户信息响应类型
export interface UserInfoResponse {
  user: {
    userId: number;
    userName: string;
    nickName: string;
    email: string;
    phonenumber: string;
    avatar: string;
  };
  roles: any[];
  permissions: any[];
}

// 登录方法
export function login(username: string, password: string, code: string, uuid: string) {
  const data = {
    username,
    password,
    code,
    uuid
  }
  return request({
    url: '/login',
    headers: {
      isToken: false
    },
    method: 'post',
    data: data
  })
}

// 获取验证码
export function getCodeImg() {
  return request({
    url: '/captchaImage',
    headers: {
      isToken: false
    },
    method: 'get',
    timeout: 20000
  })
}

// 获取用户信息
export function getInfo() {
  return request<UserInfoResponse>({
    url: '/getInfo',
    method: 'get'
  })
}

// 获取路由信息
export function getRouters() {
  return request({
    url: '/getRouters',
    method: 'get'
  })
}

