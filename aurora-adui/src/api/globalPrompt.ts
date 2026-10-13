/**
 * 全局提示词管理 API 接口定义(当前生效 / 历史版本 / 保存新版并生效 / 启用历史版本)
 * url 镜像后端路径,不带 /api 前缀(request 的 baseURL 已为 /api)
 *
 * @author Aurora
 */
import request from '@/utils/request';

/**
 * 全局提示词对象
 * content 是送给大模型的原始文本;同一时刻仅一条 isActive='1' 生效,其余为历史
 */
export interface GlobalPrompt {
  id?: number;
  // 提示词内容
  content?: string;
  // 是否生效:1 生效 / 0 历史
  isActive?: string;
  createTime?: string;
  updateTime?: string;
}

/**
 * 历史版本查询参数(分页,create_time 倒序)
 */
export interface GlobalPromptQuery {
  pageNum: number;
  pageSize: number;
}

/**
 * 查询当前生效的全局提示词
 */
export function getActiveGlobalPrompt() {
  return request({
    url: '/system/global-prompt/active',
    method: 'get'
  });
}

/**
 * 分页查询历史版本(create_time 倒序)
 */
export function listGlobalPrompt(query: GlobalPromptQuery) {
  return request({
    url: '/system/global-prompt/list',
    method: 'get',
    params: query
  });
}

/**
 * 保存为新版本并即时生效(新插一条 isActive='1',其余置 0)
 */
export function saveGlobalPrompt(data: { content: string }) {
  return request({
    url: '/system/global-prompt',
    method: 'post',
    data
  });
}

/**
 * 启用某历史版本(该条置 1、其余置 0)
 */
export function activateGlobalPrompt(id: number) {
  return request({
    url: '/system/global-prompt/active/' + id,
    method: 'put'
  });
}
