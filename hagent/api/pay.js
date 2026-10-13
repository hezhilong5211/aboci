/**
 * 微信支付相关 API
 */

import { get, post } from './request.js'

/**
 * 创建支付订单并下单
 * @param {Object} data - { amount, subject }，amount 单位为分
 * @returns {Promise<Object>} - { orderNo, payParams }
 */
export function createOrder(data) {
	return post('/api/mobile/pay/create', data)
}

/**
 * 查询订单状态
 * @param {string} orderNo - 商户订单号
 * @param {number|boolean} [syncWechat] - 是否主动同步微信（兜底补单）
 * @returns {Promise<Object>} - { orderNo, status, statusText, amount, subject, payTime }
 */
export function queryOrder(orderNo, syncWechat) {
	return get('/api/mobile/pay/query', { orderNo, syncWechat })
}

/**
 * 我的购买记录(只读分页,按下单时间倒序;虚拟商品不支持退款)
 * @param {number} [pageNum=1] - 页码
 * @param {number} [pageSize=20] - 每页条数
 * @returns {Promise<Object>} - IPage,records 为 PurchaseRecordVO 数组、total 总数
 */
export function getPurchaseRecords(pageNum = 1, pageSize = 20) {
	return get('/api/mobile/pay/orders', { pageNum, pageSize })
}
