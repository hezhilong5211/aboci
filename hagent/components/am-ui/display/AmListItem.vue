<template>
	<view class="am-list-item" hover-class="am-tap-feedback" :hover-stay-time="70" @tap="emit('tap')">
		<view class="am-list-item__left">
			<slot name="icon" />
			<view class="am-list-item__main">
				<text class="am-list-item__title">{{ title }}</text>
				<text class="am-list-item__desc" v-if="desc">{{ desc }}</text>
			</view>
		</view>
		<view class="am-list-item__right">
			<slot name="right" />
			<text class="am-list-item__arrow" v-if="arrow">›</text>
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
	title: {
		type: String,
		default: '',
	},
	desc: {
		type: String,
		default: '',
	},
	arrow: {
		type: Boolean,
		default: true,
	},
})

const emit = defineEmits(['tap'])
</script>

<style lang="scss" scoped>
.am-list-item {
	min-height: 102rpx;
	display: flex;
	align-items: center;
	justify-content: space-between;
	border-bottom: 1rpx solid #edf2fb;
}

.am-list-item:last-child {
	border-bottom: none;
}

.am-list-item__left {
	display: flex;
	align-items: center;
	min-width: 0;
}

.am-list-item__main {
	display: flex;
	flex-direction: column;
	min-width: 0;
}

.am-list-item__title {
	font-size: 24rpx;
	color: #263959;
	line-height: 34rpx;
}

.am-list-item__desc {
	margin-top: 4rpx;
	font-size: 20rpx;
	color: #8c9ab0;
	line-height: 28rpx;
}

.am-list-item__right {
	display: flex;
	align-items: center;
	flex-shrink: 0;
}

.am-list-item__arrow {
	font-size: 28rpx;
	color: #a8b4c6;
}
</style>
