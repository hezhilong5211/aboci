/**
 * 豆子充值套餐相关 API
 * 对接后端 C 端豆子套餐接口（/api/mobile/bean/package）
 */

import { get, post } from './request.js'

/**
 * 获取豆子充值套餐列表
 * @returns {Promise<Array>} - [{ packageId, name?, beans, price(元), sort }]
 */
export function getPackages() {
	return get('/api/mobile/bean/package/list')
}

/**
 * 充值豆子，创建订单并下单
 * @param {Object} data - { packageId }
 * @returns {Promise<Object>} - { orderNo, payParams }
 */
export function recharge(data) {
	return post('/api/mobile/bean/package/recharge', data)
}
