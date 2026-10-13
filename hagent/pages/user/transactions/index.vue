<template>
	<view class="trans-page" :class="pagePerformanceClass">
		<!-- 自绘导航栏:返回箭头 + 标题，paddingTop 留状态栏安全区 -->
		<view class="nav-bar" :style="{ paddingTop: statusBarHeight + 'px' }">
			<view class="nav-inner">
				<view class="nav-back" @tap="goBack">
					<text class="back-icon">‹</text>
				</view>
				<text class="nav-title">豆子明细</text>
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
			:lower-threshold="scrollLowerThreshold"
			@scrolltolower="loadMore"
		>
			<view class="content-inner" :style="[contentStyle, { paddingTop: (statusBarHeight + 44) + 'px' }]">
				<!-- 余额卡:蓝渐变，居中大数字 + 累计获得/消耗 -->
				<view class="balance-card">
					<text class="balance-label">当前豆子余额</text>
					<view class="balance-amount-row">
						<image class="balance-icon" src="/static/豆子.png" mode="aspectFit" />
						<text class="balance-amount">{{ formatNumber(account.balance) }}</text>
					</view>
					<view class="balance-stats">
						<view class="stat-item">
							<text class="stat-label">累计获得</text>
							<text class="stat-value">{{ formatNumber(account.totalEarned) }}</text>
						</view>
						<view class="stat-divider" />
						<view class="stat-item">
							<text class="stat-label">累计消耗</text>
							<text class="stat-value">{{ formatNumber(account.totalConsumed) }}</text>
						</view>
					</view>
				</view>

				<!-- 流水列表 -->
				<view class="trans-list" v-if="transactions.length">
					<view
						class="trans-item"
						v-for="item in transactions"
						:key="item.id"
					>
						<!-- 左图标:收入绿底加号 / 支出蓝底 -->
						<view class="item-icon" :class="isIncome(item) ? 'icon-income' : 'icon-expense'">
							<text class="item-icon-text">{{ isIncome(item) ? '+' : '-' }}</text>
						</view>
						<!-- 中:标题 + 时间 -->
						<view class="item-body">
							<text class="item-title">{{ itemTitle(item) }}</text>
							<text class="item-time">{{ item.createTime }}</text>
						</view>
						<!-- 右:金额，收入绿 / 支出红 -->
						<text class="item-amount" :class="isIncome(item) ? 'amount-income' : 'amount-expense'">
							{{ formatChange(item.changeValue) }}
						</text>
					</view>
				</view>

				<!-- 加载更多 / 触底提示 -->
				<view class="list-footer" v-if="transactions.length">
					<text class="footer-text" v-if="loading">加载中...</text>
					<text class="footer-text" v-else-if="!hasMore">没有更多了</text>
				</view>

				<!-- 空态 -->
				<view class="empty" v-if="!transactions.length && !loading">
					<text class="empty-text">暂无豆子流水</text>
				</view>
			</view>
		</scroll-view>
	</view>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getBalance, getTransactions } from '@/api/account.js'
import { usePerformanceMode } from '@/composables/usePerformanceMode.js'
import { usePageScroll } from '@/composables/usePageScroll.js'

const { pagePerformanceClass } = usePerformanceMode()
const { scrollEnhanced, scrollBounces, scrollShowScrollbar, scrollLowerThreshold, contentStyle } = usePageScroll()

// 状态栏高度（自绘导航栏安全区）
const statusBarHeight = ref(44)
uni.getSystemInfo({
	success: (info) => {
		statusBarHeight.value = info.statusBarHeight || 44
	},
})

// 账户余额（对接 BeanAccountVO: balance/totalEarned/totalConsumed）
const account = reactive({
	balance: 0,
	totalEarned: 0,
	totalConsumed: 0,
})

// 流水列表（分页累加，对接 IPage<BeanTransaction>）
const transactions = ref([])
const pageNum = ref(1)
const pageSize = 10
const total = ref(0)
const loading = ref(false)
// 是否还有下一页：已加载条数 < 总数
const hasMore = ref(true)

// 交易类型中文映射（remark 为空时兜底）
const TYPE_LABELS = {
	GIFT: '赠送',
	ISSUE: '发放',
	RECHARGE: '充值',
	DEDUCT: '扣费',
	REFUND: '退还',
	ADJUST: '调整',
	EXPIRE: '会员赠豆到期',
}

/** 千分位格式化（余额/累计均为整数豆） */
function formatNumber(value) {
	const num = Number(value) || 0
	return num.toLocaleString('en-US')
}

/** 是否为收入：变动值 >= 0 视为收入 */
function isIncome(item) {
	return (Number(item.changeValue) || 0) >= 0
}

/** 流水标题：优先 remark，空则按 type 兜底中文 */
function itemTitle(item) {
	if (item.remark) return item.remark
	return TYPE_LABELS[item.type] || '豆子变动'
}

/** 金额展示：带正负号 + 千分位 */
function formatChange(value) {
	const num = Number(value) || 0
	const sign = num >= 0 ? '+' : '-'
	return sign + Math.abs(num).toLocaleString('en-US')
}

/** 拉取余额（仅首屏一次） */
async function fetchBalance() {
	try {
		const res = await getBalance()
		account.balance = (res && res.balance) || 0
		account.totalEarned = (res && res.totalEarned) || 0
		account.totalConsumed = (res && res.totalConsumed) || 0
	} catch (e) {
		// 错误已由 request 统一提示，此处保持默认 0 展示
	}
}

