<template>
	<view class="showcase-shell" :class="pagePerformanceClass" @touchstart="handleTouchStart" @touchend="handleTouchEnd">
		<view class="showcase-nav" :style="{ paddingTop: statusBarHeight + 'px' }">
			<view class="showcase-nav-inner">
				<view class="showcase-back" @tap="goBack">
					<text class="showcase-back-text">‹</text>
				</view>
				<view class="showcase-title-wrap">
					<text class="showcase-title">{{ title }}</text>
					<text class="showcase-subtitle" v-if="subtitle">{{ subtitle }}</text>
				</view>
				<view class="showcase-placeholder" />
			</view>
		</view>

		<scroll-view class="showcase-scroll" scroll-y :enhanced="scrollEnhanced" :bounces="scrollBounces" :show-scrollbar="scrollShowScrollbar" :lower-threshold="scrollLowerThreshold">
			<view class="showcase-inner" :style="[contentStyle, { paddingTop: (statusBarHeight + 72) + 'px' }]">
				<slot />
			</view>
		</scroll-view>
	</view>
</template>

<script setup>
import { ref } from 'vue'
import { usePerformanceMode } from '@/composables/usePerformanceMode.js'
import { usePageScroll } from '@/composables/usePageScroll.js'
import { usePageGesture } from '@/composables/usePageGesture.js'

defineProps({
	title: {
		type: String,
		default: 'UI 组件库',
	},
	subtitle: {
		type: String,
		default: '',
	},
})

const { pagePerformanceClass } = usePerformanceMode()
const { scrollEnhanced, scrollBounces, scrollShowScrollbar, scrollLowerThreshold, contentStyle } = usePageScroll()
const { handleTouchStart, handleTouchEnd } = usePageGesture({ onBack: goBack })
const statusBarHeight = ref(44)

uni.getSystemInfo({
	success: (info) => {
		statusBarHeight.value = info.statusBarHeight || 44
	},
})

function goBack() {
	uni.navigateBack()
}
</script>

<style lang="scss" scoped>
.showcase-shell {
	min-height: 100vh;
	background: var(--am-color-bg-page);
	position: relative;
}

.showcase-nav {
	position: fixed;
	top: 0;
	left: 0;
	right: 0;
	z-index: 20;
	background: var(--am-color-bg-page);
	border-bottom: 1rpx solid var(--am-color-border-light);
}

.showcase-nav-inner {
	height: 72px;
	padding: 0 28rpx;
	display: flex;
	align-items: center;
	justify-content: space-between;
	box-sizing: border-box;
}

.showcase-back,
.showcase-placeholder {
	width: 72rpx;
	height: 72rpx;
	display: flex;
	align-items: center;
	justify-content: center;
}

.showcase-back-text {
	font-size: 54rpx;
	color: var(--am-color-text-title);
	line-height: 1;
}

.showcase-title-wrap {
	flex: 1;
	display: flex;
	flex-direction: column;
	align-items: center;
	min-width: 0;
}

.showcase-title {
	font-size: 32rpx;
	font-weight: 700;
	color: var(--am-color-text-title);
	line-height: 42rpx;
}

.showcase-subtitle {
	margin-top: 2rpx;
	font-size: 18rpx;
	color: var(--am-color-text-tertiary);
	line-height: 26rpx;
}

.showcase-scroll {
	height: 100vh;
}

.showcase-inner {
	padding: 0 28rpx;
}
</style>
