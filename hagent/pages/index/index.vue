<template>
	<view class="main-container">
		<view class="top-navigation-host">
			<ConversationTopNav
				:mode="topNavigationMode"
				:status-bar-height="navigationMetrics.statusBarHeight"
				:navigation-bar-height="navigationMetrics.navigationBarHeight"
				:right-safe-width="navigationMetrics.rightSafeWidth"
				:show-balance="showChatBalance"
				:balance-text="balanceText"
				:tabs="topNavigationTabs"
				:active-tab-key="topNavigationActiveKey"
				@open-history="openHistoryFromNav"
				@recharge="goRechargeFromNav"
				@select-tab="selectTopNavigationTab"
			/>
		</view>

		<view class="tab-stage">
			<!-- 对话（首屏） -->
			<view class="tab-panel" :class="{ 'tab-panel--active': activeTab === 'chat' }" v-show="activeTab === 'chat'">
				<ChatContent
					ref="chatRef"
					:navigation-metrics="navigationMetrics"
				/>
			</view>
			<!-- 生成（懒加载） -->
			<view class="tab-panel" :class="{ 'tab-panel--active': activeTab === 'create' }" v-show="activeTab === 'create'" v-if="tabLoaded['create']">
				<CreateContent
					ref="createRef"
					:status-bar-height="0"
					:overlay-status-bar-height="navigationMetrics.statusBarHeight"
					@navigation-change="createNavigationState = $event"
				/>
			</view>
			<!-- 案例（懒加载） -->
			<view class="tab-panel" :class="{ 'tab-panel--active': activeTab === 'cases' }" v-show="activeTab === 'cases'" v-if="tabLoaded['cases']">
				<CaseContent
					ref="caseRef"
					:status-bar-height="0"
					:overlay-status-bar-height="navigationMetrics.statusBarHeight"
					native-navigation
					@navigation-change="caseNavigationState = $event"
				/>
			</view>
			<!-- 我的（懒加载） -->
			<view class="tab-panel" :class="{ 'tab-panel--active': activeTab === 'profile' }" v-show="activeTab === 'profile'" v-if="tabLoaded['profile']">
				<ProfileContent :status-bar-height="0" native-navigation />
			</view>
		</view>

		<CustomTabBar />

		<!-- 登录提示弹窗:游客进首页自动弹一次,引导「去登录」;可「先逛逛」关闭继续浏览 -->
		<view v-if="loginModalVisible" class="login-modal-mask" @tap="closeLoginModal">
			<view class="login-modal-card" @tap.stop>
				<image class="login-modal-logo" src="/static/logo_abopi_mark.png" mode="heightFix" />
				<text class="login-modal-title">登录后开启全部创作功能</text>
				<view class="login-modal-btn" @tap="goLogin">
					<text class="login-modal-btn-text">去登录</text>
				</view>
				<view class="login-modal-guest" @tap="closeLoginModal">
					<text class="login-modal-guest-text">先逛逛 ›</text>
				</view>
			</view>
		</view>
	</view>
</template>

<script setup>
import { ref, watch, computed } from 'vue'
import { onShareAppMessage, onShareTimeline } from '@dcloudio/uni-app'
import { useTabbarStore } from '@/store/tabbar.js'
import { useAppStore } from '@/store/app.js'
import { useUserStore } from '@/store/user.js'
import { useAccountStore } from '@/store/account.js'
import { useConversationStore } from '@/store/conversation.js'
import { getBalance } from '@/api/account.js'
import appConfig from '@/utils/config.js'
import { useLoginGuard } from '@/composables/useLoginGuard.js'
import CustomTabBar from '@/components/CustomTabBar.vue'
import ChatContent from './ChatContent.vue'
import ConversationTopNav from './components/ConversationTopNav.vue'
import CreateContent from '@/pages/create/CreateContent.vue'
import CaseContent from '@/pages/cases/CaseContent.vue'
import ProfileContent from '@/pages/user/components/ProfileContent.vue'

const tabbarStore = useTabbarStore()
const activeTab = computed(() => tabbarStore.activeTab)
const chatRef = ref(null)

