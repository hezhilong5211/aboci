import request from '@/utils/request';

// 查询服务器信息
export function getServer() {
    return request({
        url: '/monitor/server',
        method: 'get'
    });
}
