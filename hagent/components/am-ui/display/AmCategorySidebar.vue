<template>
	<view class="am-category-sidebar">
		<view
			class="am-category-sidebar__item"
			:class="{ 'am-category-sidebar__item--active': modelValue === item.value }"
			v-for="item in items"
			:key="item.value"
			@tap="selectItem(item)"
		>
			<view class="am-category-sidebar__icon">
				<text class="am-category-sidebar__icon-text">{{ item.icon }}</text>
			</view>
			<text class="am-category-sidebar__label">{{ item.label }}</text>
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
	modelValue: {
		type: String,
		default: '',
	},
	items: {
		type: Array,
		default: () => [],
	},
})

const emit = defineEmits(['update:modelValue', 'change'])

function selectItem(item) {
	emit('update:modelValue', item.value)
	emit('change', item)
}
</script>

<style lang="scss" scoped>
.am-category-sidebar {
	width: 96rpx;
	height: 100%;
	background: #ffffff;
	border-right: 1rpx solid var(--am-color-border-light);
	display: flex;
	flex-direction: column;
	align-items: center;
	padding-top: 16rpx;
	box-sizing: border-box;
}

.am-category-sidebar__item {
	width: 100%;
	min-height: 96rpx;
	display: flex;
	flex-direction: column;
	align-items: center;
	justify-content: center;
	gap: 8rpx;
	position: relative;
}

.am-category-sidebar__item--active::before {
	content: '';
	position: absolute;
	left: 0;
	top: 24rpx;
	bottom: 24rpx;
	width: 6rpx;
	border-radius: 0 999rpx 999rpx 0;
	background: var(--am-color-primary);
}

.am-category-sidebar__icon {
	width: 38rpx;
	height: 38rpx;
	border-radius: 12rpx;
	display: flex;
	align-items: center;
	justify-content: center;
}

.am-category-sidebar__item--active .am-category-sidebar__icon {
	background: var(--am-color-primary);
}

.am-category-sidebar__icon-text {
	font-size: 28rpx;
	color: var(--am-color-text-title);
	line-height: 1;
}

.am-category-sidebar__item--active .am-category-sidebar__icon-text {
	color: #ffffff;
}

.am-category-sidebar__label {
	font-size: 20rpx;
	color: var(--am-color-text-secondary);
	line-height: 28rpx;
}

.am-category-sidebar__item--active .am-category-sidebar__label {
	color: var(--am-color-primary);
	font-weight: 700;
}
</style>
