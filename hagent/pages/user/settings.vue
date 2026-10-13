<template>
	<view class="settings-page" :class="pagePerformanceClass" @touchstart="handleTouchStart" @touchend="handleTouchEnd">
		<!-- 自定义导航栏 -->
		<view class="nav-bar" :style="{ paddingTop: statusBarHeight + 'px' }">
			<view class="nav-inner">
				<view class="nav-back" @tap="goBack">
					<text class="back-icon">‹</text>
				</view>
				<text class="nav-title">设置</text>
				<view class="nav-placeholder" />
			</view>
		</view>

		<!-- 内容区域 -->
		<scroll-view class="content" scroll-y :enhanced="scrollEnhanced" :bounces="scrollBounces" :show-scrollbar="scrollShowScrollbar" :lower-threshold="scrollLowerThreshold">
			<view class="content-inner" :style="[contentStyle, { paddingTop: (statusBarHeight + 44) + 'px' }]">
				<!-- 个人信息分组 -->
				<text class="section-title">个人信息</text>
				<view class="card-group">
				<!-- 头像 -->
				<view class="setting-item">
					<text class="item-label">头像</text>
					<view class="item-right">
						<!-- #ifdef MP-WEIXIN -->
						<button class="avatar-btn" open-type="chooseAvatar" @chooseavatar="onChooseAvatar">
							<image
								class="avatar-thumb"
								:src="avatarDisplay"
								mode="aspectFill"
							/>
						</button>
						<!-- #endif -->
						<!-- #ifndef MP-WEIXIN -->
						<image
							class="avatar-thumb"
							:src="avatarDisplay"
							mode="aspectFill"
							@tap="chooseAvatar"
						/>
						<!-- #endif -->
						<text class="item-arrow">›</text>
					</view>
				</view>
				<!-- 昵称 -->
				<view class="setting-item">
					<text class="item-label">昵称</text>
					<view class="item-right">
						<!-- #ifdef MP-WEIXIN -->
						<input
							v-model="formData.nickName"
							class="item-input"
							placeholder="请输入昵称"
							maxlength="20"
							type="nickname"
							placeholder-class="item-placeholder"
							@blur="handleNickNameBlur"
						/>
						<!-- #endif -->
						<!-- #ifndef MP-WEIXIN -->
						<input
							v-model="formData.nickName"
							class="item-input"
							placeholder="请输入昵称"
							maxlength="20"
							placeholder-class="item-placeholder"
							@blur="handleNickNameBlur"
						/>
						<!-- #endif -->
					</view>
				</view>
				</view>

				<!-- 账号安全分组 -->
				<text class="section-title">账号安全</text>
				<view class="card-group">
				<!-- 手机号 -->
				<view class="setting-item">
					<text class="item-label">手机号</text>
					<view class="item-right">
						<text class="item-value" v-if="userStore.hasPhone">{{ formatPhone(userStore.phone) }}</text>
						<text class="item-value unbound" v-else>未绑定</text>
						<text class="item-arrow" v-if="!userStore.hasPhone" @tap="handleBindPhone">›</text>
					</view>
				</view>
				</view>

				<!-- 交易分组 -->
				<text class="section-title">交易</text>
				<view class="card-group">
				<!-- 购买记录:只读订单列表(虚拟商品不支持退款) -->
				<view class="setting-item" @tap="goPurchaseRecords">
					<text class="item-label">购买记录</text>
					<view class="item-right">
						<text class="item-arrow">›</text>
					</view>
				</view>
				</view>

				<!-- 通用分组 -->
				<text class="section-title">通用</text>
				<view class="card-group">
				<!-- 清除缓存 -->
				<view class="setting-item" @tap="handleClearCache">
					<text class="item-label">清除缓存</text>
					<view class="item-right">
						<text class="item-value">{{ cacheSize }}</text>
						<text class="item-arrow">›</text>
					</view>
				</view>
				<!-- 关于我们 -->
				<view class="setting-item" @tap="handleAbout">
					<text class="item-label">关于我们</text>
					<view class="item-right">
						<text class="item-value">v1.0.0</text>
						<text class="item-arrow">›</text>
					</view>
				</view>
				<!-- 用户协议 -->
				<view class="setting-item" @tap="openAgreement('user')">
					<text class="item-label">用户协议</text>
					<view class="item-right">
						<text class="item-arrow">›</text>
					</view>
				</view>
				<!-- 隐私政策 -->
				<view class="setting-item" @tap="openAgreement('privacy')">
					<text class="item-label">隐私政策</text>
					<view class="item-right">
						<text class="item-arrow">›</text>
					</view>
				</view>
				</view>

				<!-- 退出登录 -->
				<view class="action-section" v-if="userStore.isLoggedIn">
					<button class="btn-logout" @tap="handleLogout">
						退出登录
					</button>
				</view>
			</view>
		</scroll-view>
	</view>
</template>

