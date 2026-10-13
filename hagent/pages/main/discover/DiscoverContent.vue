<template>
	<view class="discover-page" :class="pagePerformanceClass">
		<AmSearchNavBar
			v-model="keyword"
			:show-back="false"
			placeholder="搜索帖子、话题、用户"
			right-text="发布"
			@right="goPublish"
			@search="handleSearch"
		/>

		<scroll-view class="page-scroll" scroll-y :enhanced="scrollEnhanced" :bounces="scrollBounces" :show-scrollbar="scrollShowScrollbar" :lower-threshold="scrollLowerThreshold">
			<view class="discover-inner" :style="contentStyle">
				<AmCard class="discover-hero" padding="28rpx">
					<view class="hero-main">
						<text class="hero-kicker">Aurora Community</text>
						<text class="hero-title">发现社区里的真实经验</text>
						<text class="hero-desc">浏览热门帖子、关注话题，也可以发布自己的动态。</text>
					</view>
					<AmCommunityStats :items="communityStats" />
				</AmCard>

				<AmCard class="tabs-card" padding="0">
					<AmTabs v-model="activeTab" :options="tabs" />
				</AmCard>

				<view class="section-block">
					<AmSection title="热门话题" more-text="全部" @more="showToast('话题广场准备中')">
						<scroll-view class="topic-scroll" scroll-x :show-scrollbar="false">
							<view class="topic-row">
								<AmTopicCard
									v-for="topic in topics"
									:key="topic.title"
									class="topic-card"
									v-bind="topic"
									@tap="selectTopic(topic)"
									@follow="toggleTopic(topic)"
								/>
							</view>
						</scroll-view>
					</AmSection>
				</view>

				<view class="side-grid">
					<AmCard padding="24rpx">
						<AmSection title="推荐关注">
							<view class="follow-list">
								<AmUserFollowCard
									v-for="user in followUsers"
									:key="user.name"
									v-bind="user"
									@tap="showProfile(user)"
									@follow="toggleUser(user)"
								/>
							</view>
						</AmSection>
					</AmCard>
					<AmCard padding="24rpx">
						<AmSection title="社区动态">
							<AmNotificationItem
								v-for="item in notifications"
								:key="item.title + item.time"
								v-bind="item"
								@tap="showToast(item.title)"
								@action="showToast('已关注')"
							/>
						</AmSection>
					</AmCard>
				</view>

				<view class="post-list">
					<AmPostCard
						v-for="post in filteredPosts"
						:key="post.id"
						:post="post"
						@tap="goDetail(post)"
						@author-tap="showProfile(post)"
						@preview="previewPostImages(post, $event)"
						@video-tap="showToast('视频播放能力待接入')"
						@action="handlePostAction(post, $event)"
					/>
				</view>
			</view>
		</scroll-view>

		<AmFab text="发布" icon="＋" bottom="150rpx" @tap="goPublish" />
		<AmProfileSheet v-model:visible="profileVisible" :profile="activeProfile" @action="showToast('已进入沟通流程')" />
		<AmReportSheet :visible="reportVisible" @close="reportVisible = false" @select="handleReportSelect" />

	</view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { usePageScroll } from '@/composables/usePageScroll.js'
import { usePerformanceMode } from '@/composables/usePerformanceMode.js'

const keyword = ref('')
const activeTab = ref('hot')
const profileVisible = ref(false)
const reportVisible = ref(false)
const activeProfile = ref({})
const { scrollEnhanced, scrollBounces, scrollShowScrollbar, scrollLowerThreshold, contentStyle } = usePageScroll({ tabbar: true, extraBottom: '38rpx' })
const { pagePerformanceClass } = usePerformanceMode()

const tabs = [
	{ label: '热门', value: 'hot' },
	{ label: '关注', value: 'follow' },
	{ label: '最新', value: 'new' },
]

const communityStats = [
	{ icon: '▣', value: '12.4w', label: '帖子数' },
	{ icon: '♥', value: '256.7w', label: '获赞数' },
	{ icon: '●', value: '8.6w', label: '关注数' },
	{ icon: '◉', value: '16.3w', label: '粉丝数' },
]

const imageSet = ['/static/home-bg.png', '/static/profile-bg.png', '/static/login-bg.png']

const topics = ref([
	{ cover: '/static/home-bg.png', title: '治愈系风景', desc: '分享旅途和生活中的温柔瞬间', posts: '28.4w', follows: '162.3w', hot: true, followed: false },
	{ cover: '/static/profile-bg.png', title: '今日碎片', desc: '记录今天发生的小事', posts: '16.2w', follows: '88.5w', hot: false, followed: true },
])

const followUsers = ref([
	{ avatar: '/static/default-avatar.png', name: '小满同学', desc: '热爱生活，喜欢记录和分享穿搭、摄影、旅行。', posts: '128', fans: '2.4w', followed: true },
	{ avatar: '/static/default-avatar.png', name: '阿森', desc: '社区运营官，整理热门话题和精选内容。', posts: '86', fans: '1.8w', followed: false },
])

