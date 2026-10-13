/**
 * DIY 页面配置相关 API
 */

import { get } from './request.js'

/**
 * 获取当前启用的首页配置
 */
export function getHomePage() {
	return get('/api/mobile/diy/home')
}

/**
 * 获取指定 DIY 页面配置
 * @param {number} id - 页面ID
 */
export function getDiyPage(id) {
	return get(`/api/mobile/diy/page/${id}`)
}
