import { usePlatform } from '@/composables/usePlatform.js'
import {
	createVirtualOrder,
	queryVirtualOrder,
	reportVirtualPaymentFailure,
	refreshWxSession,
} from '@/api/virtualPay.js'

const GOODS_MODE = 'short_series_goods'
const SUCCESS_QUERY_DELAYS = [0, 600, 1200, 2200, 3500]
const FAILURE_QUERY_DELAYS = [0, 500, 1000, 1500]
const LOCAL_WATCH_MAX_MS = 3 * 60 * 1000

const ERROR_MESSAGES = {
	'-15005': '微信登录状态校验失败，请重试',
	'-15006': '支付签名校验失败，请联系客服',
	'-15007': '微信登录状态已过期，请重试',
	'-15010': '商品尚未在微信虚拟支付中发布',
	'-15013': '商品价格与微信道具配置不一致',
	'-15014': '商品配置正在生效，请稍后重试',
}

function wait(ms) {
	if (!ms) return Promise.resolve()
	return new Promise((resolve) => setTimeout(resolve, ms))
}

function readErrorCode(error) {
	const rawCode = error?.errCode ?? error?.err_code ?? error?.errno ?? error?.code
	const numberCode = Number(rawCode)
	return Number.isNaN(numberCode) ? rawCode : numberCode
}

function createVirtualPaymentError(message, code, original, cancelled = false) {
	const error = new Error(message)
	error.code = code
	error.userMessage = message
	error.cancelled = cancelled
	error.original = original
	error.isVirtualPaymentError = true
	return error
}

function normalizeVirtualPaymentError(error) {
	if (error?.isVirtualPaymentError) return error

	const code = readErrorCode(error)
	const errMsg = String(error?.errMsg || '')
	const cancelled = Number(code) === -2 || errMsg.toLowerCase().includes('cancel')
	if (cancelled) {
		return createVirtualPaymentError('已取消支付', code, error, true)
	}

	const mappedMessage = ERROR_MESSAGES[String(code)]
	const message = mappedMessage || error?.msg || '支付失败，请重试'
	return createVirtualPaymentError(message, code, error)
}

function isSessionExpiredError(error) {
	return Number(readErrorCode(error)) === -15007
}

function isPaid(order) {
	return Number(order?.status) === 1
}

function isPendingRefund(order) {
	return Number(order?.status) === 4
}

function readPaymentClientInfo() {
	let device = {}
	let app = {}
	let miniProgram = {}
	try {
		if (typeof uni.getDeviceInfo === 'function') device = uni.getDeviceInfo() || {}
		if (typeof uni.getAppBaseInfo === 'function') app = uni.getAppBaseInfo() || {}
		if (typeof wx !== 'undefined' && typeof wx.getAccountInfoSync === 'function') {
			miniProgram = wx.getAccountInfoSync()?.miniProgram || {}
		}
		if (!Object.keys(device).length && !Object.keys(app).length) {
			device = uni.getSystemInfoSync() || {}
			app = device
		}
	} catch {
		// 设备信息只用于诊断，读取失败不影响支付。
	}
	return {
		platform: device.platform || app.platform || '',
		system: device.system || app.system || '',
		model: device.model || '',
		wechatVersion: app.version || device.version || '',
		sdkVersion: app.SDKVersion || device.SDKVersion || '',
		miniProgramVersion: miniProgram.version || '',
		envVersion: miniProgram.envVersion || '',
	}
}

function reportPaymentFailure(payload, paymentError, productType, productId) {
	if (isVirtualPaymentCancelled(paymentError)) return
	const errCode = readErrorCode(paymentError)
	const errMsg = String(paymentError?.errMsg || paymentError?.message || '')
	let rawError = ''
	try {
		rawError = JSON.stringify(paymentError || {})
	} catch {
		// 极少数循环引用错误对象只上报 code/msg。
	}
	// 日志上报不得延迟或改变用户的支付结果。
	void reportVirtualPaymentFailure({
		orderNo: payload.orderNo,
		errCode: errCode === undefined || errCode === null ? 'unknown' : String(errCode),
		errMsg,
		rawError,
		productType,
		productId,
		...readPaymentClientInfo(),
	}).catch(() => {})
}

function pendingRefundError(order) {
	return createVirtualPaymentError(
		order?.fulfillmentError || '支付已完成，但会员已到期，加油包未发放，请联系客服退款',
		'PAID_PENDING_REFUND',
	)
}

/**
 * 判断错误是否由用户主动取消支付。
 */
export function isVirtualPaymentCancelled(error) {
	if (error?.cancelled) return true
	const errMsg = String(error?.errMsg || '')
	return Number(readErrorCode(error)) === -2 || errMsg.toLowerCase().includes('cancel')
}

/**
 * 获取可展示给用户的虚拟支付错误文案。
 */
export function getVirtualPaymentErrorMessage(error, fallback = '支付失败，请重试') {
	return error?.userMessage || error?.msg || fallback
}

/**
 * 微信小程序道具直购流程。
 *
 * 统一负责能力检测、session_key 刷新、下单、调起支付、查单和 loading 配对。
 */
