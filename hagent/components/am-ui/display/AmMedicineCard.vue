<template>
	<view class="am-medicine-card">
		<!-- 左侧药品图标 -->
		<view class="am-medicine-card__icon" :style="{ background: iconBg }">
			<image v-if="iconImage" class="am-medicine-card__icon-img" :src="iconImage" mode="aspectFit" />
			<text v-else class="am-medicine-card__icon-text">{{ icon }}</text>
		</view>
		<!-- 中间信息 -->
		<view class="am-medicine-card__info">
			<text class="am-medicine-card__name">{{ name }}</text>
			<text class="am-medicine-card__dosage">{{ dosage }}</text>
			<view class="am-medicine-card__time-row" v-if="nextTime">
				<text class="am-medicine-card__time-label">下次服药：</text>
				<text class="am-medicine-card__time-value">{{ nextTime }}</text>
			</view>
		</view>
		<!-- 右侧操作 -->
		<view class="am-medicine-card__action">
			<view v-if="!taken" class="am-medicine-card__btn" @tap.stop="emit('take')">
				<text class="am-medicine-card__btn-text">服用</text>
			</view>
			<view v-else class="am-medicine-card__taken">
				<text class="am-medicine-card__taken-text">已服用 ✓</text>
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
	/** 药品图标文字 */
	icon: { type: String, default: '💊' },
	/** 药品图标图片（优先于icon文字） */
	iconImage: { type: String, default: '' },
	/** 图标背景色 */
	iconBg: { type: String, default: 'linear-gradient(135deg, #72AEFF 0%, #4B8FFF 100%)' },
	/** 药品名称 */
	name: { type: String, default: '' },
	/** 剂量用法 */
	dosage: { type: String, default: '' },
	/** 下次服药时间 */
	nextTime: { type: String, default: '' },
	/** 是否已服用 */
	taken: { type: Boolean, default: false },
})

const emit = defineEmits(['take'])
</script>

<style lang="scss" scoped>
.am-medicine-card {
	display: flex;
	align-items: center;
	padding: 24rpx 28rpx;
	background: #ffffff;
	border-radius: 22rpx;
	box-shadow: var(--am-shadow-card);
}

.am-medicine-card__icon {
	width: 88rpx;
	height: 88rpx;
	border-radius: 20rpx;
	display: flex;
	align-items: center;
	justify-content: center;
	flex-shrink: 0;
	margin-right: 22rpx;
}

.am-medicine-card__icon-img {
	width: 52rpx;
	height: 52rpx;
}

.am-medicine-card__icon-text {
	font-size: 40rpx;
	line-height: 1;
}

.am-medicine-card__info {
	flex: 1;
	min-width: 0;
}

.am-medicine-card__name {
	display: block;
	font-size: 28rpx;
	font-weight: 700;
	color: var(--am-color-text-title);
	line-height: 38rpx;
}

.am-medicine-card__dosage {
	display: block;
	margin-top: 4rpx;
	font-size: 22rpx;
	color: var(--am-color-text-tertiary);
	line-height: 30rpx;
}

.am-medicine-card__time-row {
	display: flex;
	align-items: center;
	margin-top: 4rpx;
}

.am-medicine-card__time-label {
	font-size: 22rpx;
	color: var(--am-color-text-tertiary);
	line-height: 30rpx;
}

.am-medicine-card__time-value {
	font-size: 24rpx;
	color: var(--am-color-primary);
	font-weight: 700;
	line-height: 30rpx;
}

.am-medicine-card__action {
	flex-shrink: 0;
	margin-left: 16rpx;
}

.am-medicine-card__btn {
	padding: 10rpx 28rpx;
	border-radius: 999rpx;
	border: 2rpx solid var(--am-color-primary);
	background: rgba(58, 122, 254, 0.06);
}

.am-medicine-card__btn-text {
	font-size: 24rpx;
	color: var(--am-color-primary);
	font-weight: 600;
	line-height: 32rpx;
}

.am-medicine-card__taken {
	padding: 10rpx 16rpx;
}

.am-medicine-card__taken-text {
	font-size: 22rpx;
	color: var(--am-color-text-tertiary);
	line-height: 30rpx;
}

/* 轻量模式 */
.am-performance-light .am-medicine-card {
	box-shadow: none;
}
</style>