const notifications = [
	{ avatar: '/static/default-avatar.png', title: '小美 赞了你的帖子', desc: '这张照片好漂亮呀', time: '2分钟前' },
	{ avatar: '/static/default-avatar.png', title: '阿杰 评论了你的帖子', desc: '这个地方好治愈啊！', time: '15分钟前' },
	{ avatar: '', title: '系统通知', desc: '你的帖子已被推荐到热门', time: '2小时前', actionText: '查看' },
]

const posts = ref([
	{
		id: 1,
		author: '小满同学',
		avatar: '/static/default-avatar.png',
		badge: '认证',
		time: '2小时前 · 来自 iPhone',
		title: '今天在海边看到最美的日落',
		content: '风不算大，云层刚好散开，整个海面都像被温柔地照亮。把这组照片分享给正在忙碌的你。',
		images: imageSet,
		topics: ['治愈系风景', '今日碎片'],
		likes: '1.2k',
		comments: '132',
		stars: '256',
		shares: '112',
		liked: true,
		tab: 'hot',
	},
	{
		id: 2,
		author: '阿森',
		avatar: '/static/default-avatar.png',
		badge: '版主',
		time: '刚刚 · 社区精选',
		title: '周末城市徒步路线整理',
		content: '把最近大家提到频率最高的几条路线整理了一下，适合半天出门，强度不高。',
		images: ['/static/profile-bg.png', '/static/home-bg.png'],
		videoCover: '/static/home-bg.png',
		videoDuration: '04:32',
		topics: ['周末计划', '城市漫步'],
		likes: '856',
		comments: '64',
		stars: '118',
		shares: '42',
		tab: 'new',
	},
])

const filteredPosts = computed(() => {
	const list = activeTab.value === 'hot' ? posts.value : posts.value.filter((item) => item.tab === activeTab.value || activeTab.value === 'follow')
	if (!keyword.value) return list
	return list.filter((item) => [item.title, item.content, item.author].some((text) => text.includes(keyword.value)))
})

function goPublish() {
	uni.navigateTo({ url: '/pages/community/publish' })
}

function goDetail(post) {
	uni.navigateTo({ url: `/pages/community/detail?id=${post.id}` })
}

function handleSearch(value) {
	keyword.value = value || keyword.value
}

function previewPostImages(post, index) {
	if (!post.images || !post.images.length) return
	uni.previewImage({
		urls: post.images,
		current: post.images[index] || post.images[0],
	})
}

function showProfile(source) {
	activeProfile.value = {
		name: source.author || source.name,
		avatar: source.avatar,
		desc: source.desc || '社区活跃用户，喜欢分享生活经验。',
		tags: ['社区达人', '高质量内容'],
		stats: [
			{ label: '帖子', value: source.posts || '128' },
			{ label: '关注', value: '246' },
			{ label: '粉丝', value: source.fans || '2.4w' },
		],
		info: [
			{ label: '所在地', value: '上海' },
			{ label: '加入时间', value: '2024-05' },
			{ label: '最近活跃', value: '刚刚在线' },
		],
	}
	profileVisible.value = true
}

function toggleTopic(topic) {
	topic.followed = !topic.followed
	showToast(topic.followed ? '已关注话题' : '已取消关注')
}

function toggleUser(user) {
	user.followed = !user.followed
	showToast(user.followed ? '已关注用户' : '已取消关注')
}

function selectTopic(topic) {
	keyword.value = topic.title
}

function handlePostAction(post, action) {
	if (action.key === 'comment') {
		goDetail(post)
		return
	}
	if (action.key === 'share') {
		reportVisible.value = true
		return
	}
	showToast(action.active ? '已操作' : action.label)
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
.discover-page {
	position: relative;
	height: 100%;
	background: #f5f7fb;
	overflow: hidden;
	display: flex;
	flex-direction: column;
}

.page-scroll {
	flex: 1;
	min-height: 0;
}

.discover-inner {
	padding: 24rpx;
	box-sizing: border-box;
}

.discover-hero {
	display: flex;
	flex-direction: column;
	gap: 24rpx;
	background: linear-gradient(135deg, #f0f6ff 0%, #ffffff 58%, #eef7f3 100%);
}

.hero-main {
	display: flex;
	flex-direction: column;
}

.hero-kicker {
	font-size: 22rpx;
	color: var(--am-color-primary);
	font-weight: 700;
	line-height: 30rpx;
}

.hero-title {
	margin-top: 8rpx;
	font-size: 38rpx;
	color: var(--am-color-text-title);
	font-weight: 900;
	line-height: 50rpx;
}

.hero-desc {
	margin-top: 8rpx;
	font-size: 24rpx;
	color: var(--am-color-text-secondary);
	line-height: 36rpx;
}

.tabs-card,
.section-block,
.side-grid,
.post-list {
	margin-top: 22rpx;
}

.topic-scroll {
	width: 100%;
}

.topic-row {
	display: inline-flex;
	gap: 18rpx;
	padding: 4rpx 0 10rpx;
}

.topic-card {
	width: 560rpx;
}

.side-grid {
	display: grid;
	grid-template-columns: 1fr;
	gap: 22rpx;
}

.follow-list {
	display: grid;
	grid-template-columns: repeat(2, minmax(0, 1fr));
	gap: 18rpx;
}

.post-list {
	display: flex;
	flex-direction: column;
	gap: 22rpx;
}

.am-performance-light .discover-hero {
	background: #ffffff;
}
</style>
