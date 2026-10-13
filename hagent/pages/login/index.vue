<template>
	<view class="login-page">
		<!-- 顶部安全区占位（不画系统胶囊） -->
		<view class="status-bar" :style="{ height: statusBarHeight + 'px' }" />

		<!-- Logo 与文案区 -->
		<view class="header-section">
			<image class="logo-image" src="/static/logo_abopi_mark.png" mode="heightFix" />
			<text class="main-title">你的专属造型助手</text>
			<text class="sub-title">能聊会做 —— 一个对话框，问答、识图、出图{{ '\n' }}一步到位。登录后即可开始创作。</text>
		</view>

		<!-- 底部固定操作区 -->
		<view class="bottom-section" :style="{ paddingBottom: 128 + safeAreaBottom + 'rpx' }">
			<!-- #ifdef MP-WEIXIN -->
			<button
				class="wx-login-btn"
				:open-type="agreed ? 'getPhoneNumber' : ''"
				@getphonenumber="onGetPhoneNumber"
				@tap="onMainTap"
			>
				<text class="wx-login-text">手机号快捷登录</text>
			</button>
			<!-- #endif -->
			<!-- #ifndef MP-WEIXIN -->
			<view class="wx-login-btn" @tap="onNonMpLogin">
				<text class="wx-login-text">手机号快捷登录</text>
			</view>
			<!-- #endif -->

			<!-- 协议勾选行 -->
			<view class="agreement-row">
				<view class="agree-box" :class="{ checked: agreed }" @tap="toggleAgree">
					<text v-if="agreed" class="agree-tick">✓</text>
				</view>
				<text class="agree-text">
					登录即代表同意
					<text class="agree-link" @tap.stop="openAgreement('user')">《用户协议》</text>
					与
					<text class="agree-link" @tap.stop="openAgreement('privacy')">《隐私政策》</text>
				</text>
			</view>

			<!-- 先逛逛 -->
			<view class="guest-row" @tap="goGuest">
				<text class="guest-text">先逛逛 ›</text>
			</view>
		</view>

		<!-- 02 未勾选协议提示弹窗 -->
		<view v-if="showAgreementModal" class="modal-mask" @tap="closeAgreementModal">
			<view class="modal-card" @tap.stop>
				<text class="modal-title">服务协议及隐私保护</text>
				<text class="modal-desc">
					为了更好地保障你的权益，请阅读并同意
					<text class="agree-link" @tap.stop="openAgreement('user')">《用户协议》</text>
					与
					<text class="agree-link" @tap.stop="openAgreement('privacy')">《隐私政策》</text>
					后再登录。
				</text>
				<view class="modal-actions">
					<!-- #ifdef MP-WEIXIN -->
					<button
						class="modal-confirm-btn"
						open-type="getPhoneNumber"
						@getphonenumber="onModalGetPhoneNumber"
					>
						同意并登录
					</button>
					<!-- #endif -->
					<!-- #ifndef MP-WEIXIN -->
					<view class="modal-confirm-btn" @tap="onModalNonMpConfirm">同意并登录</view>
					<!-- #endif -->
					<view class="modal-cancel-btn" @tap="closeAgreementModal">
						<text class="modal-cancel-text">取消</text>
					</view>
				</view>
			</view>
		</view>
	</view>
</template>

<script setup>
import { ref } from 'vue'
import { useUserStore } from '@/store/user.js'
import { useAccountStore } from '@/store/account.js'
import { wxPhoneLogin } from '@/api/auth.js'
import { getBalance } from '@/api/account.js'
import { setRefreshToken } from '@/api/request.js'
import { flushDistributionAttribution } from '@/utils/distribution-attribution.js'

const userStore = useUserStore()
const accountStore = useAccountStore()

// ====== 状态 ======
const agreed = ref(false)
const logging = ref(false)
const showAgreementModal = ref(false)

// 顶部安全区高度（px）
const statusBarHeight = ref(0)
// 底部安全区（rpx），iPhone 全面屏底部留白
const safeAreaBottom = ref(0)

const sysInfo = typeof uni.getWindowInfo === 'function'
	? uni.getWindowInfo()
	: uni.getSystemInfoSync()
statusBarHeight.value = sysInfo.statusBarHeight || 0
// safeAreaInsets 单位 px，换算成 rpx（×2 基准 375）
const insetBottom = (sysInfo.safeAreaInsets && sysInfo.safeAreaInsets.bottom) || 0
safeAreaBottom.value = insetBottom * 2

