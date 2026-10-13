<template>
	<view class="favorites-page" :class="pagePerformanceClass">
		<view class="nav-bar" :style="{ paddingTop: statusBarHeight + 'px' }">
			<view class="nav-inner">
				<view class="nav-back" @tap="goBack">
					<text class="back-icon">‹</text>
				</view>
				<text class="nav-title">我的收藏</text>
				<view class="nav-placeholder" />
			</view>
		</view>

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
				<view v-if="favorites.length" class="favorite-list">
					<view
						v-for="item in favorites"
						:key="item.id"
						class="favorite-card"
						hover-class="favorite-card--pressed"
						:hover-stay-time="70"
						@tap="useFavorite(item)"
					>
						<view class="favorite-head">
							<text class="favorite-name">{{ item.name }}</text>
							<text class="favorite-date">{{ formatDate(item.createTime) }}</text>
						</view>
						<text class="favorite-content">{{ item.content }}</text>
						<view class="favorite-foot">
							<text class="favorite-use">点击填入对话</text>
							<text class="favorite-delete" @tap.stop="confirmDelete(item)">删除</text>
						</view>
					</view>
				</view>

				<view v-else-if="!loading" class="empty-state">
					<view class="empty-icon">
						<text class="empty-icon-text">☆</text>
					</view>
					<text class="empty-title">暂无收藏</text>
					<text class="empty-sub">在对话提示词下方点击“收藏”即可保存</text>
				</view>

				<view v-if="loading || favorites.length" class="list-footer">
					<text class="footer-text" v-if="loading">加载中...</text>
					<text class="footer-text" v-else-if="!hasMore">没有更多了</text>
				</view>
			</view>
		</scroll-view>
	</view>
</template>

<script setup>
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { deletePromptFavorite, listPromptFavorites } from '@/api/agent.js'
import { useConversationStore } from '@/store/conversation.js'
import { useTabbarStore } from '@/store/tabbar.js'
import { usePerformanceMode } from '@/composables/usePerformanceMode.js'
import { usePageScroll } from '@/composables/usePageScroll.js'
import { useStatusBarHeight } from '@/composables/useStatusBarHeight.js'

const conversationStore = useConversationStore()
const tabbarStore = useTabbarStore()
const { pagePerformanceClass } = usePerformanceMode()
const { scrollEnhanced, scrollBounces, scrollShowScrollbar, scrollLowerThreshold, contentStyle } = usePageScroll()
const statusBarHeight = useStatusBarHeight()

const favorites = ref([])
const pageNum = ref(1)
const pageSize = 10
const total = ref(0)
const loading = ref(false)
const hasMore = ref(true)

function goBack() {
	uni.navigateBack()
}

async function fetchFavorites(reset = false) {
	if (loading.value) return
	if (reset) {
		pageNum.value = 1
		total.value = 0
		hasMore.value = true
		favorites.value = []
	}
	if (!hasMore.value) return
	loading.value = true
	try {
		const page = await listPromptFavorites(pageNum.value, pageSize)
		const records = Array.isArray(page?.records) ? page.records : []
		total.value = Number(page?.total) || 0
		favorites.value = favorites.value.concat(records)
		hasMore.value = favorites.value.length < total.value
		if (hasMore.value) pageNum.value += 1
	} catch (e) {
		hasMore.value = false
	} finally {
		loading.value = false
	}
}

function loadMore() {
	fetchFavorites()
}

function useFavorite(item) {
	if (!item || !item.content) return
	conversationStore.setPromptDraft(item.content)
	tabbarStore.switchTo('chat')
	uni.switchTab({ url: '/pages/index/index' })
}

function confirmDelete(item) {
	if (!item || item.id == null) return
	uni.showModal({
		title: '删除收藏',
		content: `确定删除“${item.name}”吗？`,
		confirmColor: '#ef4444',
		success: async (res) => {
			if (!res.confirm) return
			try {
				await deletePromptFavorite(item.id)
				uni.showToast({ title: '已删除', icon: 'success' })
				await fetchFavorites(true)
			} catch (e) {
				// 业务错误由请求层统一提示。
			}
		},
	})
}

