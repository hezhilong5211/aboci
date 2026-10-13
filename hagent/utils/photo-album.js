const ALBUM_SCOPE = 'scope.writePhotosAlbum'
const PRIVACY_REJECTED_ERRNOS = new Set([103, 104])
const PRIVACY_NOT_DECLARED_ERRNO = 112
let permissionRequest = null

function errorMessage(error) {
	return String((error && (error.errMsg || error.message)) || '')
}

function errorNumber(error) {
	const value = error && (error.errno ?? error.errCode)
	const number = Number(value)
	return Number.isFinite(number) ? number : null
}

function isPrivacyNotDeclared(error) {
	const message = errorMessage(error).toLowerCase()
	return errorNumber(error) === PRIVACY_NOT_DECLARED_ERRNO
		|| /not declared.*privacy agreement|privacy api banned|privacy.*not declared/.test(message)
}

function isPrivacyRejected(error) {
	const message = errorMessage(error).toLowerCase()
	return PRIVACY_REJECTED_ERRNOS.has(errorNumber(error))
		|| /privacy.*(deny|denied|disagree|not agree|not agreed)/.test(message)
}

function isSystemAlbumPermissionFailure(error) {
	const message = errorMessage(error).toLowerCase()
	return /system permission denied/.test(message)
		|| /system.*(?:album|photo|photos).*permission/.test(message)
		|| /(?:album|photo|photos).*system.*permission/.test(message)
}

function logFailure(stage, error) {
	console.warn('[PhotoAlbum]', stage, {
		errno: errorNumber(error),
		errMsg: errorMessage(error),
	})
}

function showToast(title, icon = 'none') {
	try {
		uni.showToast({ title, icon })
	} catch (error) {
		logFailure('showToast', error)
	}
}

function callUni(method, options = {}) {
	return new Promise((resolve, reject) => {
		if (typeof uni[method] !== 'function') {
			reject(new Error(`${method} is not supported`))
			return
		}
		uni[method]({ ...options, success: resolve, fail: reject })
	})
}

function getSystemAlbumAuthorization() {
	let authorization = ''
	// #ifdef MP-WEIXIN
	if (typeof wx.getAppAuthorizeSetting === 'function') {
		try {
			authorization = wx.getAppAuthorizeSetting()?.albumAuthorized || ''
		} catch (error) {
			logFailure('getAppAuthorizeSetting', error)
		}
	}
	// #endif
	return authorization
}

function showPrivacyFailure(error) {
	const rejected = isPrivacyRejected(error)
	return new Promise((resolve) => {
		try {
			uni.showModal({
				title: rejected ? '需要隐私授权' : '保存暂不可用',
				content: rejected
					? '请在微信弹出的《小程序隐私保护指引》中勾选并同意；若刚刚拒绝，请稍后重新点击保存。'
					: '当前版本的相册（仅写入）权限尚未配置完成，请稍后再试。',
				showCancel: false,
				confirmText: '知道了',
				complete: () => resolve(false),
			})
		} catch (modalError) {
			logFailure('showPrivacyFailure', modalError)
			resolve(false)
		}
	})
}

function guideSystemAlbumPermission() {
	return new Promise((resolve) => {
		try {
			uni.showModal({
				title: '需要系统相册权限',
				content: 'iPhone 当前禁止微信写入照片，请前往系统设置允许微信访问照片，返回后重新保存。',
				confirmText: '去系统设置',
				success: (result) => {
					if (!result.confirm) {
						resolve(false)
						return
					}
					try {
						// #ifdef MP-WEIXIN
						if (typeof wx.openAppAuthorizeSetting === 'function') {
							wx.openAppAuthorizeSetting({
								fail: (error) => logFailure('openAppAuthorizeSetting', error),
								complete: () => resolve(false),
							})
							return
						}
						// #endif
						showToast('请在手机系统设置中开启微信照片权限')
						resolve(false)
					} catch (settingError) {
						logFailure('openAppAuthorizeSetting', settingError)
						showToast('请在手机系统设置中开启微信照片权限')
						resolve(false)
					}
				},
				fail: () => resolve(false),
			})
		} catch (modalError) {
			logFailure('showSystemAlbumPermission', modalError)
			resolve(false)
		}
	})
}

