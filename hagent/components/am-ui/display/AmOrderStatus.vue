<template>
	<view class="am-order-status">
		<view class="am-order-status__header">
			<view>
				<text class="am-order-status__title">{{ title }}</text>
				<text class="am-order-status__desc" v-if="desc">{{ desc }}</text>
			</view>
			<AmTag :text="statusText" :type="statusType" />
		</view>
		<view class="am-order-status__steps">
			<view
				class="am-order-status__step"
				:class="{ 'am-order-status__step--active': index <= current }"
				v-for="(item, index) in steps"
				:key="item"
			>
				<view class="am-order-status__dot" />
				<text class="am-order-status__step-text">{{ item }}</text>
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


import AmTag from '../base/AmTag.vue'

defineProps({
	title: {
		type: String,
		default: '',
	},
	desc: {
		type: String,
		default: '',
	},
	statusText: {
		type: String,
		default: '进行中',
	},
	statusType: {
		type: String,
		default: 'primary',
	},
	steps: {
		type: Array,
		default: () => [],
	},
	current: {
		type: Number,
		default: 0,
	},
})
</script>

<style lang="scss" scoped>
.am-order-status {
	padding: 26rpx;
	border-radius: 24rpx;
	background: #ffffff;
	box-shadow: var(--am-shadow-card);
	box-sizing: border-box;
}

.am-order-status__header {
	display: flex;
	align-items: flex-start;
	justify-content: space-between;
	gap: 18rpx;
}

.am-order-status__title,
.am-order-status__desc {
	display: block;
}

.am-order-status__title {
	font-size: 30rpx;
	color: var(--am-color-text-title);
	font-weight: 800;
	line-height: 40rpx;
}

.am-order-status__desc {
	margin-top: 6rpx;
	font-size: 22rpx;
	color: var(--am-color-text-secondary);
	line-height: 32rpx;
}

.am-order-status__steps {
	display: grid;
	grid-template-columns: repeat(4, 1fr);
	gap: 8rpx;
	margin-top: 26rpx;
}

.am-order-status__step {
	display: flex;
	flex-direction: column;
	align-items: center;
	gap: 10rpx;
}

.am-order-status__dot {
	width: 20rpx;
	height: 20rpx;
	border-radius: 50%;
	background: var(--am-color-border);
}

.am-order-status__step--active .am-order-status__dot {
	background: var(--am-color-primary);
}

.am-order-status__step-text {
	font-size: 20rpx;
	color: var(--am-color-text-tertiary);
	line-height: 28rpx;
	text-align: center;
}

.am-order-status__step--active .am-order-status__step-text {
	color: var(--am-color-primary);
	font-weight: 600;
}
</style>
