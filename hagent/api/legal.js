/**
 * 协议政策 API
 */

import { get } from './request.js'

/**
 * 获取协议政策详情
 * @param {string} type - privacy / terms
 */
export function getLegalDocument(type) {
	return get(`/api/mobile/legal/${type}`, {}, { showError: false })
}
