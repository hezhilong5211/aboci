<template>
	<view class="am-coupon-card" :class="{ 'am-coupon-card--disabled': disabled }" @tap="handleTap">
		<view class="am-coupon-card__value">
			<text class="am-coupon-card__amount">{{ amount }}</text>
			<text class="am-coupon-card__unit">{{ unit }}</text>
		</view>
		<view class="am-coupon-card__main">
			<text class="am-coupon-card__title">{{ title }}</text>
			<text class="am-coupon-card__desc">{{ desc }}</text>
			<text class="am-coupon-card__time" v-if="time">{{ time }}</text>
		</view>
		<view class="am-coupon-card__action">
			<text class="am-coupon-card__action-text">{{ actionText }}</text>
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


const props = defineProps({
	amount: {
		type: [String, Number],
		default: '',
	},
	unit: {
		type: String,
		default: '元',
	},
	title: {
		type: String,
		default: '',
	},
	desc: {
		type: String,
		default: '',
	},
	time: {
		type: String,
		default: '',
	},
	actionText: {
		type: String,
		default: '领取',
	},
	disabled: {
		type: Boolean,
		default: false,
	},
})

const emit = defineEmits(['tap'])

function handleTap(event) {
	if (props.disabled) return
	emit('tap', event)
}
</script>

<style lang="scss" scoped>
.am-coupon-card {
	position: relative;
	display: flex;
	align-items: center;
	gap: 22rpx;
	padding: 26rpx;
	border-radius: 24rpx;
	background: linear-gradient(135deg, #fff7ed 0%, #fff1f2 100%);
	overflow: hidden;
	box-sizing: border-box;
}

.am-coupon-card--disabled {
	filter: grayscale(1);
	opacity: 0.58;
}

.am-coupon-card__value {
	width: 136rpx;
	height: 112rpx;
	border-radius: 22rpx;
	background: #ffffff;
	display: flex;
	align-items: baseline;
	justify-content: center;
}

.am-coupon-card__amount {
	font-size: 46rpx;
	color: var(--am-color-danger);
	font-weight: 800;
	line-height: 112rpx;
}

.am-coupon-card__unit {
	font-size: 22rpx;
	color: var(--am-color-danger);
	font-weight: 700;
}

.am-coupon-card__main {
	flex: 1;
	min-width: 0;
}

.am-coupon-card__title {
	display: block;
	font-size: 28rpx;
	color: var(--am-color-text-title);
	font-weight: 800;
	line-height: 38rpx;
}

.am-coupon-card__desc,
.am-coupon-card__time {
	display: block;
	font-size: 22rpx;
	color: var(--am-color-text-secondary);
	line-height: 32rpx;
}

.am-coupon-card__desc {
	margin-top: 6rpx;
}

.am-coupon-card__time {
	margin-top: 4rpx;
}

.am-coupon-card__action {
	width: 88rpx;
	height: 52rpx;
	border-radius: 999rpx;
	background: var(--am-color-danger);
	display: flex;
	align-items: center;
	justify-content: center;
}

.am-coupon-card__action-text {
	font-size: 22rpx;
	color: #ffffff;
	font-weight: 700;
	line-height: 1;
}
</style>
