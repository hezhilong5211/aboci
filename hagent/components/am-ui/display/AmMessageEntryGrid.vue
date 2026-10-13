<template>
	<view class="am-message-entry-grid">
		<view class="am-message-entry-grid__item" v-for="item in items" :key="item.key || item.label" @tap="emit('tap', item)">
			<AmBadge :value="item.badge" :show="Number(item.badge) > 0">
				<view class="am-message-entry-grid__icon" :style="{ background: item.background || '#eef3ff' }">
					<text class="am-message-entry-grid__icon-text" :style="{ color: item.color || '#2f6bff' }">{{ item.icon }}</text>
				</view>
			</AmBadge>
			<text class="am-message-entry-grid__label">{{ item.label }}</text>
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


import AmBadge from './AmBadge.vue'

defineProps({
	items: {
		type: Array,
		default: () => [],
	},
})

const emit = defineEmits(['tap'])
</script>

<style lang="scss" scoped>
.am-message-entry-grid {
	display: grid;
	grid-template-columns: repeat(5, minmax(0, 1fr));
	gap: 16rpx;
}

.am-message-entry-grid__item {
	min-width: 0;
	display: flex;
	flex-direction: column;
	align-items: center;
	gap: 12rpx;
}

.am-message-entry-grid__icon {
	width: 72rpx;
	height: 72rpx;
	border-radius: 50%;
	display: flex;
	align-items: center;
	justify-content: center;
}

.am-message-entry-grid__icon-text {
	font-size: 36rpx;
	font-weight: 800;
	line-height: 1;
}

.am-message-entry-grid__label {
	max-width: 100%;
	font-size: 22rpx;
	color: var(--am-color-text-title);
	line-height: 30rpx;
	white-space: nowrap;
	overflow: hidden;
	text-overflow: ellipsis;
}
</style>
