<template>
	<view class="home-content" :class="pagePerformanceClass" :style="pageVars">
		<image v-if="visualOptions.enableFixedBackgroundImage" class="page-bg" src="/static/home-bg.png" mode="aspectFill" />

		<view class="fixed-top">
			<view class="top-bar" :style="{ paddingTop: statusBarHeight + 'px' }">
				<view class="greeting-block">
					<text class="greeting-title">您好，{{ displayName }}</text>
					<text class="greeting-desc">欢迎来到我们的平台</text>
				</view>
				<view class="top-actions">
					<!-- #ifndef MP-WEIXIN -->
					<view class="action-btn" @tap="showComingSoon('搜索')">
						<image class="action-icon-image" src="/static/top-icons/search.png" mode="aspectFit" />
					</view>
					<view class="action-btn" @tap="showComingSoon('消息通知')">
						<image class="action-icon-image notice" src="/static/top-icons/notice.png" mode="aspectFit" />
						<view class="action-dot" />
					</view>
					<!-- #endif -->
				</view>
			</view>
		</view>

		<scroll-view class="page-scroll" scroll-y :enhanced="scrollEnhanced" :bounces="scrollBounces" :show-scrollbar="scrollShowScrollbar" :lower-threshold="scrollLowerThreshold">
			<view class="page-inner" :style="contentStyle">
				<view class="banner-carousel">
					<AmCarousel :items="bannerItems" autoplay :interval="3600" />
				</view>

				<AmCard class="service-card" padding="26rpx">
					<AmSection title="快捷入口">
						<AmGrid :columns="5" row-gap="28rpx">
							<view class="service-item" v-for="item in quickEntries" :key="item.title" @tap="handleEntryTap(item)">
								<AmIconBox :icon="item.icon" :background="item.bg" :shadow="visualOptions.enableDeepShadow" />
								<text class="service-title">{{ item.title }}</text>
								<text class="service-subtitle">{{ item.subtitle }}</text>
							</view>
						</AmGrid>
					</AmSection>
				</AmCard>

				<AmCard class="capability-card" padding="26rpx">
					<AmSection title="组件能力矩阵" more-text="全部组件" @more="navigateTo('/pages/ui-showcase/index')">
						<view class="capability-grid">
							<AmQuickEntry
								v-for="item in capabilityItems"
								:key="item.title"
								:variant="item.variant"
								:icon="item.icon"
								:title="item.title"
								:desc="item.desc"
								:badge="item.badge"
								@tap="navigateTo(item.route)"
							/>
						</view>
					</AmSection>
				</AmCard>

				<AmCard class="flow-card" variant="blue" padding="28rpx">
					<AmSection title="扩展路径">
						<view class="flow-list">
							<view class="flow-item" v-for="(item, index) in flowItems" :key="item.title">
								<view class="flow-index">
									<text class="flow-index-text">{{ index + 1 }}</text>
								</view>
								<view class="flow-main">
									<view class="flow-title-row">
										<text class="flow-title">{{ item.title }}</text>
										<AmTag :text="item.tag" :type="item.type" />
									</view>
									<text class="flow-desc">{{ item.desc }}</text>
								</view>
							</view>
						</view>
					</AmSection>
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
useAssetPreload(['/static/home-bg.png'], { enabled: visualOptions.value.enablePreload })

const props = defineProps({
	statusBarHeight: { type: Number, default: 44 },
})
const showModal = ref(false)
const modalTitle = ref('')
const pageVars = computed(() => ({
	'--status-bar-height': `${props.statusBarHeight}px`,
}))

const displayName = computed(() => {
	return userStore.isLoggedIn ? (userStore.nickName || '张小明') : '张小明'
})

