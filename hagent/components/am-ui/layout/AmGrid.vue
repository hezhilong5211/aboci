<template>
	<view class="am-grid" :style="gridStyle">
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
	columns: {
		type: Number,
		default: 4,
	},
	rowGap: {
		type: String,
		default: '28rpx',
	},
	columnGap: {
		type: String,
		default: '0',
	},
})

const gridStyle = computed(() => ({
	gridTemplateColumns: `repeat(${props.columns}, 1fr)`,
	rowGap: props.rowGap,
	columnGap: props.columnGap,
}))
</script>

<style lang="scss" scoped>
.am-grid {
	display: grid;
	width: 100%;
}
</style>