function formatDate(value) {
	if (!value) return ''
	return String(value).replace('T', ' ').slice(0, 16)
}

onLoad(() => {
	fetchFavorites()
})
</script>

<style lang="scss" scoped>
.favorites-page {
	min-height: 100vh;
	background: #f6f7fb;
}

.nav-bar {
	position: fixed;
	top: 0;
	left: 0;
	right: 0;
	z-index: 30;
	background: #f6f7fb;
}

.nav-inner {
	height: 44px;
	display: flex;
	align-items: center;
	justify-content: space-between;
	padding: 0 16rpx;
}

.nav-back,
.nav-placeholder {
	width: 72rpx;
	height: 72rpx;
	display: flex;
	align-items: center;
	justify-content: center;
}

.back-icon {
	font-size: 52rpx;
	font-weight: 300;
	color: #0f172a;
}

.nav-title {
	font-size: 34rpx;
	font-weight: 600;
	color: #0f172a;
}

.content {
	height: 100vh;
}

.content-inner {
	box-sizing: border-box;
	padding-left: 32rpx;
	padding-right: 32rpx;
}

.favorite-list {
	display: flex;
	flex-direction: column;
	gap: 20rpx;
	padding-top: 24rpx;
}

.favorite-card {
	padding: 28rpx;
	border: 1rpx solid rgba(96, 140, 224, 0.12);
	border-radius: 28rpx;
	background: #fff;
	box-shadow: 0 8rpx 28rpx rgba(20, 20, 40, 0.05);
}

.favorite-card--pressed {
	opacity: 0.88;
}

.favorite-head {
	display: flex;
	align-items: center;
	gap: 20rpx;
}

.favorite-name {
	flex: 1;
	min-width: 0;
	overflow: hidden;
	text-overflow: ellipsis;
	white-space: nowrap;
	font-size: 30rpx;
	font-weight: 700;
	color: #172033;
}

.favorite-date {
	flex-shrink: 0;
	font-size: 22rpx;
	color: #a0a6b2;
}

.favorite-content {
	display: -webkit-box;
	margin-top: 18rpx;
	overflow: hidden;
	-webkit-box-orient: vertical;
	-webkit-line-clamp: 5;
	font-size: 27rpx;
	line-height: 1.65;
	color: #5b6475;
	white-space: pre-wrap;
	word-break: break-word;
}

.favorite-foot {
	display: flex;
	align-items: center;
	justify-content: space-between;
	margin-top: 24rpx;
	padding-top: 18rpx;
	border-top: 1rpx solid #f0f2f5;
}

.favorite-use {
	font-size: 24rpx;
	font-weight: 600;
	color: #4f84ea;
}

.favorite-delete {
	padding: 8rpx 0 8rpx 24rpx;
	font-size: 24rpx;
	color: #a0a6b2;
}

.empty-state {
	display: flex;
	flex-direction: column;
	align-items: center;
	padding-top: 240rpx;
}

.empty-icon {
	width: 108rpx;
	height: 108rpx;
	border-radius: 32rpx;
	background: #deeafb;
	display: flex;
	align-items: center;
	justify-content: center;
}

.empty-icon-text {
	font-size: 62rpx;
	line-height: 1;
	color: #4f84ea;
}

.empty-title {
	margin-top: 28rpx;
	font-size: 30rpx;
	font-weight: 600;
	color: #334155;
}

.empty-sub {
	margin-top: 12rpx;
	font-size: 24rpx;
	color: #94a3b8;
}

.list-footer {
	padding: 32rpx 0 20rpx;
	text-align: center;
}

.footer-text {
	font-size: 24rpx;
	color: #a0a6b2;
}

.am-performance-light .favorite-card {
	box-shadow: none;
}
</style>