/** 切换协议勾选 */
function toggleAgree() {
	agreed.value = !agreed.value
}

/** 主按钮点击（小程序端）：未勾选协议时只弹 02 弹窗，不触发微信授权(避免 getPhoneNumber 频繁调用被限流) */
function onMainTap() {
	if (!agreed.value) {
		showAgreementModal.value = true
	}
	// 已勾选时 open-type=getPhoneNumber 生效，授权由 onGetPhoneNumber 处理
}

/** 微信手机号授权回调（仅在已勾选协议、open-type 生效时触发） */
function onGetPhoneNumber(e) {
	doWxPhoneLogin(e)
}

/** 弹窗内「同意并登录」按钮触发（小程序端）：用户再次点击完成授权，此处置 agreed=true 再登录 */
function onModalGetPhoneNumber(e) {
	agreed.value = true
	showAgreementModal.value = false
	doWxPhoneLogin(e)
}

/** 关闭未勾选协议弹窗 */
function closeAgreementModal() {
	showAgreementModal.value = false
}

/** 微信手机号登录核心流程（复用原有逻辑） */
async function doWxPhoneLogin(e) {
	// 用户拒绝授权
	if (e.detail.errMsg !== 'getPhoneNumber:ok') {
		uni.showToast({ title: '您取消了授权', icon: 'none' })
		return
	}

	const phoneCode = e.detail.code
	if (!phoneCode) {
		uni.showToast({ title: '获取手机号失败，请重试', icon: 'none' })
		return
	}

	logging.value = true
	try {
		uni.showLoading({ title: '登录中...' })

		// 同时获取 wx.login 的 code
		const loginRes = await uni.login({ provider: 'weixin' })

		const res = await wxPhoneLogin({
			loginCode: loginRes.code,
			phoneCode: phoneCode,
		})

		handleLoginSuccess(res)
	} catch (err) {
		uni.hideLoading()
		console.error('微信手机号登录失败:', err)
	} finally {
		logging.value = false
	}
}

/** 非小程序端主按钮点击 */
function onNonMpLogin() {
	if (!agreed.value) {
		showAgreementModal.value = true
		return
	}
	uni.showToast({ title: '仅小程序端支持', icon: 'none' })
}

/** 非小程序端弹窗内「同意并登录」点击 */
function onModalNonMpConfirm() {
	agreed.value = true
	showAgreementModal.value = false
	uni.showToast({ title: '仅小程序端支持', icon: 'none' })
}

// ====== 登录成功 ======
function handleLoginSuccess(res) {
	uni.hideLoading()
	userStore.setToken(res.token)
	if (res.refreshToken) {
		setRefreshToken(res.refreshToken)
	}
	if (res.userInfo) {
		userStore.setUserInfo(res.userInfo)
	}
	void flushDistributionAttribution()
	// 登录后主动拉一次余额:首页存活不重挂、onMounted 不再跑,须在此写入 store 让首页豆子即时刷新
	loadBalanceAfterLogin()
	uni.showToast({ title: '登录成功', icon: 'success' })
	setTimeout(() => {
		const pages = getCurrentPages()
		if (pages.length > 1) {
			uni.navigateBack()
		} else {
			uni.switchTab({ url: '/pages/index/index' })
		}
	}, 1000)
}

// 登录后加载豆子余额写入 accountStore(失败不打断登录主流程)
async function loadBalanceAfterLogin() {
	try {
		const data = await getBalance()
		const balance = typeof data === 'number' ? data : data && data.balance
		if (balance != null) accountStore.setBalance(balance)
	} catch (e) {
		// 忽略:登录主流程不受影响
	}
}

/** 打开协议 */
function openAgreement(type) {
	const legalType = type === 'privacy' ? 'privacy' : 'terms'
	uni.navigateTo({ url: `/pages/legal/detail?type=${legalType}` })
}

/** 先逛逛（游客）：从首页弹窗 navigateTo 来则返回首页(不重弹),否则回首页 */
function goGuest() {
	const pages = getCurrentPages()
	if (pages.length > 1) {
		uni.navigateBack()
	} else {
		uni.reLaunch({ url: '/pages/index/index' })
	}
}
</script>

<style lang="scss" scoped>
.login-page {
	min-height: 100vh;
	display: flex;
	flex-direction: column;
	background: #F6F7FB;
	position: relative;
}

/* ====== 顶部安全区 ====== */
.status-bar {
	width: 100%;
}

