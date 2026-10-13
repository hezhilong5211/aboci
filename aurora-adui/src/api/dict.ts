import request from '@/utils/request';

// 字典类型对象
export interface SysDictType {
    dictId?: number;
    dictName?: string;
    dictType?: string;
    status?: string;
    createBy?: string;
    createTime?: string;
    updateBy?: string;
    updateTime?: string;
    remark?: string;
}

// 字典数据对象
export interface SysDictData {
    dictDataId?: number;
    dictSort?: number;
    dictLabel?: string;
    dictValue?: string;
    dictType?: string;
    status?: string;
    createBy?: string;
    createTime?: string;
    updateBy?: string;
    updateTime?: string;
    remark?: string;
}

// 查询字典类型列表
export function listType(query: any) {
    return request({
        url: '/system/dict/type/list',
        method: 'get',
        params: query
    });
}

// 查询字典类型详细
export function getType(dictId: number) {
    return request({
        url: `/system/dict/type/${dictId}`,
        method: 'get'
    });
}

// 新增字典类型
export function addType(data: SysDictType) {
    return request({
        url: '/system/dict/type',
        method: 'post',
        data
    });
}

// 修改字典类型
export function updateType(data: SysDictType) {
    return request({
        url: '/system/dict/type',
        method: 'put',
        data
    });
}

// 删除字典类型
export function delType(dictId: number | number[]) {
    return request({
        url: `/system/dict/type/${dictId}`,
        method: 'delete'
    });
}

// 查询字典数据列表
export function listData(query: any) {
    return request({
        url: '/system/dict/data/list',
        method: 'get',
        params: query
    });
}

// 查询字典数据详细
export function getData(dictCode: number) {
    return request({
        url: `/system/dict/data/${dictCode}`,
        method: 'get'
    });
}

// 根据字典类型查询字典数据
export function getDicts(dictType: string) {
    return request({
        url: `/system/dict/data/type/${dictType}`,
        method: 'get'
    });
}

// 新增字典数据
export function addData(data: SysDictData) {
    return request({
        url: '/system/dict/data',
        method: 'post',
        data
    });
}

// 修改字典数据
export function updateData(data: SysDictData) {
    return request({
        url: '/system/dict/data',
        method: 'put',
        data
    });
}

// 删除字典数据
export function delData(dictCode: number | number[]) {
    return request({
        url: `/system/dict/data/${dictCode}`,
        method: 'delete'
    });
}
