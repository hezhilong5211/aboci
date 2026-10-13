<template>
	<!-- 案例瀑布流卡:封面按指定比例(aspect-ratio + cover 裁切) + 标题 + 「比例 · 使用次数」 -->
	<view class="case-card" @tap="$emit('tap')">
		<view class="case-card__cover" :style="coverStyle">
			<image
				v-if="cover"
				class="case-card__img"
				:src="cover"
				mode="aspectFill"
				lazy-load
			/>
			<!-- 无封面占位:斜纹底 -->
			<view v-else class="case-card__placeholder" />
			<!-- 视频角标:仅 video 分类显示播放按钮 -->
			<view v-if="isVideo" class="case-card__play">
				<view class="case-card__play-icon" />
			</view>
		</view>
		<view class="case-card__body">
			<text class="case-card__title">{{ title }}</text>
			<text class="case-card__meta">{{ metaText }}</text>
		</view>
	</view>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
	// 已拼接 baseUrl 的封面地址(由页面端处理)
	cover: { type: String, default: '' },
	title: { type: String, default: '' },
	// 使用次数(原始数值)
	useCount: { type: Number, default: 0 },
	// 是否视频分类(显示播放角标)
	isVideo: { type: Boolean, default: false },
	// 画幅比例(如 '3:4'),决定卡片高度,实现错落
	aspectRatio: { type: String, default: '3:4' },
})

defineEmits(['tap'])

// 比例字符串 → CSS aspect-ratio(如 '3:4' → '3 / 4');非法兜底 3/4
const coverStyle = computed(() => {
	const m = String(props.aspectRatio || '').split(':')
	const w = Number(m[0])
	const h = Number(m[1])
	const ratio = w > 0 && h > 0 ? `${w} / ${h}` : '3 / 4'
	return `aspect-ratio: ${ratio};`
})

// 使用次数格式化:>=1000 显示 x.xk
const useText = computed(() => {
	const n = Number(props.useCount) || 0
	if (n >= 1000) {
		return (n / 1000).toFixed(1) + 'k 使用'
	}
	return n + ' 使用'
})

// 次要信息:比例 · 使用次数
const metaText = computed(() => `${props.aspectRatio} · ${useText.value}`)
</script>

<style lang="scss" scoped>
.case-card {
	transition: transform 0.15s ease, opacity 0.15s ease;
}

.case-card:active {
	transform: scale(0.98);
	opacity: 0.96;
}

/* 封面容器:比例由 :style 的 aspect-ratio 控制,圆角裁切 */
.case-card__cover {
	position: relative;
	width: 100%;
	border-radius: 32rpx;
	overflow: hidden;
	background: #eef4fc;
}

.case-card__img {
	width: 100%;
	height: 100%;
	display: block;
	/* 加载占位:图未出时显示条纹底 */
	background: repeating-linear-gradient(135deg, #e2ecf8 0 14rpx, #eef4fc 14rpx 28rpx);
}

.case-card__placeholder {
	width: 100%;
	height: 100%;
	background: repeating-linear-gradient(135deg, #e2ecf8 0 14rpx, #eef4fc 14rpx 28rpx);
}

/* 视频播放角标 */
.case-card__play {
	position: absolute;
	left: 50%;
	top: 50%;
	transform: translate(-50%, -50%);
	width: 84rpx;
	height: 84rpx;
	border-radius: 50%;
	background: rgba(255, 255, 255, 0.9);
	display: flex;
	align-items: center;
	justify-content: center;
}

.case-card__play-icon {
	width: 0;
	height: 0;
	margin-left: 6rpx;
	border-style: solid;
	border-width: 16rpx 0 16rpx 26rpx;
	border-color: transparent transparent transparent #4f84ea;
}

.case-card__body {
	padding: 12rpx 8rpx 0;
}

.case-card__title {
	display: block;
	font-size: 26rpx;
	font-weight: 500;
	color: #15161b;
	line-height: 1.3;
	overflow: hidden;
	text-overflow: ellipsis;
	white-space: nowrap;
}

.case-card__meta {
	display: block;
	margin-top: 4rpx;
	font-size: 22rpx;
	color: #9499a6;
	line-height: 1.3;
}
</style>