/* ====== Logo 与文案 ====== */
.header-section {
	padding: 240rpx 64rpx 0;
	display: flex;
	flex-direction: column;
}

.logo-image {
	height: 124rpx;
	width: auto;
	display: block;
}

.main-title {
	font-size: 54rpx;
	font-weight: 700;
	color: #0f172a;
	letter-spacing: 1rpx;
	margin-top: 56rpx;
}

.sub-title {
	font-size: 30rpx;
	color: #64748b;
	line-height: 1.7;
	margin-top: 28rpx;
}

/* ====== 底部固定操作区 ====== */
.bottom-section {
	position: absolute;
	left: 64rpx;
	right: 64rpx;
	bottom: 0;
}

/* 手机号快捷登录按钮 */
.wx-login-btn {
	position: relative;
	width: 100%;
	height: 108rpx;
	border-radius: 54rpx;
	background: linear-gradient(135deg, #4F84EA, #8FB4FB);
	color: #fff;
	display: flex;
	align-items: center;
	justify-content: center;
	box-shadow: 0 20rpx 48rpx rgba(96, 140, 224, 0.32);
	border: none;
	padding: 0;
	margin: 0;
	/* 重置微信小程序 button 默认样式(默认浅灰底 + 大 line-height,会透出灰块) */
	line-height: normal;
	overflow: hidden;
	box-sizing: border-box;

	&::after {
		border: none;
	}

	&:active {
		opacity: 0.92;
	}
}

.wx-login-text {
	font-size: 32rpx;
	font-weight: 700;
	color: #fff;
	line-height: normal;
}

/* 协议勾选行 */
.agreement-row {
	display: flex;
	align-items: flex-start;
	justify-content: center;
	margin-top: 36rpx;
	padding: 0 16rpx;
}

.agree-box {
	width: 34rpx;
	height: 34rpx;
	border: 3rpx solid #C4CAD6;
	border-radius: 10rpx;
	flex-shrink: 0;
	margin-top: 2rpx;
	margin-right: 14rpx;
	display: flex;
	align-items: center;
	justify-content: center;
}

.agree-box.checked {
	background: #4F84EA;
	border-color: #4F84EA;
}

.agree-tick {
	font-size: 22rpx;
	color: #fff;
	font-weight: 700;
}

.agree-text {
	font-size: 24rpx;
	color: #94a3b8;
	line-height: 1.6;
}

.agree-link {
	color: #4F84EA;
}

/* 先逛逛 */
.guest-row {
	text-align: center;
	margin-top: 32rpx;
}

.guest-text {
	font-size: 26rpx;
	color: #64748b;
}

/* ====== 02 未勾选协议弹窗 ====== */
.modal-mask {
	position: fixed;
	top: 0;
	left: 0;
	right: 0;
	bottom: 0;
	background: rgba(20, 24, 40, 0.42);
	display: flex;
	align-items: center;
	justify-content: center;
	z-index: 999;
}

.modal-card {
	width: 600rpx;
	background: #fff;
	border-radius: 44rpx;
	padding: 52rpx 44rpx 36rpx;
	box-shadow: 0 60rpx 120rpx rgba(20, 24, 40, 0.28);
	display: flex;
	flex-direction: column;
}

.modal-title {
	font-size: 34rpx;
	font-weight: 700;
	color: #15161B;
	text-align: center;
}

.modal-desc {
	font-size: 28rpx;
	color: #64748b;
	line-height: 1.75;
	text-align: center;
	margin-top: 28rpx;
}

.modal-actions {
	display: flex;
	flex-direction: column;
	margin-top: 44rpx;
}

.modal-confirm-btn {
	width: 100%;
	height: 96rpx;
	border-radius: 48rpx;
	background: linear-gradient(135deg, #4F84EA, #8FB4FB);
	color: #fff;
	font-size: 32rpx;
	font-weight: 600;
	display: flex;
	align-items: center;
	justify-content: center;
	line-height: 96rpx;
	border: none;
	padding: 0;
	box-shadow: 0 16rpx 40rpx rgba(96, 140, 224, 0.3);

	&::after {
		border: none;
	}

	&:active {
		opacity: 0.92;
	}
}

.modal-cancel-btn {
	width: 100%;
	height: 96rpx;
	display: flex;
	align-items: center;
	justify-content: center;
	margin-top: 20rpx;
}

.modal-cancel-text {
	font-size: 32rpx;
	color: #94a3b8;
}
</style>