// 生成页组件引用:切回生成 tab 时重拉近期作品(首次加载失败/切回刷新)
const createRef = ref(null)
const caseRef = ref(null)
const createNavigationState = ref({ tabs: [], activeKey: '' })
const caseNavigationState = ref({ tabs: [], activeKey: '' })

const topNavigationMode = computed(() => {
	if (activeTab.value === 'chat') return 'chat'
	if (activeTab.value === 'create' || activeTab.value === 'cases') return 'tabs'
	return 'blank'
})

const topNavigationState = computed(() => {
	if (activeTab.value === 'create') return createNavigationState.value
	if (activeTab.value === 'cases') return caseNavigationState.value
	return { tabs: [], activeKey: '' }
})
const topNavigationTabs = computed(() => topNavigationState.value.tabs || [])
const topNavigationActiveKey = computed(() => topNavigationState.value.activeKey)

function selectTopNavigationTab(tab) {
	if (!tab) return
	const pageRef =
		activeTab.value === 'create'
			? createRef
			: activeTab.value === 'cases'
				? caseRef
				: null
	const page = pageRef && pageRef.value
	if (page && page.selectNavigationTab) {
		page.selectNavigationTab(tab.key)
	}
}

function resolveNavigationMetrics() {
	let windowWidth = 375
	let statusBarHeight = 20
	let navigationBarHeight = 44
	let rightSafeWidth = 96

	try {
		const windowInfo =
			typeof uni.getWindowInfo === 'function' ? uni.getWindowInfo() : uni.getSystemInfoSync()
		windowWidth = Number(windowInfo && windowInfo.windowWidth) || windowWidth
		statusBarHeight = Number(windowInfo && windowInfo.statusBarHeight) || statusBarHeight

		if (typeof uni.getMenuButtonBoundingClientRect === 'function') {
			const menuButton = uni.getMenuButtonBoundingClientRect()
			if (menuButton && menuButton.width && menuButton.height) {
				const topGap = Math.max(0, Number(menuButton.top) - statusBarHeight)
				navigationBarHeight = Math.max(
					44,
					Math.round(Number(menuButton.height) + topGap * 2)
				)
				rightSafeWidth = Math.max(
					12,
					Math.round(windowWidth - Number(menuButton.left) + 8)
				)
			}
		}
	} catch (e) {
		// 非微信环境使用稳定兜底尺寸。
	}

	return {
		statusBarHeight,
		navigationBarHeight,
		rightSafeWidth,
	}
}

const navigationMetrics = resolveNavigationMetrics()

// 登录弹窗:由 useLoginGuard 全局控制,各入口未登录时 requireLogin() 触发;此处仅渲染与关闭
const { loginModalVisible, hideLoginModal } = useLoginGuard()
function goLogin() {
	hideLoginModal()
	uni.navigateTo({ url: '/pages/login/index' })
}
function closeLoginModal() {
	hideLoginModal()
}

// ====== 分享(会话+朋友圈):标题/卡片图取后台「系统设置-分享设置」,App 启动拉入 store ======
const appStore = useAppStore()

// 分享卡片图完整地址;未配置返回空(微信自动用页面截图兜底)
function shareImageUrl() {
	const url = appStore.shareConfig && appStore.shareConfig.imageUrl
	if (!url) return ''
	return url.startsWith('http') ? url : appConfig.baseUrl + url
}

function buildSharePayload(withPath) {
	const payload = { title: (appStore.shareConfig && appStore.shareConfig.title) || '啊播吡 AI 创作' }
	if (withPath) payload.path = '/pages/index/index'
	const img = shareImageUrl()
	if (img) payload.imageUrl = img
	return payload
}

// 转发给好友/群(卡片图 5:4)
onShareAppMessage(() => buildSharePayload(true))
// 分享到朋友圈(复用同一张图,微信自动裁中间 1:1)
onShareTimeline(() => buildSharePayload(false))

// 切 tab 只刷豆子余额:页面 v-show 存活不重挂,onMounted 不再跑,故切到含余额展示的 tab 时
// 单独拉一次余额写回 store(首页 pill / 我的页豆子卡响应式更新),不重载整页
const userStore = useUserStore()
const accountStore = useAccountStore()
const conversationStore = useConversationStore()
const showChatBalance = computed(
	() => activeTab.value === 'chat' && conversationStore.messages.length > 0
)
const balanceText = computed(() =>
	String(accountStore.balance || 0).replace(/\B(?=(\d{3})+(?!\d))/g, ',')
)

