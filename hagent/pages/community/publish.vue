<template>
	<view class="community-publish" :class="pagePerformanceClass">
		<AmNavBar title="发布动态" right-text="草稿" :fixed="false" @back="goBack" @right="saveDraft">
			<template #left>
				<text class="publish-close">×</text>
			</template>
		</AmNavBar>

		<scroll-view class="page-scroll" scroll-y :enhanced="scrollEnhanced" :bounces="scrollBounces" :show-scrollbar="scrollShowScrollbar" :lower-threshold="scrollLowerThreshold">
			<view class="publish-inner" :style="contentStyle">
				<AmCard class="publish-card" padding="0" radius="28rpx">
					<AmPostComposer
						v-model:content="content"
						v-model:files="files"
						:topics="selectedTopics"
						:mentions="mentions"
						:visibility="visibility"
						:location="location"
						:toolbar-actions="toolbarActions"
						@submit="submitPost"
						@cancel="goBack"
						@topic-tap="topicSheetVisible = true"
						@visibility-tap="visibilitySheetVisible = true"
						@location-tap="chooseLocation"
						@toolbar-action="handleToolbarAction"
					/>
				</AmCard>

				<AmTipCard
					class="publish-tip"
					title="发布建议"
					desc="内容尽量真实清晰，图片保持横竖比例一致，话题建议控制在 1-3 个。"
					icon="✧"
					clickable
					@tap="showToast('发布建议已展示')"
				/>
			</view>
		</scroll-view>

		<AmBottomSheet v-model:visible="topicSheetVisible" title="选择话题">
			<AmTopicSelector
				v-model:keyword="topicKeyword"
				:topics="topics"
				:selected-values="selectedTopics"
				@select="toggleTopic"
			/>
		</AmBottomSheet>

		<AmActionSheet
			:visible="visibilitySheetVisible"
			:actions="visibilityActions"
			cancel-text="取消"
			@close="visibilitySheetVisible = false"
			@select="selectVisibility"
		/>
	</view>
</template>

<script setup>
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { usePageScroll } from '@/composables/usePageScroll.js'
import { usePerformanceMode } from '@/composables/usePerformanceMode.js'

const content = ref('')
const files = ref([])
const selectedTopics = ref(['治愈系风景'])
const mentions = ref([])
const visibility = ref('公开')
const location = ref('')
const topicKeyword = ref('')
const topicSheetVisible = ref(false)
const visibilitySheetVisible = ref(false)
const { scrollEnhanced, scrollBounces, scrollShowScrollbar, scrollLowerThreshold, contentStyle, disablePageBounce } = usePageScroll({ extraBottom: '26rpx' })
const { pagePerformanceClass } = usePerformanceMode()

const topics = [
	{ title: '治愈系风景', desc: '28.4w 帖子', cover: '/static/home-bg.png' },
	{ title: '今日碎片', desc: '16.2w 帖子', cover: '/static/profile-bg.png' },
	{ title: '旅行日记', desc: '12.8w 帖子', cover: '/static/login-bg.png' },
	{ title: '周末计划', desc: '9.7w 帖子', cover: '/static/home-bg.png' },
]

const visibilityActions = [
	{ label: '公开', value: '公开' },
	{ label: '仅关注可见', value: '仅关注可见' },
	{ label: '仅自己可见', value: '仅自己可见' },
]

const toolbarActions = [
	{ key: 'mention', label: '提及好友', icon: '@' },
	{ key: 'emoji', label: '表情', icon: '☺' },
	{ key: 'poll', label: '投票', icon: '▥' },
	{ key: 'activity', label: '活动', icon: '□' },
]

onShow(() => {
	disablePageBounce()
})

function goBack() {
	uni.navigateBack()
}

function saveDraft() {
	showToast('草稿已保存')
}

function toggleTopic(topic) {
	const title = topic.title
	if (selectedTopics.value.includes(title)) {
		selectedTopics.value = selectedTopics.value.filter((item) => item !== title)
		return
	}
	if (selectedTopics.value.length >= 3) {
		showToast('最多选择 3 个话题')
		return
	}
	selectedTopics.value = selectedTopics.value.concat(title)
}

function selectVisibility(item) {
	visibility.value = item.value || item.label
	visibilitySheetVisible.value = false
}

function chooseLocation() {
	showToast('位置选择准备中')
}

function handleToolbarAction(item) {
	showToast(`${item.label}准备中`)
}

function submitPost() {
	if (!content.value.trim() && !files.value.length) {
		showToast('请输入内容或上传图片')
		return
	}
	uni.showToast({ title: '发布成功', icon: 'success' })
	setTimeout(() => {
		uni.navigateBack()
	}, 500)
}

function showToast(title) {
	uni.showToast({ title, icon: 'none' })
}
</script>

<style lang="scss" scoped>
.community-publish {
	height: 100vh;
	background: #f5f7fb;
	overflow: hidden;
	display: flex;
	flex-direction: column;
}

.page-scroll {
	flex: 1;
	min-height: 0;
}

.publish-inner {
	padding: 24rpx 28rpx 42rpx;
	box-sizing: border-box;
}

.publish-close {
	font-size: 52rpx;
	color: var(--am-color-text-title);
	line-height: 1;
	font-weight: 300;
}

.publish-card {
	overflow: hidden;
}

.publish-tip {
	margin-top: 34rpx;
}

:deep(.am-nav-bar) {
	border-bottom: none;
}
</style>
