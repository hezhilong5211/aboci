<template>
	<view class="purchase-page" :class="pagePerformanceClass">
		<!-- 自绘导航栏:返回箭头 + 标题，paddingTop 留状态栏安全区 -->
		<view class="nav-bar" :style="{ paddingTop: statusBarHeight + 'px' }">
			<view class="nav-inner">
				<view class="nav-back" @tap="goBack">
					<text class="back-icon">‹</text>
				</view>
				<text class="nav-title">开通会员</text>
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
				<!-- 等级卡片列表 -->
				<view class="level-list" v-if="levels.length">
					<view
						class="level-card"
						v-for="item in levels"
						:key="item.levelId"
						:class="{ selected: selectedId === item.levelId }"
						:style="cardStyle(item)"
						@tap="selectLevel(item)"
					>
						<view class="level-glow" />
						<!-- 选中标记 -->
						<view class="level-check" v-if="selectedId === item.levelId">
							<text class="level-check-text">✓</text>
						</view>
						<text class="level-name">{{ item.name }}</text>
						<view class="level-price-row">
							<text class="level-price-symbol">¥</text>
							<text class="level-price">{{ formatPrice(item.price) }}</text>
						</view>
						<view class="level-meta">
							<text class="level-meta-item">{{ formatMembershipMeta(item) }}</text>
						</view>
					</view>
				</view>
				<image
					v-if="levels.length"
					class="purchase-notice"
					src="/static/membership/recharge-notice.jpg"
					mode="widthFix"
				/>

				<!-- 空态 -->
				<view class="empty" v-if="!levels.length && !loading">
					<text class="empty-text">暂无可开通的会员等级</text>
				</view>
			</view>
		</scroll-view>

		<!-- 底部开通按钮 -->
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
			<!-- #ifdef MP-WEIXIN -->
			<button class="submit-btn" :class="{ disabled: !canSubmit }" :disabled="!canSubmit" @tap="handlePurchase">
				{{ submitText }}
			</button>
			<!-- #endif -->
			<!-- #ifndef MP-WEIXIN -->
			<button class="submit-btn disabled" disabled @tap="handleAppTip">
				请在微信小程序中开通
			</button>
			<!-- #endif -->
		</view>
	</view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getLevels } from '@/api/membership.js'
import { usePerformanceMode } from '@/composables/usePerformanceMode.js'
import { usePageScroll } from '@/composables/usePageScroll.js'
import { useSafeNavigation } from '@/composables/useSafeNavigation.js'
import { useStatusBarHeight } from '@/composables/useStatusBarHeight.js'
import {
	getVirtualPaymentErrorMessage,
	isVirtualPaymentCancelled,
	useVirtualPayment,
} from '@/composables/useVirtualPayment.js'
import config from '@/utils/config.js'
import { useConversationStore } from '@/store/conversation.js'

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

// 会员等级列表（对接 { levelId, name, price(元), durationDays, grantBeans, cardBg }）
const levels = ref([])
// 当前选中的等级 ID
const selectedId = ref(null)
// 列表加载中
const loading = ref(false)
// 支付中标志，防重复点击
const paying = ref(false)
// 协议必须由用户主动勾选，进入页面时默认不同意。
const agreementAccepted = ref(false)

// 选中的等级对象
const selectedLevel = computed(() => levels.value.find((l) => l.levelId === selectedId.value) || null)

// 是否可提交：已选等级、主动同意协议且非支付中
const canSubmit = computed(() => !!selectedLevel.value && agreementAccepted.value && !paying.value)

// 按钮文案
const submitText = computed(() => {
	if (paying.value) return '处理中...'
	if (selectedLevel.value) return `立即开通 ¥${formatPrice(selectedLevel.value.price)}`
	return '请选择会员等级'
})

// 价格格式化（元，保留两位）
function formatPrice(price) {
	return Number(price || 0).toFixed(2)
}

// 会员时长按后台配置动态显示，整年/整月补充对应天数。
function formatMembershipMeta(item) {
	const grantBeans = Number(item?.grantBeans || 0)
	const durationDays = Number(item?.durationDays || 0)
	let durationText = `${durationDays}天`
	if (durationDays > 0 && durationDays % 365 === 0) {
		durationText = `${durationDays / 365}年（${durationDays}天）`
	} else if (durationDays > 0 && durationDays % 30 === 0) {
		durationText = `${durationDays / 30}个月（${durationDays}天）`
	}
	return `送${grantBeans}豆，有效期${durationText}`
}

// 等级卡背景:cardBg 非空时叠加该图，渐变作兜底打底（与 ProfileContent 一致）
function cardStyle(item) {
	const gradient = 'linear-gradient(135deg, #4F84EA, #8FB4FB)'
	const bg = item.cardBg
	if (bg) {
		const bgUrl = bg.startsWith('http') ? bg : config.baseUrl + bg
		return {
			backgroundImage: `url("${bgUrl}"), ${gradient}`,
			backgroundSize: 'cover',
			backgroundPosition: 'center',
		}
	}
	return { backgroundImage: gradient }
}

// 选择等级
function selectLevel(item) {
	selectedId.value = item.levelId
}

