<template>
	<view class="recharge-page" :class="pagePerformanceClass">
		<!-- 自绘导航栏:返回箭头 + 标题，paddingTop 留状态栏安全区 -->
		<view class="nav-bar" :style="{ paddingTop: statusBarHeight + 'px' }">
			<view class="nav-inner">
				<view class="nav-back" @tap="goBack">
					<text class="back-icon">‹</text>
				</view>
				<text class="nav-title">豆子充值</text>
				<view class="nav-placeholder" />
			</view>
		</view>

		<!-- 内容滚动区 -->
		<scroll-view
			class="content"
			scroll-y
			:enhanced="scrollEnhanced"
			:bounces="scrollBounces"
			:show-scrollbar="scrollShowScrollbar"
		>
			<view class="content-inner" :style="{ paddingTop: (statusBarHeight + 44) + 'px' }">
				<!-- 当前余额卡 -->
				<view class="balance-card">
					<text class="balance-label">当前豆子余额</text>
					<view class="balance-amount-row">
						<image class="balance-icon" src="/static/豆子.png" mode="aspectFit" />
						<text class="balance-amount">{{ balanceDisplay }}</text>
					</view>
					<view v-if="membershipState === 'member'" class="balance-validity">
						<view class="balance-validity-head">
							<view class="balance-validity-dot" />
							<text class="balance-validity-title">加油包永久有效</text>
						</view>
						<text class="balance-validity-desc">当前会员有效至 {{ membershipExpireText }}；购买后豆子不限使用时间。</text>
					</view>
				</view>

				<!-- 会员状态：加油包只允许有效会员购买 -->
				<view v-if="membershipState === 'loading'" class="membership-state-card">
					<text class="membership-state-title">正在确认会员状态...</text>
				</view>

				<view v-else-if="membershipState === 'nonmember'" class="membership-state-card membership-gate-card">
					<text class="membership-gate-badge">会员专享</text>
					<text class="membership-state-title">加油包仅限有效会员购买</text>
					<text class="membership-state-desc">开通会员后即可购买；加油包豆永久有效，会员到期后仍可使用。</text>
				</view>

				<view v-else-if="membershipState === 'error'" class="membership-state-card">
					<text class="membership-state-title">会员状态加载失败</text>
					<text class="membership-state-desc">请检查网络后重新加载</text>
				</view>

				<!-- 档位卡片网格 -->
				<view class="package-grid" v-if="membershipState === 'member' && packages.length">
					<view
						class="package-card"
						v-for="item in packages"
						:key="item.packageId"
						:class="{ selected: selectedId === item.packageId }"
						@tap="selectPackage(item)"
					>
						<text class="package-name" v-if="item.name">{{ item.name }}</text>
						<view class="package-beans-row">
							<text class="package-beans">{{ item.beans }}</text>
							<image class="package-bean-icon" src="/static/豆子.png" mode="aspectFit" />
						</view>
						<text class="package-price">¥{{ formatPrice(item.price) }}</text>
					</view>
				</view>

				<!-- 空态 -->
				<view class="empty" v-if="membershipState === 'member' && !packages.length && !loading">
					<text class="empty-text">暂无充值套餐</text>
				</view>

				<image
					v-if="membershipState === 'member'"
					class="recharge-notice"
					src="/static/membership/recharge-notice.jpg"
					mode="widthFix"
				/>
			</view>
		</scroll-view>

		<!-- 底部充值按钮 -->
		<view class="footer">
			<view class="agreement-tip">
				<view class="agreement-consent" @tap="toggleAgreement">
					<view class="agreement-checkbox" :class="{ checked: agreementAccepted }">
						<text v-if="agreementAccepted" class="agreement-checkmark">✓</text>
					</view>
					<text>我已阅读并同意</text>
				</view>
				<text class="agreement-link" @tap="openPurchaseAgreement">《充值与会员购买协议》</text>
			</view>
			<button v-if="membershipState === 'loading'" class="submit-btn disabled" disabled>
				正在确认会员状态...
			</button>
			<button v-if="membershipState === 'nonmember'" class="submit-btn" @tap="goMembership">
				先开通会员
			</button>
			<button v-if="membershipState === 'error'" class="submit-btn" @tap="refreshPage">
				重新加载
			</button>
			<!-- #ifdef MP-WEIXIN -->
			<button v-if="membershipState === 'member'" class="submit-btn" :class="{ disabled: !canSubmit }" :disabled="!canSubmit" @tap="handleRecharge">
				{{ submitText }}
			</button>
			<!-- #endif -->
			<!-- #ifndef MP-WEIXIN -->
			<button v-if="membershipState === 'member'" class="submit-btn disabled" disabled @tap="handleAppTip">
				请在微信小程序中充值
			</button>
			<!-- #endif -->
		</view>
	</view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { getPackages } from '@/api/beanPackage.js'
