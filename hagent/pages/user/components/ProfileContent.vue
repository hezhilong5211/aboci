<template>
	<view class="profile-content" :class="pagePerformanceClass">
		<scroll-view
			class="page-scroll"
			scroll-y
			:enhanced="scrollEnhanced"
			:bounces="scrollBounces"
			:show-scrollbar="scrollShowScrollbar"
			:lower-threshold="scrollLowerThreshold"
			refresher-enabled
			:refresher-triggered="refreshing"
			:refresher-threshold="72"
			refresher-default-style="black"
			refresher-background="#f5f6f9"
			@refresherrefresh="onPullRefresh"
		>
			<view class="page-inner" :style="[contentStyle, { paddingTop: statusBarHeight + 'px' }]">
				<!-- 大标题 -->
				<text v-if="!nativeNavigation" class="page-title">我的</text>

				<!-- 用户行 -->
				<view class="user-row">
					<!-- 点头像换头像 -->
					<!-- #ifdef MP-WEIXIN -->
					<button class="user-avatar-ring avatar-btn-reset" :open-type="userStore.isLoggedIn ? 'chooseAvatar' : ''" @chooseavatar="onChooseWxAvatar" @tap="onAvatarTap">
						<image class="user-avatar" :src="avatarUrl" mode="aspectFill" />
					</button>
					<!-- #endif -->
					<!-- #ifndef MP-WEIXIN -->
					<view class="user-avatar-ring" @tap="chooseAvatarFromAlbum">
						<image class="user-avatar" :src="avatarUrl" mode="aspectFill" />
					</view>
					<!-- #endif -->
					<view class="user-main" hover-class="am-tap-feedback" :hover-stay-time="70" @tap="onUserMainTap">
						<text class="user-name">{{ displayName }}</text>
						<text class="user-no">{{ phoneMasked }}</text>
					</view>
				</view>

				<!-- 豆子卡 -->
				<view class="beans-card" :style="beansCardStyle">
					<view class="beans-glow"></view>
					<view class="beans-top">
						<text class="beans-value">{{ beansDisplay }}</text>
						<image class="beans-unit" src="/static/豆子.png" mode="aspectFit" />
					</view>
					<view class="beans-member-row" hover-class="am-tap-feedback" :hover-stay-time="70" @tap="goMembership">
						<text class="beans-member">{{ memberDisplay }}</text>
						<text class="beans-member-action">{{ membershipActionText }} ›</text>
					</view>
					<view class="beans-recharge" hover-class="am-tap-feedback" :hover-stay-time="70" @tap="goRecharge">
						<text class="beans-recharge-text">充值</text>
					</view>
				</view>

				<!-- 列表卡 -->
				<view class="menu-card">
					<template v-for="(item, index) in menuItems" :key="item.title">
						<!-- 联系我们:微信原生客服会话入口(open-type=contact),样式对齐普通菜单行 -->
						<button
							v-if="item.contact"
							class="menu-item menu-item-contact"
							open-type="contact"
						>
							<view class="menu-icon">
								<image class="menu-icon-image" :src="item.icon" mode="aspectFit" />
							</view>
							<text class="menu-title">{{ item.title }}</text>
							<text class="menu-chevron">›</text>
						</button>
						<view
							v-else
							class="menu-item"
							:class="{ 'is-first': index === 0 }"
							hover-class="am-tap-feedback"
							:hover-stay-time="70"
							@tap="handleMenuTap(item)"
						>
							<view class="menu-icon">
								<image class="menu-icon-image" :src="item.icon" mode="aspectFit" />
							</view>
							<text class="menu-title">{{ item.title }}</text>
							<text class="menu-chevron">›</text>
						</view>
					</template>
				</view>
			</view>
		</scroll-view>

	</view>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useUserStore } from '@/store/user.js'
import { useAccountStore } from '@/store/account.js'
import { useLoginGuard } from '@/composables/useLoginGuard.js'
import { usePerformanceMode } from '@/composables/usePerformanceMode.js'
import { usePageScroll } from '@/composables/usePageScroll.js'
import { getUserInfo, updateUserInfo, getMembership } from '@/api/user.js'
import { getBalance } from '@/api/account.js'
import { uploadFile } from '@/api/common.js'
import { getDistributionMe } from '@/api/distribution.js'
import config from '@/utils/config.js'

