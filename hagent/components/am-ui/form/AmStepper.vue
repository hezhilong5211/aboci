<template>
	<view class="am-stepper" :class="{ 'am-stepper--disabled': disabled }">
		<view class="am-stepper__btn" :class="{ 'am-stepper__btn--disabled': minusDisabled }" @tap="minus">
			<text class="am-stepper__symbol">−</text>
		</view>
		<text class="am-stepper__value">{{ modelValue }}</text>
		<view class="am-stepper__btn" :class="{ 'am-stepper__btn--disabled': plusDisabled }" @tap="plus">
			<text class="am-stepper__symbol">＋</text>
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


import { computed } from 'vue'

const props = defineProps({
	modelValue: {
		type: Number,
		default: 0,
	},
	min: {
		type: Number,
		default: 0,
	},
	max: {
		type: Number,
		default: 999,
	},
	step: {
		type: Number,
		default: 1,
	},
	disabled: {
		type: Boolean,
		default: false,
	},
})

const emit = defineEmits(['update:modelValue', 'change'])
const minusDisabled = computed(() => props.disabled || props.modelValue <= props.min)
const plusDisabled = computed(() => props.disabled || props.modelValue >= props.max)

function setValue(value) {
	const next = Math.max(props.min, Math.min(props.max, value))
	emit('update:modelValue', next)
	emit('change', next)
}

function minus() {
	if (minusDisabled.value) return
	setValue(props.modelValue - props.step)
}

function plus() {
	if (plusDisabled.value) return
	setValue(props.modelValue + props.step)
}
</script>

<style lang="scss" scoped>
.am-stepper {
	display: inline-flex;
	align-items: center;
	height: 64rpx;
	border: 1rpx solid #d9e1ec;
	border-radius: 14rpx;
	overflow: hidden;
	background: #ffffff;
}

.am-stepper--disabled {
	opacity: 0.48;
}

.am-stepper__btn {
	width: 64rpx;
	height: 64rpx;
	display: flex;
	align-items: center;
	justify-content: center;
	background: #f8fafc;
}

.am-stepper__btn--disabled {
	opacity: 0.42;
}

.am-stepper__symbol {
	font-size: 28rpx;
	color: #334155;
	line-height: 1;
}

.am-stepper__value {
	min-width: 76rpx;
	text-align: center;
	font-size: 26rpx;
	color: #1f2a37;
	line-height: 64rpx;
	border-left: 1rpx solid #d9e1ec;
	border-right: 1rpx solid #d9e1ec;
}
</style>
