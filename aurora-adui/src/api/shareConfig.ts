import request from '@/utils/request';

export interface ShareConfig {
  title?: string;
  image?: string;
}

// 查询系统设置页的小程序分享配置。
export function getShareConfig() {
  return request({
    url: '/system/share-config',
    method: 'get'
  });
}

// 仅更新 mobile.share.title / mobile.share.image。
export function updateShareConfig(data: ShareConfig) {
  return request({
    url: '/system/share-config',
    method: 'put',
    data
  });
}
