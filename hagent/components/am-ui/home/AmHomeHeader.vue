<template>
	<view class="am-home-header" :class="[`am-home-header--${variant}`, { 'am-home-header--image': !!backgroundImage }]" :style="headerStyle">
		<image class="am-home-header__bg-image" v-if="backgroundImage" :src="backgroundImage" mode="aspectFill" />
		<view class="am-home-header__mask" v-if="backgroundImage" />

		<view class="am-home-header__top">
			<view class="am-home-header__main">
				<text class="am-home-header__title">{{ title }}</text>
				<text class="am-home-header__subtitle" v-if="subtitle">{{ subtitle }}</text>
			</view>
			<view class="am-home-header__actions" v-if="actions.length || avatar">
				<view
					v-for="item in actions"
					:key="item.key || item.icon || item.text"
					class="am-home-header__action"
					:class="{ 'am-home-header__action--button': item.text }"
					@tap="emit('action', item)"
				>
					<text class="am-home-header__action-icon" v-if="item.icon">{{ item.icon }}</text>
					<text class="am-home-header__action-text" v-if="item.text">{{ item.text }}</text>
					<text class="am-home-header__badge" v-if="item.badge">{{ item.badge }}</text>
				</view>
				<image class="am-home-header__avatar" v-if="avatar" :src="avatar" mode="aspectFill" @tap="emit('avatar')" />
			</view>
		</view>

		<view class="am-home-header__search" v-if="showSearch" @tap="emit('search')">
			<text class="am-home-header__search-icon">{{ searchIcon }}</text>
			<text class="am-home-header__search-placeholder">{{ searchPlaceholder }}</text>
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
	variant: {
		type: String,
		default: 'plain',
	},
	title: {
		type: String,
		default: 'Aurora',
	},
	subtitle: {
		type: String,
		default: '',
	},
	searchPlaceholder: {
		type: String,
		default: '搜索内容 / 话题 / 用户',
	},
	searchIcon: {
		type: String,
		default: '⌕',
	},
	showSearch: {
		type: Boolean,
		default: true,
	},
	actions: {
		type: Array,
		default: () => [],
	},
	avatar: {
		type: String,
		default: '',
	},
	safeArea: {
		type: Boolean,
		default: true,
	},
	background: {
		type: String,
		default: '',
	},
	backgroundImage: {
		type: String,
		default: '',
	},
})

const emit = defineEmits(['search', 'action', 'avatar'])
const statusBarHeight = ref(44)

uni.getSystemInfo({
	success: (info) => {
		statusBarHeight.value = info.statusBarHeight || 44
	},
})

const headerStyle = computed(() => {
	const background = props.background || (props.variant === 'gradient' ? 'linear-gradient(135deg, #dbeafe 0%, #eff6ff 100%)' : '#ffffff')
	return {
		paddingTop: props.safeArea ? `${statusBarHeight.value}px` : '0',
		background,
	}
})
</script>

<style lang="scss" scoped>
.am-home-header {
	position: relative;
	overflow: hidden;
	box-sizing: border-box;
	padding-left: 28rpx;
	padding-right: 28rpx;
	padding-bottom: 24rpx;
	border-radius: 28rpx;
	box-shadow: var(--am-shadow-card);
}

.am-home-header--image {
	background: #1f2937;
}

.am-home-header__bg-image,
.am-home-header__mask {
	position: absolute;
	left: 0;
	right: 0;
	top: 0;
	bottom: 0;
	width: 100%;
	height: 100%;
}

.am-home-header__mask {
	background: linear-gradient(180deg, rgba(15, 23, 42, 0.2) 0%, rgba(15, 23, 42, 0.58) 100%);
}

.am-home-header__top,
.am-home-header__search {
	position: relative;
	z-index: 1;
}

.am-home-header__top {
	min-height: 88rpx;
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 22rpx;
}

.am-home-header__main {
	flex: 1;
	min-width: 0;
	display: flex;
	flex-direction: column;
}

.am-home-header__title {
	font-size: 38rpx;
	color: var(--am-color-text-title);
	font-weight: 800;
	line-height: 50rpx;
	overflow: hidden;
	white-space: nowrap;
	text-overflow: ellipsis;
}

.am-home-header__subtitle {
	margin-top: 4rpx;
	font-size: 22rpx;
	color: var(--am-color-text-secondary);
	line-height: 32rpx;
	overflow: hidden;
	white-space: nowrap;
	text-overflow: ellipsis;
}

.am-home-header--image .am-home-header__title,
.am-home-header--image .am-home-header__subtitle {
	color: #ffffff;
}

.am-home-header--image .am-home-header__subtitle {
	opacity: 0.88;
}

.am-home-header__actions {
	display: flex;
	align-items: center;
	gap: 14rpx;
	flex-shrink: 0;
}

.am-home-header__action {
	position: relative;
	min-width: 62rpx;
	height: 62rpx;
	padding: 0 16rpx;
	border-radius: 999rpx;
	background: rgba(255, 255, 255, 0.88);
	border: 1rpx solid rgba(226, 232, 240, 0.92);
	display: flex;
	align-items: center;
	justify-content: center;
	box-sizing: border-box;
}

.am-home-header__action--button {
	gap: 6rpx;
	border-color: rgba(37, 99, 235, 0.32);
}

.am-home-header__action-icon {
	font-size: 28rpx;
	color: var(--am-color-text-title);
	line-height: 1;
}

.am-home-header__action-text {
	font-size: 22rpx;
	color: var(--am-color-primary);
	font-weight: 700;
	line-height: 30rpx;
}

.am-home-header__badge {
	position: absolute;
	right: -6rpx;
	top: -8rpx;
	min-width: 28rpx;
	height: 28rpx;
	padding: 0 7rpx;
	border-radius: 999rpx;
	background: var(--am-color-danger);
	color: #ffffff;
	font-size: 18rpx;
	line-height: 28rpx;
	text-align: center;
	box-sizing: border-box;
}

.am-home-header__avatar {
	width: 62rpx;
	height: 62rpx;
	border-radius: 50%;
	background: var(--am-color-bg-muted);
}

.am-home-header__search {
	margin-top: 16rpx;
	height: 72rpx;
	padding: 0 24rpx;
	border-radius: 18rpx;
	background: rgba(248, 250, 252, 0.94);
	display: flex;
	align-items: center;
	gap: 14rpx;
	box-sizing: border-box;
	border: 1rpx solid rgba(226, 232, 240, 0.72);
}

.am-home-header--gradient .am-home-header__search,
.am-home-header--image .am-home-header__search {
	background: rgba(255, 255, 255, 0.88);
}

.am-home-header__search-icon {
	font-size: 28rpx;
	color: var(--am-color-text-tertiary);
	line-height: 1;
}

.am-home-header__search-placeholder {
	font-size: 24rpx;
	color: var(--am-color-text-tertiary);
	line-height: 34rpx;
}

.am-performance-light .am-home-header {
	box-shadow: none;
}
</style>
