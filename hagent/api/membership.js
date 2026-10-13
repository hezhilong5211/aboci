/**
 * 会员相关 API
 * 对接后端 C 端会员接口（/api/mobile/membership）
 */

import { get, post } from './request.js'

/**
 * 获取会员等级列表
 * @returns {Promise<Array>} - [{ levelId, name, price(元), durationDays, grantBeans, cardBg }]
 */
export function getLevels() {
	return get('/api/mobile/membership/levels')
}

/**
 * 购买/续费会员，创建订单并下单
 * @param {Object} data - { levelId }
 * @returns {Promise<Object>} - { orderNo, payParams }
 */
export function purchase(data) {
	return post('/api/mobile/membership/purchase', data)
}
