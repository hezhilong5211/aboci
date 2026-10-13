<template>
	<view class="am-search-nav-bar" :style="navStyle">
		<view class="am-search-nav-bar__inner">
			<view class="am-search-nav-bar__back" v-if="showBack" @tap="emit('back')">
				<text class="am-search-nav-bar__back-text">‹</text>
			</view>
			<view class="am-search-nav-bar__search" @tap="emit('searchTap')">
				<text class="am-search-nav-bar__icon">⌕</text>
				<input
					class="am-search-nav-bar__input"
					:value="modelValue"
					:placeholder="placeholder"
					:disabled="readonly"
					@input="handleInput"
					@confirm="emit('search', modelValue)"
				/>
			</view>
			<view class="am-search-nav-bar__right" v-if="rightText" @tap="emit('right')">
				<text class="am-search-nav-bar__right-text">{{ rightText }}</text>
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
		type: String,
		default: '',
	},
	placeholder: {
		type: String,
		default: '搜索',
	},
	rightText: {
		type: String,
		default: '',
	},
	showBack: {
		type: Boolean,
		default: true,
	},
	readonly: {
		type: Boolean,
		default: false,
	},
	safeArea: {
		type: Boolean,
		default: true,
	},
	background: {
		type: String,
		default: '#ffffff',
	},
})

const emit = defineEmits(['update:modelValue', 'back', 'right', 'search', 'searchTap'])
const statusBarHeight = ref(44)

uni.getSystemInfo({
	success: (info) => {
		statusBarHeight.value = info.statusBarHeight || 44
	},
})

const navStyle = computed(() => ({
	paddingTop: props.safeArea ? `${statusBarHeight.value}px` : '0',
	background: props.background,
}))

function handleInput(event) {
	emit('update:modelValue', event.detail.value)
}
</script>

<style lang="scss" scoped>
.am-search-nav-bar {
	box-sizing: border-box;
	width: 100%;
	border-bottom: 1rpx solid var(--am-color-border-light);
}

.am-search-nav-bar__inner {
	height: 88rpx;
	padding: 0 24rpx;
	display: flex;
	align-items: center;
	gap: 18rpx;
	box-sizing: border-box;
}

.am-search-nav-bar__back {
	width: 54rpx;
	height: 72rpx;
	display: flex;
	align-items: center;
	justify-content: center;
	flex-shrink: 0;
}

.am-search-nav-bar__back-text {
	font-size: 56rpx;
	color: var(--am-color-text-title);
	line-height: 1;
}

.am-search-nav-bar__search {
	flex: 1;
	min-width: 0;
	height: 66rpx;
	padding: 0 22rpx;
	border-radius: 999rpx;
	background: var(--am-color-bg-muted);
	display: flex;
	align-items: center;
	gap: 12rpx;
	box-sizing: border-box;
}

.am-search-nav-bar__icon {
	font-size: 26rpx;
	color: var(--am-color-text-tertiary);
	line-height: 1;
}

.am-search-nav-bar__input {
	flex: 1;
	height: 100%;
	font-size: 24rpx;
	color: var(--am-color-text-title);
}

.am-search-nav-bar__right {
	min-width: 54rpx;
	height: 72rpx;
	display: flex;
	align-items: center;
	justify-content: flex-end;
}

.am-search-nav-bar__right-text {
	font-size: 24rpx;
	color: var(--am-color-primary);
	font-weight: 600;
	line-height: 34rpx;
}
</style>
