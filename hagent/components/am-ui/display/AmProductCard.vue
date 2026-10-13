<template>
	<view class="am-product-card" :class="{ 'am-product-card--horizontal': horizontal }" @tap="emit('tap')">
		<view class="am-product-card__cover">
			<image class="am-product-card__image" v-if="image" :src="image" mode="aspectFill" />
			<text class="am-product-card__placeholder" v-else>商品</text>
		</view>
		<view class="am-product-card__main">
			<text class="am-product-card__title">{{ title }}</text>
			<text class="am-product-card__desc" v-if="desc">{{ desc }}</text>
			<view class="am-product-card__tags" v-if="tags.length">
				<AmTag v-for="tag in tags" :key="tag" :text="tag" plain />
			</view>
			<view class="am-product-card__footer">
				<view class="am-product-card__price-wrap">
					<text class="am-product-card__price">¥{{ price }}</text>
					<text class="am-product-card__origin" v-if="originPrice">¥{{ originPrice }}</text>
				</view>
				<view class="am-product-card__cart" @tap.stop="emit('add')">
					<text class="am-product-card__cart-text">＋</text>
				</view>
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


import AmTag from '../base/AmTag.vue'

defineProps({
	image: {
		type: String,
		default: '',
	},
	title: {
		type: String,
		default: '',
	},
	desc: {
		type: String,
		default: '',
	},
	price: {
		type: [String, Number],
		default: '',
	},
	originPrice: {
		type: [String, Number],
		default: '',
	},
	tags: {
		type: Array,
		default: () => [],
	},
	horizontal: {
		type: Boolean,
		default: false,
	},
})

const emit = defineEmits(['tap', 'add'])
</script>

<style lang="scss" scoped>
.am-product-card {
	overflow: hidden;
	border-radius: 22rpx;
	background: #ffffff;
	box-shadow: var(--am-shadow-card);
}

.am-product-card--horizontal {
	display: flex;
}

.am-product-card__cover {
	height: 260rpx;
	background: linear-gradient(135deg, #eaf2ff 0%, #dbeafe 100%);
	display: flex;
	align-items: center;
	justify-content: center;
}

.am-product-card--horizontal .am-product-card__cover {
	width: 220rpx;
	height: auto;
	min-height: 220rpx;
	flex-shrink: 0;
}

.am-product-card__image {
	width: 100%;
	height: 100%;
}

.am-product-card__placeholder {
	font-size: 28rpx;
	color: var(--am-color-primary);
	font-weight: 700;
}

.am-product-card__main {
	flex: 1;
	min-width: 0;
	padding: 22rpx;
	box-sizing: border-box;
}

.am-product-card__title {
	display: block;
	font-size: 28rpx;
	color: var(--am-color-text-title);
	font-weight: 700;
	line-height: 40rpx;
}

.am-product-card__desc {
	display: block;
	margin-top: 6rpx;
	font-size: 22rpx;
	color: var(--am-color-text-secondary);
	line-height: 32rpx;
}

.am-product-card__tags {
	display: flex;
	align-items: center;
	flex-wrap: wrap;
	gap: 10rpx;
	margin-top: 12rpx;
}

.am-product-card__footer {
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 18rpx;
	margin-top: 18rpx;
}

.am-product-card__price-wrap {
	display: flex;
	align-items: baseline;
	gap: 10rpx;
}

.am-product-card__price {
	font-size: 32rpx;
	color: var(--am-color-danger);
	font-weight: 800;
	line-height: 42rpx;
}

.am-product-card__origin {
	font-size: 20rpx;
	color: var(--am-color-text-tertiary);
	text-decoration: line-through;
	line-height: 28rpx;
}

.am-product-card__cart {
	width: 56rpx;
	height: 56rpx;
	border-radius: 50%;
	background: var(--am-color-primary);
	display: flex;
	align-items: center;
	justify-content: center;
}

.am-product-card__cart-text {
	font-size: 28rpx;
	color: #ffffff;
	line-height: 1;
}
</style>
