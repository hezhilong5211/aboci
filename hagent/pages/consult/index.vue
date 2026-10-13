<template>
	<view class="consult-page" :class="pagePerformanceClass" :style="pageVars" @touchstart="handleTouchStart" @touchend="handleTouchEnd">
		<AmNavBar title="在线咨询" subtitle="顾问小林正在为您服务" right-text="资料" @back="goBack" @right="openProfile" />

		<scroll-view class="consult-scroll" scroll-y :enhanced="scrollEnhanced" :bounces="scrollBounces" :show-scrollbar="scrollShowScrollbar" :lower-threshold="scrollLowerThreshold" :scroll-into-view="lastMessageId">
			<view class="consult-inner" :style="contentStyle">
				<view class="consult-notice">
					<text class="consult-notice-text">本页面为 AM-UI 在线咨询演示，点击对方头像或右上角可查看资料。</text>
				</view>
				<view
					class="consult-message"
					v-for="message in messages"
					:key="message.id"
					:id="`message-${message.id}`"
				>
					<AmChatBubble
						:type="message.type"
						:avatar-text="message.avatarText"
						:name="message.name"
						:text="message.text"
						:time="message.time"
						:status="message.status"
						@avatar-tap="handleAvatarTap(message)"
					/>
				</view>
			</view>
		</scroll-view>

		<view class="consult-input">
			<AmInput v-model="inputValue" placeholder="请输入咨询内容" clearable />
			<AmButton text="发送" @tap="sendMessage" />
		</view>

		<AmProfileSheet
			:visible="showProfile"
			:profile="consultantProfile"
			button-text="继续咨询"
			@close="showProfile = false"
			@action="showProfile = false"
		/>
	</view>
</template>

<script setup>
import { computed, nextTick, ref } from 'vue'
import { usePerformanceMode } from '@/composables/usePerformanceMode.js'
import { usePageScroll } from '@/composables/usePageScroll.js'
import { usePageGesture } from '@/composables/usePageGesture.js'

const { pagePerformanceClass } = usePerformanceMode()
const { scrollEnhanced, scrollBounces, scrollShowScrollbar, scrollLowerThreshold, contentStyle } = usePageScroll({ inputBar: true })
const { handleTouchStart, handleTouchEnd } = usePageGesture({ onBack: goBack })
const statusBarHeight = ref(44)
const showProfile = ref(false)
const inputValue = ref('')
const lastMessageId = ref('')

uni.getSystemInfo({
	success: (info) => {
		statusBarHeight.value = info.statusBarHeight || 44
	},
})

const pageVars = computed(() => ({
	'--status-bar-height': `${statusBarHeight.value}px`,
}))

const consultantProfile = {
	name: '顾问小林',
	avatar: '',
	desc: '高级客户顾问，擅长账户安全、会员服务与订单处理',
	tags: ['在线', '认证顾问', '响应快'],
	stats: [
		{ label: '满意度', value: '98%' },
		{ label: '服务数', value: '1.2k' },
		{ label: '平均响应', value: '1分钟' },
	],
	info: [
		{ label: '服务时间', value: '09:00-21:00' },
		{ label: '所属团队', value: '客户成功组' },
		{ label: '擅长问题', value: '会员/订单/售后' },
	],
}

const messages = ref([
	{
		id: 1,
		type: 'other',
		avatarText: '顾',
		name: '顾问小林',
		text: '您好，我是顾问小林，可以帮您查看会员服务、订单进度和账户安全问题。',
		time: '10:20',
	},
	{
		id: 2,
		type: 'self',
		avatarText: '我',
		name: '我',
		text: '我想了解会员服务的权益和开通方式。',
		time: '10:21',
		status: '已送达',
	},
	{
		id: 3,
		type: 'other',
		avatarText: '顾',
		name: '顾问小林',
		text: '可以的。会员权益包含优先响应、专属资料和订单进度提醒，我可以先为您展示适合的方案。',
		time: '10:22',
	},
])

function openProfile() {
	showProfile.value = true
}

function goBack() {
	uni.navigateBack()
}

function handleAvatarTap(message) {
	if (message.type !== 'other') return
	openProfile()
}

function getTimeText() {
	const date = new Date()
	const hour = String(date.getHours()).padStart(2, '0')
	const minute = String(date.getMinutes()).padStart(2, '0')
	return `${hour}:${minute}`
}

function scrollToBottom() {
	nextTick(() => {
		const last = messages.value[messages.value.length - 1]
		lastMessageId.value = last ? `message-${last.id}` : ''
	})
}

function sendMessage() {
	const text = inputValue.value.trim()
	if (!text) return
	const nextId = messages.value.length + 1
	messages.value.push({
		id: nextId,
		type: 'self',
		avatarText: '我',
		name: '我',
		text,
		time: getTimeText(),
		status: '已发送',
	})
	inputValue.value = ''
	messages.value.push({
		id: nextId + 1,
		type: 'other',
		avatarText: '顾',
		name: '顾问小林',
		text: '已收到，我会根据您的问题继续为您整理处理建议。',
		time: getTimeText(),
	})
	scrollToBottom()
}
</script>

<style lang="scss" scoped>
.consult-page {
	height: 100vh;
	background: var(--am-color-bg-page);
	position: relative;
	overflow: hidden;
}

.consult-scroll {
	height: 100vh;
}

.consult-inner {
	min-height: 100vh;
	padding: calc(var(--status-bar-height) + 116rpx) 28rpx 0;
	box-sizing: border-box;
}

.consult-notice {
	padding: 18rpx 22rpx;
	border-radius: 18rpx;
	background: var(--am-color-primary-weak);
	margin-bottom: 26rpx;
}

.consult-notice-text {
	font-size: 22rpx;
	color: var(--am-color-primary);
	line-height: 34rpx;
}

.consult-input {
	position: fixed;
	left: 0;
	right: 0;
	bottom: 0;
	z-index: 25;
	padding: 18rpx 28rpx calc(18rpx + env(safe-area-inset-bottom));
	background: #ffffff;
	border-top: 1rpx solid var(--am-color-border-light);
	display: grid;
	grid-template-columns: 1fr auto;
	gap: 16rpx;
	box-sizing: border-box;
}
</style>
