/**
 * AI 相关 API 接口定义
 * 
 * @author Aurora
 */

/**
 * 消息角色类型
 */
export type MessageRole = 'user' | 'assistant' | 'system';

/**
 * 消息接口
 */
export interface Message {
  id: string;
  role: MessageRole;
  content: string;
  timestamp: number;
}

/**
 * 聊天请求参数
 */
export interface ChatRequest {
  message: string;
  conversationId?: string;
  stream?: boolean;
}

/**
 * 聊天响应（非流式）
 */
export interface ChatResponse {
  code: number;
  message: string;
  data: {
    content: string;
    conversationId?: string;
  };
}

/**
 * 发送聊天消息（非流式）
 */
export async function sendChatMessage(
  message: string,
  conversationId?: string
): Promise<ChatResponse> {
  const token = localStorage.getItem('token');
  const response = await fetch('/api/ai/chat', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(token && { Authorization: `Bearer ${token}` }),
    },
    body: JSON.stringify({
      message,
      conversationId,
      stream: false,
    }),
  });

  if (!response.ok) {
    throw new Error(`HTTP error! status: ${response.status}`);
  }

  return response.json();
}

/**
 * 创建 SSE 流式聊天连接
 * 
 * @param message 用户消息
 * @param conversationId 会话 ID（可选）
 * @param onMessage 接收消息的回调函数
 * @param onError 错误处理回调
 * @returns AbortController 用于取消请求
 */
export function createChatStream(
  message: string,
  conversationId: string | undefined,
  onMessage: (chunk: string) => void,
  onError?: (error: Error) => void
): AbortController {
  const controller = new AbortController();
  const token = localStorage.getItem('token');

  fetch('/api/ai/chat/stream', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
      ...(token && { Authorization: `Bearer ${token}` }),
    },
    body: JSON.stringify({
      message,
      conversationId,
      stream: true,
    }),
    signal: controller.signal,
  })
    .then(async (response) => {
      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
      }

      const reader = response.body?.getReader();
      const decoder = new TextDecoder();

      if (!reader) {
        throw new Error('Response body is not readable');
      }

      let buffer = '';

      while (true) {
        const { done, value } = await reader.read();

        if (done) {
          break;
        }

        // 解码数据块
        buffer += decoder.decode(value, { stream: true });

        // 处理 SSE 格式的数据
        const lines = buffer.split('\n');
        buffer = lines.pop() || ''; // 保留最后一个不完整的行

        for (const line of lines) {
          if (line.startsWith('data: ')) {
            const data = line.slice(6); // 移除 'data: ' 前缀

            // 检查是否是结束标记
            if (data === '[DONE]') {
              return;
            }

            try {
              // 尝试解析 JSON（如果后端返回 JSON 格式）
              const parsed = JSON.parse(data);
              if (parsed.content) {
                onMessage(parsed.content);
              } else if (typeof parsed === 'string') {
                onMessage(parsed);
              }
            } catch {
              // 如果不是 JSON，直接作为文本处理
              if (data.trim()) {
                onMessage(data);
              }
            }
          }
        }
      }
    })
    .catch((error) => {
      if (error.name !== 'AbortError') {
        onError?.(error);
      }
    });

  return controller;
}

// ==========================================
// AI Model Management API
// ==========================================
import request from '@/utils/request';

export interface SysAiModel {
  id?: number;
  modelName: string;
  apiKey: string;
  // SecretKey,仅可灵等双密钥模型
  secretKey?: string;
  baseUrl: string;
  modelDeployment: string;
  temperature: number;
  maxTokens: number;
  provider: string;
  modelType: string;
  // 对话接口协议：历史模型走 chat_completions，GPT-5.6 等可选 responses
  apiProtocol?: 'chat_completions' | 'responses';
  isActive: number;
  // 是否默认(同 model_type 唯一,1默认);仅图像模型,出图选型按默认走
  isDefault?: number;
  sortOrder: number;
  // 允许的画质集合,逗号分隔(如 1K,2K),仅图像模型;未配=不限制
  supportedResolutions?: string;
  // 允许的画幅比例集合,逗号分隔(如 1:1,4:3),仅图像模型;未配=不限制
  supportedAspectRatios?: string;
  // 默认分辨率(如 1K),仅图像模型;移动端不传分辨率时后端用此值
  defaultResolution?: string;
  // 计费单价(豆子),与后端 BigDecimal 对应,可空;按 modelType 显隐录入
  // 文本-每百万token输入/缓存未命中
  beanPerMtokenInput?: number | string;
  // 文本-每百万token输入·缓存命中(仅 deepseek 填)
  beanPerMtokenInputCached?: number | string;
  // 文本-每百万token输出
  beanPerMtokenOutput?: number | string;
  // 图片-每张
  beanPerImage?: number | string;
  // 图片-1K/2K/4K 每张，未配置时后端兼容沿用 beanPerImage
  beanPerImage1k?: number | string;
  beanPerImage2k?: number | string;
  beanPerImage4k?: number | string;
  // 视频-每秒
  beanPerSecond?: number | string;
  createTime?: string;
  updateTime?: string;
}

export function listSysAiModel(query: any) {
  return request({
    url: '/ai/model/list',
    method: 'get',
    params: query
  })
}

export function getSysAiModel(id: number) {
  return request({
    url: '/ai/model/' + id,
    method: 'get'
  })
}

export function addSysAiModel(data: SysAiModel) {
  return request({
    url: '/ai/model',
    method: 'post',
    data: data
  })
}

export function updateSysAiModel(data: SysAiModel) {
  return request({
    url: '/ai/model',
    method: 'put',
    data: data
  })
}

export function delSysAiModel(id: number | number[]) {
  return request({
    url: '/ai/model/' + id,
    method: 'delete'
  })
}

export function testSysAiModel(data: SysAiModel) {
  return request({
    url: '/ai/model/test',
    method: 'post',
    data: data
  })
}

// 设为默认模型(同 model_type 唯一);出图选型按默认走
export function setDefaultSysAiModel(id: number) {
  return request({
    url: '/ai/model/setDefault/' + id,
    method: 'put'
  })
}
