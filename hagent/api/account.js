/**
 * 账户（豆子）相关 API
 * 对接后端 MobileBeanController（/api/mobile/bean）
 */

import { get } from './request.js'

/**
 * 查询豆子余额
 */
export function getBalance() {
	return get('/api/mobile/bean/balance')
}

/**
 * 查询豆子交易流水
 * @param {Object} params - 分页/筛选参数（如 { pageNum, pageSize }）
 */
export function getTransactions(params) {
	return get('/api/mobile/bean/transactions', params)
}
