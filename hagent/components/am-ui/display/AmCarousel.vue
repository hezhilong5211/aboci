<template>
	<view class="am-carousel" :class="[`am-carousel--${variant}`]">
		<swiper
			class="am-carousel__swiper"
			:class="{ 'am-carousel__swiper--content': variant === 'content' }"
			:current="currentIndex"
			:autoplay="playing"
			:interval="interval"
			:circular="circular"
			:duration="duration"
			@change="handleChange"
		>
			<swiper-item v-for="(item, index) in items" :key="item.id || item.title || index">
				<view class="am-carousel__slide">
					<view v-if="variant === 'card'" class="am-carousel-card">
						<image class="am-carousel-card__image" :src="item.image" mode="aspectFill" />
						<view class="am-carousel-card__body">
							<text class="am-carousel-card__title">{{ item.title }}</text>
							<text class="am-carousel-card__desc">{{ item.desc }}</text>
						</view>
						<text class="am-carousel__count">{{ index + 1 }}/{{ items.length }}</text>
					</view>

					<view v-else-if="variant === 'content'" class="am-carousel-content">
						<image class="am-carousel-content__image" :src="item.image" mode="aspectFill" />
						<view class="am-carousel-content__body">
							<text class="am-carousel-content__title">{{ item.title }}</text>
							<text class="am-carousel-content__desc">{{ item.desc }}</text>
							<view class="am-carousel-content__button" v-if="item.buttonText" @tap.stop="emit('action', item)">
								<text class="am-carousel-content__button-text">{{ item.buttonText }}</text>
							</view>
						</view>
					</view>

					<view v-else-if="variant === 'promo'" class="am-carousel-promo" :style="{ background: item.background || '#ff8aa0' }">
						<view class="am-carousel-promo__copy">
							<text class="am-carousel-promo__kicker">{{ item.kicker }}</text>
							<text class="am-carousel-promo__title">{{ item.title }}</text>
							<text class="am-carousel-promo__desc">{{ item.desc }}</text>
							<view class="am-carousel-promo__button" v-if="item.buttonText" @tap.stop="emit('action', item)">
								<text class="am-carousel-promo__button-text">{{ item.buttonText }}</text>
							</view>
						</view>
						<image class="am-carousel-promo__image" v-if="item.image" :src="item.image" mode="aspectFill" />
					</view>

					<view v-else class="am-carousel-cover">
						<image class="am-carousel-cover__image" :src="item.image" mode="aspectFill" />
						<view class="am-carousel-cover__mask" />
						<view class="am-carousel-cover__copy">
							<text class="am-carousel-cover__title">{{ item.title }}</text>
							<text class="am-carousel-cover__desc">{{ item.desc }}</text>
						</view>
						<view class="am-carousel-control" v-if="variant === 'control'">
							<text class="am-carousel-control__button" @tap.stop="prev">‹</text>
							<text class="am-carousel-control__pause" @tap.stop="togglePlaying">{{ playing ? 'Ⅱ' : '▶' }}</text>
							<text class="am-carousel-control__button" @tap.stop="next">›</text>
						</view>
						<text class="am-carousel__count" v-if="variant === 'thumb' || variant === 'control'">{{ index + 1 }}/{{ items.length }}</text>
					</view>
				</view>
			</swiper-item>
		</swiper>

		<view class="am-carousel__dots" v-if="showDots && variant !== 'thumb'">
			<text
				v-for="(item, index) in items"
				:key="item.id || index"
				class="am-carousel__dot"
				:class="{ 'am-carousel__dot--active': currentIndex === index }"
			/>
		</view>

		<scroll-view class="am-carousel-thumbs" scroll-x :show-scrollbar="false" v-if="variant === 'thumb'">
			<view class="am-carousel-thumbs__inner">
				<image
					v-for="(item, index) in items"
					:key="item.id || item.title || index"
					class="am-carousel-thumbs__image"
					:class="{ 'am-carousel-thumbs__image--active': currentIndex === index }"
					:src="item.image"
					mode="aspectFill"
					@tap="setCurrent(index)"
				/>
			</view>
		</scroll-view>
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


import { ref, watch } from 'vue'

