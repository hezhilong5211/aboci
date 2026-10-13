<template>
	<view class="am-image-grid" :class="[`am-image-grid--${displayImages.length}`]">
		<view
			class="am-image-grid__item"
			v-for="(item, index) in displayImages"
			:key="item + index"
			@tap="emit('preview', index)"
		>
			<image class="am-image-grid__image" :src="item" mode="aspectFill" />
			<view class="am-image-grid__more" v-if="index === max - 1 && images.length > max">
				<text class="am-image-grid__more-text">+{{ images.length - max }}</text>
			</view>
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

const props = defineProps({
	images: {
		type: Array,
		default: () => [],
	},
	max: {
		type: Number,
		default: 9,
	},
})

const emit = defineEmits(['preview'])
const displayImages = computed(() => props.images.slice(0, props.max))
</script>

<style lang="scss" scoped>
.am-image-grid {
	display: grid;
	grid-template-columns: repeat(3, 1fr);
	gap: 8rpx;
}

.am-image-grid--1 {
	grid-template-columns: 1fr;
}

.am-image-grid--2,
.am-image-grid--4 {
	grid-template-columns: repeat(2, 1fr);
}

.am-image-grid__item {
	position: relative;
	aspect-ratio: 1;
	border-radius: 12rpx;
	overflow: hidden;
	background: var(--am-color-bg-muted);
}

.am-image-grid--1 .am-image-grid__item {
	aspect-ratio: 16 / 10;
}

.am-image-grid__image {
	width: 100%;
	height: 100%;
}

.am-image-grid__more {
	position: absolute;
	inset: 0;
	background: rgba(15, 23, 42, 0.48);
	display: flex;
	align-items: center;
	justify-content: center;
}

.am-image-grid__more-text {
	font-size: 32rpx;
	color: #ffffff;
	font-weight: 800;
	line-height: 1;
}
</style>
