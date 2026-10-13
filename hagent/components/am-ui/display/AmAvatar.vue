<template>
	<view class="am-avatar" :style="avatarStyle">
		<image class="am-avatar__image" v-if="src" :src="src" mode="aspectFill" />
		<text class="am-avatar__text" v-else>{{ text }}</text>
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


import { computed } from 'vue'

const props = defineProps({
	src: {
		type: String,
		default: '',
	},
	text: {
		type: String,
		default: 'A',
	},
	size: {
		type: String,
		default: '88rpx',
	},
	radius: {
		type: String,
		default: '50%',
	},
	background: {
		type: String,
		default: '#2f6bff',
	},
})

const avatarStyle = computed(() => ({
	width: props.size,
	height: props.size,
	borderRadius: props.radius,
	background: props.background,
}))
</script>

<style lang="scss" scoped>
.am-avatar {
	display: flex;
	align-items: center;
	justify-content: center;
	overflow: hidden;
	flex-shrink: 0;
}

.am-avatar__image {
	width: 100%;
	height: 100%;
}

.am-avatar__text {
	font-size: 30rpx;
	color: #ffffff;
	font-weight: 700;
	line-height: 1;
}
</style>