const userStore = useUserStore()
const accountStore = useAccountStore()
const { requireLogin } = useLoginGuard()
const { pagePerformanceClass } = usePerformanceMode()
const { scrollEnhanced, scrollBounces, scrollShowScrollbar, scrollLowerThreshold, contentStyle } = usePageScroll({ tabbar: true })

const props = defineProps({
	statusBarHeight: { type: Number, default: 44 },
	nativeNavigation: { type: Boolean, default: false },
})

const nativeNavigation = computed(() => props.nativeNavigation)

// ====== 本地状态(本组件读写,不进 store)======
// 豆子余额(数字),null 表示未取到
const beans = ref(null)
// 会员信息 { levelName, remainDays, cardBg }
const membership = ref(null)
const distributionEntryVisible = ref(false)
const refreshing = ref(false)
let loadAllPromise = null

// ====== 展示用计算属性 ======

// 头像:未登录用灰色访客图;已登录拼 baseUrl,空头像用本地默认图
const avatarUrl = computed(() => {
	if (!userStore.isLoggedIn) return '/static/avatar-guest.png'
	let url = userStore.avatarUrl
	if (!url) return '/static/default-avatar.png'
	// 兼容历史脏数据:存成完整 URL 的,剥离域名只留 /profile 起的相对路径,再拼当前 baseUrl
	const i = url.indexOf('/profile/')
	if (i > 0) url = url.slice(i)
	return url.startsWith('http') ? url : config.baseUrl + url
})

// 昵称:未登录显示「立即登录」;已登录无昵称才显示占位
const displayName = computed(() => {
	if (!userStore.isLoggedIn) return '立即登录'
	return userStore.nickName || '未设置昵称'
})

// 副行:登录后显示脱敏手机号(中间 4 位打码),未绑定显示占位;未登录留空
const phoneMasked = computed(() => {
	if (!userStore.isLoggedIn) return ''
	const p = userStore.phone
	if (!p) return '未绑定手机'
	return p.length === 11 ? p.slice(0, 3) + '****' + p.slice(7) : p
})

// 豆子余额:千分位显示,未取到显示占位
const beansDisplay = computed(() => {
	if (beans.value === null || beans.value === undefined) return '--'
	return Number(beans.value).toLocaleString('en-US')
})

// 会员文案:有会员显示「{levelName} · 剩 {remainDays} 天」,否则「普通用户」
const memberDisplay = computed(() => {
	const m = membership.value
	if (m && m.levelName) {
		return `${m.levelName} · 剩 ${m.remainDays || 0} 天`
	}
	return '普通用户'
})

// 会员入口文案:已是会员显示「续费」,否则「开通」
const membershipActionText = computed(() => (membership.value && membership.value.levelName ? '续费' : '开通'))

// 豆子卡背景:cardBg 非空时叠加该图,渐变作兜底打底
const beansCardStyle = computed(() => {
	const bg = membership.value?.cardBg
	const gradient = 'linear-gradient(135deg, #4F84EA, #8FB4FB)'
	if (bg) {
		const bgUrl = bg.startsWith('http') ? bg : config.baseUrl + bg
		return {
			backgroundImage: `url("${bgUrl}"), ${gradient}`,
			backgroundSize: 'cover',
			backgroundPosition: 'center',
		}
	}
	return { backgroundImage: gradient }
})

const menuItems = computed(() => {
	const items = [
		{ icon: '/static/profile/works.png', title: '我的作品', url: '/pages/user/works/index' },
		{ icon: '/static/profile/favorites.png', title: '我的收藏', url: '/pages/user/favorites/index' },
		{ icon: '/static/profile/beans-detail.png', title: '豆子明细', url: '/pages/user/transactions/index' },
	]
	if (distributionEntryVisible.value) {
		items.push({ icon: '/static/profile/distribution.png', title: '推广中心', url: '/pages/distribution/index' })
	}
	items.push(
		{ icon: '/static/profile/prompt.png', title: '个性化提示词', url: '/pages/user/prompt/index' },
		// 微信原生客服会话入口(需公众平台开通客服并绑定客服人员;开发者工具无效,真机验)
		{ icon: '/static/profile/contact.png', title: '联系我们', contact: true },
		{ icon: '/static/profile/settings.png', title: '设置', url: '/pages/user/settings' },
	)
	return items
})

