<template>
	<view class="am-composer-toolbar">
		<view
			class="am-composer-toolbar__item"
			:class="{ 'am-composer-toolbar__item--disabled': item.disabled }"
			v-for="item in actions"
			:key="item.key || item.label"
			@tap="handleTap(item)"
		>
			<text class="am-composer-toolbar__icon">{{ item.icon }}</text>
			<text class="am-composer-toolbar__label">{{ item.label }}</text>
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

function handleTap(item) {
	if (item.disabled) return
	emit('action', item)
}
</script>

<style lang="scss" scoped>
.am-composer-toolbar {
	display: grid;
	grid-template-columns: repeat(4, minmax(0, 1fr));
	gap: 18rpx;
}

.am-composer-toolbar__item {
	height: 70rpx;
	border-radius: 16rpx;
	background: #f7f8fa;
	display: flex;
	align-items: center;
	justify-content: center;
	gap: 10rpx;
	transition: opacity 0.15s ease;
}

.am-composer-toolbar__item:active {
	opacity: 0.82;
}

.am-composer-toolbar__item--disabled {
	opacity: 0.46;
}

.am-composer-toolbar__icon {
	font-size: 28rpx;
	color: var(--am-color-text-body);
	line-height: 1;
}

.am-composer-toolbar__label {
	font-size: 26rpx;
	color: var(--am-color-text-title);
	line-height: 34rpx;
	white-space: nowrap;
}

.am-performance-light .am-composer-toolbar__item {
	transition: none;
}
</style>
