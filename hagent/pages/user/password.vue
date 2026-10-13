<template>
	<view class="password-page" :class="pagePerformanceClass" @touchstart="handleTouchStart" @touchend="handleTouchEnd">
		<!-- 自定义导航栏 -->
		<view class="nav-bar" :style="{ paddingTop: statusBarHeight + 'px' }">
			<view class="nav-inner">
				<view class="nav-back" @tap="goBack">
					<text class="back-icon">‹</text>
				</view>
				<text class="nav-title">{{ isSetMode ? '设置密码' : '修改密码' }}</text>
				<view class="nav-placeholder" />
			</view>
		</view>

		<!-- 内容区域 -->
		<scroll-view class="content" scroll-y :enhanced="scrollEnhanced" :bounces="scrollBounces" :show-scrollbar="scrollShowScrollbar" :lower-threshold="scrollLowerThreshold">
			<view class="content-inner" :style="[contentStyle, { paddingTop: (statusBarHeight + 44) + 'px' }]">

				<!-- 设置密码模式（未设密码） -->
				<view v-if="isSetMode" class="form-section">
					<text class="section-title">首次设置登录密码</text>
					<text class="section-desc">设置后可使用手机号+密码登录</text>

					<view class="card-group">
						<view class="form-item">
							<text class="form-label">新密码</text>
							<view class="form-input-wrap">
								<input
									v-model="newPassword"
									:password="!showNewPwd"
									maxlength="20"
									placeholder="8-20位，需含字母和数字"
									class="form-input"
									placeholder-class="form-placeholder"
									@input="filterPasswordInput('newPassword', $event)"
								/>
								<view class="eye-btn" @tap="showNewPwd = !showNewPwd">
									<text class="eye-icon">{{ showNewPwd ? '◉' : '◎' }}</text>
								</view>
							</view>
						</view>
						<view class="form-divider" />
						<view class="form-item">
							<text class="form-label">确认密码</text>
							<view class="form-input-wrap">
								<input
									v-model="confirmPassword"
									:password="!showConfirmPwd"
									maxlength="20"
									placeholder="再次输入密码"
									class="form-input"
									placeholder-class="form-placeholder"
									@input="filterPasswordInput('confirmPassword', $event)"
								/>
								<view class="eye-btn" @tap="showConfirmPwd = !showConfirmPwd">
									<text class="eye-icon">{{ showConfirmPwd ? '◉' : '◎' }}</text>
								</view>
							</view>
						</view>
					</view>

					<!-- 密码错误提示 -->
					<text v-if="errorTip" class="error-tip">{{ errorTip }}</text>

					<button class="btn-submit" :loading="submitting" @tap="handleSetPassword">
						确认设置
					</button>
				</view>

				<!-- 修改密码模式（已设密码） -->
				<view v-else class="form-section">
					<text class="section-title">修改登录密码</text>
					<text class="section-desc">需通过手机短信验证身份</text>

					<view class="card-group">
						<!-- 手机号（只读） -->
						<view class="form-item">
							<text class="form-label">手机号</text>
							<view class="form-input-wrap">
								<text class="form-value">{{ formatPhone(userStore.phone) }}</text>
							</view>
						</view>
						<view class="form-divider" />
						<!-- 验证码 -->
						<view class="form-item">
							<text class="form-label">验证码</text>
							<view class="form-input-wrap">
								<input
									v-model="smsCode"
									type="number"
									maxlength="6"
									placeholder="请输入验证码"
									class="form-input"
									placeholder-class="form-placeholder"
								/>
								<view
									class="code-btn"
									:class="{ disabled: countdown > 0 || sendingCode }"
									@tap="handleSendCode"
								>
									<text class="code-btn-text">{{ countdown > 0 ? `${countdown}s` : '获取验证码' }}</text>
								</view>
							</view>
						</view>
						<view class="form-divider" />
						<!-- 新密码 -->
						<view class="form-item">
							<text class="form-label">新密码</text>
							<view class="form-input-wrap">
								<input
									v-model="newPassword"
									:password="!showNewPwd"
									maxlength="20"
									placeholder="8-20位，需含字母和数字"
									class="form-input"
									placeholder-class="form-placeholder"
									@input="filterPasswordInput('newPassword', $event)"
								/>
								<view class="eye-btn" @tap="showNewPwd = !showNewPwd">
									<text class="eye-icon">{{ showNewPwd ? '◉' : '◎' }}</text>
								</view>
							</view>
						</view>
						<view class="form-divider" />
						<!-- 确认密码 -->
						<view class="form-item">
							<text class="form-label">确认密码</text>
							<view class="form-input-wrap">
								<input
									v-model="confirmPassword"
									:password="!showConfirmPwd"
									maxlength="20"
									placeholder="再次输入新密码"
									class="form-input"
									placeholder-class="form-placeholder"
									@input="filterPasswordInput('confirmPassword', $event)"
								/>
								<view class="eye-btn" @tap="showConfirmPwd = !showConfirmPwd">
									<text class="eye-icon">{{ showConfirmPwd ? '◉' : '◎' }}</text>
								</view>
							</view>
						</view>
					</view>

					<!-- 密码错误提示 -->
					<text v-if="errorTip" class="error-tip">{{ errorTip }}</text>

					<button class="btn-submit" :loading="submitting" @tap="handleChangePassword">
						确认修改
					</button>
				</view>
			</view>
		</scroll-view>
	</view>
