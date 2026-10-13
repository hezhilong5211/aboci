<template>
	<view class="am-skeleton">
		<view class="am-skeleton__row" v-for="index in rows" :key="index" :style="{ width: getWidth(index) }" />
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


const props = defineProps({
	rows: {
		type: Number,
		default: 3,
	},
	widths: {
		type: Array,
		default: () => ['100%', '86%', '62%'],
	},
})

function getWidth(index) {
	return props.widths[(index - 1) % props.widths.length]
}
</script>

<style lang="scss" scoped>
.am-skeleton {
	width: 100%;
}

.am-skeleton__row {
	height: 28rpx;
	border-radius: 999rpx;
	background: #edf2f8;
	margin-bottom: 18rpx;
}

.am-skeleton__row:last-child {
	margin-bottom: 0;
}
</style>
