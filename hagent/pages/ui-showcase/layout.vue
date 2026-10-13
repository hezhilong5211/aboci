<template>
	<ShowcaseShell title="布局与主题" subtitle="NavBar / SafeArea / Sticky / Theme">
		<AmCard class="demo-card" padding="28rpx">
			<AmSection title="导航栏 NavBar">
				<view class="navbar-demo">
					<AmNavBar title="页面标题" subtitle="自定义导航栏" right-text="资料" :fixed="false" :safe-area="false" />
				</view>
			</AmSection>
		</AmCard>

		<AmCard class="demo-card" padding="28rpx">
			<AmSection title="搜索导航 SearchNavBar">
				<view class="navbar-demo">
					<AmSearchNavBar v-model="searchKeyword" placeholder="搜索商品或内容" right-text="筛选" :safe-area="false" />
				</view>
			</AmSection>
		</AmCard>

		<AmCard class="demo-card" padding="28rpx">
			<AmSection title="安全区与吸顶">
				<AmStatusBar background="#eaf2ff" />
				<AmSafeArea>
					<AmStickyBar top="0">
						<view class="sticky-demo">
							<text class="sticky-demo-text">吸顶区域</text>
						</view>
					</AmStickyBar>
				</AmSafeArea>
			</AmSection>
		</AmCard>

		<AmCard class="demo-card" padding="28rpx">
			<AmSection title="颜色 Color">
				<view class="swatch-grid">
					<view class="swatch-item" v-for="item in colors" :key="item.name">
						<view class="swatch-block" :style="{ background: item.value }" />
						<text class="swatch-name">{{ item.name }}</text>
						<text class="swatch-value">{{ item.value }}</text>
					</view>
				</view>
			</AmSection>
		</AmCard>

		<AmCard class="demo-card" padding="28rpx">
			<AmSection title="字体 Typography">
				<view class="type-row" v-for="item in typography" :key="item.name">
					<text class="type-name">{{ item.name }}</text>
					<text class="type-sample" :style="{ fontSize: item.size }">Aurora 移动端</text>
				</view>
			</AmSection>
		</AmCard>

		<AmCard class="demo-card" padding="28rpx">
			<AmSection title="宫格 Grid">
				<AmGrid :columns="4" row-gap="24rpx">
					<view class="grid-demo" v-for="item in 8" :key="item">
						<view class="grid-box">{{ item }}</view>
					</view>
				</AmGrid>
			</AmSection>
		</AmCard>

		<AmCard class="demo-card" padding="28rpx">
			<AmSection title="圆角与阴影">
				<view class="shape-row">
					<view class="shape-card radius-sm">SM</view>
					<view class="shape-card radius-md">MD</view>
					<view class="shape-card radius-xl">XL</view>
				</view>
			</AmSection>
		</AmCard>
	</ShowcaseShell>
</template>

<script setup>
import { ref } from 'vue'
import ShowcaseShell from './components/ShowcaseShell.vue'

const searchKeyword = ref('')

const colors = [
	{ name: 'Primary', value: '#2f6bff' },
	{ name: 'Success', value: '#22c55e' },
	{ name: 'Warning', value: '#ffb020' },
	{ name: 'Danger', value: '#ff5a5f' },
	{ name: 'Title', value: '#1f2a37' },
	{ name: 'Body', value: '#334155' },
	{ name: 'Secondary', value: '#64748b' },
	{ name: 'Surface', value: '#ffffff' },
]

const typography = [
	{ name: 'Mini 18rpx', size: '18rpx' },
	{ name: 'Small 22rpx', size: '22rpx' },
	{ name: 'Base 26rpx', size: '26rpx' },
	{ name: 'Large 32rpx', size: '32rpx' },
	{ name: 'Title 44rpx', size: '44rpx' },
]
</script>

<style lang="scss" scoped>
.demo-card {
	margin-bottom: 22rpx;
}

.navbar-demo {
	border: 1rpx solid var(--am-color-border-light);
	border-radius: 22rpx;
	overflow: hidden;
}

.sticky-demo {
	height: 68rpx;
	padding: 0 22rpx;
	display: flex;
	align-items: center;
	background: #ffffff;
}

.sticky-demo-text {
	font-size: 24rpx;
	color: var(--am-color-primary);
	font-weight: 700;
	line-height: 34rpx;
}

.swatch-grid {
	display: grid;
	grid-template-columns: repeat(4, 1fr);
	gap: 18rpx;
}

.swatch-item {
	min-width: 0;
}

.swatch-block {
	height: 58rpx;
	border-radius: 14rpx;
	border: 1rpx solid var(--am-color-border-light);
}

.swatch-name,
.swatch-value {
	display: block;
	text-align: center;
}

.swatch-name {
	margin-top: 8rpx;
	font-size: 20rpx;
	color: var(--am-color-text-body);
	line-height: 28rpx;
}

.swatch-value {
	font-size: 16rpx;
	color: var(--am-color-text-tertiary);
	line-height: 24rpx;
}

.type-row {
	display: flex;
	align-items: center;
	justify-content: space-between;
	padding: 18rpx 0;
	border-bottom: 1rpx solid var(--am-color-border-light);
}

.type-row:last-child {
	border-bottom: none;
}

.type-name {
	font-size: 22rpx;
	color: var(--am-color-text-secondary);
}

.type-sample {
	color: var(--am-color-text-title);
	font-weight: 700;
}

.grid-demo {
	display: flex;
	justify-content: center;
}

.grid-box {
	width: 78rpx;
	height: 78rpx;
	border-radius: 18rpx;
	background: var(--am-color-primary-weak);
	color: var(--am-color-primary);
	font-size: 26rpx;
	font-weight: 700;
	line-height: 78rpx;
	text-align: center;
}

.shape-row {
	display: flex;
	gap: 18rpx;
}

.shape-card {
	flex: 1;
	height: 92rpx;
	background: var(--am-color-bg-soft);
	box-shadow: var(--am-shadow-card);
	color: var(--am-color-primary);
	font-size: 24rpx;
	font-weight: 700;
	line-height: 92rpx;
	text-align: center;
}

.radius-sm {
	border-radius: 10rpx;
}

.radius-md {
	border-radius: 18rpx;
}

.radius-xl {
	border-radius: 28rpx;
}
</style>