</template>

<script setup>
import { ref, computed, onUnmounted } from 'vue'
import { useUserStore } from '@/store/user.js'
import { setPassword, changePassword } from '@/api/user.js'
import { sendSmsCode } from '@/api/auth.js'
import { usePerformanceMode } from '@/composables/usePerformanceMode.js'
import { usePageScroll } from '@/composables/usePageScroll.js'
import { usePageGesture } from '@/composables/usePageGesture.js'

const userStore = useUserStore()
const { pagePerformanceClass } = usePerformanceMode()
const { scrollEnhanced, scrollBounces, scrollShowScrollbar, scrollLowerThreshold, contentStyle } = usePageScroll()
const { handleTouchStart, handleTouchEnd } = usePageGesture({ onBack: goBack })

const statusBarHeight = ref(44)
uni.getSystemInfo({
	success: (info) => {
		statusBarHeight.value = info.statusBarHeight || 44
	},
})

// ====== 状态 ======
// 页面进入时锁定模式，防止设置成功后 hasPassword 变化导致视图闪烁
const isSetMode = ref(!userStore.hasPassword)
const newPassword = ref('')
const confirmPassword = ref('')
const smsCode = ref('')
const showNewPwd = ref(false)
const showConfirmPwd = ref(false)
const submitting = ref(false)
const countdown = ref(0)
const sendingCode = ref(false)
const errorTip = ref('')
let timer = null

onUnmounted(() => {
	if (timer) {
		clearInterval(timer)
		timer = null
	}
})

/** 密码强度校验：8-20位 ASCII 可打印字符，必须含字母+数字 */
const passwordPattern = /^(?=.*[a-zA-Z])(?=.*\d)[\x21-\x7e]{8,20}$/

/**
 * 过滤密码输入：实时移除中文/空格等非 ASCII 可打印字符
 * @param {string} field - 字段名（newPassword / confirmPassword）
 */
function filterPasswordInput(field, e) {
	const raw = e.detail.value || ''
	// 移除所有非 ASCII 可打印字符（保留 0x21-0x7E）
	const filtered = raw.replace(/[^\x21-\x7e]/g, '')
	if (filtered !== raw) {
		if (field === 'newPassword') {
			newPassword.value = filtered
		} else {
			confirmPassword.value = filtered
		}
		errorTip.value = '密码仅支持英文字母、数字和特殊符号'
		setTimeout(() => { errorTip.value = '' }, 3000)
	} else {
		errorTip.value = ''
	}
}

function validatePassword() {
	errorTip.value = ''
	if (!newPassword.value) {
		errorTip.value = '请输入密码'
		return false
	}
	if (!passwordPattern.test(newPassword.value)) {
		errorTip.value = '密码需8-20位，且同时包含字母和数字'
		return false
	}
	if (newPassword.value !== confirmPassword.value) {
		errorTip.value = '两次输入的密码不一致'
		return false
	}
	return true
}

/** 返回 */
function goBack() {
	uni.navigateBack()
}

/** 格式化手机号 */
function formatPhone(phone) {
	if (!phone || phone.length < 11) return phone
	return phone.replace(/(\d{3})\d{4}(\d{4})/, '$1****$2')
}

// ====== 发送验证码 ======
async function handleSendCode() {
	if (countdown.value > 0 || sendingCode.value) return

	sendingCode.value = true
	try {
		await sendSmsCode({ phone: userStore.phone })
		uni.showToast({ title: '验证码已发送', icon: 'success' })
		countdown.value = 60
		timer = setInterval(() => {
			countdown.value--
			if (countdown.value <= 0) {
				clearInterval(timer)
				timer = null
			}
		}, 1000)
	} catch (e) {
		// 错误已由 request 统一处理
	} finally {
		sendingCode.value = false
	}
}

// ====== 设置密码 ======
async function handleSetPassword() {
	uni.hideKeyboard()
	if (!validatePassword()) return

	submitting.value = true
	try {
		await setPassword({
			password: newPassword.value,
			confirmPassword: confirmPassword.value,
		})
		uni.showToast({ title: '密码设置成功', icon: 'success' })
		// 先返回再更新 store，避免视图闪烁
		setTimeout(() => {
			uni.navigateBack()
			userStore.updateUserInfo({ hasPassword: true })
		}, 1000)
	} catch (e) {
		// 错误已由 request 统一处理
	} finally {
		submitting.value = false
	}
}