const props = defineProps({
	items: {
		type: Array,
		default: () => [],
	},
	variant: {
		type: String,
		default: 'basic',
	},
	autoplay: {
		type: Boolean,
		default: false,
	},
	interval: {
		type: Number,
		default: 3000,
	},
	duration: {
		type: Number,
		default: 400,
	},
	circular: {
		type: Boolean,
		default: true,
	},
	showDots: {
		type: Boolean,
		default: true,
	},
})

const emit = defineEmits(['change', 'action'])
const currentIndex = ref(0)
const playing = ref(props.autoplay)

watch(() => props.autoplay, (value) => {
	playing.value = value
})

watch(() => props.items.length, (length) => {
	if (currentIndex.value >= length) {
		currentIndex.value = 0
	}
})

function handleChange(event) {
	currentIndex.value = event.detail.current
	emit('change', currentIndex.value)
}

function setCurrent(index) {
	currentIndex.value = index
	emit('change', index)
}

function prev() {
	const length = props.items.length
	if (!length) return
	currentIndex.value = currentIndex.value <= 0 ? length - 1 : currentIndex.value - 1
	emit('change', currentIndex.value)
}

function next() {
	const length = props.items.length
	if (!length) return
	currentIndex.value = currentIndex.value >= length - 1 ? 0 : currentIndex.value + 1
	emit('change', currentIndex.value)
}

function togglePlaying() {
	playing.value = !playing.value
}
</script>

<style lang="scss" scoped>
.am-carousel {
	width: 100%;
	position: relative;
}

.am-carousel__swiper {
	width: 100%;
	height: 300rpx;
}

.am-carousel__swiper--content {
	height: 260rpx;
}

.am-carousel__slide,
.am-carousel-cover,
.am-carousel-card,
.am-carousel-content,
.am-carousel-promo {
	width: 100%;
	height: 100%;
	position: relative;
	box-sizing: border-box;
}

.am-carousel-cover,
.am-carousel-card,
.am-carousel-content,
.am-carousel-promo {
	border-radius: 20rpx;
	overflow: hidden;
	background: #ffffff;
}

.am-carousel-cover__image,
.am-carousel-card__image {
	width: 100%;
	height: 100%;
}

.am-carousel-cover__mask {
	position: absolute;
	left: 0;
	right: 0;
	bottom: 0;
	height: 58%;
	background: linear-gradient(180deg, rgba(15, 23, 42, 0) 0%, rgba(15, 23, 42, 0.58) 100%);
}

.am-carousel-cover__copy {
	position: absolute;
	left: 28rpx;
	right: 28rpx;
	bottom: 34rpx;
	display: flex;
	flex-direction: column;
}

.am-carousel-cover__title {
	font-size: 30rpx;
	color: #ffffff;
	font-weight: 800;
	line-height: 40rpx;
}

.am-carousel-cover__desc {
	margin-top: 6rpx;
	font-size: 22rpx;
	color: rgba(255, 255, 255, 0.86);
	line-height: 32rpx;
}

.am-carousel-card {
	box-shadow: var(--am-shadow-card);
}

.am-carousel-card__image {
	height: 160rpx;
}

.am-carousel-card__body {
	padding: 18rpx 22rpx 0;
	display: flex;
	flex-direction: column;
}

.am-carousel-card__title,
.am-carousel-content__title {
	font-size: 28rpx;
	color: var(--am-color-text-title);
	font-weight: 800;
	line-height: 38rpx;
}

.am-carousel-card__desc,
.am-carousel-content__desc {
	margin-top: 8rpx;
	font-size: 22rpx;
	color: var(--am-color-text-secondary);
	line-height: 32rpx;
}

.am-carousel-content {
	display: flex;
	padding: 20rpx;
	gap: 20rpx;
	box-shadow: var(--am-shadow-card);
}

.am-carousel-content__image {
	width: 210rpx;
	height: 100%;
	border-radius: 18rpx;
	flex-shrink: 0;
}

.am-carousel-content__body {
	flex: 1;
	min-width: 0;
	padding: 12rpx 0;
	display: flex;
	flex-direction: column;
	justify-content: center;
}

