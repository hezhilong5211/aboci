<template>
	<view class="works-page" :class="pagePerformanceClass">
		<!-- 自绘导航栏:返回箭头 + 标题，paddingTop 留状态栏安全区 -->
		<view class="nav-bar" :style="{ paddingTop: statusBarHeight + 'px' }">
			<view class="nav-inner">
				<view class="nav-back" @tap="goBack">
					<text class="back-icon">‹</text>
				</view>
				<text class="nav-title">我的作品</text>
				<!-- 右侧刷新:重置到第 1 页重新拉取 -->
				<view class="nav-refresh" :class="{ 'is-loading': loading }" @tap="refreshWorks">
					<text class="refresh-icon">⟳</text>
				</view>
			</view>
		</view>

		<!-- 内容滚动区 -->
		<scroll-view
			class="content"
			scroll-y
			:enhanced="scrollEnhanced"
			:bounces="scrollBounces"
			:show-scrollbar="scrollShowScrollbar"
			:lower-threshold="scrollLowerThreshold"
			@scrolltolower="loadMore"
		>
			<view class="content-inner" :style="[contentStyle, { paddingTop: (statusBarHeight + 44) + 'px' }]">
				<text class="page-tip" v-if="works.length">按生成批次展示</text>
				<view class="batch-list" v-if="works.length">
					<view
						class="batch-card"
						v-for="item in works"
						:key="item.taskId"
						@tap="openBatch(item)"
					>
						<view class="batch-mosaic" :class="'count-' + Math.min(batchPreviews(item).length, 4)">
							<view class="mosaic-item" v-for="preview in batchPreviews(item)" :key="preview.seq">
								<CachedGeneratedImage
									class="mosaic-image"
									:src="preview.previewUrl"
									:fallback-src="preview.originalUrl"
									:user-id="currentUserId"
									cache-type="preview"
									mode="aspectFill"
									lazy-load
								/>
								<text class="ai-image-label">AI生成</text>
							</view>
						</view>
						<view class="batch-body">
							<view class="batch-tags">
								<text class="source-tag" :class="{ agent: item.sourceType === 'AGENT' }">
									{{ sourceLabel(item.sourceType) }}
								</text>
								<text class="count-tag">成功 {{ item.successCount || 0 }}/{{ item.plannedCount || 1 }}</text>
								<text class="reference-tag" v-if="item.hasReferences">有参考图</text>
								<text class="failed-tag" v-if="item.failedCount">失败 {{ item.failedCount }}</text>
							</view>
							<text class="batch-summary am-selectable-text" user-select>{{ item.promptSummary || item.prompt || '未命名作品' }}</text>
							<view class="batch-footer">
								<text class="batch-time">{{ item.createTime }}</text>
								<text class="detail-link">查看详情 ›</text>
							</view>
						</view>
					</view>
				</view>

				<!-- 加载更多 / 触底提示 -->
				<view class="list-footer" v-if="works.length">
					<text class="footer-text" v-if="loading">加载中...</text>
					<text class="footer-text" v-else-if="!hasMore">没有更多了</text>
				</view>

				<!-- 空态 -->
				<view class="empty" v-if="!works.length && !loading">
					<text class="empty-text">还没有作品</text>
				</view>
			</view>
		</scroll-view>
	</view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import config from '@/utils/config.js'
import { getWorks } from '@/api/agent.js'
import { usePerformanceMode } from '@/composables/usePerformanceMode.js'
import { usePageScroll } from '@/composables/usePageScroll.js'
import { useUserStore } from '@/store/user.js'
import CachedGeneratedImage from '@/components/common/CachedGeneratedImage.vue'

const { pagePerformanceClass } = usePerformanceMode()
const { scrollEnhanced, scrollBounces, scrollShowScrollbar, scrollLowerThreshold, contentStyle } = usePageScroll()
const userStore = useUserStore()
const currentUserId = computed(() => userStore.userInfo && userStore.userInfo.userId)

