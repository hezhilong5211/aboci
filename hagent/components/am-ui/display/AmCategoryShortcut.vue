<template>
	<view class="am-category-shortcut">
		<view class="am-category-shortcut__item" v-for="item in items" :key="item.value || item.label" @tap="emit('tap', item)">
			<image class="am-category-shortcut__cover" v-if="item.cover" :src="item.cover" mode="aspectFill" />
			<view class="am-category-shortcut__icon" v-else :style="{ background: item.background || '#eef3ff' }">
				<text class="am-category-shortcut__icon-text">{{ item.icon }}</text>
			</view>
			<text class="am-category-shortcut__label">{{ item.label }}</text>
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
	items: {
		type: Array,
		default: () => [],
	},
})

const emit = defineEmits(['tap'])
</script>

<style lang="scss" scoped>
.am-category-shortcut {
	display: grid;
	grid-template-columns: repeat(4, minmax(0, 1fr));
	gap: 22rpx;
}

.am-category-shortcut__item {
	min-width: 0;
	display: flex;
	flex-direction: column;
	align-items: center;
	gap: 10rpx;
}

.am-category-shortcut__cover,
.am-category-shortcut__icon {
	width: 72rpx;
	height: 72rpx;
	border-radius: 14rpx;
}

.am-category-shortcut__cover {
	background: var(--am-color-bg-muted);
}

.am-category-shortcut__icon {
	display: flex;
	align-items: center;
	justify-content: center;
}

.am-category-shortcut__icon-text {
	font-size: 34rpx;
	color: #ffffff;
	line-height: 1;
}

.am-category-shortcut__label {
	max-width: 100%;
	font-size: 22rpx;
	color: var(--am-color-text-title);
	line-height: 30rpx;
	white-space: nowrap;
	overflow: hidden;
	text-overflow: ellipsis;
}
</style>
