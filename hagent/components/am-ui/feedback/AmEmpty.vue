<template>
	<view class="am-empty">
		<view class="am-empty__box">
			<view class="am-empty__lid" />
		</view>
		<text class="am-empty__title">{{ title }}</text>
		<text class="am-empty__desc" v-if="desc">{{ desc }}</text>
		<slot />
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
	title: {
		type: String,
		default: '暂无数据',
	},
	desc: {
		type: String,
		default: '',
	},
})
</script>

<style lang="scss" scoped>
.am-empty {
	display: flex;
	flex-direction: column;
	align-items: center;
	padding: 48rpx 24rpx;
}

.am-empty__box {
	position: relative;
	width: 104rpx;
	height: 78rpx;
	border-radius: 14rpx 14rpx 20rpx 20rpx;
	background: #e8edf6;
	margin-bottom: 24rpx;
}

.am-empty__lid {
	position: absolute;
	left: 20rpx;
	top: -18rpx;
	width: 64rpx;
	height: 30rpx;
	border-radius: 10rpx;
	background: #d9e1ee;
	transform: rotate(-14deg);
}

.am-empty__title {
	font-size: 28rpx;
	font-weight: 600;
	color: #1f2a37;
	line-height: 40rpx;
}

.am-empty__desc {
	margin-top: 8rpx;
	font-size: 22rpx;
	color: #98a2b3;
	line-height: 32rpx;
}
</style>
