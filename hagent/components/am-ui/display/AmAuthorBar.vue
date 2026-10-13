<template>
	<view class="am-author-bar" @tap="emit('tap')">
		<AmAvatar :src="avatar" :text="name" :size="avatarSize" />
		<view class="am-author-bar__main">
			<view class="am-author-bar__title-row">
				<text class="am-author-bar__name">{{ name }}</text>
				<AmTag v-if="tag" :text="tag" plain />
			</view>
			<text class="am-author-bar__desc" v-if="desc">{{ desc }}</text>
		</view>
		<slot name="right">
			<text class="am-author-bar__right" v-if="rightText">{{ rightText }}</text>
		</slot>
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
import AmAvatar from './AmAvatar.vue'

defineProps({
	avatar: {
		type: String,
		default: '',
	},
	name: {
		type: String,
		default: '',
	},
	desc: {
		type: String,
		default: '',
	},
	tag: {
		type: String,
		default: '',
	},
	rightText: {
		type: String,
		default: '',
	},
	avatarSize: {
		type: String,
		default: '72rpx',
	},
})

const emit = defineEmits(['tap'])
</script>

<style lang="scss" scoped>
.am-author-bar {
	display: flex;
	align-items: center;
	gap: 18rpx;
	min-width: 0;
}

.am-author-bar__main {
	flex: 1;
	min-width: 0;
}

.am-author-bar__title-row {
	display: flex;
	align-items: center;
	gap: 10rpx;
	min-width: 0;
}

.am-author-bar__name {
	font-size: 26rpx;
	color: var(--am-color-text-title);
	font-weight: 700;
	line-height: 36rpx;
	overflow: hidden;
	white-space: nowrap;
	text-overflow: ellipsis;
}

.am-author-bar__desc {
	display: block;
	margin-top: 4rpx;
	font-size: 22rpx;
	color: var(--am-color-text-secondary);
	line-height: 30rpx;
	overflow: hidden;
	white-space: nowrap;
	text-overflow: ellipsis;
}

.am-author-bar__right {
	font-size: 24rpx;
	color: var(--am-color-primary);
	font-weight: 600;
	line-height: 34rpx;
}
</style>
