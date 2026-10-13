<template>
	<view class="am-doctor-card" @tap="emit('tap')">
		<!-- 左侧：头像 + 角标 -->
		<view class="am-doctor-card__avatar-wrap">
			<image class="am-doctor-card__avatar" :src="avatar" mode="aspectFill" />
			<view class="am-doctor-card__badge" v-if="titleBadge">
				<text class="am-doctor-card__badge-text">{{ titleBadge }}</text>
			</view>
		</view>
		<!-- 右侧：信息区 -->
		<view class="am-doctor-card__info">
			<view class="am-doctor-card__name-row">
				<text class="am-doctor-card__name">{{ name }}</text>
				<view class="am-doctor-card__dept-tag" v-if="department">
					<text class="am-doctor-card__dept-text">{{ department }}</text>
				</view>
				<view class="am-doctor-card__title-tag" v-if="title">
					<text class="am-doctor-card__title-text">{{ title }}</text>
				</view>
			</view>
			<text class="am-doctor-card__hospital" v-if="hospital">{{ hospital }}</text>
			<text class="am-doctor-card__specialty" v-if="specialty">擅长：{{ specialty }}</text>
			<view class="am-doctor-card__footer">
				<view class="am-doctor-card__stats">
					<text class="am-doctor-card__star">★</text>
					<text class="am-doctor-card__score">{{ score }}</text>
					<text class="am-doctor-card__divider">·</text>
					<text class="am-doctor-card__count">接诊 {{ consultCount }} 次</text>
				</view>
				<view class="am-doctor-card__btn" @tap.stop="emit('consult')">
					<text class="am-doctor-card__btn-text">问诊</text>
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
defineProps({
	/** 医生头像 */
	avatar: { type: String, default: '' },
	/** 医生姓名 */
	name: { type: String, default: '' },
	/** 科室 */
	department: { type: String, default: '' },
	/** 职称 */
	title: { type: String, default: '' },
	/** 头像角标文字（如"主任"） */
	titleBadge: { type: String, default: '' },
	/** 医院 */
	hospital: { type: String, default: '' },
	/** 擅长领域 */
	specialty: { type: String, default: '' },
	/** 评分 */
	score: { type: [String, Number], default: '' },
	/** 接诊次数 */
	consultCount: { type: [String, Number], default: '' },
})

const emit = defineEmits(['tap', 'consult'])
</script>

<style lang="scss" scoped>
.am-doctor-card {
	display: flex;
	padding: 28rpx;
	background: #ffffff;
	border-radius: 22rpx;
	box-shadow: var(--am-shadow-card);
}

.am-doctor-card__avatar-wrap {
	position: relative;
	flex-shrink: 0;
	margin-right: 24rpx;
}

.am-doctor-card__avatar {
	width: 120rpx;
	height: 120rpx;
	border-radius: 50%;
	background: #eef3ff;
}

.am-doctor-card__badge {
	position: absolute;
	bottom: -6rpx;
	left: 50%;
	transform: translateX(-50%);
	padding: 2rpx 14rpx;
	border-radius: 16rpx;
	background: var(--am-color-primary);
	white-space: nowrap;
}

.am-doctor-card__badge-text {
	font-size: 18rpx;
	color: #ffffff;
	font-weight: 700;
	line-height: 26rpx;
}

.am-doctor-card__info {
	flex: 1;
	min-width: 0;
}

.am-doctor-card__name-row {
	display: flex;
	align-items: center;
	gap: 10rpx;
	flex-wrap: wrap;
}

.am-doctor-card__name {
	font-size: 30rpx;
	font-weight: 800;
	color: var(--am-color-text-title);
	line-height: 40rpx;
}

.am-doctor-card__dept-tag {
	padding: 2rpx 12rpx;
	border-radius: 8rpx;
	background: rgba(58, 122, 254, 0.12);
}

.am-doctor-card__dept-text {
	font-size: 20rpx;
	color: var(--am-color-primary);
	font-weight: 600;
	line-height: 28rpx;
}

.am-doctor-card__title-tag {
	padding: 2rpx 12rpx;
	border-radius: 8rpx;
	background: rgba(100, 116, 139, 0.1);
}

.am-doctor-card__title-text {
	font-size: 20rpx;
	color: var(--am-color-text-secondary);
	font-weight: 500;
	line-height: 28rpx;
}

.am-doctor-card__hospital {
	display: block;
	margin-top: 8rpx;
	font-size: 22rpx;
	color: var(--am-color-text-secondary);
	line-height: 30rpx;
}

.am-doctor-card__specialty {
	display: block;
	margin-top: 6rpx;
	font-size: 22rpx;
	color: var(--am-color-text-tertiary);
	line-height: 30rpx;
	overflow: hidden;
	text-overflow: ellipsis;
	white-space: nowrap;
}

.am-doctor-card__footer {
	display: flex;
	align-items: center;
	justify-content: space-between;
	margin-top: 12rpx;
}

.am-doctor-card__stats {
	display: flex;
	align-items: center;
	gap: 6rpx;
}

.am-doctor-card__star {
	font-size: 22rpx;
	color: #FFB020;
	line-height: 1;
}

.am-doctor-card__score {
	font-size: 22rpx;
	color: var(--am-color-text-title);
	font-weight: 600;
	line-height: 30rpx;
}

.am-doctor-card__divider {
	font-size: 22rpx;
	color: var(--am-color-text-tertiary);
	line-height: 1;
}

.am-doctor-card__count {
	font-size: 22rpx;
	color: var(--am-color-text-tertiary);
	line-height: 30rpx;
}

.am-doctor-card__btn {
	padding: 8rpx 28rpx;
	border-radius: 999rpx;
	border: 2rpx solid var(--am-color-primary);
}

.am-doctor-card__btn-text {
	font-size: 24rpx;
	color: var(--am-color-primary);
	font-weight: 600;
	line-height: 32rpx;
}

/* 轻量模式 */
.am-performance-light .am-doctor-card {
	box-shadow: none;
}
</style>
