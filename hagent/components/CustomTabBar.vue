<template>
	<view class="custom-tabbar">
		<view
			class="tab-item"
			:class="{ 'tab-item--active': activeIndex === index }"
			v-for="(item, index) in tabList"
			:key="index"
			hover-class="am-tap-feedback"
			:hover-stay-time="70"
			@tap="switchTab(index)"
		>
			<!-- 后台上传了图标:用后台图(选中态用 -active 版本) -->
			<image
				class="tab-icon"
				:src="resolveIcon(activeIndex === index ? item.selectedIconPath : item.iconPath)"
				mode="aspectFit"
				v-if="hasIcon(item, index)"
			/>
			<!-- 兜底:用本地 setB_line 图标(按 key,选中态切 -active;profile→mine) -->
			<image
				class="tab-icon"
				:src="localIcon(item.key, activeIndex === index)"
				mode="aspectFit"
				v-else-if="localIcon(item.key)"
			/>
			<!-- 未知 key 最终兜底:emoji 占位 -->
			<view class="tab-icon tab-icon-placeholder" v-else>
				<text class="tab-icon-emoji">{{ getFallbackIcon(item) }}</text>
			</view>
			<text class="tab-text">{{ item.text || '菜单' }}</text>
		</view>
	</view>
	<!-- 底部占位，防止页面内容被遮挡 -->
	<view class="tabbar-placeholder"></view>
</template>

<script setup>
import { computed } from 'vue'
import { useTabbarStore } from '@/store/tabbar.js'
import appConfig from '@/utils/config.js'

const tabbarStore = useTabbarStore()
const config = computed(() => tabbarStore.config)
const tabList = computed(() => tabbarStore.visibleTabs || [])
const textColor = computed(() => config.value.color || '#999999')
const selectedColor = computed(() => config.value.selectedColor || '#4C6EF5')
const bgColor = computed(() => config.value.backgroundColor || '#ffffff')
/** 当前激活 tab 在 visibleTabs 中的索引（通过 key 匹配） */
const activeIndex = computed(() => {
	return tabList.value.findIndex((item) => item.key === tabbarStore.activeTab)
})

/**
 * 是否有后台上传的图标 URL:选中态看 selectedIconPath,未选看 iconPath。
 * 有图标 URL 则用 image 渲染,否则走 emoji 兜底。
 */
function hasIcon(item, index) {
	const isActive = activeIndex.value === index
	const icon = isActive ? item.selectedIconPath : item.iconPath
	return !!icon
}

/**
 * 后台上传的图标多为相对路径(如 /profile/avatar/xxx.png),拼 baseUrl 成完整 URL;
 * 包内 /static 直用;http(s) 与启动时本地化的临时文件(wxfile:// 等非 / 开头)原样返回。
 */
function resolveIcon(url) {
	if (!url) return ''
	if (url.startsWith('/static')) return url
	return url.startsWith('/') ? appConfig.baseUrl + url : url
}

// 兜底本地图标(static/tabbar/,按 tab key;profile 复用 mine 图);每项 [未选, 选中]
const LOCAL_ICONS = {
	chat: ['/static/tabbar/chat.png', '/static/tabbar/chat_active.png'],
	create: ['/static/tabbar/create.png', '/static/tabbar/create_active.png'],
	cases: ['/static/tabbar/cases.png', '/static/tabbar/cases_active.png'],
	profile: ['/static/tabbar/mine.png', '/static/tabbar/mine_active.png'],
}

// 取本地兜底图标:key 未知返回空串(再退回 emoji);isActive 取选中态图
function localIcon(key, isActive) {
	const pair = LOCAL_ICONS[key]
	if (!pair) return ''
	return isActive ? pair[1] : pair[0]
}

// 兜底图标:本地图标也没有时用 emoji 占位
function getFallbackIcon(item) {
	switch (item.key) {
		case 'chat':
			return '💬'
		case 'create':
			return '✨'
		case 'cases':
			return '🖼'
		case 'profile':
			return '👤'
		default:
			return '•'
	}
}

/**
 * 切换 Tab
 * 通过 tab key 切换（单页面容器架构）
 */
function switchTab(index) {
	if (index === activeIndex.value) return
	const item = tabList.value[index]
	if (!item) return
	// 检查当前是否在首页容器
	const pages = getCurrentPages()
	const currentPage = pages[pages.length - 1]
	const route = currentPage?.route || ''
	if (route !== 'pages/index/index') {
		tabbarStore.switchTo(item.key)
		uni.switchTab({ url: '/pages/index/index' })
		return
	}
	tabbarStore.switchTo(item.key)
}
</script>

<style lang="scss" scoped>
.custom-tabbar {
	position: fixed;
	bottom: 0;
	left: 0;
	right: 0;
	height: 100rpx;
	display: flex;
	align-items: center;
	justify-content: space-around;
	/* 顶部 1px 细线（令牌 #EDEEF2） */
	border-top: 1rpx solid #edeef2;
	z-index: 100;
	/* 不透明兜底，避免不支持 backdrop-filter 时透出内容 */
	background-color: #ffffff;
	/* 白底 92% 透明 + 毛玻璃 */
	background-color: rgba(255, 255, 255, 0.92);
	backdrop-filter: saturate(180%) blur(20rpx);
	-webkit-backdrop-filter: saturate(180%) blur(20rpx);
	/* 适配底部安全区域（iPhone X 等） */
	padding-bottom: constant(safe-area-inset-bottom);
	padding-bottom: env(safe-area-inset-bottom);
}

.tab-item {
	flex: 1;
	display: flex;
	flex-direction: column;
	align-items: center;
	justify-content: center;
	height: 100%;
	/* 点击轻微缩放 */
	transition: transform 0.15s ease, opacity 0.15s ease;
}

.tab-item:active {
	transform: scale(0.98);
	opacity: 0.9;
}

.tab-icon {
	width: 44rpx;
	height: 44rpx;
	flex-shrink: 0;
	margin-bottom: 6rpx;
}

.tab-icon-placeholder {
	display: flex;
	align-items: center;
	justify-content: center;
}

.tab-icon-emoji {
	font-size: 40rpx;
	line-height: 1;
}

.tab-text {
	font-size: 20rpx;
	line-height: 1.2;
	/* 未选中（令牌占位灰 #AEB2BD） */
	color: #aeb2bd;
	font-family: -apple-system, BlinkMacSystemFont, 'Helvetica Neue', sans-serif;
}

/* 选中态：文字品牌主色 #4F84EA */
.tab-item--active .tab-text {
	color: #4f84ea;
	font-weight: 600;
}

.tabbar-placeholder {
	height: 100rpx;
	/* 加上安全区域 */
	padding-bottom: constant(safe-area-inset-bottom);
	padding-bottom: env(safe-area-inset-bottom);
}
</style>
