<template>
	<ShowcaseShell title="UI 组件库" subtitle="Aurora Mobile Design System">
		<AmCard class="intro-card" padding="34rpx">
			<text class="intro-kicker">Mobile UI</text>
			<text class="intro-title">组件分类展示</text>
			<text class="intro-desc">按基础、表单、展示、轮播、首页组合、反馈、布局与主题分组查看组件。</text>
		</AmCard>

		<view class="category-list">
			<AmCard
				v-for="item in categories"
				:key="item.title"
				clickable
				class="category-card"
				padding="28rpx"
				@tap="goCategory(item.route)"
			>
				<AmIconBox :icon="item.icon" :background="item.bg" :shadow="false" />
				<view class="category-main">
					<text class="category-title">{{ item.title }}</text>
					<text class="category-desc">{{ item.desc }}</text>
					<view class="category-tags">
						<AmTag v-for="tag in item.tags" :key="tag" :text="tag" plain />
					</view>
				</view>
				<text class="category-arrow">›</text>
			</AmCard>
		</view>
	</ShowcaseShell>
</template>

<script setup>
import ShowcaseShell from './components/ShowcaseShell.vue'

const categories = [
	{
		icon: '▣',
		title: '基础组件',
		desc: '按钮、卡片、图标容器、标签、进度条和悬浮按钮',
		route: '/pages/ui-showcase/base',
		bg: 'linear-gradient(135deg, #5B9DFF 0%, #3E7DFF 100%)',
		tags: ['Button', 'FAB', 'Progress'],
	},
	{
		icon: '▤',
		title: '表单组件',
		desc: '输入、搜索、筛选、选择器、滑块、上传和步进器',
		route: '/pages/ui-showcase/form',
		bg: 'linear-gradient(135deg, #72E7C0 0%, #38D6A0 100%)',
		tags: ['Filter', 'Picker', 'Upload'],
	},
	{
		icon: '◉',
		title: '展示组件',
		desc: '头像、徽标、内容卡片、评论、电商订单、聊天和时间轴',
		route: '/pages/ui-showcase/display',
		bg: 'linear-gradient(135deg, #938BFF 0%, #6A63FF 100%)',
		tags: ['Content', 'Commerce', 'Chat'],
	},
	{
		icon: '◌',
		title: '轮播组件',
		desc: 'Banner、卡片轮播、缩略图导航、图文活动和控制条样式',
		route: '/pages/ui-showcase/carousel',
		bg: 'linear-gradient(135deg, #4F8CFF 0%, #6D5BFF 100%)',
		tags: ['Banner', 'Thumb', 'Promo'],
	},
	{
		icon: '▦',
		title: '首页组合组件',
		desc: '首页头部、快捷入口、模板卡片，后续底部导航也归入这里',
		route: '/pages/ui-showcase/home',
		bg: 'linear-gradient(135deg, #20C997 0%, #12B886 100%)',
		tags: ['Header', 'Entry', 'Template'],
	},
	{
		icon: '!',
		title: '反馈组件',
		desc: '弹窗、底部弹出、操作菜单、异常状态、结果页和加载状态',
		route: '/pages/ui-showcase/feedback',
		bg: 'linear-gradient(135deg, #FFC97B 0%, #FFA245 100%)',
		tags: ['Result', 'Error', 'Toast'],
	},
	{
		icon: '◇',
		title: '布局与主题',
		desc: '状态栏、安全区、导航栏、吸顶、宫格和 Theme 规范',
		route: '/pages/ui-showcase/layout',
		bg: 'linear-gradient(135deg, #72AEFF 0%, #4B8FFF 100%)',
		tags: ['SafeArea', 'Sticky', 'NavBar'],
	},
]

function goCategory(route) {
	uni.navigateTo({
		url: route,
		fail: (error) => {
			console.error('组件展示页跳转失败：', route, error)
			uni.redirectTo({
				url: route,
				fail: () => {
					uni.showToast({
						title: '页面打开失败，请重新运行到模拟器',
						icon: 'none',
					})
				},
			})
		},
	})
}
</script>

<style lang="scss" scoped>
.intro-card {
	margin-bottom: 22rpx;
}

.intro-kicker {
	display: block;
	font-size: 24rpx;
	color: var(--am-color-primary);
	font-weight: 700;
	line-height: 34rpx;
}

.intro-title {
	display: block;
	margin-top: 8rpx;
	font-size: 44rpx;
	color: var(--am-color-text-title);
	font-weight: 800;
	line-height: 58rpx;
}

.intro-desc {
	display: block;
	margin-top: 12rpx;
	font-size: 24rpx;
	color: var(--am-color-text-secondary);
	line-height: 36rpx;
}

.category-list {
	display: flex;
	flex-direction: column;
	gap: 20rpx;
}

.category-card {
	display: flex;
	align-items: center;
	gap: 22rpx;
}

.category-main {
	flex: 1;
	min-width: 0;
}

.category-title {
	display: block;
	font-size: 28rpx;
	color: var(--am-color-text-title);
	font-weight: 700;
	line-height: 38rpx;
}

.category-desc {
	display: block;
	margin-top: 6rpx;
	font-size: 22rpx;
	color: var(--am-color-text-secondary);
	line-height: 32rpx;
}

.category-tags {
	display: flex;
	align-items: center;
	gap: 10rpx;
	flex-wrap: wrap;
	margin-top: 14rpx;
}

.category-arrow {
	font-size: 42rpx;
	color: var(--am-color-text-tertiary);
	line-height: 1;
}
</style>