const bannerItems = [
	{
		image: '/static/home-banners/ecommerce.png',
		title: '电商页面参考',
		desc: '商品、优惠券、订单状态等组件可组合为商城首页',
	},
	{
		image: '/static/home-banners/community.png',
		title: '社区页面参考',
		desc: '帖子、话题、关注和互动组件适合内容社区场景',
	},
	{
		image: '/static/home-banners/medical-saas.png',
		title: '医疗与 SaaS 场景',
		desc: '问诊预约、数据卡片、待办流程可组合为业务工作台',
	},
]

const quickEntries = [
	{ icon: '购', title: '电商', subtitle: '商品订单', bg: 'linear-gradient(135deg, #FFB86C 0%, #FF7A45 100%)', route: '/pages/shop/index' },
	{ icon: '社', title: '社区', subtitle: '帖子互动', bg: 'linear-gradient(135deg, #938BFF 0%, #6A63FF 100%)', developing: true },
	{ icon: '医', title: '医疗', subtitle: '问诊预约', bg: 'linear-gradient(135deg, #72E7C0 0%, #38D6A0 100%)', route: '/pages/medical/index' },
	{ icon: '台', title: 'SaaS', subtitle: '审批数据', bg: 'linear-gradient(135deg, #72AEFF 0%, #4B8FFF 100%)', developing: true },
	{ icon: '组', title: '组件总览', subtitle: '分类查看', bg: 'linear-gradient(135deg, #5B9DFF 0%, #3E7DFF 100%)', route: '/pages/ui-showcase/index' },
]

const capabilityItems = [
	{ icon: '基', title: '基础组件', desc: '按钮、卡片、图标容器、标签', variant: 'soft', route: '/pages/ui-showcase/base' },
	{ icon: '表', title: '表单组件', desc: '输入、选择、上传、筛选', variant: 'success', route: '/pages/ui-showcase/form' },
	{ icon: '展', title: '展示组件', desc: '商品、帖子、消息、订单', variant: 'primary', route: '/pages/ui-showcase/display', badge: '重点' },
	{ icon: '首', title: '首页组合', desc: '头部、快捷入口、模板卡片', variant: 'warm', route: '/pages/ui-showcase/home' },
]

const flowItems = [
	{ title: '选择业务类型', desc: '先确定电商、社区、医疗或 SaaS 工作台的页面骨架。', tag: '场景', type: 'primary' },
	{ title: '组合 AM-UI 组件', desc: '按页面需要拼装卡片、列表、表单、反馈和导航组件。', tag: '组件', type: 'success' },
	{ title: '接入接口与权限', desc: '保留展示层结构，后续接入 API、权限和业务状态即可落地。', tag: '扩展', type: 'warning' },
]

function showComingSoon(title) {
	modalTitle.value = title
	showModal.value = true
}

function showDeveloping(title) {
	showComingSoon(`${title}正在开发`)
}

function navigateTo(route) {
	uni.navigateTo({ url: route })
}

function handleEntryTap(item) {
	if (item.developing) {
		showDeveloping(item.title)
		return
	}
	if (item.route) {
		navigateTo(item.route)
		return
	}
	showComingSoon(item.title)
}
</script>

<style lang="scss" scoped>
.home-content {
	height: 100%;
	background: #eef5ff;
	position: relative;
	overflow: hidden;
}

.page-bg {
	position: absolute;
	inset: 0;
	width: 100%;
	height: 100%;
	z-index: 0;
}

.fixed-top {
	position: absolute;
	top: 0;
	left: 0;
	right: 0;
	z-index: 5;
	padding: 0 28rpx;
	background: linear-gradient(180deg, rgba(238, 245, 255, 0.94) 0%, rgba(238, 245, 255, 0.68) 78%, rgba(238, 245, 255, 0) 100%);
}

.page-scroll {
	height: 100%;
	position: relative;
	z-index: 2;
	overscroll-behavior: none;
}

.page-inner {
	padding: calc(var(--status-bar-height) + 170rpx) 28rpx 0;
}

.top-bar {
	display: flex;
	align-items: center;
	justify-content: space-between;
	padding-bottom: 34rpx;
}