import { getBalance } from '@/api/account.js'
import { getMembership } from '@/api/user.js'
import { usePerformanceMode } from '@/composables/usePerformanceMode.js'
import { usePageScroll } from '@/composables/usePageScroll.js'
import { useSafeNavigation } from '@/composables/useSafeNavigation.js'
import { useStatusBarHeight } from '@/composables/useStatusBarHeight.js'
import { useConversationStore } from '@/store/conversation.js'
import {
	getVirtualPaymentErrorMessage,
	isVirtualPaymentCancelled,
	useVirtualPayment,
} from '@/composables/useVirtualPayment.js'

const { pagePerformanceClass } = usePerformanceMode()
const { scrollEnhanced, scrollBounces, scrollShowScrollbar } = usePageScroll()
const { payVirtualGoods } = useVirtualPayment()
const { backOrFallback } = useSafeNavigation('profile')
const conversationStore = useConversationStore()
const resumeTask = ref(false)

onLoad((query) => {
	resumeTask.value = String(query?.resumeTask || '') === '1'
})

// 状态栏高度（自绘导航栏安全区）
const statusBarHeight = useStatusBarHeight()

// 充值套餐列表（对接 { packageId, name?, beans, price(元), sort }）
const packages = ref([])
// 当前选中的套餐 ID
const selectedId = ref(null)
// 当前余额，null 表示未取到
const balance = ref(null)
// 列表加载中
const loading = ref(false)
// 支付中标志，防重复点击
const paying = ref(false)
// 协议必须由用户主动勾选，进入页面时默认不同意。
const agreementAccepted = ref(false)
// 会员状态：loading / member / nonmember / error
const membershipState = ref('loading')
// 当前会员信息：{ levelName, expireTime, remainDays, cardBg }
const membership = ref(null)

// 选中的套餐对象
const selectedPackage = computed(() => packages.value.find((p) => p.packageId === selectedId.value) || null)

// 是否可提交：有效会员、已选套餐、主动同意协议且非支付中
const canSubmit = computed(() => membershipState.value === 'member'
	&& !!selectedPackage.value
	&& agreementAccepted.value
	&& !paying.value)

// 有效期展示，接口时间为 ISO 本地时间时去掉 T 与秒，保持小程序端易读。
const membershipExpireText = computed(() => {
	const raw = membership.value?.expireTime
	if (!raw) return '当前会员到期时'
	return String(raw).replace('T', ' ').slice(0, 16)
})

// 余额展示:千分位，未取到显示占位
const balanceDisplay = computed(() => {
	if (balance.value === null || balance.value === undefined) return '--'
	return Number(balance.value).toLocaleString('en-US')
})

// 按钮文案
const submitText = computed(() => {
	if (paying.value) return '处理中...'
	if (selectedPackage.value) return `立即充值 ¥${formatPrice(selectedPackage.value.price)}`
	return '请选择充值档位'
})

