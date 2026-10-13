<template>
	<view class="profile-v2-page" :class="pagePerformanceClass">
		<scroll-view class="page-scroll" scroll-y :enhanced="scrollEnhanced" :bounces="scrollBounces" :show-scrollbar="scrollShowScrollbar" :lower-threshold="scrollLowerThreshold">
			<view class="hero-section">
				<image v-if="visualOptions.enableFixedBackgroundImage" class="hero-bg" src="/static/profile-bg.png" mode="widthFix" />

				<view class="hero-toolbar" :style="{ paddingTop: statusBarHeight + 'px' }">
					<view class="toolbar-spacer" />
					<view class="toolbar-actions">
						<!-- #ifndef MP-WEIXIN -->
						<view class="toolbar-btn" @tap="goSettings">
							<image class="toolbar-icon-image" src="/static/top-icons/settings.png" mode="aspectFit" />
						</view>
						<!-- #endif -->
					</view>
				</view>

				<view class="user-panel" @tap="handleUserTap">
					<AmAvatar class="user-avatar" :src="avatarUrl" text="极" size="122rpx" />
					<view class="user-main">
						<view class="user-name-row">
							<text class="user-name">{{ displayName }}</text>
							<view class="verified-tag" v-if="userStore.isLoggedIn && userStore.hasPhone">
								<text class="verified-tag-icon">✓</text>
								<text class="verified-tag-text">已认证</text>
							</view>
						</view>
						<text class="user-id" v-if="userStore.isLoggedIn">ID: {{ userStore.userInfo?.userId || '12345678' }}</text>
						<text class="user-id" v-else>ID: 12345678</text>
						<text class="user-slogan">{{ userStore.isLoggedIn ? '专注成就专业，服务创造价值' : '登录解锁更多功能' }}</text>
					</view>
				</view>
			</view>

			<view class="content-area" :style="contentStyle">
				<AmCard variant="soft" clickable class="member-card" padding="24rpx 24rpx 20rpx" @tap="showComingSoon('会员中心')">
					<view class="member-top">
						<view class="member-level-wrap">
							<view class="member-badge">
								<text class="member-badge-icon">♛</text>
							</view>
							<text class="member-level">普通会员</text>
						</view>
						<view class="member-entry">
							<text class="member-entry-text">会员中心</text>
							<text class="member-entry-arrow">›</text>
						</view>
					</view>

					<view class="member-progress-row">
						<text class="member-growth-label">当前成长值 680</text>
						<AmProgress :percent="68" />
					</view>
					<text class="member-growth-tip">再得320成长值升级为银卡会员</text>

					<view class="benefit-panel">
						<view
							class="benefit-item"
							v-for="(item, idx) in memberBenefits"
							:key="idx"
							@tap.stop="showComingSoon(item.title)"
						>
							<view class="benefit-icon-box">
								<text class="benefit-icon">{{ item.char }}</text>
							</view>
							<text class="benefit-title">{{ item.title }}</text>
							<text class="benefit-subtitle">{{ item.subtitle }}</text>
						</view>
					</view>
				</AmCard>

				<AmCard class="section-card" padding="26rpx 26rpx 18rpx">
					<AmSection title="我的订单" more-text="全部订单" @more="showComingSoon('全部订单')">
					<AmGrid :columns="5">
						<view class="order-item" v-for="(item, idx) in orderItems" :key="idx" @tap="showComingSoon(item.title)">
							<view class="order-icon-wrap">
								<view class="order-icon-circle" :style="{ background: item.bg }">
									<text class="order-icon">{{ item.char }}</text>
								</view>
								<view class="order-badge" v-if="item.badge">
									<text class="order-badge-text">{{ item.badge }}</text>
								</view>
							</view>
							<text class="order-text">{{ item.title }}</text>
						</view>
					</AmGrid>
					</AmSection>
				</AmCard>

				<AmCard class="section-card" padding="26rpx 26rpx 18rpx">
					<AmSection title="我的服务">
					<AmGrid :columns="4" row-gap="30rpx">
						<view class="service-item" v-for="(item, idx) in serviceItems" :key="idx" @tap="showComingSoon(item.title)">
							<AmIconBox
								:icon="item.char"
								background="transparent"
								size="70rpx"
								radius="18rpx"
								color="#263959"
								:shadow="false"
							/>
							<text class="service-text">{{ item.title }}</text>
						</view>
					</AmGrid>
					</AmSection>
				</AmCard>

				<AmCard class="section-card tools-card" padding="26rpx 26rpx 0">
					<AmSection title="更多工具">
					<view class="tool-list">
						<AmListItem v-for="(item, idx) in toolItems" :key="idx" :title="item.title" @tap="handleToolTap(item)">
							<template #icon>
								<AmIconBox
									:icon="item.char"
									background="transparent"
									size="56rpx"
									radius="16rpx"
									color="#263959"
									:shadow="false"
								/>
							</template>
						</AmListItem>
					</view>
					</AmSection>
				</AmCard>

				<AmCard variant="soft" clickable class="contact-bar" padding="22rpx 22rpx 22rpx 18rpx" @tap="showComingSoon('在线客服')">
					<view class="contact-left">
						<view class="contact-icon-box">
							<text class="contact-icon">⌁</text>
						</view>
						<view class="contact-copy">
							<text class="contact-title">有问题？联系在线客服</text>
							<text class="contact-subtitle">7×12小时为您服务</text>
						</view>
					</view>
					<view class="contact-btn">
						<text class="contact-btn-text">联系客服</text>
					</view>
				</AmCard>
			</view>
		</scroll-view>



		<AmModal :visible="showModal" :title="modalTitle" @close="showModal = false" />
	</view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useUserStore } from '@/store/user.js'
