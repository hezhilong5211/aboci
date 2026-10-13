<template>
	<view class="am-status-bar" :style="statusBarStyle" />
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


import { computed, ref } from 'vue'

const props = defineProps({
	background: {
		type: String,
		default: 'transparent',
	},
})

const statusBarHeight = ref(44)

uni.getSystemInfo({
	success: (info) => {
		statusBarHeight.value = info.statusBarHeight || 44
	},
})

const statusBarStyle = computed(() => ({
	height: `${statusBarHeight.value}px`,
	background: props.background,
}))
</script>

<style lang="scss" scoped>
.am-status-bar {
	width: 100%;
	flex-shrink: 0;
}
</style>
