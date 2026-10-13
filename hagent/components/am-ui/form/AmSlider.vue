<template>
	<view class="am-slider" :class="{ 'am-slider--disabled': disabled }">
		<view class="am-slider__header" v-if="label || showValue">
			<text class="am-slider__label" v-if="label">{{ label }}</text>
			<text class="am-slider__value" v-if="showValue">{{ modelValue }}{{ unit }}</text>
		</view>
		<slider
			class="am-slider__control"
			:value="modelValue"
			:min="min"
			:max="max"
			:step="step"
			:disabled="disabled"
			activeColor="#2f6bff"
			backgroundColor="#d9e1ec"
			block-color="#ffffff"
			:block-size="22"
			@changing="handleChange"
			@change="handleChange"
		/>
		<view class="am-slider__marks" v-if="showMarks">
			<text class="am-slider__mark">{{ min }}{{ unit }}</text>
			<text class="am-slider__mark">{{ max }}{{ unit }}</text>
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
		default: 100,
	},
	step: {
		type: Number,
		default: 1,
	},
	label: {
		type: String,
		default: '',
	},
	unit: {
		type: String,
		default: '',
	},
	showValue: {
		type: Boolean,
		default: true,
	},
	showMarks: {
		type: Boolean,
		default: true,
	},
	disabled: {
		type: Boolean,
		default: false,
	},
})

const emit = defineEmits(['update:modelValue', 'change'])

function handleChange(event) {
	if (props.disabled) return
	const value = Number(event.detail.value)
	emit('update:modelValue', value)
	emit('change', value)
}
</script>

<style lang="scss" scoped>
.am-slider {
	width: 100%;
}

.am-slider--disabled {
	opacity: 0.48;
}

.am-slider__header {
	display: flex;
	align-items: center;
	justify-content: space-between;
	margin-bottom: 6rpx;
}

.am-slider__label {
	font-size: 26rpx;
	color: var(--am-color-text-body);
	line-height: 36rpx;
}

.am-slider__value {
	font-size: 24rpx;
	color: var(--am-color-primary);
	font-weight: 600;
	line-height: 34rpx;
}

.am-slider__control {
	margin: 0;
}

.am-slider__marks {
	display: flex;
	align-items: center;
	justify-content: space-between;
	margin-top: 4rpx;
}

.am-slider__mark {
	font-size: 20rpx;
	color: var(--am-color-text-tertiary);
	line-height: 28rpx;
}
</style>