// 价格格式化（元，保留两位）
function formatPrice(price) {
	return Number(price || 0).toFixed(2)
}

// 选择套餐
function selectPackage(item) {
	selectedId.value = item.packageId
}

/** 拉取充值套餐列表 */
async function loadPackages() {
	loading.value = true
	try {
		const list = await getPackages()
		packages.value = Array.isArray(list) ? list : []
		// 当前选中项已下架时，自动回到第一档。
		if (packages.value.length && !packages.value.some((item) => item.packageId === selectedId.value)) {
			selectedId.value = packages.value[0].packageId
		}
		if (!packages.value.length) selectedId.value = null
	} catch (e) {
		// 错误已由 request 统一提示
	} finally {
		loading.value = false
	}
}

/** 拉取会员状态。非会员不请求套餐，避免用户进入不可履约的支付流程。 */
async function loadMembershipStatus() {
	membershipState.value = 'loading'
	try {
		const info = await getMembership()
		membership.value = info || null
		membershipState.value = info?.levelName ? 'member' : 'nonmember'
		return membershipState.value
	} catch (e) {
		membership.value = null
		membershipState.value = 'error'
		return 'error'
	}
}

/** 拉取豆子余额 */
async function loadBalance() {
	try {
		// 后端返回 BeanAccountVO：{ balance, totalEarned, totalConsumed }
		const res = await getBalance()
		balance.value = res?.balance ?? null
		return true
	} catch (e) {
		// 错误已由 request 统一提示
		return false
	}
}

/** 首次进入、开通会员返回本页时都重新确认资格和余额。 */
async function refreshPage() {
	const state = await loadMembershipStatus()
	loadBalance()
	if (state === 'member') {
		await loadPackages()
	} else {
		packages.value = []
		selectedId.value = null
		loading.value = false
	}
}

onShow(() => {
	refreshPage()
})

/** 返回上一页 */
function goBack() {
	backOrFallback()
}

/** 非会员跳转开通页；返回时 onShow 会自动刷新会员状态。 */
function goMembership() {
	const suffix = resumeTask.value ? '?resumeTask=1' : ''
	uni.navigateTo({ url: '/pages/membership/purchase/index' + suffix })
}

function openPurchaseAgreement() {
	uni.navigateTo({ url: '/pages/legal/detail?type=recharge' })
}

function toggleAgreement() {
	agreementAccepted.value = !agreementAccepted.value
}

// #ifdef MP-WEIXIN
/** 发起充值（小程序端） */
async function handleRecharge() {
	if (!agreementAccepted.value) {
		uni.showToast({ title: '请先阅读并同意协议', icon: 'none' })
		return
	}
	if (!canSubmit.value) return
	paying.value = true
	try {
		const result = await payVirtualGoods({
			productType: 'BEAN_RECHARGE',
			productId: selectedPackage.value.packageId,
			onServerPaid: async () => {
				// 支付推送一到就更新底层余额，不等模拟器二维码关窗。
				const refreshed = await loadBalance()
				if (!refreshed) throw new Error('余额预刷新失败')
				uni.$emit('user-account-refresh')
			},
		})

		if (result.paid) {
			uni.showToast({ title: '充值成功', icon: 'success', duration: 1500 })
			if (!result.preSynced) {
				// 客户端回调先结束的真机路径在这里刷新。
				loadBalance()
				uni.$emit('user-account-refresh')
			}
			if (resumeTask.value && conversationStore.pendingResumeTask) {
				conversationStore.markPendingResumeReady()
				uni.$emit('agent-resume-ready')
				setTimeout(() => uni.navigateBack(), 500)
			}
		} else {
			uni.showToast({ title: '支付结果确认中，请稍后查看', icon: 'none' })
		}
	} catch (e) {
		if (isVirtualPaymentCancelled(e)) {
			uni.showToast({ title: '已取消支付', icon: 'none' })
		} else {
			uni.showToast({
				title: getVirtualPaymentErrorMessage(e, '充值失败，请重试'),
				icon: 'none',
			})
		}
	} finally {
		paying.value = false
	}
}
// #endif

