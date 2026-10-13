<template>
	<view class="am-sticky-bar" :style="stickyStyle">
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


import { computed } from 'vue'

const props = defineProps({
	top: {
		type: String,
		default: '0',
	},
	zIndex: {
		type: Number,
		default: 20,
	},
	background: {
		type: String,
		default: '#ffffff',
	},
})

const stickyStyle = computed(() => ({
	top: props.top,
	zIndex: props.zIndex,
	background: props.background,
}))
</script>

<style lang="scss" scoped>
.am-sticky-bar {
	position: sticky;
	left: 0;
	right: 0;
	box-sizing: border-box;
	width: 100%;
	border-bottom: 1rpx solid var(--am-color-border-light);
}
</style>