// ====== 数据拉取 ======

/** 拉取用户信息并同步 store */
async function loadUserInfo() {
	try {
		const info = await getUserInfo()
		if (info) userStore.setUserInfo(info)
		return true
	} catch (e) {
		// 错误已由 request 统一提示
		return false
	}
}

/** 拉取豆子余额 */
async function loadBalance() {
	try {
		// 后端返回 BeanAccountVO：{ balance, totalEarned, totalConsumed }
		const res = await getBalance()
		beans.value = res?.balance ?? null
		if (res?.balance != null) accountStore.setBalance(res.balance)
		return true
	} catch (e) {
		// 错误已由 request 统一提示
		return false
	}
}

/** 拉取会员信息 */
async function loadMembership() {
	try {
		membership.value = await getMembership()
		return true
	} catch (e) {
		// 错误已由 request 统一提示
		return false
	}
}

/** 只由服务端成功响应决定入口；瞬时请求失败时保留最近一次已确认状态。 */
async function loadDistributionEntry() {
	try {
		const result = await getDistributionMe({ showError: false })
		distributionEntryVisible.value = result?.entryVisible === true
		return true
	} catch (e) {
		// 网络抖动或 Token 刷新中的单次失败，不等价于服务端撤销分销身份。
		// 真正退出登录会由 userStore 监听统一清空，成功返回 entryVisible=false 仍会隐藏。
		return false
	}
}

// 拉取当前用户的全部展示数据(用户信息 + 余额 + 会员)
function loadAll() {
	if (!userStore.isLoggedIn) return Promise.resolve([])
	if (loadAllPromise) return loadAllPromise
	loadAllPromise = Promise.all([loadUserInfo(), loadBalance(), loadMembership(), loadDistributionEntry()])
		.finally(() => {
			loadAllPromise = null
		})
	return loadAllPromise
}

// 个人中心使用内部 scroll-view，直接接入其原生下拉刷新器，避免单页面容器下 pages.json 刷新钩子不触发。
async function onPullRefresh() {
	if (refreshing.value) return
	refreshing.value = true
	const startedAt = Date.now()
	try {
		if (!userStore.isLoggedIn) {
			uni.showToast({ title: '登录后可刷新账户', icon: 'none' })
			return
		}
		const results = await loadAll()
		if (results.length > 0 && results.every(Boolean)) {
			uni.showToast({ title: '账户信息已刷新', icon: 'none', duration: 1000 })
		}
	} finally {
		// 避免极快请求让刷新头一闪而过，保持轻量但可感知的反馈。
		const wait = Math.max(0, 320 - (Date.now() - startedAt))
		setTimeout(() => {
			refreshing.value = false
		}, wait)
	}
}

// 充值/开通会员成功后由对应页面 uni.$emit 通知,刷新余额与会员展示
function onAccountRefresh() {
	if (!userStore.isLoggedIn) return
	void Promise.all([loadBalance(), loadMembership()])
}

// 本组件由父页面 v-if 懒加载:挂载时机即首次进入「我的」tab,故在 onMounted 拉数据
onMounted(() => {
	uni.$on('user-account-refresh', onAccountRefresh)
	if (!userStore.isLoggedIn) return
	void loadAll()
})

// 组件销毁时移除监听,避免重复绑定
onUnmounted(() => {
	uni.$off('user-account-refresh', onAccountRefresh)
})

// 游客在「我的」tab 登录后子组件不会重新挂载,监听登录态变化补拉数据
watch(() => userStore.isLoggedIn, (logged) => {
	if (logged) {
		void loadAll()
	} else {
		beans.value = null
		membership.value = null
		distributionEntryVisible.value = false
	}
})

// 首页容器切回“我的”时会轻量刷新账户 store；同步到本地展示，避免余额卡停留旧值。
watch(() => accountStore.balance, (value) => {
	if (userStore.isLoggedIn && value != null) beans.value = value
})

// ====== 头像编辑 ======
// 点用户行(未登录态为「立即登录」):未登录触发登录弹窗
function onUserMainTap() {
	if (!userStore.isLoggedIn) requireLogin()
}

