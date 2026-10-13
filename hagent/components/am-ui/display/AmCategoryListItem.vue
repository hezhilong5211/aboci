<template>
	<view class="am-category-list-item" @tap="emit('tap', category)">
		<image class="am-category-list-item__cover" :src="category.cover" mode="aspectFill" />
		<view class="am-category-list-item__main">
			<text class="am-category-list-item__title">{{ category.title }}</text>
			<text class="am-category-list-item__desc">{{ category.desc }}</text>
			<text class="am-category-list-item__meta">{{ category.posts }} 帖子 · {{ category.members }} 成员</text>
		</view>
		<view
			class="am-category-list-item__follow"
			:class="{ 'am-category-list-item__follow--active': category.followed }"
			@tap.stop="emit('follow', category)"
		>
			<text class="am-category-list-item__follow-text">{{ category.followed ? '已关注' : '关注' }}</text>
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
	category: {
		type: Object,
		default: () => ({}),
	},
})

const emit = defineEmits(['tap', 'follow'])
</script>

<style lang="scss" scoped>
.am-category-list-item {
	display: flex;
	align-items: center;
	gap: 18rpx;
	padding: 18rpx 0;
}

.am-category-list-item__cover {
	width: 82rpx;
	height: 82rpx;
	border-radius: 14rpx;
	background: var(--am-color-bg-muted);
	flex-shrink: 0;
}

.am-category-list-item__main {
	flex: 1;
	min-width: 0;
	display: flex;
	flex-direction: column;
}

.am-category-list-item__title {
	font-size: 26rpx;
	color: var(--am-color-text-title);
	font-weight: 700;
	line-height: 36rpx;
}

.am-category-list-item__desc,
.am-category-list-item__meta {
	margin-top: 2rpx;
	font-size: 22rpx;
	color: var(--am-color-text-secondary);
	line-height: 32rpx;
	white-space: nowrap;
	overflow: hidden;
	text-overflow: ellipsis;
}

.am-category-list-item__meta {
	color: var(--am-color-text-tertiary);
}

.am-category-list-item__follow {
	min-width: 76rpx;
	height: 48rpx;
	padding: 0 16rpx;
	border-radius: 12rpx;
	border: 1rpx solid rgba(47, 107, 255, 0.38);
	display: flex;
	align-items: center;
	justify-content: center;
	box-sizing: border-box;
	flex-shrink: 0;
}

.am-category-list-item__follow--active {
	border-color: var(--am-color-border-light);
	background: #f6f8fb;
}

.am-category-list-item__follow-text {
	font-size: 22rpx;
	color: var(--am-color-primary);
	line-height: 30rpx;
}

.am-category-list-item__follow--active .am-category-list-item__follow-text {
	color: var(--am-color-text-secondary);
}
</style>