import { usePerformanceMode } from '@/composables/usePerformanceMode.js'
import { usePageScroll } from '@/composables/usePageScroll.js'
import { useAssetPreload } from '@/composables/useAssetPreload.js'

const userStore = useUserStore()
const { pagePerformanceClass, visualOptions } = usePerformanceMode()
const { scrollEnhanced, scrollBounces, scrollShowScrollbar, scrollLowerThreshold, contentStyle } = usePageScroll({ tabbar: true })
const statusBarHeight = ref(44)

uni.getSystemInfo({
	success: (info) => {
		statusBarHeight.value = info.statusBarHeight || 44
	},
})

const showModal = ref(false)
const modalTitle = ref('')

function showComingSoon(title) {
	modalTitle.value = title
	showModal.value = true
}

const avatarUrl = computed(() => {
	const url = userStore.avatarUrl
	if (!url) return '/static/default-avatar.png'
	if (url.startsWith('/uploads/')) {
		const baseUrl = uni.getStorageSync('apiBaseUrl') || 'http://localhost:8001'
		return baseUrl + url
	}
	return url
})

const displayName = computed(() => {
	if (!userStore.isLoggedIn) return '点击登录'
	return userStore.nickName || '张小明'
})

const memberBenefits = [
	{ char: '◌', title: '专属客服', subtitle: '7×12小时在线' },
	{ char: '⌁', title: '咨询特权', subtitle: '优先响应' },
	{ char: '↓', title: '资料下载特权', subtitle: '免费下载' },
	{ char: '✩', title: '更多权益', subtitle: '敬请期待' },
]

const orderItems = [
	{ char: '▭', title: '待付款', badge: 2, bg: 'rgba(255, 187, 77, 0.13)' },
	{ char: '☰', title: '待处理', badge: 3, bg: 'rgba(74, 144, 255, 0.12)' },
	{ char: '◔', title: '处理中', badge: 0, bg: 'rgba(85, 193, 151, 0.14)' },
	{ char: '✓', title: '已完成', badge: 0, bg: 'rgba(85, 193, 151, 0.14)' },
	{ char: '✕', title: '已取消', badge: 0, bg: 'rgba(152, 162, 179, 0.12)' },
]

const serviceItems = [
	{ char: '☷', title: '我的咨询' },
	{ char: '☆', title: '我的收藏' },
	{ char: '◷', title: '浏览记录' },
	{ char: '⇩', title: '我的下载' },
	{ char: '▤', title: '发票管理' },
	{ char: '⌑', title: '优惠券' },
	{ char: '◎', title: '地址管理' },
	{ char: '?', title: '帮助中心' },
]

const toolItems = [
	{ char: '◔', title: '账号与安全', route: '/pages/user/settings' },
	{ char: '◡', title: '消息通知', route: '' },
	{ char: 'i', title: '关于我们', route: '' },
]

function handleUserTap() {
	if (!userStore.isLoggedIn) {
		uni.navigateTo({ url: '/pages/login/index' })
	}
}

function goSettings() {
	if (!userStore.isLoggedIn) {
		uni.navigateTo({ url: '/pages/login/index' })
		return
	}
	uni.navigateTo({ url: '/pages/user/settings' })
}

function handleToolTap(item) {
	if (item.route) {
		if (!userStore.isLoggedIn) {
			uni.navigateTo({ url: '/pages/login/index' })
			return
		}
		uni.navigateTo({ url: item.route })
		return
	}
	showComingSoon(item.title)
}
</script>