function openHistoryFromNav() {
	chatRef.value && chatRef.value.openHistory && chatRef.value.openHistory()
}

function goRechargeFromNav() {
	chatRef.value && chatRef.value.goRecharge && chatRef.value.goRecharge()
}

async function refreshBalanceOnly() {
	if (!userStore.isLoggedIn) return
	try {
		const data = await getBalance()
		const balance = typeof data === 'number' ? data : data && data.balance
		if (balance != null) accountStore.setBalance(balance)
	} catch (e) {
		// 静默:余额刷新失败不影响页面
	}
}

// 懒加载：首次切换到某个 tab 时才创建其组件（v-if），之后保持存活（v-show）
const tabLoaded = ref({ chat: true, create: false, cases: false, profile: false })
// 跳过 immediate 首次:首屏余额由各 tab 组件 onMounted 自行拉取,避免重复请求
let firstTabRun = true
watch(activeTab, (val) => {
	const wasLoaded = !!tabLoaded.value[val]
	if (!tabLoaded.value[val]) {
		tabLoaded.value[val] = true
	}
	// 真正切换到首页对话 / 生成 / 我的(均展示余额)时刷新豆子(首次挂载不刷)
	// 某 tab 首次创建时由子组件自行加载，避免首次切入“我的”同时发两次余额请求。
	if (!firstTabRun && wasLoaded && (val === 'chat' || val === 'create' || val === 'profile')) {
		refreshBalanceOnly()
	}
	// 切回生成 tab:重拉近期作品(修首次加载失败后一直空、且保持最新)
	if (!firstTabRun && wasLoaded && val === 'create') {
		createRef.value && createRef.value.reloadRecentWorks && createRef.value.reloadRecentWorks()
	}
	firstTabRun = false
}, { immediate: true })
</script>

<style lang="scss" scoped>
.main-container {
	height: 100vh;
	position: relative;
	overflow: hidden;
	display: flex;
	flex-direction: column;
	background: #f6f7fb;
}

.top-navigation-host {
	position: relative;
	z-index: 60;
	width: 100%;
	flex-shrink: 0;
}

.tab-stage {
	position: relative;
	flex: 1;
	min-height: 0;
	overflow: hidden;
}

.tab-panel {
	height: 100%;
	width: 100%;
	position: absolute;
	top: 0;
	left: 0;
}

.tab-panel--active {
	animation: am-tab-fade-in 0.16s ease-out;
}

@keyframes am-tab-fade-in {
	from { opacity: 0.72; }
	to { opacity: 1; }
}

/* ====== 登录提示弹窗 ====== */
.login-modal-mask {
	position: fixed;
	top: 0;
	left: 0;
	right: 0;
	bottom: 0;
	background: rgba(20, 24, 40, 0.42);
	display: flex;
	align-items: center;
	justify-content: center;
	z-index: 1000;
}

.login-modal-card {
	width: 580rpx;
	background: #fff;
	border-radius: 32rpx;
	padding: 56rpx 48rpx 40rpx;
	display: flex;
	flex-direction: column;
	align-items: center;
	box-shadow: 0 60rpx 120rpx rgba(20, 24, 40, 0.28);
}

.login-modal-logo {
	height: 72rpx;
	width: auto;
	margin-bottom: 32rpx;
}

.login-modal-title {
	font-size: 36rpx;
	font-weight: 700;
	color: #0f172a;
}

.login-modal-btn {
	margin-top: 44rpx;
	width: 100%;
	height: 96rpx;
	border-radius: 48rpx;
	background: linear-gradient(135deg, #4F84EA, #8FB4FB);
	display: flex;
	align-items: center;
	justify-content: center;
	box-shadow: 0 16rpx 40rpx rgba(96, 140, 224, 0.3);
}

.login-modal-btn:active {
	opacity: 0.92;
}

.login-modal-btn-text {
	font-size: 32rpx;
	font-weight: 600;
	color: #fff;
}

.login-modal-guest {
	margin-top: 28rpx;
	padding: 8rpx;
}

.login-modal-guest-text {
	font-size: 26rpx;
	color: #94a3b8;
}
</style>