function guideMiniProgramAlbumPermission() {
	return new Promise((resolve) => {
		try {
			uni.showModal({
				title: '需要保存到相册权限',
				content: '该权限只用于把你选择的二维码或作品写入手机相册，不会读取其他照片。',
				confirmText: '去设置',
				success: (result) => {
					if (!result.confirm) {
						resolve(false)
						return
					}
					try {
						uni.openSetting({
							success: (setting) => {
								const granted = setting.authSetting?.[ALBUM_SCOPE] === true
								if (!granted) showToast('尚未开启保存到相册权限')
								resolve(granted)
							},
							fail: (error) => {
								logFailure('openSetting', error)
								showToast('设置页打开失败，请稍后重试')
								resolve(false)
							},
						})
					} catch (settingError) {
						logFailure('openSetting', settingError)
						resolve(false)
					}
				},
				fail: () => resolve(false),
			})
		} catch (modalError) {
			logFailure('showMiniProgramAlbumPermission', modalError)
			resolve(false)
		}
	})
}

async function handlePermissionFailure(error) {
	logFailure('permission', error)
	if (isPrivacyRejected(error) || isPrivacyNotDeclared(error)) {
		await showPrivacyFailure(error)
		return false
	}
	if (getSystemAlbumAuthorization() === 'denied' || isSystemAlbumPermissionFailure(error)) {
		return guideSystemAlbumPermission()
	}
	return guideMiniProgramAlbumPermission()
}

async function checkPhotoAlbumAccess() {
	// #ifdef MP-WEIXIN
	if (getSystemAlbumAuthorization() === 'denied') {
		return guideSystemAlbumPermission()
	}

	let setting
	try {
		setting = await callUni('getSetting')
	} catch (error) {
		logFailure('getSetting', error)
		return true
	}

	const status = setting.authSetting?.[ALBUM_SCOPE]
	if (status === true) return true
	if (status === false) return guideMiniProgramAlbumPermission()

	try {
		await callUni('authorize', { scope: ALBUM_SCOPE })
		return true
	} catch (error) {
		return handlePermissionFailure(error)
	}
	// #endif

	// #ifndef MP-WEIXIN
	return true
	// #endif
}

export async function ensurePhotoAlbumAccess() {
	if (permissionRequest) return permissionRequest
	permissionRequest = checkPhotoAlbumAccess()
		.catch((error) => {
			logFailure('ensurePhotoAlbumAccess', error)
			showToast('权限检查失败，请稍后重试')
			return false
		})
		.finally(() => {
			permissionRequest = null
		})
	return permissionRequest
}

export async function saveImageToPhotoAlbum(filePath, options = {}) {
	const { checkPermission = true, silent = false } = options
	if (!filePath) {
		if (!silent) showToast('图片文件不存在')
		return false
	}

	if (checkPermission) {
		const allowed = await ensurePhotoAlbumAccess()
		if (!allowed) return false
	}

	try {
		await callUni('saveImageToPhotosAlbum', { filePath })
		return true
	} catch (error) {
		logFailure('saveImageToPhotosAlbum', error)
		if (silent) return false

		if (isPrivacyRejected(error) || isPrivacyNotDeclared(error)) {
			await showPrivacyFailure(error)
			return false
		}
		if (getSystemAlbumAuthorization() === 'denied' || isSystemAlbumPermissionFailure(error)) {
			await guideSystemAlbumPermission()
			return false
		}

		let setting = null
		try {
			setting = await callUni('getSetting')
		} catch (settingError) {
			logFailure('getSettingAfterSaveFailure', settingError)
		}
		const message = errorMessage(error).toLowerCase()
		if (setting?.authSetting?.[ALBUM_SCOPE] === false || /auth|authorize|permission/.test(message)) {
			await guideMiniProgramAlbumPermission()
			return false
		}

		showToast('保存失败，请稍后重试')
		return false
	}
}