// #ifndef MP-WEIXIN
/** 非小程序端占位提示 */
function handleAppTip() {
	uni.showToast({ title: '请在微信小程序中充值', icon: 'none' })
}
// #endif
</script>

<style lang="scss" scoped>
.recharge-page {
	height: 100vh;
	background: #f5f6f9;
	display: flex;
	flex-direction: column;
}

/* ====== 自绘导航栏 ====== */
.nav-bar {
	position: fixed;
	top: 0;
	left: 0;
	right: 0;
	z-index: 30;
	background-color: #ffffff;
}

.nav-inner {
	height: 44px;
	display: flex;
	align-items: center;
	justify-content: space-between;
	padding: 0 16rpx;
}

.nav-back {
	width: 72rpx;
	height: 72rpx;
	display: flex;
	align-items: center;
	justify-content: center;
}

.back-icon {
	font-size: 52rpx;
	color: #1a1a1a;
	font-weight: 300;
}

.nav-title {
	font-size: 34rpx;
	font-weight: 600;
	color: #1a1a1a;
}

.nav-placeholder {
	width: 72rpx;
}

/* ====== 内容区 ====== */
.content {
	flex: 1;
	height: 0;
}

.content-inner {
	padding: 0 32rpx 40rpx;
	box-sizing: border-box;
}

.agreement-tip {
	padding: 0 12rpx 18rpx;
	display: flex;
	align-items: center;
	justify-content: center;
	font-size: 22rpx;
	color: #8a909c;
}

.agreement-consent {
	display: flex;
	align-items: center;
}

.agreement-checkbox {
	width: 28rpx;
	height: 28rpx;
	margin-right: 10rpx;
	border: 2rpx solid #b8bfca;
	border-radius: 6rpx;
	box-sizing: border-box;
	display: flex;
	align-items: center;
	justify-content: center;
}

.agreement-checkbox.checked {
	border-color: #4f84ea;
	background-color: #4f84ea;
}

.agreement-checkmark {
	color: #ffffff;
	font-size: 22rpx;
	font-weight: 700;
	line-height: 1;
}

.agreement-link {
	color: #4f84ea;
}

