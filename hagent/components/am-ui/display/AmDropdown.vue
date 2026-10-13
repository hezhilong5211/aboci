<template>
	<view class="am-dropdown" :class="{ 'am-dropdown--open': opened, 'am-dropdown--disabled': disabled }">
		<view class="am-dropdown__trigger" @tap="toggle">
			<text class="am-dropdown__text" :class="{ 'am-dropdown__text--placeholder': !selectedLabel }">
				{{ selectedLabel || placeholder }}
			</text>
			<text class="am-dropdown__arrow">⌄</text>
		</view>
		<view class="am-dropdown__menu" v-if="opened">
			<view
				class="am-dropdown__option"
				:class="{ 'am-dropdown__option--active': item.value === modelValue }"
				v-for="item in normalizedOptions"
				:key="item.value"
				@tap.stop="select(item)"
			>
				<text class="am-dropdown__option-text">{{ item.label }}</text>
				<text class="am-dropdown__check" v-if="item.value === modelValue">✓</text>
			</view>
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


import { computed, ref } from 'vue'

const props = defineProps({
	modelValue: {
		type: [String, Number],
		default: '',
	},
	options: {
		type: Array,
		default: () => [],
	},
	placeholder: {
		type: String,
		default: '请选择',
	},
	disabled: {
		type: Boolean,
		default: false,
	},
})

const emit = defineEmits(['update:modelValue', 'change', 'open', 'close'])
const opened = ref(false)

const normalizedOptions = computed(() => props.options.map((item) => {
	if (typeof item === 'string' || typeof item === 'number') {
		return { label: String(item), value: item }
	}
	return item
}))

const selectedLabel = computed(() => {
	const item = normalizedOptions.value.find((option) => option.value === props.modelValue)
	return item ? item.label : ''
})

function toggle() {
	if (props.disabled) return
	opened.value = !opened.value
	emit(opened.value ? 'open' : 'close')
}

function select(item) {
	opened.value = false
	emit('update:modelValue', item.value)
	emit('change', item.value, item)
	emit('close')
}
</script>

<style lang="scss" scoped>
.am-dropdown {
	position: relative;
	width: 100%;
	z-index: 1;
}

.am-dropdown--open {
	z-index: 8;
}

.am-dropdown--disabled {
	opacity: 0.48;
}

.am-dropdown__trigger {
	box-sizing: border-box;
	height: 72rpx;
	padding: 0 22rpx;
	border: 2rpx solid var(--am-color-border);
	border-radius: var(--am-radius-control);
	background: var(--am-color-bg-surface);
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 18rpx;
}

.am-dropdown--open .am-dropdown__trigger {
	border-color: var(--am-color-primary);
}

.am-dropdown__text {
	flex: 1;
	min-width: 0;
	font-size: 26rpx;
	color: var(--am-color-text-title);
	line-height: 36rpx;
}

.am-dropdown__text--placeholder {
	color: var(--am-color-text-tertiary);
}

.am-dropdown__arrow {
	font-size: 30rpx;
	color: var(--am-color-text-tertiary);
	line-height: 1;
}

.am-dropdown__menu {
	position: absolute;
	top: 84rpx;
	left: 0;
	right: 0;
	padding: 10rpx;
	border-radius: 18rpx;
	background: #ffffff;
	box-shadow: 0 12rpx 34rpx rgba(80, 115, 170, 0.12);
}

.am-dropdown__option {
	min-height: 64rpx;
	padding: 0 16rpx;
	border-radius: 12rpx;
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 16rpx;
}

.am-dropdown__option--active {
	background: var(--am-color-primary-weak);
}

.am-dropdown__option-text {
	font-size: 24rpx;
	color: var(--am-color-text-body);
	line-height: 34rpx;
}

.am-dropdown__check {
	font-size: 24rpx;
	color: var(--am-color-primary);
	line-height: 1;
}

.am-performance-light .am-dropdown__menu {
	box-shadow: none;
	border: 1rpx solid var(--am-color-border-light);
}
</style>
