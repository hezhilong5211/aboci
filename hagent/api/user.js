/**
 * 用户信息相关 API
 */

import { get, put, post } from './request.js'

/**
 * 获取当前用户信息
 */
export function getUserInfo() {
	return get('/api/mobile/user/info')
}

/**
 * 更新用户信息（昵称、头像等）
 * @param {Object} data - { nickName, avatarUrl, gender }
 */
export function updateUserInfo(data) {
	return put('/api/mobile/user/info', data)
}

/**
 * 获取当前用户会员信息
 * @returns {Promise<Object>} - { levelName, expireTime, remainDays, cardBg }
 *   非会员：levelName=null、remainDays=0、cardBg=null
 */
export function getMembership() {
	return get('/api/mobile/user/membership')
}

/**
 * 绑定手机号
 * @param {Object} data - { phone, code }
 */
export function bindPhone(data) {
	return post('/api/mobile/user/bindPhone', data)
}

/**
 * 首次设置密码
 * @param {Object} data - { password, confirmPassword }
 */
export function setPassword(data) {
	return post('/api/mobile/user/password', data)
}

/**
 * 修改密码（需短信验证码）
 * @param {Object} data - { phone, code, newPassword, confirmPassword }
 */
export function changePassword(data) {
	return put('/api/mobile/user/password', data)
}
