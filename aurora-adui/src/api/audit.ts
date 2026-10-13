/**
 * 内容审核日志 API 接口定义
 *
 * @author Aurora
 */
import request from '@/utils/request';

/**
 * 审核日志记录(对应后端审核日志实体,字段 camelCase)
 */
export interface AuditRecord {
  id: number;
  userId: number;
  // 对象类型:text(文本)/ media(图片/视频)
  objectType: string;
  // 业务类型(如对话、出图等)
  bizType?: string;
  conversationId?: string;
  taskId?: string;
  // 送审内容摘要
  contentSnippet?: string;
  // 命中建议:pass / review / risky / error
  suggest?: string;
  // 命中标签
  label?: string;
  // 处置结果:PASS / REJECT / ERROR
  result?: string;
  // 处置原因
  reason?: string;
  traceId?: string;
  createTime?: string;
}

/**
 * 审核日志查询参数
 */
export interface AuditRecordQuery {
  pageNum: number;
  pageSize: number;
  objectType?: string;
  suggest?: string;
  result?: string;
  userId?: string;
  // 时间范围(格式 YYYY-MM-DD HH:mm:ss)
  beginTime?: string;
  endTime?: string;
}

/**
 * 查询审核日志列表(只读)
 */
export function listAuditRecord(params: AuditRecordQuery) {
  return request({
    url: '/ai/audit/list',
    method: 'get',
    params
  });
}