.greeting-block {
	display: flex;
	flex-direction: column;
	padding-top: 36rpx;
}

.greeting-title {
	font-size: 32rpx;
	font-weight: 700;
	color: #1b2a43;
	line-height: 44rpx;
}

.greeting-desc {
	margin-top: 8rpx;
	font-size: 22rpx;
	color: #627694;
	line-height: 32rpx;
}

.top-actions {
	display: flex;
	align-items: center;
	gap: 22rpx;
	padding-top: 36rpx;
}

.action-btn {
	position: relative;
	width: 76rpx;
	height: 76rpx;
	display: flex;
	align-items: center;
	justify-content: center;
	background: rgba(255, 255, 255, 0.16);
	border-radius: 50%;
}

.action-icon-image {
	width: 42rpx;
	height: 42rpx;
}

.action-icon-image.notice {
	width: 40rpx;
	height: 40rpx;
}

.action-dot {
	position: absolute;
	top: 10rpx;
	right: 4rpx;
	width: 18rpx;
	height: 18rpx;
	border-radius: 50%;
	background: #ff5c57;
}

.banner-carousel {
	border-radius: 28rpx;
	overflow: hidden;
	box-shadow: 0 12rpx 30rpx rgba(88, 119, 173, 0.08);
}

.service-card,
.capability-card,
.flow-card {
	margin-top: 20rpx;
	border-radius: 28rpx;
	background: rgba(255, 255, 255, 0.95);
	box-shadow: 0 10rpx 28rpx rgba(88, 119, 173, 0.06);
}

.service-item {
	display: flex;
	flex-direction: column;
	align-items: center;
	min-width: 0;
}

.service-title {
	margin-top: 16rpx;
	font-size: 22rpx;
	font-weight: 700;
	color: #1e2c44;
	line-height: 30rpx;
}

.service-subtitle {
	margin-top: 6rpx;
	font-size: 18rpx;
	color: #92a0b6;
	line-height: 26rpx;
	white-space: nowrap;
}

.capability-grid {
	display: grid;
	grid-template-columns: repeat(2, 1fr);
	gap: 18rpx;
}

.flow-card {
	margin-bottom: 20rpx;
	background: linear-gradient(180deg, rgba(218, 234, 255, 0.94) 0%, rgba(236, 246, 255, 0.96) 100%);
}

.flow-list {
	display: flex;
	flex-direction: column;
	gap: 18rpx;
}

.flow-item {
	display: flex;
	align-items: flex-start;
	gap: 18rpx;
	padding: 18rpx;
	border-radius: 22rpx;
	background: rgba(255, 255, 255, 0.72);
}

.flow-index {
	width: 46rpx;
	height: 46rpx;
	border-radius: 50%;
	background: #3f88ff;
	display: flex;
	align-items: center;
	justify-content: center;
	flex-shrink: 0;
}

.flow-index-text {
	font-size: 22rpx;
	color: #ffffff;
	font-weight: 800;
	line-height: 1;
}

.flow-main {
	flex: 1;
	min-width: 0;
}

.flow-title-row {
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 16rpx;
}

.flow-title {
	font-size: 26rpx;
	color: #1d2c43;
	font-weight: 800;
	line-height: 36rpx;
}

.flow-desc {
	display: block;
	margin-top: 8rpx;
	font-size: 21rpx;
	color: #627694;
	line-height: 32rpx;
}

.am-performance-light {
	background: #eef5ff;
}

.am-performance-light .fixed-top {
	background: #eef5ff;
}

.am-performance-light .action-btn {
	background: rgba(255, 255, 255, 0.72);
}

.am-performance-light .banner-carousel,
.am-performance-light .service-card,
.am-performance-light .capability-card,
.am-performance-light .flow-card {
	box-shadow: none;
}

.am-performance-light .flow-card {
	background: #dcecff;
}
</style>
