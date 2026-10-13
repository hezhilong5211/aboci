<template>
	<view class="am-post-detail-header">
		<AmAuthorBar :avatar="avatar" :name="name" :desc="desc" :tag="tag">
			<template #right>
				<view class="am-post-detail-header__actions">
					<AmButton size="small" :text="followed ? '已关注' : '关注'" :variant="followed ? 'secondary' : 'primary'" @tap.stop="emit('follow')" />
					<view class="am-post-detail-header__more" @tap.stop="emit('more')">
						<text class="am-post-detail-header__more-text">•••</text>
					</view>
				</view>
			</template>
		</AmAuthorBar>
		<view class="am-post-detail-header__meta">
			<text class="am-post-detail-header__meta-text">{{ time }}</text>
			<text class="am-post-detail-header__meta-text">{{ views }} 浏览</text>
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


import AmButton from '../base/AmButton.vue'
import AmAuthorBar from './AmAuthorBar.vue'

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
	time: {
		type: String,
		default: '',
	},
	views: {
		type: String,
		default: '0',
	},
	followed: {
		type: Boolean,
		default: false,
	},
})

const emit = defineEmits(['follow', 'more'])
</script>

<style lang="scss" scoped>
.am-post-detail-header {
	width: 100%;
}

.am-post-detail-header__actions {
	display: flex;
	align-items: center;
	gap: 12rpx;
}

.am-post-detail-header__more {
	width: 48rpx;
	height: 48rpx;
	display: flex;
	align-items: center;
	justify-content: center;
}

.am-post-detail-header__more-text {
	font-size: 24rpx;
	color: var(--am-color-text-secondary);
	line-height: 1;
}

.am-post-detail-header__meta {
	display: flex;
	align-items: center;
	gap: 20rpx;
	margin-top: 16rpx;
}

.am-post-detail-header__meta-text {
	font-size: 22rpx;
	color: var(--am-color-text-tertiary);
	line-height: 30rpx;
}
</style>