<script setup>
import { ref, reactive, computed } from 'vue'
import { useUserStore } from '@/store/user.js'
import { useAuth } from '@/composables/useAuth.js'
import { updateUserInfo } from '@/api/user.js'
import { uploadFile } from '@/api/common.js'
import config from '@/utils/config.js'
import { clearNonAuthStorage } from '@/utils/storage.js'
import { usePerformanceMode } from '@/composables/usePerformanceMode.js'
import { usePageScroll } from '@/composables/usePageScroll.js'
import { usePageGesture } from '@/composables/usePageGesture.js'

const userStore = useUserStore()
const { handleLogout } = useAuth()
const { pagePerformanceClass } = usePerformanceMode()
const { scrollEnhanced, scrollBounces, scrollShowScrollbar, scrollLowerThreshold, contentStyle } = usePageScroll()
const { handleTouchStart, handleTouchEnd } = usePageGesture({ onBack: goBack })
const saving = ref(false)
const cacheSize = ref('0.0MB')

// 获取状态栏高度
const statusBarHeight = ref(44)
uni.getSystemInfo({
	success: (info) => {
		statusBarHeight.value = info.statusBarHeight || 44
	}
})

const formData = reactive({
	nickName: userStore.nickName || '',
	avatarUrl: userStore.avatarUrl || '',
	gender: userStore.userInfo?.gender || 0,
})

// 头像显示:库里存相对路径,显示时拼 baseUrl;兼容历史完整 URL(剥离域名只留 /profile 起的相对路径)
const avatarDisplay = computed(() => {
	let url = formData.avatarUrl
	if (!url) return '/static/default-avatar.png'
	const i = url.indexOf('/profile/')
	if (i > 0) url = url.slice(i)
	return url.startsWith('http') ? url : config.baseUrl + url
})
const savedData = reactive({
	nickName: formData.nickName,
	avatarUrl: formData.avatarUrl,
	gender: formData.gender,
})

/** 返回上一页 */
function goBack() {
	uni.navigateBack()
}

/** 格式化手机号 */
function formatPhone(phone) {
	if (!phone || phone.length < 11) return phone
	return phone.replace(/(\d{3})\d{4}(\d{4})/, '$1****$2')
}

/** 选择头像（APP 端） */
function chooseAvatar() {
	uni.chooseImage({
		count: 1,
		sizeType: ['compressed'],
		sourceType: ['album', 'camera'],
		success: async (res) => {
			const tempPath = res.tempFilePaths[0]
			await uploadAndSaveAvatar(tempPath)
		}
	})
}

/** 微信头像选择回调（小程序端） */
async function onChooseAvatar(e) {
	const { avatarUrl } = e.detail
	if (!avatarUrl) return
	await uploadAndSaveAvatar(avatarUrl)
}

/** 上传头像并保存（通用） */
async function uploadAndSaveAvatar(tempPath) {
	try {
		uni.showLoading({ title: '上传中...' })
		const uploadRes = await uploadFile(tempPath, 'avatars')
		const rawUrl = uploadRes.url || uploadRes.path || ''
		// 只存相对路径,显示由 avatarDisplay 拼 baseUrl;避免把环境地址(IP/localhost)写死进库
		formData.avatarUrl = rawUrl
		uni.hideLoading()
		const success = await saveProfileChange('头像已保存')
		if (!success) {
			formData.avatarUrl = savedData.avatarUrl
		}
	} catch (e) {
		uni.hideLoading()
		formData.avatarUrl = savedData.avatarUrl
	}
}

/** 昵称失焦后自动保存 */
async function handleNickNameBlur() {
	const value = formData.nickName.trim()
	if (!value) {
		formData.nickName = savedData.nickName
		uni.showToast({ title: '请输入昵称', icon: 'none' })
		return
	}
	if (value === savedData.nickName) return
	formData.nickName = value
	const success = await saveProfileChange('昵称已保存')
	if (!success) {
		formData.nickName = savedData.nickName
	}
}

/** 保存资料变更 */
async function saveProfileChange(successTitle = '已保存') {
	if (saving.value) return false

	saving.value = true
	try {
		await updateUserInfo({
			nickName: formData.nickName,
			avatarUrl: formData.avatarUrl,
			gender: formData.gender,
		})
		userStore.updateUserInfo({
			nickName: formData.nickName,
			avatarUrl: formData.avatarUrl,
			gender: formData.gender,
		})
		savedData.nickName = formData.nickName
		savedData.avatarUrl = formData.avatarUrl
		savedData.gender = formData.gender
		uni.showToast({ title: successTitle, icon: 'success' })
		return true
	} catch (e) {
		// 错误已由 request 统一处理
		return false
	} finally {
		saving.value = false
	}
}

/** 绑定手机号 */
function handleBindPhone() {
	uni.showToast({ title: '待开发', icon: 'none' })
}

