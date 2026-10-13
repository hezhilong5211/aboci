<template>
	<view class="am-community-stats">
		<view class="am-community-stats__item" v-for="item in items" :key="item.label">
			<view class="am-community-stats__icon" :style="{ background: item.bg || '#eaf2ff' }">
				<text class="am-community-stats__icon-text">{{ item.icon }}</text>
			</view>
			<text class="am-community-stats__value">{{ item.value }}</text>
			<text class="am-community-stats__label">{{ item.label }}</text>
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
.am-community-stats {
	display: grid;
	grid-template-columns: repeat(2, 1fr);
	gap: 16rpx;
}

.am-community-stats__item {
	padding: 28rpx 18rpx;
	border-radius: 22rpx;
	background: #ffffff;
	box-shadow: var(--am-shadow-card);
	display: flex;
	flex-direction: column;
	align-items: center;
}

.am-community-stats__icon {
	width: 58rpx;
	height: 58rpx;
	border-radius: 50%;
	display: flex;
	align-items: center;
	justify-content: center;
}

.am-community-stats__icon-text {
	font-size: 28rpx;
	line-height: 1;
}

.am-community-stats__value {
	margin-top: 18rpx;
	font-size: 32rpx;
	color: var(--am-color-text-title);
	font-weight: 800;
	line-height: 42rpx;
}

.am-community-stats__label {
	margin-top: 4rpx;
	font-size: 22rpx;
	color: var(--am-color-text-secondary);
	line-height: 30rpx;
}
</style>
