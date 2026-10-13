<template>
	<view class="am-tip-card" :class="{ 'am-tip-card--clickable': clickable }" @tap="handleTap">
		<view class="am-tip-card__icon">
			<slot name="icon">
				<text class="am-tip-card__icon-text">{{ icon }}</text>
			</slot>
		</view>
		<view class="am-tip-card__main">
			<text class="am-tip-card__title">{{ title }}</text>
			<text class="am-tip-card__desc">{{ desc }}</text>
		</view>
		<text class="am-tip-card__arrow" v-if="arrow">›</text>
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
	icon: {
		type: String,
		default: '✦',
	},
	title: {
		type: String,
		default: '',
	},
	desc: {
		type: String,
		default: '',
	},
	arrow: {
		type: Boolean,
		default: true,
	},
	clickable: {
		type: Boolean,
		default: false,
	},
})

const emit = defineEmits(['tap'])

function handleTap(event) {
	if (!props.clickable) return
	emit('tap', event)
}
</script>

<style lang="scss" scoped>
.am-tip-card {
	box-sizing: border-box;
	width: 100%;
	padding: 26rpx 28rpx;
	border-radius: 28rpx;
	background: #ffffff;
	box-shadow: var(--am-shadow-card);
	display: flex;
	align-items: center;
	gap: 18rpx;
}

.am-tip-card--clickable {
	transition: opacity 0.15s ease;
}

.am-tip-card--clickable:active {
	opacity: 0.86;
}

.am-tip-card__icon {
	width: 48rpx;
	height: 48rpx;
	border-radius: 14rpx;
	background: #f2edff;
	display: flex;
	align-items: center;
	justify-content: center;
	flex-shrink: 0;
}

.am-tip-card__icon-text {
	font-size: 30rpx;
	color: #7c4dff;
	line-height: 1;
}

.am-tip-card__main {
	flex: 1;
	min-width: 0;
	display: flex;
	flex-direction: column;
}

.am-tip-card__title {
	font-size: 28rpx;
	color: var(--am-color-text-title);
	font-weight: 800;
	line-height: 38rpx;
}

.am-tip-card__desc {
	margin-top: 8rpx;
	font-size: 24rpx;
	color: var(--am-color-text-secondary);
	line-height: 36rpx;
}

.am-tip-card__arrow {
	font-size: 44rpx;
	color: var(--am-color-text-body);
	line-height: 1;
	flex-shrink: 0;
}

.am-performance-light .am-tip-card {
	box-shadow: none;
}

.am-performance-light .am-tip-card--clickable {
	transition: none;
}
</style>
