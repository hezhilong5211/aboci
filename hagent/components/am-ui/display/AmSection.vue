<template>
	<view class="am-section">
		<view class="am-section__header" v-if="title || moreText || $slots.extra">
			<text class="am-section__title">{{ title }}</text>
			<slot name="extra">
				<view class="am-section__more" v-if="moreText" @tap="emit('more')">
					<text class="am-section__more-text">{{ moreText }}</text>
					<text class="am-section__more-arrow">›</text>
				</view>
			</slot>
		</view>
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
		default: '',
	},
	moreText: {
		type: String,
		default: '',
	},
})

const emit = defineEmits(['more'])
</script>

<style lang="scss" scoped>
.am-section__header {
	display: flex;
	align-items: center;
	justify-content: space-between;
	margin-bottom: 22rpx;
}

.am-section__title {
	font-size: 32rpx;
	font-weight: 700;
	color: #1d2b42;
	line-height: 40rpx;
}

.am-section__more {
	display: inline-flex;
	align-items: center;
	gap: 6rpx;
}

.am-section__more-text,
.am-section__more-arrow {
	font-size: 22rpx;
	color: #a0acbf;
	line-height: 1;
}
</style>