<style lang="scss" scoped>
.profile-v2-page {
	min-height: 100vh;
	background: #f5f8fe;
	position: relative;
}

.page-scroll {
	height: 100vh;
}

.hero-section {
	position: relative;
	height: 372rpx;
}

.hero-bg {
	position: absolute;
	top: -10rpx;
	right: -24rpx;
	width: 108%;
	height: auto;
}

.hero-toolbar {
	position: relative;
	z-index: 3;
	display: flex;
	justify-content: space-between;
}

.toolbar-spacer {
	flex: 1;
	height: 44px;
}

.toolbar-actions {
	height: 44px;
	display: flex;
	align-items: center;
	gap: 22rpx;
	padding-top: 36rpx;
	padding-right: 28rpx;
}

.toolbar-btn {
	width: 76rpx;
	height: 76rpx;
	border-radius: 50%;
	background: rgba(255, 255, 255, 0.22);
	display: flex;
	align-items: center;
	justify-content: center;
}

.toolbar-icon {
	font-size: 48rpx;
	color: #31405f;
	font-weight: 600;
	line-height: 1;
}

.toolbar-icon-image {
	width: 42rpx;
	height: 42rpx;
}

.user-panel {
	position: absolute;
	left: 34rpx;
	right: 34rpx;
	bottom: 47rpx;
	z-index: 3;
	display: flex;
	align-items: center;
}

.user-avatar {
	border: 6rpx solid rgba(255, 255, 255, 0.95);
}

.user-main {
	flex: 1;
	min-width: 0;
	padding-left: 24rpx;
}

.user-name-row {
	display: flex;
	align-items: center;
	gap: 14rpx;
	margin-bottom: 8rpx;
}

.user-name {
	font-size: 34rpx;
	line-height: 42rpx;
	font-weight: 700;
	color: #24324a;
}

.verified-tag {
	height: 38rpx;
	padding: 0 14rpx;
	border-radius: 999rpx;
	background: rgba(62, 129, 255, 0.12);
	display: inline-flex;
	align-items: center;
	gap: 6rpx;
}

.verified-tag-icon {
	width: 20rpx;
	height: 20rpx;
	border-radius: 50%;
	background: #3f87ff;
	color: #fff;
	font-size: 14rpx;
	line-height: 20rpx;
	text-align: center;
}

.verified-tag-text {
	font-size: 20rpx;
	color: #3f87ff;
	line-height: 1;
}

.user-id {
	display: block;
	margin-bottom: 10rpx;
	font-size: 22rpx;
	color: #556988;
	line-height: 30rpx;
}

.user-slogan {
	display: block;
	font-size: 22rpx;
	color: #7e8ea8;
	line-height: 32rpx;
}

.content-area {
	margin-top: -22rpx;
	padding: 0 30rpx 0;
	position: relative;
	z-index: 5;
}

.member-card,
.section-card,
.contact-bar {
	box-sizing: border-box;
}

