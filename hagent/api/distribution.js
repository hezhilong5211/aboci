import config from '@/utils/config.js'
import { getStorage } from '@/utils/storage.js'
import { get, post } from './request.js'

export function recordDistributionVisit(data) {
	return post('/api/mobile/distribution/visit', data, { showError: false })
}

export function bindDistributionAttribution(data) {
	return post('/api/mobile/distribution/attribution/bind', data, { showError: false })
}

export function getDistributionMe(options = {}) {
	return get('/api/mobile/distribution/me', {}, options)
}

export function getDistributionDashboard() {
	return get('/api/mobile/distribution/dashboard')
}

export function getDistributionQrcode() {
	return get('/api/mobile/distribution/qrcode')
}

export function getDistributionUsers(params) {
	return get('/api/mobile/distribution/users', params)
}

export function getDistributionCommissions(params) {
	return get('/api/mobile/distribution/commissions', params)
}

export function getDistributionWithdrawals(params) {
	return get('/api/mobile/distribution/withdrawals', params)
}

export function getDistributionWithdrawal(id) {
	return get(`/api/mobile/distribution/withdrawals/${id}`)
}

export function applyBankWithdrawal(data) {
	return post('/api/mobile/distribution/withdrawals', data)
}

export function applyQrWithdrawal(data, receiptPath) {
	const token = getStorage(config.tokenKey)
	return new Promise((resolve, reject) => {
		uni.uploadFile({
			url: `${config.baseUrl}/api/mobile/distribution/withdrawals`,
			filePath: receiptPath,
			name: 'receipt',
			formData: {
				applyAmount: String(data.applyAmount),
				methodType: data.methodType,
				payeeName: data.payeeName,
				privacyAccepted: String(data.privacyAccepted),
			},
			header: token ? { Authorization: `Bearer ${token}` } : {},
			timeout: config.timeout,
			success: (res) => {
				let body
				try {
					body = JSON.parse(res.data)
				} catch (e) {
					uni.showToast({ title: '提交失败', icon: 'none' })
					reject(e)
					return
				}
				if (res.statusCode === 200 && body.code === 200) {
					resolve(body.data)
					return
				}
				uni.showToast({ title: body.msg || '提交失败', icon: 'none' })
				reject(body)
			},
			fail: (error) => {
				uni.showToast({ title: '网络异常，请稍后重试', icon: 'none' })
				reject(error)
			},
		})
	})
}