export function useVirtualPayment() {
	const { isIos, isDevtools } = usePlatform()
	let loadingVisible = false
	let devtoolsTipShown = false

	function showLoading(title) {
		if (loadingVisible) {
			uni.hideLoading()
			loadingVisible = false
		}
		uni.showLoading({ title, mask: true })
		loadingVisible = true
	}

	function hideLoading() {
		if (!loadingVisible) return
		uni.hideLoading()
		loadingVisible = false
	}

	function assertVirtualPaymentSupported() {
		const hasWx = typeof wx !== 'undefined'
		const hasApi = hasWx && typeof wx.requestVirtualPayment === 'function'
		let canUse = hasApi

		if (hasApi && typeof wx.canIUse === 'function') {
			try {
				canUse = wx.canIUse('requestVirtualPayment')
			} catch {
				canUse = hasApi
			}
		}

		if (!canUse) {
			throw createVirtualPaymentError(
				'当前微信版本不支持虚拟支付，请升级微信后重试',
				'UNSUPPORTED',
			)
		}
	}

	function checkWechatSession() {
		return new Promise((resolve) => {
			if (typeof wx === 'undefined' || typeof wx.checkSession !== 'function') {
				resolve(false)
				return
			}
			wx.checkSession({
				success: () => resolve(true),
				fail: () => resolve(false),
			})
		})
	}

	function getWechatLoginCode() {
		return new Promise((resolve, reject) => {
			wx.login({
				success: (result) => {
					if (result?.code) {
						resolve(result.code)
						return
					}
					reject(createVirtualPaymentError('获取微信登录状态失败，请重试', 'WX_LOGIN_EMPTY'))
				},
				fail: (error) => {
					reject(createVirtualPaymentError('获取微信登录状态失败，请重试', 'WX_LOGIN_FAILED', error))
				},
			})
		})
	}

	async function refreshWechatSession() {
		const code = await getWechatLoginCode()
		await refreshWxSession(code)
	}

	async function ensureWechatSession() {
		const valid = await checkWechatSession()
		// 即使客户端 session 有效，也刷新一次服务端 session_key：兼容迁移前存量用户，
		// 并避免同一账号在另一设备登录后服务端密钥与当前设备不一致。
		showLoading(valid ? '准备支付...' : '更新登录状态...')
		await refreshWechatSession()
		hideLoading()
	}

	function validatePayPayload(payload) {
		if (!payload?.orderNo || !payload?.paySig || !payload?.signature) {
			throw createVirtualPaymentError('虚拟支付参数不完整，请联系客服', 'INVALID_PAYLOAD')
		}
		if (payload.mode !== GOODS_MODE) {
			throw createVirtualPaymentError('虚拟支付模式配置错误，请联系客服', 'INVALID_MODE')
		}
		if (typeof payload.signData !== 'string' || !payload.signData) {
			throw createVirtualPaymentError('虚拟支付签名原串无效，请联系客服', 'INVALID_SIGN_DATA')
		}
		const env = Number(payload.env)
		if (env !== 0 && env !== 1) {
			throw createVirtualPaymentError('虚拟支付环境配置错误，请联系客服', 'INVALID_ENV')
		}
	}

	function invokeVirtualPayment(payload) {
		return new Promise((resolve, reject) => {
			wx.requestVirtualPayment({
				mode: payload.mode,
				// signData 已在服务端参与双签名，必须保持原字符串字节不变。
				signData: payload.signData,
				paySig: payload.paySig,
				signature: payload.signature,
				success: resolve,
				fail: reject,
			})
		})
	}

	function showDevtoolsPaymentTip() {
		if (!isDevtools.value || devtoolsTipShown) return Promise.resolve()
		devtoolsTipShown = true
		return new Promise((resolve) => {
			uni.showModal({
				title: '模拟器支付提示',
				content: '扫码支付成功后，请手动关闭二维码窗口。订单状态会在后台自动同步。',
				showCancel: false,
				complete: resolve,
			})
		})
	}

	/**
	 * 支付弹窗打开期间只读本地订单状态。
	 * 微信服务端推送一到就能感知已支付，且不会高频调用微信查单接口。
	 */
	async function watchPaidOrder(orderNo, control) {
		const deadline = Date.now() + LOCAL_WATCH_MAX_MS
		let delay = 600

		while (!control.stopped && Date.now() < deadline) {
			await wait(delay)
			if (control.stopped) break
			try {
				const order = await queryVirtualOrder(orderNo, false)
				if (isPaid(order)) {
					return { type: 'server-paid', order }
				}
				if (isPendingRefund(order)) {
					return { type: 'pending-refund', order }
				}
			} catch {
				// 短暂网络失败不中断支付弹窗，下一轮继续。
			}
			delay = Math.min(Math.round(delay * 1.35), 2500)
		}

		return { type: control.stopped ? 'watch-stopped' : 'watch-timeout' }
	}

	/** 客户端支付窗口结束后做有限次权威查单，补偿推送丢失。 */
	async function confirmOrder(orderNo, paymentSucceeded) {
		const delays = paymentSucceeded ? SUCCESS_QUERY_DELAYS : FAILURE_QUERY_DELAYS
		let latestOrder = null

		for (let index = 0; index < delays.length; index += 1) {
			const delay = delays[index]
			await wait(delay)
			try {
				// 首次和最后一次走微信权威查单，中间只等消息推送落库。
				const syncWechat = index === 0 || index === delays.length - 1
				latestOrder = await queryVirtualOrder(orderNo, syncWechat)
				if (isPaid(latestOrder) || isPendingRefund(latestOrder)) break
			} catch {
				// 回调可能尚未到达，单次查单失败不覆盖微信客户端的支付结果。
			}
		}

		return latestOrder
	}

	async function runPaymentAttempt(
		productType,
		productId,
		retriedAfterSessionExpiry,
		replacesOrderNo = '',
		onServerPaid,
	) {
		showLoading(retriedAfterSessionExpiry ? '重新下单中...' : '下单中...')
		const payload = await createVirtualOrder({
			productType,
			productId,
			clientPlatform: isIos.value ? 'ios' : 'other',
			replacesOrderNo,
		})
		validatePayPayload(payload)

		if (isIos.value && Number(payload.env) === 1) {
			throw createVirtualPaymentError(
				'iOS 不支持虚拟支付沙箱，请切换正式环境后再试',
				'IOS_SANDBOX_UNSUPPORTED',
			)
		}

		hideLoading()
		await showDevtoolsPaymentTip()

		const watchControl = { stopped: false }
		// 立即给支付 Promise 挂上 catch，即使服务端先确认支付并返回，
		// 后到的模拟器 cancel 也不会变成未处理 Promise。
		const paymentOutcomePromise = invokeVirtualPayment(payload)
			.then((result) => ({ type: 'client-success', result }))
			.catch((error) => ({ type: 'client-error', error }))
		const watchOutcomePromise = watchPaidOrder(payload.orderNo, watchControl)

		let outcome = await Promise.race([paymentOutcomePromise, watchOutcomePromise])
		if (outcome.type === 'server-paid') {
			watchControl.stopped = true
			let preSynced = false
			if (typeof onServerPaid === 'function') {
				try {
					await onServerPaid(outcome.order)
					preSynced = true
				} catch {
					// 页面数据预刷新失败不影响支付结果，关窗后由页面再刷一次。
				}
			}
			if (isDevtools.value) {
				// 开发者工具的二维码窗口无法由小程序关闭；
				// 后台已确认支付后只等用户关窗，关闭后立即返回，不再查单。
				await paymentOutcomePromise
			}
			return {
				paid: true,
				orderNo: payload.orderNo,
				order: outcome.order,
				confirmedByServer: true,
				preSynced,
			}
		}
		if (outcome.type === 'pending-refund') {
			watchControl.stopped = true
			if (isDevtools.value) await paymentOutcomePromise
			throw pendingRefundError(outcome.order)
		}
		if (outcome.type === 'watch-timeout') {
			// 超过三分钟仍未支付时停止后台轮询，继续等待用户关闭支付窗口。
			outcome = await paymentOutcomePromise
		}
		watchControl.stopped = true

		const paymentSucceeded = outcome.type === 'client-success'
		const paymentError = outcome.type === 'client-error' ? outcome.error : null
		if (paymentError) {
			reportPaymentFailure(payload, paymentError, productType, productId)
		}

		if (paymentError && isSessionExpiredError(paymentError) && !retriedAfterSessionExpiry) {
			showLoading('更新登录状态...')
			try {
				await refreshWechatSession()
			} finally {
				hideLoading()
			}
			// outTradeNo 不可重复使用，刷新 session_key 后必须重新创建订单。
			return runPaymentAttempt(productType, productId, true, payload.orderNo, onServerPaid)
		}

		// 不直接信任客户端回调：成功、失败都查本地订单，解决回调竞态。
		showLoading('确认支付结果...')
		let order
		try {
			order = await confirmOrder(payload.orderNo, paymentSucceeded)
		} finally {
			hideLoading()
		}

		if (isPaid(order)) {
			return { paid: true, orderNo: payload.orderNo, order }
		}
		if (isPendingRefund(order)) {
			throw pendingRefundError(order)
		}

		if (paymentError) {
			throw normalizeVirtualPaymentError(paymentError)
		}

		return { paid: false, orderNo: payload.orderNo, order }
	}

	async function payVirtualGoods({ productType, productId, onServerPaid }) {
		if (!['MEMBERSHIP', 'BEAN_RECHARGE'].includes(productType)) {
			throw createVirtualPaymentError('商品类型不支持虚拟支付', 'INVALID_PRODUCT_TYPE')
		}
		if (productId === null || productId === undefined || productId === '') {
			throw createVirtualPaymentError('商品参数无效，请重新选择', 'INVALID_PRODUCT_ID')
		}

		try {
			assertVirtualPaymentSupported()
			await ensureWechatSession()
			return await runPaymentAttempt(productType, productId, false, '', onServerPaid)
		} catch (error) {
			throw normalizeVirtualPaymentError(error)
		} finally {
			hideLoading()
		}
	}

	return { payVirtualGoods }
}
