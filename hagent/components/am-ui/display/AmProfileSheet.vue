<template>
	<AmBottomSheet :visible="visible" :title="title" @update:visible="emit('update:visible', $event)" @close="close">
		<view class="am-profile-sheet">
			<view class="am-profile-sheet__header">
				<AmAvatar :src="profile.avatar" :text="profile.name" size="108rpx" />
				<view class="am-profile-sheet__main">
					<text class="am-profile-sheet__name">{{ profile.name }}</text>
					<text class="am-profile-sheet__desc">{{ profile.desc }}</text>
					<view class="am-profile-sheet__tags">
						<AmTag v-for="tag in profile.tags" :key="tag" :text="tag" plain />
					</view>
				</view>
			</view>
			<view class="am-profile-sheet__stats">
				<view class="am-profile-sheet__stat" v-for="item in profile.stats" :key="item.label">
					<text class="am-profile-sheet__stat-value">{{ item.value }}</text>
					<text class="am-profile-sheet__stat-label">{{ item.label }}</text>
				</view>
			</view>
			<view class="am-profile-sheet__info">
				<view class="am-profile-sheet__row" v-for="item in profile.info" :key="item.label">
					<text class="am-profile-sheet__row-label">{{ item.label }}</text>
					<text class="am-profile-sheet__row-value">{{ item.value }}</text>
				</view>
			</view>
		</view>
		<template #footer>
			<AmButton block :text="buttonText" @tap="emit('action')" />
		</template>
	</AmBottomSheet>
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
import AmTag from '../base/AmTag.vue'
import AmBottomSheet from '../feedback/AmBottomSheet.vue'
import AmAvatar from './AmAvatar.vue'

defineProps({
	visible: {
		type: Boolean,
		default: false,
	},
	title: {
		type: String,
		default: '对方资料',
	},
	buttonText: {
		type: String,
		default: '发起沟通',
	},
	profile: {
		type: Object,
		default: () => ({
			name: '',
			avatar: '',
			desc: '',
			tags: [],
			stats: [],
			info: [],
		}),
	},
})

const emit = defineEmits(['update:visible', 'close', 'action'])

function close() {
	emit('update:visible', false)
	emit('close')
}
</script>

<style lang="scss" scoped>
.am-profile-sheet {
	width: 100%;
}

.am-profile-sheet__header {
	display: flex;
	align-items: center;
	gap: 22rpx;
}

.am-profile-sheet__main {
	flex: 1;
	min-width: 0;
}

.am-profile-sheet__name {
	display: block;
	font-size: 32rpx;
	color: var(--am-color-text-title);
	font-weight: 800;
	line-height: 44rpx;
}

.am-profile-sheet__desc {
	display: block;
	margin-top: 6rpx;
	font-size: 24rpx;
	color: var(--am-color-text-secondary);
	line-height: 34rpx;
}

.am-profile-sheet__tags {
	display: flex;
	align-items: center;
	flex-wrap: wrap;
	gap: 10rpx;
	margin-top: 12rpx;
}

.am-profile-sheet__stats {
	display: grid;
	grid-template-columns: repeat(3, 1fr);
	gap: 14rpx;
	margin-top: 28rpx;
}

.am-profile-sheet__stat {
	padding: 18rpx 10rpx;
	border-radius: 18rpx;
	background: var(--am-color-bg-soft);
	text-align: center;
}

.am-profile-sheet__stat-value,
.am-profile-sheet__stat-label {
	display: block;
}

.am-profile-sheet__stat-value {
	font-size: 28rpx;
	color: var(--am-color-primary);
	font-weight: 800;
	line-height: 38rpx;
}

.am-profile-sheet__stat-label {
	margin-top: 4rpx;
	font-size: 20rpx;
	color: var(--am-color-text-secondary);
	line-height: 28rpx;
}

.am-profile-sheet__info {
	margin-top: 24rpx;
	border-top: 1rpx solid var(--am-color-border-light);
}

.am-profile-sheet__row {
	min-height: 74rpx;
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 18rpx;
	border-bottom: 1rpx solid var(--am-color-border-light);
}

.am-profile-sheet__row-label {
	font-size: 24rpx;
	color: var(--am-color-text-secondary);
	line-height: 34rpx;
}

.am-profile-sheet__row-value {
	font-size: 24rpx;
	color: var(--am-color-text-title);
	font-weight: 600;
	line-height: 34rpx;
}
</style>
