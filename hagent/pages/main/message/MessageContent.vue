<template>
	<view class="message-content" :class="pagePerformanceClass">
		<view class="message-header" :style="{ paddingTop: statusBarHeight + 'px' }">
			<view class="message-title-row">
				<text class="message-title">消息</text>
				<view class="message-actions">
					<text class="message-action" @tap="showToast('搜索消息')">⌕</text>
					<text class="message-action" @tap="showToast('消息设置')">∷</text>
				</view>
			</view>
		</view>

		<scroll-view class="message-scroll" scroll-y :enhanced="scrollEnhanced" :bounces="scrollBounces" :show-scrollbar="scrollShowScrollbar" :lower-threshold="scrollLowerThreshold">
			<view class="message-main" :style="[contentStyle, { paddingTop: headerHeight }]">
				<AmCard padding="26rpx 22rpx" radius="28rpx">
					<AmMessageEntryGrid :items="messageEntries" @tap="handleEntryTap" />
				</AmCard>

				<view class="message-list">
					<AmMessageListItem
						v-for="item in messages"
						:key="item.title + item.time"
						:message="item"
						@tap="handleMessageTap"
					/>
				</view>
			</view>
		</scroll-view>
	</view>
</template>

<script setup>
import { ref } from 'vue'
import { usePageScroll } from '@/composables/usePageScroll.js'
import { usePerformanceMode } from '@/composables/usePerformanceMode.js'

const { pagePerformanceClass } = usePerformanceMode()
const { scrollEnhanced, scrollBounces, scrollShowScrollbar, scrollLowerThreshold, contentStyle } = usePageScroll({ tabbar: true })
const statusBarHeight = ref(44)
const headerHeight = ref('96px')

uni.getSystemInfo({
	success: (info) => {
		statusBarHeight.value = info.statusBarHeight || 44
		headerHeight.value = `${statusBarHeight.value + 52}px`
	},
})

const messageEntries = [
	{ key: 'mention', label: '@我的', icon: '@', badge: 6, color: '#2f6bff', background: '#eaf2ff' },
	{ key: 'comment', label: '评论', icon: '●', badge: 12, color: '#16b86a', background: '#e8fbf1' },
	{ key: 'like', label: '点赞', icon: '♡', badge: 24, color: '#ff4455', background: '#fff0f2' },
	{ key: 'favorite', label: '收藏', icon: '★', badge: 8, color: '#ff9500', background: '#fff4df' },
	{ key: 'system', label: '系统', icon: '♟', badge: 0, color: '#625bff', background: '#f0eeff' },
]

const messages = ref([
	{ title: '卡卡西', desc: '评论了你的帖子：这个地方太美了吧！', time: '2分钟前', avatar: '/static/default-avatar.png', unread: true },
	{ title: '旅行日记', desc: '点赞了你的帖子', time: '10分钟前', avatar: '/static/profile-bg.png', cover: '/static/home-bg.png' },
	{ title: '小黄', desc: '关注了你', time: '30分钟前', avatar: '/static/login-bg.png' },
	{ title: '官方通知', desc: '社区规则更新通知', time: '3小时前', avatar: '', nameText: '官', unread: true },
	{ title: '系统通知', desc: '你的账号已在新设备登录', time: '昨天', avatar: '', nameText: '系' },
	{ title: '设计交流群', desc: '老张：欢迎新朋友 👋', time: '昨天', avatar: '/static/home-bg.png', muted: true },
	{ title: '美食分享会', desc: '[有人@你] 小明：这个教程很棒！', time: '2天前', avatar: '/static/login-bg.png', unread: true },
	{ title: '活动助手', desc: '报名成功通知：周末摄影活动', time: '3天前', avatar: '', nameText: '活' },
])

function handleEntryTap(item) {
	showToast(item.label)
}

function handleMessageTap(item) {
	item.unread = false
	showToast(item.title)
}

function showToast(title) {
	uni.showToast({ title, icon: 'none' })
}
</script>

<style lang="scss" scoped>
.message-content {
	height: 100%;
	background: #f5f7fb;
	position: relative;
	overflow: hidden;
}

.message-header {
	position: absolute;
	top: 0;
	left: 0;
	right: 0;
	z-index: 5;
	background: rgba(255, 255, 255, 0.96);
}

.message-title-row {
	height: 104rpx;
	padding: 0 30rpx;
	display: flex;
	align-items: center;
	justify-content: center;
	position: relative;
	box-sizing: border-box;
}

.message-title {
	font-size: 32rpx;
	color: var(--am-color-text-title);
	font-weight: 800;
	line-height: 42rpx;
}

.message-actions {
	position: absolute;
	right: 30rpx;
	display: flex;
	align-items: center;
	gap: 26rpx;
}

.message-action {
	font-size: 38rpx;
	color: var(--am-color-text-title);
	line-height: 1;
}

.message-scroll {
	height: 100%;
}

.message-main {
	padding: 24rpx 28rpx 0;
	box-sizing: border-box;
}

.message-list {
	margin-top: 26rpx;
	padding: 8rpx 22rpx 28rpx;
	border-radius: 28rpx;
	background: #ffffff;
	box-shadow: var(--am-shadow-card);
	box-sizing: border-box;
}

.am-performance-light .message-list {
	box-shadow: none;
}
</style>
