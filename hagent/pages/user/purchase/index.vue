<template>
	<view class="purchase-page" :class="pagePerformanceClass">
		<!-- 自绘导航栏:返回箭头 + 标题，paddingTop 留状态栏安全区 -->
		<view class="nav-bar" :style="{ paddingTop: statusBarHeight + 'px' }">
			<view class="nav-inner">
				<view class="nav-back" @tap="goBack">
					<text class="back-icon">‹</text>
				</view>
				<text class="nav-title">购买记录</text>
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
				<!-- 订单列表(只读;点击整行复制订单号,便于客服对账) -->
				<view class="order-list" v-if="orders.length">
					<view
						class="order-item"
						v-for="item in orders"
						:key="item.orderNo"
						@tap="copyOrderNo(item.orderNo)"
					>
						<!-- 左:类型标 -->
						<view class="item-icon" :class="item.productType === 'MEMBERSHIP' ? 'icon-member' : 'icon-bean'">
							<text class="item-icon-text">{{ typeLabel(item) }}</text>
						</view>
						<!-- 中:商品名 + 时间 -->
						<view class="item-body">
							<text class="item-title">{{ item.productName || item.subject || '购买' }}</text>
							<text class="item-time">{{ item.createTime }}</text>
							<text v-if="item.status === 4" class="item-refund-tip">
								{{ item.fulfillmentError || '权益未发放，请联系客服退款' }}
							</text>
						</view>
						<!-- 右:金额 + 状态 -->
						<view class="item-right">
							<text class="item-amount">¥{{ formatAmount(item.amount) }}</text>
							<text class="item-status" :class="'status-' + item.status">{{ item.statusText }}</text>
						</view>
					</view>
				</view>

				<!-- 加载更多 / 触底提示 -->
				<view class="list-footer" v-if="orders.length">
					<text class="footer-text" v-if="loading">加载中...</text>
					<text class="footer-text" v-else-if="!hasMore">没有更多了</text>
				</view>

				<!-- 空态 -->
				<view class="empty" v-if="!orders.length && !loading">
					<text class="empty-text">暂无购买记录</text>
				</view>

				<!-- 底部说明 -->
				<view class="notice" v-if="orders.length">
					<text class="notice-text">点击记录可复制订单号 · 如显示待退款请联系客服处理</text>
				</view>
			</view>
		</scroll-view>
	</view>
</template>

<script setup>
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getPurchaseRecords } from '@/api/pay.js'
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

// 订单列表（分页累加，对接 IPage<PurchaseRecordVO>）
const orders = ref([])
const pageNum = ref(1)
const pageSize = 20
const total = ref(0)
const loading = ref(false)
const hasMore = ref(true)

/** 类型标文案:会员 / 豆子;历史订单无类型时兜底「订单」 */
function typeLabel(item) {
	if (item.productType === 'MEMBERSHIP') return '会员'
	if (item.productType === 'BEAN_RECHARGE') return '豆子'
	return '订单'
}

/** 金额分转元,保留 2 位 */
function formatAmount(fen) {
	return ((Number(fen) || 0) / 100).toFixed(2)
}

/** 点击复制订单号(客服对账凭据) */
function copyOrderNo(orderNo) {
	if (!orderNo) return
	uni.setClipboardData({
		data: orderNo,
		success: () => uni.showToast({ title: '订单号已复制', icon: 'none' }),
	})
}

/** 拉取一页订单并累加 */
async function fetchOrders() {
	if (loading.value || !hasMore.value) return
	loading.value = true
	try {
		const res = await getPurchaseRecords(pageNum.value, pageSize)
		const records = (res && res.records) || []
		total.value = (res && res.total) || 0
		orders.value = orders.value.concat(records)
		hasMore.value = orders.value.length < total.value
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
	fetchOrders()
}

/** 返回上一页 */
function goBack() {
	uni.navigateBack()
}

onLoad(() => {
	fetchOrders()
})
</script>

<style lang="scss" scoped>
.purchase-page {
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

/* ====== 订单列表(样式对齐豆子明细) ====== */
.order-list {
	margin-top: 16rpx;
	border-radius: 32rpx;
	background-color: #FFFFFF;
	border: 3rpx solid #A0C4FB;
	overflow: hidden;
}

.order-item {
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

.order-item:active {
	background-color: #F8FAFD;
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

.icon-member {
	background-color: #EFE9FB;
}

.icon-bean {
	background-color: #DEEAFB;
}

.item-icon-text {
	font-size: 22rpx;
	font-weight: 700;
	line-height: 1;
}

.icon-member .item-icon-text {
	color: #7C5CE0;
}

.icon-bean .item-icon-text {
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

.item-right {
	flex: none;
	display: flex;
	flex-direction: column;
	align-items: flex-end;
}

.item-amount {
	font-size: 30rpx;
	font-weight: 700;
	color: #0F172A;
}

/* 状态色:0待支付橙 / 1已支付绿 / 2已退款红 / 3已关闭灰 / 4待退款红 */
.item-status {
	margin-top: 4rpx;
	font-size: 22rpx;
	color: #94A3B8;
}

.status-0 {
	color: #F59E42;
}

.status-1 {
	color: #16A34A;
}

.status-2,
.status-4 {
	color: #EF4444;
}

.item-refund-tip {
	margin-top: 6rpx;
	font-size: 21rpx;
	line-height: 30rpx;
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

/* ====== 底部说明 ====== */
.notice {
	display: flex;
	justify-content: center;
	padding: 8rpx 0 40rpx;
}

.notice-text {
	font-size: 22rpx;
	color: #B3B7C2;
}
</style>