// #ifdef MP-WEIXIN
// 微信端点头像:未登录先拦登录;已登录由 button 的 open-type=chooseAvatar 触发微信头像选择
function onAvatarTap() {
	if (!userStore.isLoggedIn) requireLogin()
}
// 微信头像选择回调:e.detail.avatarUrl 为临时路径
async function onChooseWxAvatar(e) {
	const tempPath = e.detail.avatarUrl
	if (!tempPath) return
	await uploadAndSaveAvatar(tempPath)
}
// #endif

// 从相册/相机选择头像
function chooseAvatarFromAlbum() {
	if (!requireLogin()) return
	uni.chooseImage({
		count: 1,
		sizeType: ['compressed'],
		sourceType: ['album', 'camera'],
		success: async (res) => {
			const tempPath = res.tempFilePaths[0]
			if (!tempPath) return
			await uploadAndSaveAvatar(tempPath)
		},
	})
}

// 上传头像并保存:统一复用 uploadFile,url 拼接与 settings.vue 一致
async function uploadAndSaveAvatar(tempPath) {
	try {
		uni.showLoading({ title: '上传中...', mask: true })
		const uploadRes = await uploadFile(tempPath, 'avatars')
		const rawUrl = uploadRes.url || uploadRes.path || ''
		if (!rawUrl) {
			uni.hideLoading()
			return
		}
		// 只存后端返回的相对路径,显示时再拼 baseUrl;避免把环境地址(IP/localhost)写死进库
		await updateUserInfo({ avatarUrl: rawUrl })
		userStore.updateUserInfo({ avatarUrl: rawUrl })
		uni.hideLoading()
		uni.showToast({ title: '头像已更新', icon: 'success' })
	} catch (e) {
		uni.hideLoading()
	}
}

// ====== 菜单跳转 ======
function handleMenuTap(item) {
	if (!requireLogin()) return
	if (item.url) {
		uni.navigateTo({ url: item.url })
	}
}

// 跳豆子充值页(需登录)
function goRecharge() {
	if (!requireLogin()) return
	uni.navigateTo({ url: '/pages/bean/recharge/index' })
}

// 跳会员开通/续费页(需登录)
function goMembership() {
	if (!requireLogin()) return
	uni.navigateTo({ url: '/pages/membership/purchase/index' })
}
</script>

<style lang="scss" scoped>
.profile-content {
	height: 100%;
	background: #f5f6f9;
	position: relative;
	overflow: hidden;
}

.page-scroll {
	height: 100%;
	overscroll-behavior: none;
}

.page-inner {
	padding-left: 32rpx;
	padding-right: 32rpx;
}

/* 大标题 */
.page-title {
	display: block;
	margin-top: 24rpx;
	font-size: 40rpx;
	font-weight: 600;
	color: #15161b;
	line-height: 56rpx;
}

/* 用户行 */
.user-row {
	margin-top: 28rpx;
	display: flex;
	align-items: center;
	transition: transform 0.12s ease, opacity 0.12s ease;
}

.user-row:active {
	transform: scale(0.98);
	opacity: 0.92;
}