/* ====== 余额卡 ====== */
.balance-card {
	margin-top: 24rpx;
	padding: 40rpx 36rpx;
	border-radius: 36rpx;
	background: linear-gradient(135deg, #4f84ea, #8fb4fb);
	box-shadow: 0 8rpx 28rpx rgba(96, 140, 224, 0.16);
	display: flex;
	flex-direction: column;
	align-items: center;
}

.balance-label {
	font-size: 24rpx;
	color: rgba(255, 255, 255, 0.85);
	line-height: 34rpx;
}

.balance-amount-row {
	margin-top: 16rpx;
	display: flex;
	align-items: center;
	gap: 12rpx;
}

.balance-icon {
	width: 44rpx;
	height: 44rpx;
}

.balance-amount {
	font-size: 64rpx;
	font-weight: 700;
	color: #ffffff;
	line-height: 1;
	letter-spacing: -1rpx;
}

.balance-validity {
	width: 100%;
	box-sizing: border-box;
	margin-top: 28rpx;
	padding: 20rpx 22rpx;
	border: 2rpx solid rgba(255, 255, 255, 0.2);
	border-radius: 20rpx;
	background: rgba(255, 255, 255, 0.14);
}

.balance-validity-head {
	display: flex;
	align-items: center;
	gap: 12rpx;
}

.balance-validity-dot {
	width: 12rpx;
	height: 12rpx;
	border-radius: 50%;
	background: #fff07a;
	box-shadow: 0 0 12rpx rgba(255, 240, 122, 0.55);
}

.balance-validity-title {
	font-size: 24rpx;
	font-weight: 600;
	color: #ffffff;
	line-height: 34rpx;
}

.balance-validity-desc {
	display: block;
	margin-top: 8rpx;
	font-size: 22rpx;
	color: rgba(255, 255, 255, 0.82);
	line-height: 34rpx;
}

/* ====== 会员资格提示 ====== */
.membership-state-card {
	margin-top: 28rpx;
	padding: 28rpx 30rpx;
	border-radius: 24rpx;
	background: #ffffff;
	box-shadow: 0 8rpx 28rpx rgba(20, 20, 40, 0.06);
	display: flex;
	flex-direction: column;
	gap: 10rpx;
}

.membership-gate-card {
	align-items: flex-start;
	background: linear-gradient(135deg, #f3f7ff, #ffffff);
	border: 2rpx solid #dce8ff;
}

.membership-gate-badge {
	padding: 6rpx 16rpx;
	border-radius: 999rpx;
	background: #4f84ea;
	color: #ffffff;
	font-size: 22rpx;
	line-height: 30rpx;
}

.membership-state-title {
	font-size: 28rpx;
	font-weight: 600;
	color: #24262d;
	line-height: 40rpx;
}

.membership-state-desc {
	font-size: 24rpx;
	color: #7d828e;
	line-height: 38rpx;
}

/* ====== 档位网格 ====== */
.package-grid {
	margin-top: 32rpx;
	display: flex;
	flex-wrap: wrap;
	gap: 24rpx;
}

.package-card {
	width: calc((100% - 24rpx) / 2);
	box-sizing: border-box;
	padding: 36rpx 24rpx;
	border-radius: 28rpx;
	background: #ffffff;
	box-shadow: 0 8rpx 28rpx rgba(20, 20, 40, 0.07);
	display: flex;
	flex-direction: column;
	align-items: center;
	border: 4rpx solid transparent;
	transition: transform 0.12s ease;
}

.package-card:active {
	transform: scale(0.98);
}

.package-card.selected {
	border-color: #4f84ea;
	background: #f3f7ff;
}

.package-name {
	margin-bottom: 18rpx;
	font-size: 26rpx;
	font-weight: 600;
	color: #505461;
	line-height: 36rpx;
}

.package-beans-row {
	display: flex;
	align-items: center;
	gap: 8rpx;
}

.package-beans {
	font-size: 44rpx;
	font-weight: 700;
	color: #15161b;
	line-height: 1;
}

.package-bean-icon {
	width: 36rpx;
	height: 36rpx;
}

.package-price {
	margin-top: 20rpx;
	font-size: 28rpx;
	font-weight: 600;
	color: #4f84ea;
	line-height: 1;
}

/* ====== 空态 ====== */
.empty {
	padding-top: 160rpx;
	display: flex;
	justify-content: center;
}

.empty-text {
	font-size: 28rpx;
	color: #9a9ea8;
}

.recharge-notice {
	display: block;
	width: 100%;
	height: auto;
	margin-top: 20rpx;
	margin-bottom: 40rpx;
	border-radius: 24rpx;
}

/* ====== 底部按钮 ====== */
.footer {
	padding: 20rpx 32rpx;
	padding-bottom: calc(20rpx + constant(safe-area-inset-bottom));
	padding-bottom: calc(20rpx + env(safe-area-inset-bottom));
	background-color: #ffffff;
}

.submit-btn {
	width: 100%;
	height: 92rpx;
	line-height: 92rpx;
	background-color: #4f84ea;
	color: #ffffff;
	font-size: 32rpx;
	font-weight: 500;
	border-radius: 46rpx;
	border: none;

	&::after {
		border: none;
	}
}

.submit-btn.disabled {
	background-color: #b0bec5;
	color: #ffffff;
}

/* 性能降级:关闭阴影 */
.am-performance-light .balance-card,
.am-performance-light .package-card,
.am-performance-light .membership-state-card {
	box-shadow: none;
}
</style>
