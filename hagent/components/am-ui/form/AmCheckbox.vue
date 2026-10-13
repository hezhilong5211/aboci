<template>
	<view class="am-checkbox" :class="{ 'am-checkbox--disabled': disabled }" @tap="handleTap">
		<view class="am-checkbox__box" :class="{ 'am-checkbox__box--checked': modelValue }">
			<text class="am-checkbox__mark" v-if="modelValue">✓</text>
		</view>
		<text class="am-checkbox__label" v-if="label">{{ label }}</text>
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
	modelValue: {
		type: Boolean,
		default: false,
	},
	label: {
		type: String,
		default: '',
	},
	disabled: {
		type: Boolean,
		default: false,
	},
})

const emit = defineEmits(['update:modelValue', 'change'])

function handleTap() {
	if (props.disabled) return
	const next = !props.modelValue
	emit('update:modelValue', next)
	emit('change', next)
}
</script>

<style lang="scss" scoped>
.am-checkbox {
	display: inline-flex;
	align-items: center;
	gap: 12rpx;
}

.am-checkbox--disabled {
	opacity: 0.45;
}

.am-checkbox__box {
	width: 32rpx;
	height: 32rpx;
	border: 2rpx solid #cbd5e1;
	border-radius: 6rpx;
	background: #ffffff;
	display: flex;
	align-items: center;
	justify-content: center;
	box-sizing: border-box;
}

.am-checkbox__box--checked {
	background: #2f6bff;
	border-color: #2f6bff;
}

.am-checkbox__mark {
	font-size: 22rpx;
	color: #ffffff;
	font-weight: 700;
	line-height: 1;
}

.am-checkbox__label {
	font-size: 26rpx;
	color: #334155;
	line-height: 36rpx;
}
</style>
