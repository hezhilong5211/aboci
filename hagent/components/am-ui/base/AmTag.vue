<template>
	<view class="am-tag" :class="[`am-tag--${type}`, { 'am-tag--plain': plain }]">
		<text class="am-tag__text"><slot>{{ text }}</slot></text>
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
		default: '',
	},
	type: {
		type: String,
		default: 'primary',
	},
	plain: {
		type: Boolean,
		default: false,
	},
})
</script>

<style lang="scss" scoped>
.am-tag {
	display: inline-flex;
	align-items: center;
	justify-content: center;
	min-height: 34rpx;
	padding: 0 12rpx;
	border-radius: 10rpx;
	background: rgba(63, 136, 255, 0.1);
	border: 1rpx solid transparent;
}

.am-tag--success {
	background: rgba(34, 197, 94, 0.1);
}

.am-tag--warning {
	background: rgba(255, 159, 28, 0.12);
}

.am-tag--danger {
	background: rgba(255, 90, 95, 0.1);
}

.am-tag--plain {
	background: #ffffff;
	border-color: #d9e4ff;
}

.am-tag__text {
	font-size: 18rpx;
	color: #3f88ff;
	line-height: 30rpx;
}

.am-tag--success .am-tag__text {
	color: #22a65a;
}

.am-tag--warning .am-tag__text {
	color: #e58a00;
}

.am-tag--danger .am-tag__text {
	color: #e5484d;
}
</style>
