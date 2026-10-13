<template>
	<view class="am-address-card" :class="{ 'am-address-card--default': isDefault }" @tap="emit('tap')">
		<view class="am-address-card__header">
			<text class="am-address-card__name">{{ name }}</text>
			<text class="am-address-card__phone">{{ phone }}</text>
			<AmTag v-if="isDefault" text="默认" type="success" />
		</view>
		<text class="am-address-card__address">{{ address }}</text>
		<view class="am-address-card__footer" v-if="editable">
			<text class="am-address-card__edit" @tap.stop="emit('edit')">编辑</text>
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
	name: {
		type: String,
		default: '',
	},
	phone: {
		type: String,
		default: '',
	},
	address: {
		type: String,
		default: '',
	},
	isDefault: {
		type: Boolean,
		default: false,
	},
	editable: {
		type: Boolean,
		default: true,
	},
})

const emit = defineEmits(['tap', 'edit'])
</script>

<style lang="scss" scoped>
.am-address-card {
	padding: 26rpx;
	border-radius: 24rpx;
	background: #ffffff;
	box-shadow: var(--am-shadow-card);
	box-sizing: border-box;
}

.am-address-card--default {
	border: 2rpx solid rgba(47, 107, 255, 0.16);
}

.am-address-card__header {
	display: flex;
	align-items: center;
	gap: 14rpx;
	min-width: 0;
}

.am-address-card__name {
	font-size: 28rpx;
	color: var(--am-color-text-title);
	font-weight: 800;
	line-height: 38rpx;
}

.am-address-card__phone {
	font-size: 24rpx;
	color: var(--am-color-text-secondary);
	line-height: 34rpx;
}

.am-address-card__address {
	display: block;
	margin-top: 12rpx;
	font-size: 24rpx;
	color: var(--am-color-text-body);
	line-height: 38rpx;
}

.am-address-card__footer {
	display: flex;
	justify-content: flex-end;
	margin-top: 18rpx;
}

.am-address-card__edit {
	font-size: 24rpx;
	color: var(--am-color-primary);
	font-weight: 600;
	line-height: 34rpx;
}
</style>
