import request from '@/utils/request';

export interface GenerationConfig {
  generationEnabled: boolean;
  downloadWatermarkEnabled: boolean;
}

export function getGenerationConfig() {
  return request({
    url: '/system/generation-config',
    method: 'get'
  });
}

export function updateGenerationConfig(data: GenerationConfig) {
  return request({
    url: '/system/generation-config',
    method: 'put',
    data
  });
}