// ====== 修改密码 ======
async function handleChangePassword() {
	uni.hideKeyboard()
	if (!smsCode.value || smsCode.value.length < 4) {
		uni.showToast({ title: '请输入验证码', icon: 'none' })
		return
	}
	if (!validatePassword()) return

	submitting.value = true
	try {
		await changePassword({
			phone: userStore.phone,
			code: smsCode.value,
			newPassword: newPassword.value,
			confirmPassword: confirmPassword.value,
		})
		uni.showToast({ title: '密码修改成功', icon: 'success' })
		setTimeout(() => uni.navigateBack(), 1000)
	} catch (e) {
		// 错误已由 request 统一处理
	} finally {
		submitting.value = false
	}
}
</script>

<style lang="scss" scoped>
.password-page {
	min-height: 100vh;
	background: var(--am-color-bg-page, #F5F6FA);
}

/* ====== 导航栏 ====== */
.nav-bar {
	position: fixed;
	top: 0;
	left: 0;
	right: 0;
	z-index: 30;
	background: var(--am-color-bg-page, #F5F6FA);
	border-bottom: 1rpx solid var(--am-color-border-light, #F0F0F0);
}

.nav-inner {
	height: 44px;
	padding: 0 28rpx;
	display: flex;
	align-items: center;
	justify-content: space-between;
}

.nav-back,
.nav-placeholder {
	width: 72rpx;
	height: 72rpx;
	display: flex;
	align-items: center;
	justify-content: center;
}

.back-icon {
	font-size: 54rpx;
	color: var(--am-color-text-title, #1A1A1A);
	line-height: 1;
}

.nav-title {
	font-size: 32rpx;
	font-weight: 700;
	color: var(--am-color-text-title, #1A1A1A);
}

/* ====== 内容 ====== */
.content {
	height: 100vh;
}

.content-inner {
	padding: 0 28rpx;
}

.form-section {
	padding-top: 20rpx;
}

.section-title {
	font-size: 34rpx;
	font-weight: 700;
	color: var(--am-color-text-title, #1A1A1A);
	display: block;
	margin-bottom: 8rpx;
}

.section-desc {
	font-size: 24rpx;
	color: var(--am-color-text-secondary, #999);
	display: block;
	margin-bottom: 36rpx;
}

/* ====== 表单卡片 ====== */
.card-group {
	background: #FFFFFF;
	border-radius: 24rpx;
	padding: 0 28rpx;
	margin-bottom: 48rpx;
}

.form-item {
	display: flex;
	align-items: center;
	min-height: 100rpx;
}

.form-label {
	width: 140rpx;
	flex-shrink: 0;
	font-size: 28rpx;
	color: var(--am-color-text-title, #1A1A1A);
}

.form-input-wrap {
	flex: 1;
	display: flex;
	align-items: center;
}

.form-input {
	flex: 1;
	height: 100rpx;
	font-size: 28rpx;
	color: var(--am-color-text-title, #1A1A1A);
}

.form-placeholder {
	color: #C0C0C0;
	font-size: 26rpx;
}

.form-value {
	font-size: 28rpx;
	color: var(--am-color-text-secondary, #999);
}

.form-divider {
	height: 1rpx;
	background: var(--am-color-border-light, #F0F0F0);
	margin-left: 140rpx;
}

/* ====== 密码显示切换 ====== */
.eye-btn {
	flex-shrink: 0;
	padding: 0 8rpx;
}

.eye-icon {
	font-size: 32rpx;
	color: #BBBBBB;
}

/* ====== 验证码按钮 ====== */
.code-btn {
	flex-shrink: 0;
	padding: 0 4rpx;
}

.code-btn-text {
	font-size: 26rpx;
	color: #4C6EF5;
	font-weight: 500;
	white-space: nowrap;
}

.code-btn.disabled .code-btn-text {
	color: #CCCCCC;
}

/* ====== 错误提示 ====== */
.error-tip {
	display: block;
	font-size: 24rpx;
	color: #F56C6C;
	padding: 8rpx 0 16rpx;
	line-height: 1.4;
}

/* ====== 提交按钮 ====== */
.btn-submit {
	width: 100%;
	height: 96rpx;
	line-height: 96rpx;
	background: linear-gradient(135deg, #7BAAFF 0%, #5B8AF5 40%, #4C6EF5 100%);
	color: #FFFFFF;
	font-size: 32rpx;
	font-weight: 600;
	border-radius: 48rpx;
	border: none;
	letter-spacing: 4rpx;
	box-shadow: 0 8rpx 32rpx rgba(76, 110, 245, 0.3);

	&::after {
		border: none;
	}

	&:active {
		opacity: 0.9;
		transform: scale(0.98);
	}
}

.am-performance-light .card-group,
.am-performance-light .btn-submit {
	box-shadow: none;
}

.am-performance-light .btn-submit:active {
	transform: none;
}
</style>