/** 拉取一页流水并累加 */
async function fetchTransactions() {
	if (loading.value || !hasMore.value) return
	loading.value = true
	try {
		const res = await getTransactions({ pageNum: pageNum.value, pageSize })
		const records = (res && res.records) || []
		total.value = (res && res.total) || 0
		transactions.value = transactions.value.concat(records)
		hasMore.value = transactions.value.length < total.value
		if (hasMore.value) {
			pageNum.value += 1
		}
	} catch (e) {
		// 错误已由 request 统一提示，停止后续加载避免空循环
		hasMore.value = false
	} finally {
		loading.value = false
	}
}

/** 触底加载下一页 */
function loadMore() {
	fetchTransactions()
}

/** 返回上一页 */
function goBack() {
	uni.navigateBack()
}

onLoad(() => {
	fetchBalance()
	fetchTransactions()
})
</script>

<style lang="scss" scoped>
.trans-page {
	min-height: 100vh;
	background-color: #F6F7FB;
}

/* ====== 自绘导航栏 ====== */
.nav-bar {
	position: fixed;
	top: 0;
	left: 0;
	right: 0;
	z-index: 30;
	background-color: #F6F7FB;
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
	color: #0F172A;
	font-weight: 300;
}

.nav-title {
	font-size: 34rpx;
	font-weight: 600;
	color: #0F172A;
}

.nav-placeholder {
	width: 72rpx;
}

/* ====== 内容区 ====== */
.content {
	height: 100vh;
}

.content-inner {
	box-sizing: border-box;
	padding-left: 36rpx;
	padding-right: 36rpx;
	padding-top: 16rpx;
}

/* ====== 余额卡（蓝渐变） ====== */
.balance-card {
	margin-top: 16rpx;
	border-radius: 40rpx;
	background: linear-gradient(135deg, #5E92EE, #3F6FD8);
	padding: 44rpx;
	text-align: center;
	box-shadow: 0 16rpx 48rpx rgba(96, 140, 224, 0.22);
}

.balance-label {
	display: block;
	font-size: 26rpx;
	color: rgba(255, 255, 255, 0.75);
}

.balance-amount-row {
	display: flex;
	align-items: center;
	justify-content: center;
	gap: 16rpx;
	margin-top: 16rpx;
}

.balance-icon {
	width: 56rpx;
	height: 56rpx;
}

.balance-amount {
	font-size: 84rpx;
	font-weight: 800;
	color: #FFFFFF;
	letter-spacing: -2rpx;
	line-height: 1;
}

.balance-stats {
	display: flex;
	align-items: center;
	justify-content: center;
	gap: 60rpx;
	margin-top: 32rpx;
}

.stat-item {
	display: flex;
	flex-direction: column;
	align-items: center;
}

.stat-label {
	font-size: 22rpx;
	color: rgba(255, 255, 255, 0.7);
}

.stat-value {
	margin-top: 6rpx;
	font-size: 30rpx;
	font-weight: 600;
	color: #FFFFFF;
}

.stat-divider {
	width: 1rpx;
	height: 48rpx;
	background-color: rgba(255, 255, 255, 0.25);
}

/* ====== 流水列表 ====== */
.trans-list {
	margin-top: 36rpx;
	border-radius: 32rpx;
	background-color: #FFFFFF;
	border: 3rpx solid #A0C4FB;
	overflow: hidden;
}

.trans-item {
	display: flex;
	align-items: center;
	gap: 24rpx;
	padding: 28rpx 32rpx;
	position: relative;

	/* 分割线（最后一项不要） */
	&::after {
		content: '';
		position: absolute;
		bottom: 0;
		left: 32rpx;
		right: 32rpx;
		height: 1rpx;
		background-color: #EEF2F8;
	}

	&:last-child::after {
		display: none;
	}
}

.item-icon {
	width: 76rpx;
	height: 76rpx;
	border-radius: 22rpx;
	flex: none;
	display: flex;
	align-items: center;
	justify-content: center;
}

.icon-income {
	background-color: #E8F6EE;
}

.icon-expense {
	background-color: #DEEAFB;
}

.item-icon-text {
	font-size: 38rpx;
	font-weight: 700;
	line-height: 1;
}

.icon-income .item-icon-text {
	color: #16A34A;
}

.icon-expense .item-icon-text {
	color: #4F84EA;
}

.item-body {
	flex: 1;
	min-width: 0;
}

.item-title {
	display: block;
	font-size: 28rpx;
	color: #0F172A;
	/* 单行省略 */
	overflow: hidden;
	white-space: nowrap;
	text-overflow: ellipsis;
}

.item-time {
	display: block;
	margin-top: 4rpx;
	font-size: 22rpx;
	color: #94A3B8;
}

.item-amount {
	flex: none;
	font-size: 30rpx;
	font-weight: 700;
}

.amount-income {
	color: #16A34A;
}

.amount-expense {
	color: #EF4444;
}

/* ====== 列表底部 ====== */
.list-footer {
	display: flex;
	align-items: center;
	justify-content: center;
	padding: 32rpx 0;
}

.footer-text {
	font-size: 24rpx;
	color: #94A3B8;
}

/* ====== 空态 ====== */
.empty {
	display: flex;
	align-items: center;
	justify-content: center;
	padding-top: 160rpx;
}

.empty-text {
	font-size: 28rpx;
	color: #94A3B8;
}

/* 性能降级:去阴影 */
.am-performance-light .balance-card {
	box-shadow: none;
}
</style>
