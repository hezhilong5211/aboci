<template>
	<view class="am-quick-entry" :class="[`am-quick-entry--${variant}`, { 'am-quick-entry--compact': compact }]" :style="{ background }" @tap="emit('tap')">
		<view class="am-quick-entry__icon" :style="{ background: iconBackground }">
			<text class="am-quick-entry__icon-text" :style="{ color: iconColor }">{{ icon }}</text>
		</view>
		<view class="am-quick-entry__main" v-if="!compact">
			<text class="am-quick-entry__title">{{ title }}</text>
			<text class="am-quick-entry__desc" v-if="desc">{{ desc }}</text>
		</view>
		<text class="am-quick-entry__arrow" v-if="arrow && !compact">›</text>
		<text class="am-quick-entry__badge" v-if="badge">{{ badge }}</text>
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
	variant: {
		type: String,
		default: 'soft',
	},
	icon: {
		type: String,
		default: '◇',
	},
	title: {
		type: String,
		default: '',
	},
	desc: {
		type: String,
		default: '',
	},
	badge: {
		type: String,
		default: '',
	},
	background: {
		type: String,
		default: '',
	},
	iconBackground: {
		type: String,
		default: '',
	},
	iconColor: {
		type: String,
		default: '',
	},
	arrow: {
		type: Boolean,
		default: true,
	},
	compact: {
		type: Boolean,
		default: false,
	},
})

const emit = defineEmits(['tap'])
</script>

<style lang="scss" scoped>
.am-quick-entry {
	position: relative;
	min-height: 148rpx;
	padding: 24rpx;
	border-radius: 24rpx;
	background: #ffffff;
	box-shadow: var(--am-shadow-card);
	display: flex;
	align-items: center;
	gap: 20rpx;
	box-sizing: border-box;
	overflow: hidden;
}

.am-quick-entry--primary {
	background: linear-gradient(135deg, #2f6bff 0%, #4f8cff 100%);
}

.am-quick-entry--warm {
	background: linear-gradient(135deg, #fff7ed 0%, #ffedd5 100%);
}

.am-quick-entry--success {
	background: linear-gradient(135deg, #ecfdf5 0%, #dcfce7 100%);
}

.am-quick-entry--soft {
	background: linear-gradient(135deg, #f8f7ff 0%, #f5f8ff 100%);
}

.am-quick-entry--compact {
	min-height: 96rpx;
	padding: 16rpx;
	justify-content: center;
}

.am-quick-entry__icon {
	width: 76rpx;
	height: 76rpx;
	border-radius: 50%;
	background: rgba(37, 99, 235, 0.12);
	display: flex;
	align-items: center;
	justify-content: center;
	flex-shrink: 0;
}

.am-quick-entry--primary .am-quick-entry__icon {
	background: rgba(255, 255, 255, 0.92);
}

.am-quick-entry--compact .am-quick-entry__icon {
	width: 62rpx;
	height: 62rpx;
	border-radius: 18rpx;
}

.am-quick-entry__icon-text {
	font-size: 34rpx;
	color: var(--am-color-primary);
	line-height: 1;
}

.am-quick-entry--primary .am-quick-entry__icon-text {
	color: var(--am-color-primary);
}

.am-quick-entry__main {
	flex: 1;
	min-width: 0;
	display: flex;
	flex-direction: column;
}

.am-quick-entry__title {
	font-size: 28rpx;
	color: var(--am-color-text-title);
	font-weight: 800;
	line-height: 38rpx;
	overflow: hidden;
	white-space: nowrap;
	text-overflow: ellipsis;
}

.am-quick-entry__desc {
	margin-top: 6rpx;
	font-size: 21rpx;
	color: var(--am-color-text-secondary);
	line-height: 30rpx;
	display: -webkit-box;
	-webkit-line-clamp: 2;
	-webkit-box-orient: vertical;
	overflow: hidden;
}

.am-quick-entry--primary .am-quick-entry__title,
.am-quick-entry--primary .am-quick-entry__desc,
.am-quick-entry--primary .am-quick-entry__arrow {
	color: #ffffff;
}

.am-quick-entry--primary .am-quick-entry__desc {
	opacity: 0.88;
}

.am-quick-entry__arrow {
	font-size: 38rpx;
	color: var(--am-color-primary);
	line-height: 1;
	flex-shrink: 0;
}

.am-quick-entry__badge {
	position: absolute;
	right: 18rpx;
	top: 16rpx;
	min-width: 30rpx;
	height: 30rpx;
	padding: 0 8rpx;
	border-radius: 999rpx;
	background: var(--am-color-danger);
	color: #ffffff;
	font-size: 18rpx;
	line-height: 30rpx;
	text-align: center;
	box-sizing: border-box;
}

.am-performance-light .am-quick-entry {
	box-shadow: none;
}
</style>
