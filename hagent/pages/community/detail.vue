<template>
	<view class="community-detail" :class="pagePerformanceClass">
		<AmNavBar title="帖子详情" right-text="更多" :fixed="false" @back="goBack" @right="reportVisible = true" />

		<scroll-view class="page-scroll" scroll-y :enhanced="scrollEnhanced" :bounces="scrollBounces" :show-scrollbar="scrollShowScrollbar" :lower-threshold="scrollLowerThreshold">
			<view class="detail-inner" :style="contentStyle">
				<AmCard padding="28rpx">
					<AmPostDetailHeader
						v-bind="post"
						@follow="toggleFollow"
						@more="reportVisible = true"
					/>
					<view class="content-wrap">
						<AmPostContent
							:title="post.title"
							:content="post.content"
							:images="post.images"
							:topics="post.topics"
							@preview="previewImages"
						/>
					</view>
					<AmActionBar :actions="actions" @action="handleAction" />
				</AmCard>

				<AmCard class="comment-card" padding="28rpx">
					<AmSection title="全部评论" more-text="排序" @more="showToast('排序功能待接入')">
						<view class="comment-list">
							<AmCommentItem
								v-for="comment in comments"
								:key="comment.id"
								v-bind="comment"
								@reply="replyTo(comment)"
								@like="showToast('已点赞')"
							/>
						</view>
					</AmSection>
				</AmCard>
			</view>
		</scroll-view>

		<AmCommentComposer
			v-model="commentText"
			:reply-to="replyTarget"
			placeholder="说点什么吧..."
			fixed
			@send="sendComment"
			@clear-reply="replyTarget = ''"
		/>
		<AmProfileSheet v-model:visible="profileVisible" :profile="profile" @action="showToast('已进入沟通流程')" />
		<AmReportSheet :visible="reportVisible" @close="reportVisible = false" @select="handleReportSelect" />
	</view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { usePageScroll } from '@/composables/usePageScroll.js'
import { usePerformanceMode } from '@/composables/usePerformanceMode.js'

const postId = ref('')
const commentText = ref('')
const replyTarget = ref('')
const reportVisible = ref(false)
const profileVisible = ref(false)
const { scrollEnhanced, scrollBounces, scrollShowScrollbar, scrollLowerThreshold, contentStyle, disablePageBounce } = usePageScroll({ inputBar: true, extraBottom: '36rpx' })
const { pagePerformanceClass } = usePerformanceMode()

const post = ref({
	avatar: '/static/default-avatar.png',
	name: '小满同学',
	desc: '@xiaoman · 社区达人',
	tag: '认证',
	time: '2小时前',
	views: '2.3w',
	followed: false,
	title: '海边日落真的太治愈了',
	content: '今天一个人去了海边，吹吹晚风，看着太阳慢慢落下，整个人都放松下来。生活很匆忙，别忘了偶尔停下来，看看这个世界的美好。',
	images: ['/static/home-bg.png', '/static/profile-bg.png', '/static/login-bg.png'],
	topics: ['海边日落', '治愈系风景', '生活碎片'],
})

const comments = ref([
	{ id: 1, avatar: '/static/default-avatar.png', author: '阿森', content: '这个色调真的很舒服，像把一天都慢下来了。', time: '15分钟前', likeText: '32' },
	{ id: 2, avatar: '/static/default-avatar.png', author: '小美', content: '下次也想去海边走走，收藏路线了。', time: '28分钟前', likeText: '18' },
	{ id: 3, avatar: '', author: '风里', content: '照片很有氛围，特别喜欢第一张。', time: '1小时前', likeText: '9' },
])

const profile = computed(() => ({
	name: post.value.name,
	avatar: post.value.avatar,
	desc: post.value.desc,
	tags: ['社区达人', '高质量内容'],
	stats: [
		{ label: '帖子', value: '128' },
		{ label: '关注', value: '246' },
		{ label: '粉丝', value: '2.4w' },
	],
	info: [
		{ label: '所在地', value: '上海' },
		{ label: '加入时间', value: '2024-05' },
		{ label: '最近活跃', value: '刚刚在线' },
	],
}))

const actions = computed(() => [
	{ key: 'like', icon: '♥', label: '点赞', count: '1.2k', active: true },
	{ key: 'comment', icon: '◎', label: '评论', count: comments.value.length },
	{ key: 'star', icon: '☆', label: '收藏', count: '256' },
	{ key: 'share', icon: '↗', label: '分享', count: '112' },
])

onLoad((query) => {
	postId.value = query?.id || ''
})

onShow(() => {
	disablePageBounce()
})

function goBack() {
	uni.navigateBack()
}

function toggleFollow() {
	post.value.followed = !post.value.followed
	showToast(post.value.followed ? '已关注' : '已取消关注')
}

function previewImages(index) {
	uni.previewImage({
		urls: post.value.images,
		current: post.value.images[index] || post.value.images[0],
	})
}

function handleAction(action) {
	if (action.key === 'comment') return
	if (action.key === 'share') {
		reportVisible.value = true
		return
	}
	showToast(action.label)
}

function replyTo(comment) {
	replyTarget.value = comment.author
}

function sendComment(value) {
	const content = value || commentText.value
	if (!content.trim()) return
	comments.value.unshift({
		id: Date.now(),
		avatar: '/static/default-avatar.png',
		author: '我',
		content,
		time: '刚刚',
		likeText: '0',
	})
	commentText.value = ''
	replyTarget.value = ''
	showToast('评论已发布')
}

function handleReportSelect(item) {
	reportVisible.value = false
	showToast(item.label || '已处理')
}

function showToast(title) {
	uni.showToast({ title, icon: 'none' })
}
</script>

<style lang="scss" scoped>
.community-detail {
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

.detail-inner {
	padding: 24rpx;
	box-sizing: border-box;
}

.content-wrap {
	margin: 24rpx 0;
}

.comment-card {
	margin-top: 22rpx;
}

.comment-list {
	display: flex;
	flex-direction: column;
	gap: 28rpx;
}
</style>
