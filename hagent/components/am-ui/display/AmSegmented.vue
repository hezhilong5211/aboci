<template>
	<view class="am-segmented">
		<view
			class="am-segmented__item"
			:class="{ 'am-segmented__item--active': modelValue === item.value }"
			v-for="item in options"
			:key="item.value"
			hover-class="am-tap-feedback"
			:hover-stay-time="70"
			@tap="select(item)"
		>
			<text class="am-segmented__text">{{ item.label }}</text>
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
		type: [String, Number],
		default: '',
	},
	options: {
		type: Array,
		default: () => [],
	},
})

const emit = defineEmits(['update:modelValue', 'change'])

function select(item) {
	emit('update:modelValue', item.value)
	emit('change', item.value)
}
</script>

<style lang="scss" scoped>
.am-segmented {
	display: flex;
	align-items: center;
	padding: 6rpx;
	border-radius: 18rpx;
	background: #f1f5fb;
}

.am-segmented__item {
	flex: 1;
	height: 56rpx;
	border-radius: 14rpx;
	display: flex;
	align-items: center;
	justify-content: center;
}

.am-segmented__item--active {
	background: #2f6bff;
	box-shadow: 0 6rpx 16rpx rgba(47, 107, 255, 0.18);
}

.am-segmented__text {
	font-size: 24rpx;
	color: #64748b;
	line-height: 1;
}

.am-segmented__item--active .am-segmented__text {
	color: #ffffff;
	font-weight: 600;
}

.am-performance-light .am-segmented__item--active {
	box-shadow: none;
}
</style>
