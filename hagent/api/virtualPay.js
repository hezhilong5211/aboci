/**
 * 微信小程序虚拟支付相关 API。
 *
 * 客户端只传本地业务商品 ID，微信道具 ID、价格和签名均由服务端生成。
 */

import { get, post } from './request.js'
import { assertWechatLoginCode } from '@/utils/wechat-login.js'

/**
 * 创建道具直购订单。
 * @param {Object} data - { productType, productId, clientPlatform, replacesOrderNo? }
 * @returns {Promise<Object>} - { orderNo, mode, signData, paySig, signature, env }
 */
export function createVirtualOrder(data) {
	return post('/api/mobile/vpay/create', data, { showError: false })
}

/**
 * 查询虚拟支付订单，后端会在必要时向微信查单并补发权益。
 * @param {string} orderNo - 商户订单号
 * @param {boolean} syncWechat - true=微信权威查单；false=仅读本地状态
 */
export function queryVirtualOrder(orderNo, syncWechat = true) {
	return get('/api/mobile/vpay/query', { orderNo, syncWechat }, { showError: false })
}

/**
 * 刷新当前用户在服务端保存的微信 session_key。
 * @param {string} code - wx.login 返回的临时 code
 */
export async function refreshWxSession(code) {
	assertWechatLoginCode(code, { showError: false })
	return post('/api/mobile/wx/session/refresh', { code }, { showError: false })
}

/**
 * 上报 wx.requestVirtualPayment 失败回调的原始错误。
 * 该接口只写服务日志，不会改变订单状态。
 */
export function reportVirtualPaymentFailure(data) {
	return post('/api/mobile/vpay/client-failure', data, {
		showError: false,
		skipAuthRefresh: true,
	})
}
