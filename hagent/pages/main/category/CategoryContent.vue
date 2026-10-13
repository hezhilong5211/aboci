<template>
	<view class="category-content" :class="pagePerformanceClass">
		<view class="category-header" :style="{ paddingTop: statusBarHeight + 'px' }">
			<view class="category-title-row">
				<text class="category-title">分类</text>
				<text class="category-search-icon" @tap="showToast('搜索分类')">⌕</text>
			</view>
		</view>

		<view class="category-body" :style="{ paddingTop: headerHeight }">
			<AmCategorySidebar v-model="activeCategory" :items="sideCategories" @change="handleSidebarChange" />
			<scroll-view class="category-scroll" scroll-y :enhanced="scrollEnhanced" :bounces="scrollBounces" :show-scrollbar="scrollShowScrollbar" :lower-threshold="scrollLowerThreshold">
				<view class="category-main" :style="contentStyle">
					<view class="search-box">
						<AmSearchBar v-model="keyword" placeholder="搜索分类" />
					</view>

					<view class="section-block">
						<view class="section-title-row">
							<text class="section-title">我的分类</text>
							<text class="section-action" @tap="showToast('编辑分类')">编辑</text>
						</view>
						<AmCategoryShortcut :items="myCategories" @tap="selectShortcut" />
					</view>

					<view class="section-block">
						<text class="section-title">热门分类</text>
						<AmCategoryShortcut class="section-shortcut" :items="hotCategories" @tap="selectShortcut" />
					</view>

					<view class="section-block">
						<text class="section-title">全部分类</text>
						<view class="category-list">
							<AmCategoryListItem
								v-for="item in filteredCategories"
								:key="item.title"
								:category="item"
								@tap="selectCategory"
								@follow="toggleFollow"
							/>
						</view>
					</view>
				</view>
			</scroll-view>
		</view>
	</view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { usePageScroll } from '@/composables/usePageScroll.js'
import { usePerformanceMode } from '@/composables/usePerformanceMode.js'

const { pagePerformanceClass } = usePerformanceMode()
const { scrollEnhanced, scrollBounces, scrollShowScrollbar, scrollLowerThreshold, contentStyle } = usePageScroll({ tabbar: true })
const statusBarHeight = ref(44)
const activeCategory = ref('recommend')
const keyword = ref('')

uni.getSystemInfo({
	success: (info) => {
		statusBarHeight.value = info.statusBarHeight || 44
	},
})

const headerHeight = computed(() => `${statusBarHeight.value + 52}px`)

const sideCategories = [
	{ label: '推荐', value: 'recommend', icon: '⌂' },
	{ label: '生活', value: 'life', icon: '♧' },
	{ label: '兴趣', value: 'interest', icon: '♡' },
	{ label: '科技', value: 'tech', icon: '▣' },
	{ label: '娱乐', value: 'fun', icon: '☻' },
	{ label: '学习', value: 'study', icon: '✦' },
	{ label: '职场', value: 'work', icon: '□' },
	{ label: '运动', value: 'sport', icon: '◇' },
	{ label: '更多', value: 'more', icon: '⋯' },
]

const myCategories = [
	{ label: '旅行', value: 'travel', cover: '/static/home-bg.png' },
	{ label: '美食', value: 'food', cover: '/static/login-bg.png' },
	{ label: '数码', value: 'digital', cover: '/static/profile-bg.png' },
	{ label: '健身', value: 'fitness', cover: '/static/home-bg.png' },
]

const hotCategories = [
	{ label: '推荐', value: 'recommend', icon: '✦', background: 'linear-gradient(135deg, #4f86ff 0%, #2468ff 100%)' },
	{ label: '最新', value: 'latest', icon: '♥', background: 'linear-gradient(135deg, #ff7c82 0%, #ff3b45 100%)' },
	{ label: '关注', value: 'follow', icon: '✓', background: 'linear-gradient(135deg, #38d996 0%, #18b96f 100%)' },
	{ label: '问答', value: 'qa', icon: '✉', background: 'linear-gradient(135deg, #ffb23f 0%, #ff8b00 100%)' },
]

const categories = ref([
	{ title: '旅行', desc: '探索世界，发现美好', posts: '128.6万', members: '24.5万', cover: '/static/home-bg.png', followed: false },
	{ title: '美食', desc: '分享美食，品味生活', posts: '98.3万', members: '18.7万', cover: '/static/login-bg.png', followed: true },
	{ title: '数码', desc: '发现科技，体验未来', posts: '76.2万', members: '12.8万', cover: '/static/profile-bg.png', followed: false },
	{ title: '健身', desc: '自律生活，健康身体', posts: '58.7万', members: '9.6万', cover: '/static/home-bg.png', followed: false },
	{ title: '摄影', desc: '记录瞬间，定格美好', posts: '45.1万', members: '8.2万', cover: '/static/profile-bg.png', followed: false },
])

const filteredCategories = computed(() => {
	const value = keyword.value.trim()
	if (!value) return categories.value
	return categories.value.filter((item) => item.title.includes(value) || item.desc.includes(value))
})

function handleSidebarChange(item) {
	showToast(item.label)
}

function selectShortcut(item) {
	showToast(item.label)
}

function selectCategory(item) {
	showToast(item.title)
}

function toggleFollow(item) {
	item.followed = !item.followed
}

function showToast(title) {
	uni.showToast({ title, icon: 'none' })
}
</script>

<style lang="scss" scoped>
.category-content {
	height: 100%;
	background: #f5f7fb;
	position: relative;
	overflow: hidden;
}

.category-header {
	position: absolute;
	top: 0;
	left: 0;
	right: 0;
	z-index: 5;
	background: rgba(255, 255, 255, 0.96);
	border-bottom: 1rpx solid var(--am-color-border-light);
}

.category-title-row {
	height: 104rpx;
	padding: 0 30rpx;
	display: flex;
	align-items: center;
	justify-content: center;
	position: relative;
	box-sizing: border-box;
}

.category-title {
	font-size: 32rpx;
	color: var(--am-color-text-title);
	font-weight: 800;
	line-height: 42rpx;
}

.category-search-icon {
	position: absolute;
	right: 30rpx;
	font-size: 42rpx;
	color: var(--am-color-text-title);
	line-height: 1;
}

.category-body {
	height: 100%;
	box-sizing: border-box;
	display: flex;
}

.category-scroll {
	flex: 1;
	min-width: 0;
	height: 100%;
}

.category-main {
	padding: 24rpx 28rpx 0;
	box-sizing: border-box;
}

.search-box {
	margin-bottom: 26rpx;
}

.section-block {
	margin-bottom: 34rpx;
}

.section-title-row {
	display: flex;
	align-items: center;
	justify-content: space-between;
	margin-bottom: 20rpx;
}

.section-title {
	display: block;
	margin-bottom: 20rpx;
	font-size: 26rpx;
	color: var(--am-color-text-title);
	font-weight: 800;
	line-height: 36rpx;
}

.section-title-row .section-title {
	margin-bottom: 0;
}

.section-action {
	font-size: 22rpx;
	color: var(--am-color-text-secondary);
	line-height: 30rpx;
}

.section-shortcut {
	margin-top: 20rpx;
}

.category-list {
	padding-bottom: 20rpx;
}
</style>
