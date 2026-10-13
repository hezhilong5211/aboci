<template>
	<view class="am-tabs">
		<view
			class="am-tabs__item"
			:class="{ 'am-tabs__item--active': modelValue === item.value }"
			v-for="item in options"
			:key="item.value"
			@tap="select(item)"
		>
			<text class="am-tabs__text">{{ item.label }}</text>
			<view class="am-tabs__line" v-if="modelValue === item.value" />
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
.am-tabs {
	display: flex;
	align-items: center;
	border-bottom: 1rpx solid #edf2fb;
}

.am-tabs__item {
	position: relative;
	flex: 1;
	height: 72rpx;
	display: flex;
	align-items: center;
	justify-content: center;
}

.am-tabs__text {
	font-size: 26rpx;
	color: #64748b;
	line-height: 1;
}

.am-tabs__item--active .am-tabs__text {
	color: #2f6bff;
	font-weight: 700;
}

.am-tabs__line {
	position: absolute;
	left: 50%;
	bottom: 0;
	width: 42rpx;
	height: 4rpx;
	border-radius: 999rpx;
	background: #2f6bff;
	transform: translateX(-50%);
}
</style>
