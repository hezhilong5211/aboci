<template>
	<view class="am-input" :class="[`am-input--${status}`, { 'am-input--disabled': disabled }]">
		<input
			class="am-input__control"
			:type="password ? 'password' : type"
			:value="modelValue"
			:placeholder="placeholder"
			:disabled="disabled"
			:maxlength="maxlength"
			@input="handleInput"
			@focus="focused = true"
			@blur="focused = false"
		/>
		<view class="am-input__clear" v-if="clearable && modelValue && !disabled" @tap="emit('update:modelValue', '')">
			<text class="am-input__clear-text">×</text>
		</view>
	</view>
	<text class="am-input__error" v-if="errorText">{{ errorText }}</text>
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


import { computed, ref } from 'vue'

const props = defineProps({
	modelValue: {
		type: String,
		default: '',
	},
	placeholder: {
		type: String,
		default: '请输入内容',
	},
	type: {
		type: String,
		default: 'text',
	},
	password: {
		type: Boolean,
		default: false,
	},
	clearable: {
		type: Boolean,
		default: false,
	},
	disabled: {
		type: Boolean,
		default: false,
	},
	errorText: {
		type: String,
		default: '',
	},
	maxlength: {
		type: Number,
		default: -1,
	},
})

const emit = defineEmits(['update:modelValue', 'input'])
const focused = ref(false)

const status = computed(() => {
	if (props.errorText) return 'error'
	if (focused.value) return 'focus'
	return 'default'
})

function handleInput(event) {
	const value = event.detail.value
	emit('update:modelValue', value)
	emit('input', value)
}
</script>

<style lang="scss" scoped>
.am-input {
	box-sizing: border-box;
	height: 72rpx;
	border: 2rpx solid #d9e1ec;
	border-radius: 12rpx;
	background: #ffffff;
	display: flex;
	align-items: center;
	padding: 0 22rpx;
}

.am-input--focus {
	border-color: #2f6bff;
}

.am-input--error {
	border-color: #ff5a5f;
}

.am-input--disabled {
	background: #f5f6f8;
	border-color: #e5e7eb;
}

.am-input__control {
	flex: 1;
	height: 100%;
	font-size: 26rpx;
	color: #1f2a37;
}

.am-input__clear {
	width: 36rpx;
	height: 36rpx;
	border-radius: 50%;
	background: #a8b4c6;
	display: flex;
	align-items: center;
	justify-content: center;
	margin-left: 12rpx;
}

.am-input__clear-text {
	font-size: 28rpx;
	color: #ffffff;
	line-height: 1;
}

.am-input__error {
	display: block;
	margin-top: 10rpx;
	font-size: 22rpx;
	color: #ff5a5f;
	line-height: 30rpx;
}
</style>