.member-card {
	background: linear-gradient(180deg, #dceaff 0%, #e9f2ff 100%);
	border: 2rpx solid rgba(255, 255, 255, 0.72);
	box-shadow: 0 12rpx 30rpx rgba(110, 157, 234, 0.08);
	border-radius: 30rpx;
	padding: 24rpx 24rpx 20rpx;
	margin-bottom: 20rpx;
}

.member-top {
	display: flex;
	align-items: center;
	justify-content: space-between;
	margin-bottom: 20rpx;
}

.member-level-wrap {
	display: flex;
	align-items: center;
	gap: 12rpx;
}

.member-badge {
	width: 38rpx;
	height: 38rpx;
	border-radius: 12rpx;
	background: rgba(255, 255, 255, 0.48);
	display: flex;
	align-items: center;
	justify-content: center;
}

.member-badge-icon {
	font-size: 20rpx;
	color: #adc5ff;
}

.member-level {
	font-size: 28rpx;
	font-weight: 700;
	color: #1f2f47;
}

.member-entry {
	height: 56rpx;
	padding: 0 22rpx;
	border-radius: 999rpx;
	background: rgba(255, 255, 255, 0.78);
	display: inline-flex;
	align-items: center;
	gap: 8rpx;
}

.member-entry-text,
.member-entry-arrow {
	font-size: 22rpx;
	color: #468dff;
	font-weight: 600;
}

.member-progress-row {
	display: flex;
	align-items: center;
	gap: 22rpx;
	margin-bottom: 8rpx;
}

.member-growth-label {
	flex-shrink: 0;
	font-size: 22rpx;
	color: #3f88ff;
	font-weight: 600;
}

.member-progress-bar {
	flex: 1;
	height: 12rpx;
	background: rgba(187, 211, 255, 0.86);
	border-radius: 999rpx;
	overflow: hidden;
}

.member-progress-fill {
	width: 68%;
	height: 100%;
	border-radius: 999rpx;
	background: linear-gradient(90deg, #2f7cff 0%, #6eaaff 100%);
}

.member-growth-tip {
	display: block;
	margin: 0 0 22rpx 184rpx;
	font-size: 20rpx;
	color: #65758f;
	line-height: 28rpx;
}

.benefit-panel {
	background: rgba(255, 255, 255, 0.55);
	border-radius: 24rpx;
	padding: 20rpx 8rpx 18rpx;
	display: grid;
	grid-template-columns: repeat(4, 1fr);
}

.benefit-item {
	display: flex;
	flex-direction: column;
	align-items: center;
}

.benefit-icon-box {
	width: 70rpx;
	height: 70rpx;
	display: flex;
	align-items: center;
	justify-content: center;
}

.benefit-icon {
	font-size: 34rpx;
	color: #3f86ff;
}

.benefit-title {
	margin-top: 6rpx;
	font-size: 22rpx;
	color: #1e2d46;
	font-weight: 600;
	line-height: 30rpx;
	text-align: center;
}

.benefit-subtitle {
	margin-top: 4rpx;
	font-size: 18rpx;
	color: #7989a1;
	line-height: 26rpx;
	text-align: center;
}

.section-card {
	background: #ffffff;
	border-radius: 28rpx;
	box-shadow: 0 8rpx 24rpx rgba(80, 115, 170, 0.05);
	margin-bottom: 20rpx;
}

.order-item,
.service-item {
	display: flex;
	flex-direction: column;
	align-items: center;
}

.order-icon-wrap {
	position: relative;
	margin-bottom: 14rpx;
}

.order-icon-circle {
	width: 72rpx;
	height: 72rpx;
	border-radius: 50%;
	display: flex;
	align-items: center;
	justify-content: center;
}

.order-icon {
	font-size: 34rpx;
	color: #4388ff;
	font-weight: 600;
}

.order-badge {
	position: absolute;
	top: -6rpx;
	right: -12rpx;
	min-width: 34rpx;
	height: 34rpx;
	padding: 0 8rpx;
	border-radius: 18rpx;
	background: #ff5a5f;
	display: flex;
	align-items: center;
	justify-content: center;
}

.order-badge-text {
	font-size: 20rpx;
	color: #fff;
	font-weight: 700;
}

.order-text,
.service-text {
	font-size: 22rpx;
	color: #445975;
	line-height: 30rpx;
	text-align: center;
}

.tools-card {
	padding-bottom: 0;
}

.tool-list {
	border-top: 1rpx solid #edf2fb;
}

.contact-bar {
	background: linear-gradient(180deg, #eef5ff 0%, #e8f1ff 100%);
	border-radius: 24rpx;
	display: flex;
	align-items: center;
	justify-content: space-between;
}

.contact-left {
	display: flex;
	align-items: center;
	min-width: 0;
}

.contact-icon-box {
	width: 60rpx;
	height: 60rpx;
	display: flex;
	align-items: center;
	justify-content: center;
	margin-right: 14rpx;
}

.contact-icon {
	font-size: 34rpx;
	color: #3f86ff;
}

.contact-copy {
	display: flex;
	flex-direction: column;
	min-width: 0;
}

.contact-title {
	font-size: 24rpx;
	color: #24324a;
	font-weight: 600;
	line-height: 34rpx;
}

.contact-subtitle {
	font-size: 18rpx;
	color: #7f8ea8;
	line-height: 26rpx;
}

.contact-btn {
	flex-shrink: 0;
	height: 58rpx;
	padding: 0 26rpx;
	border-radius: 999rpx;
	background: linear-gradient(135deg, #2479ff 0%, #4c95ff 100%);
	display: flex;
	align-items: center;
	justify-content: center;
}

.contact-btn-text {
	font-size: 22rpx;
	color: #fff;
	font-weight: 600;
}

.am-performance-light .hero-section {
	background: #e6f1ff;
}

.am-performance-light .toolbar-btn {
	background: rgba(255, 255, 255, 0.72);
}

.am-performance-light .member-card,
.am-performance-light .section-card,
.am-performance-light .contact-bar {
	box-shadow: none;
}

.am-performance-light .member-card,
.am-performance-light .contact-bar {
	background: #eef5ff;
}
</style>
