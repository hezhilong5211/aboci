<template>
	<view class="am-page" :style="{ background }">
		<slot name="fixed" />
		<scroll-view v-if="scroll" class="am-page__scroll" scroll-y :enhanced="scrollEnhanced" :bounces="scrollBounces" :show-scrollbar="scrollShowScrollbar" :lower-threshold="scrollLowerThreshold">
			<view class="am-page__inner" :style="[innerStyle, contentStyle]">
				<slot />
			</view>
		</scroll-view>
		<view v-else class="am-page__inner" :style="[innerStyle, contentStyle]">
			<slot />
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


import { computed } from 'vue'
import { usePageScroll } from '@/composables/usePageScroll.js'

const props = defineProps({
	background: {
		type: String,
		default: '#f5f8fe',
	},
	scroll: {
		type: Boolean,
		default: true,
	},
	padding: {
		type: String,
		default: '0 30rpx',
	},
	tabbar: {
		type: Boolean,
		default: false,
	},
	inputBar: {
		type: Boolean,
		default: false,
	},
	bottomGap: {
		type: String,
		default: '0px',
	},
})

const innerStyle = computed(() => ({
	padding: props.padding,
}))

const { scrollEnhanced, scrollBounces, scrollShowScrollbar, scrollLowerThreshold, contentStyle } = usePageScroll({
	tabbar: props.tabbar,
	inputBar: props.inputBar,
	extraBottom: props.bottomGap,
})
</script>

<style lang="scss" scoped>
.am-page {
	position: relative;
	height: 100vh;
	overflow: hidden;
}

.am-page__scroll {
	height: 100vh;
}

.am-page__inner {
	box-sizing: border-box;
	min-height: 100vh;
}
</style>