// 状态栏高度（自绘导航栏安全区）
const statusBarHeight = ref(44)
uni.getSystemInfo({
	success: (info) => {
		statusBarHeight.value = info.statusBarHeight || 44
	},
})

// 作品列表（分页累加）
const works = ref([])
const pageNum = ref(1)
const pageSize = 20
const total = ref(0)
const loading = ref(false)
// 是否还有下一页：已加载条数 < 总数
const hasMore = ref(true)

/**
 * 拼接图片可访问地址。
 * 后端返回 /profile/* 相对路径，需拼 baseUrl；已是 http 的原样返回。
 */
function resolveUrl(url) {
	if (!url) return ''
	return url.startsWith('http') ? url : config.baseUrl + url
}

function batchPreviews(item) {
	const originals = (item && item.resultUrls) || []
	const previews = (item && item.previewUrls) || []
	return originals
		.map((url, index) => ({
			seq: index + 1,
			originalUrl: resolveUrl(url),
			previewUrl: resolveUrl(previews[index] || url),
		}))
		.filter((image) => image.originalUrl)
		.slice(0, 4)
}

function sourceLabel(sourceType) {
	return sourceType === 'AGENT' ? 'Agent 对话' : '生成页面'
}

/** 拉取一页作品并累加 */
async function fetchWorks() {
	if (loading.value || !hasMore.value) return
	loading.value = true
	try {
		const res = await getWorks(pageNum.value, pageSize)
		const records = (res && res.records) || []
		total.value = (res && res.total) || 0
		works.value = works.value.concat(records)
		hasMore.value = works.value.length < total.value
		if (hasMore.value) {
			pageNum.value += 1
		}
	} catch (e) {
		// 错误已由 request 统一提示，此处停止后续加载，避免空循环
		hasMore.value = false
	} finally {
		loading.value = false
	}
}

/** 触底加载下一页 */
function loadMore() {
	fetchWorks()
}

/** 刷新:清空并从第 1 页重拉(loading 中忽略,避免并发) */
function refreshWorks() {
	if (loading.value) return
	works.value = []
	pageNum.value = 1
	total.value = 0
	hasMore.value = true
	fetchWorks()
}

/** 批次卡片只展示摘要，完整内容统一进入相册详情页。 */
function openBatch(item) {
	if (!item || !item.taskId) return
	uni.navigateTo({
		url: `/pages/user/works/detail?taskId=${encodeURIComponent(item.taskId)}`,
	})
}

/** 返回上一页 */
function goBack() {
	uni.navigateBack()
}

onLoad(() => {
	fetchWorks()
})
</script>

<style lang="scss" scoped>
.works-page {
	min-height: 100vh;
	background-color: #F5F6FA;
}

/* ====== 自绘导航栏 ====== */
.nav-bar {
	position: fixed;
	top: 0;
	left: 0;
	right: 0;
	z-index: 30;
	background-color: #FFFFFF;
}

.nav-inner {
	height: 44px;
	display: flex;
	align-items: center;
	justify-content: space-between;
	padding: 0 16rpx;
}

.nav-back {
	width: 72rpx;
	height: 72rpx;
	display: flex;
	align-items: center;
	justify-content: center;
}

.back-icon {
	font-size: 52rpx;
	color: #1A1A1A;
	font-weight: 300;
}

.nav-title {
	font-size: 34rpx;
	font-weight: 600;
	color: #1A1A1A;
}

/* 右侧刷新按钮:与返回箭头同尺寸对称 */
.nav-refresh {
	width: 72rpx;
	height: 72rpx;
	display: flex;
	align-items: center;
	justify-content: center;
}

.nav-refresh:active {
	opacity: 0.6;
}

.refresh-icon {
	font-size: 40rpx;
	color: #1A1A1A;
	line-height: 1;
}

/* 加载中:图标持续旋转 */
.nav-refresh.is-loading .refresh-icon {
	animation: refresh-spin 0.8s linear infinite;
}

