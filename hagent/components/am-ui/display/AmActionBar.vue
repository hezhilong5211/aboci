<template>
	<view class="am-action-bar">
		<view
			class="am-action-bar__item"
			:class="{ 'am-action-bar__item--active': item.active }"
			v-for="item in actions"
			:key="item.key || item.label"
			@tap="emit('action', item)"
		>
			<text class="am-action-bar__icon">{{ item.icon }}</text>
			<text class="am-action-bar__label">{{ item.label }}</text>
			<text class="am-action-bar__count" v-if="item.count">{{ item.count }}</text>
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
	actions: {
		type: Array,
		default: () => [],
	},
})

const emit = defineEmits(['action'])
</script>

<style lang="scss" scoped>
.am-action-bar {
	display: flex;
	align-items: center;
	justify-content: space-around;
	gap: 10rpx;
	width: 100%;
}

.am-action-bar__item {
	flex: 1;
	min-width: 0;
	height: 62rpx;
	border-radius: 999rpx;
	display: flex;
	align-items: center;
	justify-content: center;
	gap: 8rpx;
}

.am-action-bar__item--active {
	background: var(--am-color-primary-weak);
}

.am-action-bar__icon,
.am-action-bar__label,
.am-action-bar__count {
	color: var(--am-color-text-secondary);
	line-height: 1;
}

.am-action-bar__icon {
	font-size: 28rpx;
}

.am-action-bar__label,
.am-action-bar__count {
	font-size: 22rpx;
}

.am-action-bar__item--active .am-action-bar__icon,
.am-action-bar__item--active .am-action-bar__label,
.am-action-bar__item--active .am-action-bar__count {
	color: var(--am-color-primary);
	font-weight: 600;
}
</style>
