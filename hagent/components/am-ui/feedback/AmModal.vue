<template>
	<view class="am-modal-mask" v-if="visible" @tap="close">
		<view class="am-modal-box" @tap.stop>
			<view class="am-modal-icon">
				<text class="am-modal-icon__text">{{ icon }}</text>
			</view>
			<text class="am-modal-title">{{ title }}</text>
			<text class="am-modal-desc">{{ desc }}</text>
			<view class="am-modal-button" @tap.stop="close">
				<text class="am-modal-button__text">{{ buttonText }}</text>
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


defineProps({
	visible: {
		type: Boolean,
		default: false,
	},
	title: {
		type: String,
		default: '',
	},
	desc: {
		type: String,
		default: '功能正在开发中，敬请期待～',
	},
	buttonText: {
		type: String,
		default: '我知道了',
	},
	icon: {
		type: String,
		default: '✦',
	},
})

const emit = defineEmits(['close'])

function close() {
	emit('close')
}
</script>

<style lang="scss" scoped>
.am-modal-mask {
	position: fixed;
	top: 0;
	left: 0;
	right: 0;
	bottom: 0;
	background: rgba(0, 0, 0, 0.38);
	display: flex;
	align-items: center;
	justify-content: center;
	z-index: 1000;
	transform: translateZ(0);
}

.am-modal-box {
	width: 560rpx;
	background: #ffffff;
	border-radius: 32rpx;
	padding: 56rpx 40rpx 40rpx;
	display: flex;
	flex-direction: column;
	align-items: center;
	box-sizing: border-box;
	animation: am-modal-pop 0.18s ease;
}

.am-modal-button {
	width: 100%;
	height: 88rpx;
	border-radius: 16rpx;
	background: var(--am-color-primary);
	display: flex;
	align-items: center;
	justify-content: center;
}

.am-modal-button__text {
	font-size: 26rpx;
	color: #ffffff;
	font-weight: 600;
	line-height: 1;
}

.am-modal-icon {
	width: 100rpx;
	height: 100rpx;
	border-radius: 50%;
	background: linear-gradient(135deg, #eaf3ff, #dcecff);
	display: flex;
	align-items: center;
	justify-content: center;
	margin-bottom: 24rpx;
}

.am-modal-icon__text {
	font-size: 48rpx;
	color: #4a90ff;
	font-weight: 600;
}

.am-modal-title {
	font-size: 34rpx;
	color: #1f2a37;
	font-weight: 600;
	margin-bottom: 12rpx;
}

.am-modal-desc {
	font-size: 26rpx;
	color: #98a2b3;
	margin-bottom: 36rpx;
	text-align: center;
}

.am-performance-light .am-modal-box,
.am-motion-none .am-modal-box {
	animation: none;
}

@keyframes am-modal-pop {
	from {
		opacity: 0;
		transform: scale(0.96);
	}
	to {
		opacity: 1;
		transform: scale(1);
	}
}
</style>
