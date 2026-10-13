<template>
	<view class="am-price-detail">
		<view class="am-price-detail__row" v-for="item in items" :key="item.label">
			<text class="am-price-detail__label">{{ item.label }}</text>
			<text class="am-price-detail__value" :class="{ 'am-price-detail__value--discount': item.discount }">
				{{ item.value }}
			</text>
		</view>
		<view class="am-price-detail__total">
			<text class="am-price-detail__total-label">{{ totalLabel }}</text>
			<text class="am-price-detail__total-value">{{ total }}</text>
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
	totalLabel: {
		type: String,
		default: '合计',
	},
	total: {
		type: String,
		default: '',
	},
})
</script>

<style lang="scss" scoped>
.am-price-detail {
	padding: 24rpx;
	border-radius: 22rpx;
	background: #ffffff;
	box-shadow: var(--am-shadow-card);
	box-sizing: border-box;
}

.am-price-detail__row,
.am-price-detail__total {
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 18rpx;
}

.am-price-detail__row {
	margin-bottom: 18rpx;
}

.am-price-detail__label,
.am-price-detail__value {
	font-size: 24rpx;
	line-height: 34rpx;
}

.am-price-detail__label {
	color: var(--am-color-text-secondary);
}

.am-price-detail__value {
	color: var(--am-color-text-title);
	font-weight: 600;
}

.am-price-detail__value--discount {
	color: var(--am-color-danger);
}

.am-price-detail__total {
	margin-top: 22rpx;
	padding-top: 22rpx;
	border-top: 1rpx solid var(--am-color-border-light);
}

.am-price-detail__total-label {
	font-size: 26rpx;
	color: var(--am-color-text-title);
	font-weight: 700;
	line-height: 36rpx;
}

.am-price-detail__total-value {
	font-size: 34rpx;
	color: var(--am-color-danger);
	font-weight: 800;
	line-height: 44rpx;
}
</style>