/** 拉取会员等级列表 */
async function loadLevels() {
	loading.value = true
	try {
		const list = await getLevels()
		levels.value = Array.isArray(list) ? list : []
		// 默认选中第一项
		if (levels.value.length && selectedId.value === null) {
			selectedId.value = levels.value[0].levelId
		}
	} catch (e) {
		// 错误已由 request 统一提示
	} finally {
		loading.value = false
	}
}

loadLevels()

/** 返回上一页 */
function goBack() {
	backOrFallback()
}

function openPurchaseAgreement() {
	uni.navigateTo({ url: '/pages/legal/detail?type=recharge' })
}

function toggleAgreement() {
	agreementAccepted.value = !agreementAccepted.value
}

// #ifdef MP-WEIXIN
/** 发起开通（小程序端） */
async function handlePurchase() {
	if (!agreementAccepted.value) {
		uni.showToast({ title: '请先阅读并同意协议', icon: 'none' })
		return
	}
	if (!canSubmit.value) return
	paying.value = true
	try {
		const result = await payVirtualGoods({
			productType: 'MEMBERSHIP',
			productId: selectedLevel.value.levelId,
			onServerPaid: () => {
				// 模拟器二维码未关闭时也先刷新底层个人中心。
				uni.$emit('user-account-refresh')
			},
		})

		if (result.paid) {
			uni.showToast({ title: '开通成功', icon: 'success', duration: 1200 })
			// 通知个人中心刷新会员/余额展示
			if (!result.preSynced) uni.$emit('user-account-refresh')
			if (resumeTask.value && conversationStore.pendingResumeTask) {
				conversationStore.markPendingResumeReady()
				uni.$emit('agent-resume-ready')
			}
			// Toast 先完整展示，再安全返回；避免页面销毁时产生配对警告。
			setTimeout(() => {
				uni.hideToast()
				if (resumeTask.value) {
					const pages = getCurrentPages()
					const delta = Math.min(2, Math.max(1, pages.length - 1))
					uni.navigateBack({ delta })
				} else {
					backOrFallback()
				}
			}, 1400)
		} else {
			uni.showToast({ title: '支付结果确认中，请稍后查看', icon: 'none' })
		}
	} catch (e) {
		if (isVirtualPaymentCancelled(e)) {
			uni.showToast({ title: '已取消支付', icon: 'none' })
		} else {
			uni.showToast({
				title: getVirtualPaymentErrorMessage(e, '开通失败，请重试'),
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
	uni.showToast({ title: '请在微信小程序中开通会员', icon: 'none' })
}
// #endif
</script>

<style lang="scss" scoped>
.purchase-page {
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
	padding: 0 32rpx;
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

/* ====== 等级卡片 ====== */
.level-list {
	padding-top: 24rpx;
	padding-bottom: 0;
}

.purchase-notice {
	display: block;
	width: 100%;
	margin-top: 20rpx;
	margin-bottom: 40rpx;
	border-radius: 24rpx;
}

.level-card {
	position: relative;
	margin-top: 28rpx;
	padding: 36rpx 36rpx 32rpx;
	border-radius: 36rpx;
	overflow: hidden;
	box-shadow: 0 8rpx 28rpx rgba(96, 140, 224, 0.16);
	border: 4rpx solid transparent;
	transition: transform 0.12s ease;
}

.level-card:first-child {
	margin-top: 0;
}

.level-card:active {
	transform: scale(0.99);
}

.level-card.selected {
	border-color: #ffd66b;
}

/* 右上白光晕 */
.level-glow {
	position: absolute;
	top: -100rpx;
	right: -100rpx;
	width: 260rpx;
	height: 260rpx;
	border-radius: 50%;
	background: rgba(255, 255, 255, 0.25);
	filter: blur(20rpx);
}

/* 选中标记 */
.level-check {
	position: absolute;
	top: 24rpx;
	right: 28rpx;
	width: 48rpx;
	height: 48rpx;
	border-radius: 50%;
	background: #ffd66b;
	display: flex;
	align-items: center;
	justify-content: center;
}

.level-check-text {
	font-size: 30rpx;
	font-weight: 700;
	color: #7a4f00;
	line-height: 1;
}

.level-name {
	position: relative;
	display: block;
	font-size: 36rpx;
	font-weight: 600;
	color: #ffffff;
	line-height: 1;
}

.level-price-row {
	position: relative;
	display: flex;
	align-items: baseline;
	margin-top: 24rpx;
}

.level-price-symbol {
	font-size: 32rpx;
	font-weight: 600;
	color: #ffffff;
}

.level-price {
	font-size: 60rpx;
	font-weight: 700;
	color: #ffffff;
	line-height: 1;
	margin-left: 4rpx;
}

.level-meta {
	position: relative;
	margin-top: 20rpx;
	display: flex;
	flex-wrap: wrap;
	gap: 24rpx;
}

.level-meta-item {
	font-size: 24rpx;
	color: rgba(255, 255, 255, 0.9);
	line-height: 34rpx;
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
.am-performance-light .level-card {
	box-shadow: none;
}
</style>