@keyframes refresh-spin {
	from { transform: rotate(0deg); }
	to { transform: rotate(360deg); }
}

/* ====== 内容区 ====== */
.content {
	height: 100vh;
}

.content-inner {
	box-sizing: border-box;
	padding-left: 24rpx;
	padding-right: 24rpx;
}

.page-tip {
	display: block;
	padding-top: 24rpx;
	font-size: 24rpx;
	color: #9297A3;
}

.batch-list {
	display: flex;
	flex-direction: column;
	gap: 24rpx;
	padding-top: 16rpx;
}

.batch-card {
	border-radius: 24rpx;
	background: #FFFFFF;
	overflow: hidden;
	box-shadow: 0 8rpx 28rpx rgba(20, 20, 40, 0.07);
	transition: transform 0.15s ease, opacity 0.15s ease;
}

.batch-card:active {
	transform: scale(0.99);
	opacity: 0.96;
}

.batch-mosaic {
	display: grid;
	grid-template-columns: repeat(2, 1fr);
	grid-template-rows: repeat(2, 160rpx);
	gap: 4rpx;
	height: 324rpx;
	background: #ECEEF3;
}

.batch-mosaic.count-1 {
  display: block;
}

.batch-mosaic.count-1 .mosaic-item {
  height: 100%;
}

.batch-mosaic.count-2 {
	grid-template-rows: 324rpx;
}

.mosaic-image {
	display: block;
	width: 100%;
	height: 100%;
	background: #ECEEF3;
}

.mosaic-item {
	position: relative;
	min-width: 0;
	min-height: 0;
	overflow: hidden;
}

.ai-image-label {
	position: absolute;
	right: 8rpx;
	bottom: 8rpx;
	padding: 4rpx 9rpx;
	border-radius: 8rpx;
	background: rgba(15, 23, 42, 0.72);
	font-size: 17rpx;
	font-weight: 700;
	color: #ffffff;
	line-height: 1.2;
}

.batch-body {
	padding: 22rpx 24rpx 24rpx;
}

.batch-tags {
	display: flex;
	align-items: center;
	flex-wrap: wrap;
	gap: 10rpx;
}

.source-tag,
.count-tag,
.reference-tag,
.failed-tag {
	display: inline-flex;
	align-items: center;
	height: 40rpx;
	padding: 0 14rpx;
	border-radius: 20rpx;
	font-size: 21rpx;
}

.source-tag {
	color: #2262C6;
	background: #EAF2FF;
}

.source-tag.agent {
	color: #7044BC;
	background: #F1EBFF;
}

.count-tag {
	color: #3E4653;
	background: #F1F3F6;
}

.reference-tag {
	color: #197D5A;
	background: #E9F8F2;
}

.failed-tag {
	color: #BE4B4B;
	background: #FFF0F0;
}

.batch-summary {
	display: -webkit-box;
	margin-top: 18rpx;
	overflow: hidden;
	-webkit-box-orient: vertical;
	-webkit-line-clamp: 2;
	font-size: 28rpx;
	font-weight: 500;
	line-height: 1.5;
	color: #17191F;
}

.batch-footer {
	display: flex;
	align-items: center;
	justify-content: space-between;
	margin-top: 18rpx;
}

.batch-time {
	font-size: 22rpx;
	color: #9A9EA8;
}

.detail-link {
	font-size: 24rpx;
	color: #4F84EA;
}

/* ====== 列表底部 ====== */
.list-footer {
	display: flex;
	align-items: center;
	justify-content: center;
	padding: 28rpx 0;
}

.footer-text {
	font-size: 24rpx;
	color: #9A9EA8;
}

/* ====== 空态 ====== */
.empty {
	display: flex;
	align-items: center;
	justify-content: center;
	padding-top: 200rpx;
}

.empty-text {
	font-size: 28rpx;
	color: #9A9EA8;
}

/* 性能降级:去阴影 */
.am-performance-light .batch-card {
	box-shadow: none;
}
</style>
