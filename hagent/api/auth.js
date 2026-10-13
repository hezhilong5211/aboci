/**
 * 登录/注册相关 API
 */

import { post, get } from './request.js'
import { assertWechatLoginCode } from '@/utils/wechat-login.js'

/**
 * 微信小程序登录（code 换 token）
 * @param {Object} data - { code }
 */
export async function wxLogin(data) {
	assertWechatLoginCode(data?.code)
	return post('/api/mobile/wx/login', data)
}

/**
 * 微信手机号一键登录（小程序专用）
 * @param {Object} data - { loginCode, phoneCode }
 */
export async function wxPhoneLogin(data) {
	assertWechatLoginCode(data?.loginCode)
	return post('/api/mobile/wx/phoneLogin', data)
}

/**
 * 微信绑定手机号（解密授权手机号）
 * @param {Object} data - { encryptedData, iv, sessionKey } 或 { code }（新版 getPhoneNumber）
 */
export function wxBindPhone(data) {
	return post('/api/mobile/wx/bindPhone', data)
}

/**
 * 发送短信验证码
 * @param {Object} data - { phone }
 */
export function sendSmsCode(data) {
	return post('/api/mobile/sms/send', data)
}

/**
 * 短信验证码登录
 * @param {Object} data - { phone, code }
 */
export function smsLogin(data) {
	return post('/api/mobile/sms/login', data)
}

/**
 * 手机号+密码登录
 * @param {Object} data - { phone, password }
 */
export function passwordLogin(data) {
	return post('/api/mobile/password/login', data)
}

/**
 * 退出登录
 */
export function logout() {
	return post('/api/mobile/logout')
}

/**
 * 刷新 Token
 * @param {Object} data - { refreshToken }
 */
export function refreshTokenApi(data) {
	return post('/api/mobile/refreshToken', data)
}
