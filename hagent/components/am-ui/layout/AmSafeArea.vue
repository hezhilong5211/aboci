<template>
	<view class="am-safe-area" :class="safeAreaClass">
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
		type: Boolean,
		default: false,
	},
	bottom: {
		type: Boolean,
		default: true,
	},
})

const safeAreaClass = computed(() => ({
	'am-safe-area--top': props.top,
	'am-safe-area--bottom': props.bottom,
}))
</script>

<style lang="scss" scoped>
.am-safe-area {
	box-sizing: border-box;
	width: 100%;
}

.am-safe-area--top {
	padding-top: env(safe-area-inset-top);
}

.am-safe-area--bottom {
	padding-bottom: env(safe-area-inset-bottom);
}
</style>
