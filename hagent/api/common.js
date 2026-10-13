/**
 * 通用 API（文件上传、TabBar 配置等）
 */

import config from '@/utils/config.js'
import { getStorage } from '@/utils/storage.js'
import { get } from './request.js'

/**
 * 通用文件上传
 * @param {string} filePath - 本地文件路径
 * @param {string} [category='common'] - 上传分类
 * @returns {Promise<Object>} - { path, url, name, ext, size }
 */
export function uploadFile(filePath, category = 'common') {
	const token = getStorage(config.tokenKey)

	return new Promise((resolve, reject) => {
		uni.uploadFile({
			url: `${config.baseUrl}/api/common/upload`,
			filePath,
			name: 'file',
			formData: { category },
			header: {
				'Authorization': token ? `Bearer ${token}` : ''
			},
			success: (res) => {
				try {
					const data = JSON.parse(res.data)
					if (data.code === 200) {
						resolve(data.data)
					} else {
						uni.showToast({ title: data.msg || '上传失败', icon: 'none' })
						reject(data)
					}
				} catch (e) {
					reject(e)
				}
			},
			fail: (err) => {
				uni.showToast({ title: '上传失败', icon: 'none' })
				reject(err)
			}
		})
	})
}

/**
 * 获取 TabBar 配置（移动端专用，返回解析好的配置对象）
 */
export function getTabbarConfig() {
	return get('/api/mobile/tabbar', {}, { showError: false })
}

/**
 * 获取分享配置(免鉴权;{title, imageUrl},imageUrl 为 /profile 相对路径或空)
 */
export function getShareConfig() {
	return get('/api/mobile/share', {}, { showError: false })
}

