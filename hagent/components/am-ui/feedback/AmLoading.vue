<template>
	<view class="am-loading">
		<view class="am-loading__ring" />
		<text class="am-loading__text" v-if="text">{{ text }}</text>
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
	text: {
		type: String,
		default: '加载中...',
	},
})
</script>

<style lang="scss" scoped>
.am-loading {
	display: inline-flex;
	align-items: center;
	gap: 14rpx;
}

.am-loading__ring {
	width: 30rpx;
	height: 30rpx;
	border-radius: 50%;
	border: 4rpx solid #dbe7ff;
	border-top-color: #2f6bff;
}

.am-loading__text {
	font-size: 24rpx;
	color: #64748b;
	line-height: 34rpx;
}
</style>