/** 清除缓存 */
function handleClearCache() {
	uni.showModal({
		title: '提示',
		content: '确定清除缓存数据吗？登录状态将保留。',
		success: (res) => {
			if (res.confirm) {
				try {
					clearNonAuthStorage()
					cacheSize.value = (uni.getStorageInfoSync().currentSize / 1024).toFixed(1) + 'MB'
					uni.showToast({ title: '缓存已清除', icon: 'success' })
				} catch (error) {
					uni.showToast({ title: '部分缓存未能清除，请重试', icon: 'none' })
				}
			}
		}
	})
}

/** 关于我们 */
function handleAbout() {
	uni.showToast({ title: '啊播吡 v1.0.0', icon: 'none' })
}

/** 打开协议 */
function openAgreement(type) {
	const legalType = type === 'privacy' ? 'privacy' : 'terms'
	uni.navigateTo({ url: `/pages/legal/detail?type=${legalType}` })
}

/** 购买记录:只读订单列表 */
function goPurchaseRecords() {
	uni.navigateTo({ url: '/pages/user/purchase/index' })
}

// 计算缓存大小
try {
	const info = uni.getStorageInfoSync()
	cacheSize.value = (info.currentSize / 1024).toFixed(1) + 'MB'
} catch (e) {
	cacheSize.value = '0.0MB'
}
</script>

<style lang="scss" scoped>
.settings-page {
	min-height: 100vh;
	background-color: #F6F7FB;
}

/* ====== 自定义导航栏 ====== */
.nav-bar {
	position: fixed;
	top: 0;
	left: 0;
	right: 0;
	z-index: 30;
	background-color: #FFFFFF;
}

.nav-inner {
	height: 44px;
	display: flex;
	align-items: center;
	justify-content: space-between;
	padding: 0 16rpx;
}

.nav-back {
	width: 72rpx;
	height: 72rpx;
	display: flex;
	align-items: center;
	justify-content: center;
}

.back-icon {
	font-size: 52rpx;
	color: #1A1A1A;
	font-weight: 300;
}

.nav-title {
	font-size: 34rpx;
	font-weight: 600;
	color: #1A1A1A;
}

.nav-placeholder {
	width: 72rpx;
}

/* ====== 内容区域 ====== */
.content {
	height: 100vh;
}

.content-inner {
	box-sizing: border-box;
}

/* ====== 分组标题 ====== */
.section-title {
	display: block;
	font-size: 24rpx;
	color: #94A3B8;
	padding: 28rpx 40rpx 12rpx;
	letter-spacing: 2rpx;
}

/* ====== 卡片分组 ====== */
/* 白底 + 啊播吡蓝渐变描边（设计稿 25），通过双 padding-box/border-box 背景实现 */
.card-group {
	margin: 0 24rpx;
	border-radius: 32rpx;
	overflow: hidden;
	border: 3rpx solid transparent;
	background:
		linear-gradient(#FFFFFF, #FFFFFF) padding-box,
		linear-gradient(135deg, #7FB0F7, #A0C4FB) border-box;
}

/* ====== 设置项 ====== */
.setting-item {
	display: flex;
	align-items: center;
	justify-content: space-between;
	padding: 28rpx 32rpx;
	position: relative;

	/* 分割线（最后一项不要） */
	&::after {
		content: '';
		position: absolute;
		bottom: 0;
		left: 32rpx;
		right: 32rpx;
		height: 1rpx;
		background-color: #EEF2F8;
	}

	&:last-child::after {
		display: none;
	}
}

.item-label {
	font-size: 30rpx;
	color: #1A1A1A;
}

.item-right {
	display: flex;
	align-items: center;
	gap: 12rpx;
}

.item-value {
	font-size: 28rpx;
	color: #94A3B8;
}

.item-value.unbound {
	color: #CBD5E1;
}

.item-input {
	text-align: right;
	font-size: 28rpx;
	color: #1A1A1A;
	width: 300rpx;
}

.item-placeholder {
	color: #CBD5E1;
	font-size: 28rpx;
}

.item-arrow {
	font-size: 32rpx;
	color: #CBD5E1;
	font-weight: 300;
}

.avatar-thumb {
	width: 72rpx;
	height: 72rpx;
	border-radius: 50%;
	background-color: #F6F7FB;
}

/* 小程序端头像选择按钮：清除 button 默认样式 */
.avatar-btn {
	background: none;
	padding: 0;
	margin: 0;
	line-height: normal;
	border: none;
	display: flex;
	align-items: center;

	&::after {
		border: none;
	}
}

/* ====== 操作按钮 ====== */
.action-section {
	padding: 20rpx 24rpx 0;
}

.btn-logout {
	width: 100%;
	height: 104rpx;
	line-height: 104rpx;
	background-color: #FEF2F2;
	color: #EF4444;
	font-size: 32rpx;
	font-weight: 600;
	border-radius: 30rpx;
	border: 1rpx solid #FECACA;

	&::after {
		border: none;
	}
}

</style>
