<template>
	<view class="am-filter-panel">
		<view class="am-filter-panel__search" v-if="showSearch">
			<input
				class="am-filter-panel__input"
				:value="keyword"
				:placeholder="placeholder"
				@input="handleKeywordInput"
				@confirm="emit('search', keyword)"
			/>
			<text class="am-filter-panel__search-icon">⌕</text>
		</view>
		<view class="am-filter-panel__group" v-for="group in groups" :key="group.key">
			<text class="am-filter-panel__title">{{ group.title }}</text>
			<view class="am-filter-panel__options">
				<view
					class="am-filter-panel__option"
					:class="{ 'am-filter-panel__option--active': isActive(group.key, item.value) }"
					v-for="item in group.options"
					:key="item.value"
					@tap="toggle(group, item)"
				>
					<text class="am-filter-panel__option-text">{{ item.label }}</text>
				</view>
			</view>
		</view>
		<view class="am-filter-panel__footer">
			<AmButton text="重置" variant="secondary" @tap="reset" />
			<AmButton text="确定" @tap="emit('confirm', modelValue)" />
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


import AmButton from '../base/AmButton.vue'

const props = defineProps({
	modelValue: {
		type: Object,
		default: () => ({}),
	},
	keyword: {
		type: String,
		default: '',
	},
	groups: {
		type: Array,
		default: () => [],
	},
	placeholder: {
		type: String,
		default: '搜索关键词',
	},
	showSearch: {
		type: Boolean,
		default: true,
	},
})

const emit = defineEmits(['update:modelValue', 'update:keyword', 'confirm', 'reset', 'search'])

function handleKeywordInput(event) {
	emit('update:keyword', event.detail.value)
}

function isActive(key, value) {
	const current = props.modelValue[key]
	return Array.isArray(current) ? current.includes(value) : current === value
}

function toggle(group, item) {
	const next = { ...props.modelValue }
	if (group.multiple) {
		const current = Array.isArray(next[group.key]) ? next[group.key].slice() : []
		const index = current.indexOf(item.value)
		if (index >= 0) {
			current.splice(index, 1)
		} else {
			current.push(item.value)
		}
		next[group.key] = current
	} else {
		next[group.key] = next[group.key] === item.value ? '' : item.value
	}
	emit('update:modelValue', next)
}

function reset() {
	emit('update:modelValue', {})
	emit('update:keyword', '')
	emit('reset')
}
</script>

<style lang="scss" scoped>
.am-filter-panel {
	width: 100%;
}

.am-filter-panel__search {
	position: relative;
	height: 72rpx;
	border-radius: var(--am-radius-control);
	background: var(--am-color-bg-muted);
	display: flex;
	align-items: center;
	padding: 0 22rpx 0 64rpx;
	box-sizing: border-box;
}

.am-filter-panel__input {
	flex: 1;
	height: 100%;
	font-size: 26rpx;
	color: var(--am-color-text-title);
}

.am-filter-panel__search-icon {
	position: absolute;
	left: 24rpx;
	top: 50%;
	transform: translateY(-50%);
	font-size: 28rpx;
	color: var(--am-color-text-tertiary);
	line-height: 1;
}

.am-filter-panel__group {
	margin-top: 26rpx;
}

.am-filter-panel__title {
	display: block;
	font-size: 24rpx;
	color: var(--am-color-text-title);
	font-weight: 700;
	line-height: 34rpx;
}

.am-filter-panel__options {
	display: flex;
	flex-wrap: wrap;
	gap: 14rpx;
	margin-top: 14rpx;
}

.am-filter-panel__option {
	min-width: 128rpx;
	height: 58rpx;
	padding: 0 22rpx;
	border-radius: 999rpx;
	background: var(--am-color-bg-muted);
	display: flex;
	align-items: center;
	justify-content: center;
	box-sizing: border-box;
}

.am-filter-panel__option--active {
	background: var(--am-color-primary);
}

.am-filter-panel__option-text {
	font-size: 24rpx;
	color: var(--am-color-text-secondary);
	line-height: 34rpx;
}

.am-filter-panel__option--active .am-filter-panel__option-text {
	color: #ffffff;
	font-weight: 600;
}

.am-filter-panel__footer {
	display: flex;
	align-items: center;
	justify-content: flex-end;
	gap: 18rpx;
	margin-top: 30rpx;
}
</style>
