<template>
	<AmCard class="am-stats-card" padding="24rpx">
		<text class="am-stats-card__title">{{ title }}</text>
		<view class="am-stats-card__main">
			<text class="am-stats-card__value">{{ value }}</text>
			<text class="am-stats-card__trend" :class="`am-stats-card__trend--${trendType}`" v-if="trend">
				{{ trend }}
			</text>
		</view>
		<text class="am-stats-card__desc" v-if="desc">{{ desc }}</text>
	</AmCard>
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


import AmCard from '../base/AmCard.vue'

defineProps({
	title: {
		type: String,
		default: '',
	},
	value: {
		type: [String, Number],
		default: '',
	},
	trend: {
		type: String,
		default: '',
	},
	trendType: {
		type: String,
		default: 'success',
	},
	desc: {
		type: String,
		default: '',
	},
})
</script>

<style lang="scss" scoped>
.am-stats-card__title {
	display: block;
	font-size: 22rpx;
	color: #64748b;
	line-height: 32rpx;
}

.am-stats-card__main {
	display: flex;
	align-items: flex-end;
	gap: 12rpx;
	margin-top: 12rpx;
}

.am-stats-card__value {
	font-size: 40rpx;
	color: #1f2a37;
	font-weight: 800;
	line-height: 48rpx;
}

.am-stats-card__trend {
	font-size: 20rpx;
	line-height: 30rpx;
}

.am-stats-card__trend--success {
	color: #22c55e;
}

.am-stats-card__trend--danger {
	color: #ff5a5f;
}

.am-stats-card__desc {
	display: block;
	margin-top: 12rpx;
	font-size: 20rpx;
	color: #94a3b8;
	line-height: 30rpx;
}
</style>
