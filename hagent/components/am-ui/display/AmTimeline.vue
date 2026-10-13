<template>
	<view class="am-timeline">
		<view
			class="am-timeline__item"
			:class="{ 'am-timeline__item--active': item.active, 'am-timeline__item--last': index === items.length - 1 }"
			v-for="(item, index) in items"
			:key="item.title || index"
		>
			<view class="am-timeline__axis">
				<view class="am-timeline__dot" />
				<view class="am-timeline__line" v-if="index !== items.length - 1" />
			</view>
			<view class="am-timeline__content">
				<view class="am-timeline__header">
					<text class="am-timeline__title">{{ item.title }}</text>
					<text class="am-timeline__time" v-if="item.time">{{ item.time }}</text>
				</view>
				<text class="am-timeline__desc" v-if="item.desc">{{ item.desc }}</text>
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
	items: {
		type: Array,
		default: () => [],
	},
})
</script>

<style lang="scss" scoped>
.am-timeline {
	width: 100%;
}

.am-timeline__item {
	display: flex;
	align-items: stretch;
	gap: 18rpx;
}

.am-timeline__axis {
	width: 28rpx;
	display: flex;
	flex-direction: column;
	align-items: center;
	padding-top: 8rpx;
}

.am-timeline__dot {
	width: 18rpx;
	height: 18rpx;
	border-radius: 50%;
	background: var(--am-color-border);
	border: 4rpx solid #ffffff;
	box-shadow: 0 0 0 2rpx var(--am-color-border);
	box-sizing: border-box;
}

.am-timeline__line {
	width: 2rpx;
	flex: 1;
	min-height: 68rpx;
	background: var(--am-color-border-light);
	margin-top: 8rpx;
}

.am-timeline__item--active .am-timeline__dot {
	background: var(--am-color-primary);
	box-shadow: 0 0 0 2rpx var(--am-color-primary);
}

.am-timeline__content {
	flex: 1;
	min-width: 0;
	padding-bottom: 28rpx;
}

.am-timeline__item--last .am-timeline__content {
	padding-bottom: 0;
}

.am-timeline__header {
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 18rpx;
}

.am-timeline__title {
	flex: 1;
	min-width: 0;
	font-size: 26rpx;
	color: var(--am-color-text-title);
	font-weight: 600;
	line-height: 36rpx;
}

.am-timeline__time {
	font-size: 20rpx;
	color: var(--am-color-text-tertiary);
	line-height: 28rpx;
}

.am-timeline__desc {
	display: block;
	margin-top: 6rpx;
	font-size: 22rpx;
	color: var(--am-color-text-secondary);
	line-height: 32rpx;
}

.am-performance-light .am-timeline__dot {
	box-shadow: none;
}
</style>
