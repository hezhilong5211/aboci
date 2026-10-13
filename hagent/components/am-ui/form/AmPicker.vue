<template>
	<view class="am-picker" :class="[`am-picker--${status}`, { 'am-picker--disabled': disabled }]">
		<picker
			:range="labels"
			:value="currentIndex"
			:disabled="disabled"
			@change="handleChange"
		>
			<view class="am-picker__trigger">
				<view class="am-picker__main">
					<text class="am-picker__title" v-if="title">{{ title }}</text>
					<text class="am-picker__value" :class="{ 'am-picker__value--placeholder': !selectedLabel }">
						{{ selectedLabel || placeholder }}
					</text>
				</view>
				<text class="am-picker__arrow">⌄</text>
			</view>
		</picker>
		<text class="am-picker__error" v-if="errorText">{{ errorText }}</text>
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
		type: [String, Number],
		default: '',
	},
	options: {
		type: Array,
		default: () => [],
	},
	title: {
		type: String,
		default: '',
	},
	placeholder: {
		type: String,
		default: '请选择',
	},
	disabled: {
		type: Boolean,
		default: false,
	},
	errorText: {
		type: String,
		default: '',
	},
})

const emit = defineEmits(['update:modelValue', 'change'])

const normalizedOptions = computed(() => props.options.map((item) => {
	if (typeof item === 'string' || typeof item === 'number') {
		return { label: String(item), value: item }
	}
	return item
}))

const labels = computed(() => normalizedOptions.value.map((item) => item.label))
const currentIndex = computed(() => {
	const index = normalizedOptions.value.findIndex((item) => item.value === props.modelValue)
	return index >= 0 ? index : 0
})
const selectedLabel = computed(() => {
	const item = normalizedOptions.value.find((option) => option.value === props.modelValue)
	return item ? item.label : ''
})
const status = computed(() => props.errorText ? 'error' : 'default')

function handleChange(event) {
	if (props.disabled) return
	const item = normalizedOptions.value[Number(event.detail.value)]
	if (!item) return
	emit('update:modelValue', item.value)
	emit('change', item.value, item)
}
</script>

<style lang="scss" scoped>
.am-picker {
	width: 100%;
}

.am-picker__trigger {
	box-sizing: border-box;
	min-height: 72rpx;
	padding: 0 22rpx;
	border: 2rpx solid var(--am-color-border);
	border-radius: var(--am-radius-control);
	background: var(--am-color-bg-surface);
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 18rpx;
}

.am-picker--error .am-picker__trigger {
	border-color: var(--am-color-danger);
}

.am-picker--disabled {
	opacity: 0.48;
}

.am-picker__main {
	flex: 1;
	min-width: 0;
	display: flex;
	flex-direction: column;
	justify-content: center;
}

.am-picker__title {
	font-size: 20rpx;
	color: var(--am-color-text-tertiary);
	line-height: 28rpx;
}

.am-picker__value {
	font-size: 26rpx;
	color: var(--am-color-text-title);
	line-height: 36rpx;
}

.am-picker__value--placeholder {
	color: var(--am-color-text-tertiary);
}

.am-picker__arrow {
	font-size: 30rpx;
	color: var(--am-color-text-tertiary);
	line-height: 1;
}

.am-picker__error {
	display: block;
	margin-top: 10rpx;
	font-size: 22rpx;
	color: var(--am-color-danger);
	line-height: 30rpx;
}
</style>