.user-avatar-ring {
	width: 108rpx;
	height: 108rpx;
	border-radius: 50%;
	padding: 4rpx;
	/* 白底圆中放头像 + 蓝紫粉彩环 */
	background: linear-gradient(135deg, #3AA0FF, #8B5CF6 55%, #EC4BD0);
	flex-shrink: 0;
	transition: transform 0.12s ease, opacity 0.12s ease;
}

.user-avatar-ring:active {
	transform: scale(0.96);
	opacity: 0.92;
}

.user-avatar {
	width: 100%;
	height: 100%;
	border-radius: 50%;
	/* 白底圆衬底,头像未加载时露白底而非灰底 */
	background: #ffffff;
	display: block;
	box-sizing: border-box;
	border: 3rpx solid #ffffff;
}

.user-main {
	flex: 1;
	min-width: 0;
	padding-left: 24rpx;
}

.user-name {
	display: block;
	font-size: 34rpx;
	font-weight: 600;
	color: #15161b;
	line-height: 46rpx;
}

.user-no {
	display: block;
	margin-top: 8rpx;
	font-size: 22rpx;
	color: #9a9ea8;
	line-height: 30rpx;
}

/* 豆子卡(背景渐变/会员卡背景图由内联 beansCardStyle 提供) */
.beans-card {
	position: relative;
	margin-top: 36rpx;
	padding: 40rpx 36rpx 36rpx;
	border-radius: 36rpx;
	overflow: hidden;
	box-shadow: 0 8rpx 28rpx rgba(96, 140, 224, 0.16);
}

/* 右上白光晕 */
.beans-glow {
	position: absolute;
	top: -100rpx;
	right: -100rpx;
	width: 260rpx;
	height: 260rpx;
	border-radius: 50%;
	background: rgba(255, 255, 255, 0.25);
	filter: blur(20rpx);
}

.beans-top {
	position: relative;
	display: flex;
	align-items: baseline;
	gap: 12rpx;
}

.beans-value {
	font-size: 64rpx;
	font-weight: 600;
	color: #ffffff;
	letter-spacing: -1rpx;
	line-height: 1;
}

.beans-unit {
	width: 40rpx;
	height: 40rpx;
}

/* 会员文案 + 开通/续费入口同一行,右侧留出充值按钮空间 */
.beans-member-row {
	position: relative;
	margin-top: 20rpx;
	padding-right: 180rpx;
	display: flex;
	align-items: center;
	gap: 16rpx;
}

.beans-member-row:active {
	opacity: 0.92;
}

.beans-member {
	font-size: 24rpx;
	color: rgba(255, 255, 255, 0.85);
	line-height: 34rpx;
}

.beans-member-action {
	font-size: 24rpx;
	font-weight: 600;
	color: #ffffff;
	line-height: 34rpx;
}

.beans-recharge {
	position: absolute;
	right: 36rpx;
	bottom: 36rpx;
	height: 56rpx;
	padding: 0 32rpx;
	border-radius: 28rpx;
	background: #ffffff;
	display: flex;
	align-items: center;
	justify-content: center;
	transition: transform 0.12s ease, opacity 0.12s ease;
}

.beans-recharge:active {
	transform: scale(0.98);
	opacity: 0.92;
}

.beans-recharge-text {
	font-size: 26rpx;
	font-weight: 600;
	color: #4F84EA;
	line-height: 1;
}

/* 列表卡 */
.menu-card {
	margin-top: 28rpx;
	border-radius: 36rpx;
	background: #ffffff;
	box-shadow: 0 8rpx 28rpx rgba(20, 20, 40, 0.07);
	overflow: hidden;
}

.menu-item {
	position: relative;
	display: flex;
	align-items: center;
	padding: 28rpx 32rpx;
	transition: opacity 0.12s ease;
}

.menu-item:active {
	opacity: 0.85;
}

/* 联系我们(button open-type=contact):重置微信 button 默认样式,观感与普通菜单行一致 */
.menu-item-contact {
	width: 100%;
	margin: 0;
	background: transparent;
	border: none;
	border-radius: 0;
	font-size: inherit;
	font-weight: normal;
	line-height: inherit;
	text-align: left;
	box-sizing: border-box;
}

.menu-item-contact::after {
	border: none;
}

.menu-item::before {
	content: '';
	position: absolute;
	left: 32rpx;
	right: 0;
	top: 0;
	height: 1rpx;
	background: #f1f2f5;
}

.menu-item.is-first::before {
	display: none;
}

.menu-icon {
	width: 56rpx;
	height: 56rpx;
	border-radius: 18rpx;
	background: #DEEAFB;
	display: flex;
	align-items: center;
	justify-content: center;
	flex-shrink: 0;
}

.menu-icon-image {
	width: 42rpx;
	height: 42rpx;
	display: block;
}

.menu-title {
	flex: 1;
	min-width: 0;
	padding-left: 20rpx;
	font-size: 30rpx;
	color: #15161b;
	line-height: 42rpx;
}

.menu-chevron {
	flex-shrink: 0;
	font-size: 36rpx;
	color: #cdd0d8;
	line-height: 1;
}

/* 性能降级:关闭阴影 */
.am-performance-light .beans-card,
.am-performance-light .menu-card {
	box-shadow: none;
}

/* button 复用 .user-avatar-ring 彩环,这里清除微信 button 默认样式以保持彩环视觉 */
.avatar-btn-reset {
	background: none;
	padding: 0;
	margin: 0;
	line-height: normal;
	border: none;
}

.avatar-btn-reset::after {
	border: none;
}
</style>
