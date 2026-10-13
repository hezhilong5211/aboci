<template>
	<view class="am-topic-selector">
		<view class="am-topic-selector__search">
			<text class="am-topic-selector__search-icon">⌕</text>
			<input class="am-topic-selector__input" :value="keyword" placeholder="搜索话题" @input="emit('update:keyword', $event.detail.value)" />
		</view>
		<view class="am-topic-selector__list">
			<view
				class="am-topic-selector__item"
				v-for="item in topics"
				:key="item.value || item.title || item.label"
				@tap="emit('select', item)"
			>
				<image class="am-topic-selector__cover" v-if="item.cover" :src="item.cover" mode="aspectFill" />
				<view class="am-topic-selector__main">
					<text class="am-topic-selector__title"># {{ item.label || item.title }}</text>
					<text class="am-topic-selector__meta">{{ item.count ? `${item.count} 帖子` : item.desc }}</text>
				</view>
				<text class="am-topic-selector__check" v-if="selectedValues.includes(item.value || item.title || item.label)">✓</text>
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


defineProps({
	keyword: {
		type: String,
		default: '',
	},
	topics: {
		type: Array,
		default: () => [],
	},
	selectedValues: {
		type: Array,
		default: () => [],
	},
})

const emit = defineEmits(['update:keyword', 'select'])
</script>

<style lang="scss" scoped>
.am-topic-selector__search {
	height: 64rpx;
	padding: 0 18rpx;
	border-radius: 16rpx;
	background: var(--am-color-bg-muted);
	display: flex;
	align-items: center;
	gap: 12rpx;
	box-sizing: border-box;
}

.am-topic-selector__search-icon {
	font-size: 24rpx;
	color: var(--am-color-text-tertiary);
}

.am-topic-selector__input {
	flex: 1;
	height: 100%;
	font-size: 24rpx;
	color: var(--am-color-text-title);
}

.am-topic-selector__list {
	margin-top: 18rpx;
}

.am-topic-selector__item {
	display: flex;
	align-items: center;
	gap: 16rpx;
	padding: 14rpx 0;
}

.am-topic-selector__cover {
	width: 64rpx;
	height: 64rpx;
	border-radius: 12rpx;
	background: var(--am-color-bg-muted);
}

.am-topic-selector__main {
	flex: 1;
	min-width: 0;
}

.am-topic-selector__title,
.am-topic-selector__meta {
	display: block;
}

.am-topic-selector__title {
	font-size: 24rpx;
	color: var(--am-color-text-title);
	font-weight: 700;
	line-height: 34rpx;
}

.am-topic-selector__meta {
	font-size: 20rpx;
	color: var(--am-color-text-tertiary);
	line-height: 28rpx;
}

.am-topic-selector__check {
	font-size: 28rpx;
	color: var(--am-color-primary);
	font-weight: 700;
}
</style>
