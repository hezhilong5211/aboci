<template>
	<view class="am-composer-option" @tap="emit('tap')">
		<view class="am-composer-option__left">
			<view class="am-composer-option__icon">
				<slot name="icon">
					<text class="am-composer-option__icon-text">{{ icon }}</text>
				</slot>
			</view>
			<text class="am-composer-option__title">{{ title }}</text>
		</view>
		<view class="am-composer-option__right">
			<text class="am-composer-option__value" :class="{ 'am-composer-option__value--placeholder': !value }">
				{{ value || placeholder }}
			</text>
			<text class="am-composer-option__arrow" v-if="arrow">›</text>
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


defineProps({
	icon: {
		type: String,
		default: '',
	},
	title: {
		type: String,
		default: '',
	},
	value: {
		type: String,
		default: '',
	},
	placeholder: {
		type: String,
		default: '请选择',
	},
	arrow: {
		type: Boolean,
		default: true,
	},
})

const emit = defineEmits(['tap'])
</script>

<style lang="scss" scoped>
.am-composer-option {
	min-height: 96rpx;
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 20rpx;
	border-bottom: 1rpx solid var(--am-color-border-light);
}

.am-composer-option__left,
.am-composer-option__right {
	display: flex;
	align-items: center;
	min-width: 0;
}

.am-composer-option__left {
	gap: 18rpx;
}

.am-composer-option__right {
	gap: 10rpx;
	flex-shrink: 0;
	max-width: 52%;
}

.am-composer-option__icon {
	width: 54rpx;
	height: 54rpx;
	border-radius: 14rpx;
	background: #eef3ff;
	display: flex;
	align-items: center;
	justify-content: center;
	flex-shrink: 0;
}

.am-composer-option__icon-text {
	font-size: 30rpx;
	color: var(--am-color-text-body);
	line-height: 1;
}

.am-composer-option__title {
	font-size: 28rpx;
	color: var(--am-color-text-title);
	line-height: 38rpx;
	white-space: nowrap;
}

.am-composer-option__value {
	min-width: 0;
	font-size: 28rpx;
	color: var(--am-color-text-secondary);
	line-height: 38rpx;
	white-space: nowrap;
	overflow: hidden;
	text-overflow: ellipsis;
}

.am-composer-option__value--placeholder {
	color: var(--am-color-text-tertiary);
}

.am-composer-option__arrow {
	font-size: 38rpx;
	color: #b7c2d2;
	line-height: 1;
}
</style>
