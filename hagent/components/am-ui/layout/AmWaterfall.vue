<template>
	<!-- 纯 CSS 双列容器，数据分列由页面端处理 -->
	<view class="am-waterfall" :style="{ gap: gap }">
		<view class="am-waterfall__column" :style="{ gap: gap }">
			<slot name="left" />
		</view>
		<view class="am-waterfall__column" :style="{ gap: gap }">
			<slot name="right" />
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
	/** 列间距和行间距 */
	gap: {
		type: String,
		default: '16rpx',
	},
})
</script>

<style lang="scss" scoped>
.am-waterfall {
	display: flex;
	align-items: flex-start;
}

.am-waterfall__column {
	flex: 1;
	min-width: 0;
	display: flex;
	flex-direction: column;
}
</style>
