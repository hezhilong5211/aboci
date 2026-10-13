import request from '@/utils/request'

// ==================== 操作日志 API ====================

/**
 * 操作日志查询参数
 */
export interface OperLogQuery {
    pageNum?: number
    pageSize?: number
    title?: string
    operName?: string
    businessType?: number
    status?: number
    beginTime?: string
    endTime?: string
}

/**
 * 操作日志对象
 */
export interface OperLog {
    operId: number
    title: string
    businessType: number
    method: string
    requestMethod: string
    operName: string
    operUrl: string
    operIp: string
    operLocation: string
    operParam: string
    jsonResult: string
    status: number
    errorMsg: string
    operTime: string
    costTime: number
}

// 查询操作日志列表
export function listOperLog(params?: OperLogQuery) {
  return request({
    url: '/monitor/operlog/list',
    method: 'get',
    params
  }).then((res: any) => {
    // 后端返回 IPage<SysOperLog> 类型，包含 records 和 total 属性
    return res as { records: OperLog[], total: number };
  });
}

// 删除操作日志
export function deleteOperLog(operIds: number | number[]) {
    const ids = Array.isArray(operIds) ? operIds.join(',') : operIds
    return request({
        url: `/monitor/operlog/${ids}`,
        method: 'delete'
    })
}

// 清空操作日志
export function cleanOperLog() {
    return request({
        url: '/monitor/operlog/clean',
        method: 'delete'
    })
}

// 导出操作日志
export function exportOperLog(params?: OperLogQuery) {
    return request({
        url: '/monitor/operlog/export',
        method: 'post',
        params,
        responseType: 'blob'
    })
}