.am-carousel-content__button,
.am-carousel-promo__button {
	margin-top: 18rpx;
	align-self: flex-start;
	height: 56rpx;
	padding: 0 24rpx;
	border-radius: 14rpx;
	background: var(--am-color-primary);
	display: flex;
	align-items: center;
	justify-content: center;
}

.am-carousel-content__button-text,
.am-carousel-promo__button-text {
	font-size: 22rpx;
	color: #ffffff;
	font-weight: 700;
	line-height: 30rpx;
}

.am-carousel-promo {
	padding: 30rpx;
	display: flex;
	align-items: center;
	justify-content: space-between;
}

.am-carousel-promo__copy {
	flex: 1;
	min-width: 0;
	display: flex;
	flex-direction: column;
	position: relative;
	z-index: 2;
}

.am-carousel-promo__kicker {
	font-size: 24rpx;
	color: rgba(255, 255, 255, 0.92);
	line-height: 34rpx;
}

.am-carousel-promo__title {
	margin-top: 4rpx;
	font-size: 38rpx;
	color: #ffffff;
	font-weight: 900;
	line-height: 48rpx;
}

.am-carousel-promo__desc {
	margin-top: 8rpx;
	font-size: 22rpx;
	color: rgba(255, 255, 255, 0.86);
	line-height: 32rpx;
}

.am-carousel-promo__button {
	background: rgba(255, 255, 255, 0.22);
}

.am-carousel-promo__image {
	width: 170rpx;
	height: 170rpx;
	border-radius: 28rpx;
	flex-shrink: 0;
	transform: rotate(-10deg);
}

.am-carousel-control {
	position: absolute;
	left: 18rpx;
	right: 18rpx;
	top: 50%;
	transform: translateY(-50%);
	display: flex;
	align-items: center;
	justify-content: space-between;
	pointer-events: none;
}

.am-carousel-control__button,
.am-carousel-control__pause {
	width: 56rpx;
	height: 56rpx;
	border-radius: 50%;
	background: rgba(255, 255, 255, 0.88);
	color: var(--am-color-text-title);
	font-size: 40rpx;
	line-height: 52rpx;
	text-align: center;
	pointer-events: auto;
}

.am-carousel-control__pause {
	width: 68rpx;
	height: 68rpx;
	line-height: 68rpx;
	background: rgba(15, 23, 42, 0.34);
	color: #ffffff;
	font-size: 26rpx;
}

.am-carousel__count {
	position: absolute;
	right: 18rpx;
	top: 18rpx;
	padding: 6rpx 14rpx;
	border-radius: 999rpx;
	background: rgba(15, 23, 42, 0.46);
	color: #ffffff;
	font-size: 20rpx;
	line-height: 26rpx;
}

.am-carousel__dots {
	position: absolute;
	left: 0;
	right: 0;
	bottom: 18rpx;
	display: flex;
	align-items: center;
	justify-content: center;
	gap: 8rpx;
}

.am-carousel--card .am-carousel__dots,
.am-carousel--content .am-carousel__dots,
.am-carousel--promo .am-carousel__dots {
	bottom: 12rpx;
}

.am-carousel__dot {
	width: 8rpx;
	height: 8rpx;
	border-radius: 999rpx;
	background: rgba(255, 255, 255, 0.7);
}

.am-carousel--card .am-carousel__dot,
.am-carousel--content .am-carousel__dot {
	background: #d3dbe8;
}

.am-carousel__dot--active {
	width: 22rpx;
	background: var(--am-color-primary);
}

.am-carousel-thumbs {
	margin-top: 14rpx;
	width: 100%;
	white-space: nowrap;
}

.am-carousel-thumbs__inner {
	display: flex;
	gap: 12rpx;
}

.am-carousel-thumbs__image {
	width: 96rpx;
	height: 58rpx;
	border-radius: 10rpx;
	border: 3rpx solid transparent;
	box-sizing: border-box;
	flex-shrink: 0;
}

.am-carousel-thumbs__image--active {
	border-color: var(--am-color-primary);
}

.am-performance-light .am-carousel-card,
.am-performance-light .am-carousel-content {
	box-shadow: none;
}
</style>
