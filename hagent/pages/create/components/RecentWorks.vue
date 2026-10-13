<template>
	<!-- 近期作品区:标题 + 2 列 1:1 网格(7 天内出图);空态隐藏整区由父组件控制 -->
	<view class="recent-works">
		<text class="recent-title">近期作品</text>

		<!-- 2 列等宽网格:统一 1:1 裁剪展示,点击预览看真实比例 -->
		<view class="rw-grid">
			<view class="rw-item" v-for="item in resolvedList" :key="item.key">
				<view class="rw-cell">
					<CachedGeneratedImage
						class="rw-img"
						:src="item.previewFull"
						:fallback-src="item.full"
						:user-id="currentUserId"
						cache-type="preview"
						mode="aspectFill"
						lazy-load
						@tap="preview(item.index)"
					/>
					<text class="ai-image-label">AI生成</text>
				</view>
				<view class="rw-detail" @tap="openDetail(item)">
					<text class="rw-detail-text">详情</text>
				</view>
			</view>
		</view>

		<!-- 底部小字提示 -->
		<text class="recent-foot">作品保留 7 天 · 更久请到个人中心查看</text>
	</view>
</template>

<script setup>
import { computed } from 'vue'
import config from '@/utils/config.js'
import CachedGeneratedImage from '@/components/common/CachedGeneratedImage.vue'
import { previewGeneratedImages } from '@/utils/generated-image-cache.js'
import { useUserStore } from '@/store/user.js'

const props = defineProps({
	// 已展开的图项列表:[{ full, preview }]
	works: {
		type: Array,
		default: () => [],
	},
})

const emit = defineEmits(['detail'])
const userStore = useUserStore()
const currentUserId = computed(() => userStore.userInfo && userStore.userInfo.userId)

// 带索引的图项列表(供网格渲染与预览定位)
const resolvedList = computed(() =>
	props.works.map((item, index) => ({
		full: resolveUrl(item.full || item.raw),
		previewFull: resolveUrl(item.preview || item.full || item.raw),
		taskId: item.taskId,
		seq: item.seq,
		index,
		key: `${item.taskId || 'work'}-${item.seq || index + 1}`,
	}))
)

function openDetail(item) {
	if (!item.taskId || !item.seq) return
	emit('detail', { taskId: item.taskId, seq: item.seq })
}

// 相对路径拼 baseUrl
function resolveUrl(url) {
	if (!url) return ''
	if (/^https?:\/\//.test(url)) return url
	return config.baseUrl + url
}

// 点击预览,定位到当前图
function preview(index) {
	return previewGeneratedImages(
		resolvedList.value.map((it) => it.full),
		index,
		currentUserId.value
	)
}
</script>

<style lang="scss" scoped>
.recent-works {
	margin-top: 48rpx;
}

.recent-title {
	display: block;
	margin-bottom: 24rpx;
	font-size: 28rpx;
	font-weight: 600;
	color: #0f172a;
	line-height: 1;
}

/* 2 列等宽网格(与 ResultGrid 的 grid-2x2 同款) */
.rw-grid {
	display: grid;
	grid-template-columns: 1fr 1fr;
	gap: 20rpx;
}

/* 网格单元:固定 1:1 */
.rw-cell {
	position: relative;
	aspect-ratio: 1;
	border-radius: 24rpx;
	overflow: hidden;
	background: repeating-linear-gradient(135deg, #e2ecf8 0 14rpx, #eef4fc 14rpx 28rpx);
}

.ai-image-label {
	position: absolute;
	right: 12rpx;
	bottom: 12rpx;
	padding: 5rpx 11rpx;
	border-radius: 9rpx;
	background: rgba(15, 23, 42, 0.72);
	font-size: 19rpx;
	font-weight: 700;
	color: #ffffff;
	line-height: 1.2;
}

.rw-item {
	min-width: 0;
}

.rw-detail {
	height: 56rpx;
	margin-top: 10rpx;
	border-radius: 18rpx;
	background: #eef3fb;
	display: flex;
	align-items: center;
	justify-content: center;
}

.rw-detail-text {
	font-size: 24rpx;
	font-weight: 600;
	color: #4f84ea;
}

.rw-img {
	width: 100%;
	height: 100%;
	display: block;
}

.recent-foot {
	display: block;
	margin-top: 24rpx;
	font-size: 22rpx;
	color: #94a3b8;
	line-height: 1.4;
}
</style>
