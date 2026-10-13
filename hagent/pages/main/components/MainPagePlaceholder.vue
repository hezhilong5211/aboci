<template>
	<view class="main-page" :class="pagePerformanceClass">
		<view class="hero">
			<text class="hero-title">{{ title }}</text>
			<text class="hero-desc">{{ description }}</text>
		</view>

		<view class="card">
			<text class="card-title">页面建设中</text>
			<text class="card-desc">{{ placeholder }}</text>
		</view>


	</view>
</template>

<script setup>
import { usePerformanceMode } from '@/composables/usePerformanceMode.js'

const { pagePerformanceClass } = usePerformanceMode()

defineProps({
	title: {
		type: String,
		required: true,
	},
	description: {
		type: String,
		required: true,
	},
	placeholder: {
		type: String,
		required: true,
	},
})
</script>

<style lang="scss" scoped>
.main-page {
	min-height: 100vh;
	background: #f5f8fe;
}

.hero {
	padding: 96rpx 32rpx 48rpx;
	background: linear-gradient(135deg, #4c7dff 0%, #7db2ff 100%);
	border-radius: 0 0 36rpx 36rpx;
}

.hero-title {
	display: block;
	font-size: 44rpx;
	font-weight: 700;
	color: #fff;
	line-height: 1.2;
}

.hero-desc {
	display: block;
	margin-top: 12rpx;
	font-size: 24rpx;
	color: rgba(255, 255, 255, 0.82);
	line-height: 1.5;
}

.card {
	margin: 28rpx 24rpx 0;
	padding: 32rpx 28rpx;
	background: #fff;
	border-radius: 28rpx;
	box-shadow: 0 8rpx 24rpx rgba(80, 115, 170, 0.06);
}

.card-title {
	display: block;
	font-size: 30rpx;
	font-weight: 700;
	color: #24324a;
}

.card-desc {
	display: block;
	margin-top: 12rpx;
	font-size: 24rpx;
	color: #6b7a90;
	line-height: 1.6;
}

.am-performance-light .card {
	box-shadow: none;
}
</style>
