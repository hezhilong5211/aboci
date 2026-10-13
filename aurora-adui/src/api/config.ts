import request from '@/utils/request';

// 参数配置对象
export interface SysConfig {
    configId?: number;
    configName?: string;
    configKey?: string;
    configValue?: string;
    configType?: string;
    createBy?: string;
    createTime?: string;
    updateBy?: string;
    updateTime?: string;
    remark?: string;
}

// 查询参数列表
export function listConfig(query: any) {
    return request({
        url: '/system/config/list',
        method: 'get',
        params: query
    });
}

// 查询参数详细
export function getConfig(configId: number) {
    return request({
        url: `/system/config/${configId}`,
        method: 'get'
    });
}

// 根据参数键名查询参数值
export function getConfigKey(configKey: string) {
    return request({
        url: `/system/config/configKey/${configKey}`,
        method: 'get'
    });
}

// 新增参数配置
export function addConfig(data: SysConfig) {
    return request({
        url: '/system/config',
        method: 'post',
        data
    });
}

// 修改参数配置
export function updateConfig(data: SysConfig) {
    return request({
        url: '/system/config',
        method: 'put',
        data
    });
}

// 删除参数配置
export function delConfig(configId: number | number[]) {
    return request({
        url: `/system/config/${configId}`,
        method: 'delete'
    });
}
