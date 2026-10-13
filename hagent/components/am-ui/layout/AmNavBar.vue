<template>
	<view class="am-nav-bar" :class="{ 'am-nav-bar--fixed': fixed }" :style="navStyle">
		<view class="am-nav-bar__inner">
			<view class="am-nav-bar__side" @tap="handleBack">
				<slot name="left">
					<text class="am-nav-bar__back" v-if="showBack">‹</text>
				</slot>
			</view>
			<view class="am-nav-bar__main">
				<text class="am-nav-bar__title">{{ title }}</text>
				<text class="am-nav-bar__subtitle" v-if="subtitle">{{ subtitle }}</text>
			</view>
			<view class="am-nav-bar__side am-nav-bar__side--right" @tap="handleRight">
				<slot name="right">
					<text class="am-nav-bar__right-text" v-if="rightText">{{ rightText }}</text>
				</slot>
			</view>
		</view>
	</view>
</template>

<script>
export default {
	options: {
		styleIsolation: 'shared',
		virtualHost: true
	}
}
</script>

<script setup>


import { computed, ref } from 'vue'

const props = defineProps({
	title: {
		type: String,
		default: '',
	},
	subtitle: {
		type: String,
		default: '',
	},
	rightText: {
		type: String,
		default: '',
	},
	showBack: {
		type: Boolean,
		default: true,
	},
	fixed: {
		type: Boolean,
		default: true,
	},
	safeArea: {
		type: Boolean,
		default: true,
	},
	background: {
		type: String,
		default: '#ffffff',
	},
})

const emit = defineEmits(['back', 'right'])
const statusBarHeight = ref(44)

uni.getSystemInfo({
	success: (info) => {
		statusBarHeight.value = info.statusBarHeight || 44
	},
})

const navStyle = computed(() => ({
	paddingTop: props.safeArea ? `${statusBarHeight.value}px` : '0',
	background: props.background,
}))

function handleBack() {
	if (!props.showBack) return
	emit('back')
}

function handleRight() {
	emit('right')
}
</script>

<style lang="scss" scoped>
.am-nav-bar {
	box-sizing: border-box;
	width: 100%;
	border-bottom: 1rpx solid var(--am-color-border-light);
}

.am-nav-bar--fixed {
	position: fixed;
	top: 0;
	left: 0;
	right: 0;
	z-index: 30;
}

.am-nav-bar__inner {
	height: 88rpx;
	padding: 0 28rpx;
	display: flex;
	align-items: center;
	justify-content: space-between;
	box-sizing: border-box;
}

.am-nav-bar__side {
	width: 112rpx;
	min-width: 112rpx;
	height: 88rpx;
	display: flex;
	align-items: center;
}

.am-nav-bar__side--right {
	justify-content: flex-end;
}

.am-nav-bar__back {
	font-size: 58rpx;
	color: var(--am-color-text-title);
	line-height: 1;
}

.am-nav-bar__main {
	flex: 1;
	min-width: 0;
	display: flex;
	flex-direction: column;
	align-items: center;
}

.am-nav-bar__title {
	max-width: 100%;
	font-size: 32rpx;
	color: var(--am-color-text-title);
	font-weight: 700;
	line-height: 42rpx;
	overflow: hidden;
	white-space: nowrap;
	text-overflow: ellipsis;
}

.am-nav-bar__subtitle {
	max-width: 100%;
	margin-top: 2rpx;
	font-size: 18rpx;
	color: var(--am-color-text-tertiary);
	line-height: 26rpx;
	overflow: hidden;
	white-space: nowrap;
	text-overflow: ellipsis;
}

.am-nav-bar__right-text {
	font-size: 24rpx;
	color: var(--am-color-primary);
	font-weight: 600;
	line-height: 34rpx;
}
</style>
