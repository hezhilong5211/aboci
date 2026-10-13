<template>
	<view class="page">
		<view class="nav" :style="{ paddingTop: statusBarHeight + 'px' }">
			<view class="nav-inner">
				<view class="nav-back" @tap="goBack">‹</view>
				<text class="nav-title">佣金明细</text>
				<view class="nav-space" />
			</view>
		</view>
		<scroll-view class="scroll" scroll-y @scrolltolower="loadMore">
			<view class="content" :style="{ paddingTop: (statusBarHeight + 58) + 'px' }">
				<view v-for="item in records" :key="item.commissionId" class="commission-card">
					<view class="card-head">
						<view class="product">
							<text class="product-name">{{ item.productName || productType(item.productType) }}</text>
							<text class="order-no">{{ item.orderNo }}</text>
						</view>
						<text class="status" :class="statusClass(item.status)">{{ statusText(item.status) }}</text>
					</view>
					<view class="amount-row">
						<view><text>¥{{ money(item.orderAmount) }}</text><text>订单实付</text></view>
						<view><text>{{ rate(item.rateBps) }}</text><text>佣金比例</text></view>
						<view><text class="commission-amount">¥{{ money(item.commissionAmount) }}</text><text>佣金</text></view>
					</view>
					<view v-if="Number(item.reversedAmount) > 0 || item.refundNo" class="refund-tip">
						退款已冲正 ¥{{ money(item.reversedAmount) }}{{ Number(item.debtOffsetAmount) > 0 ? '，其中待抵扣 ¥' + money(item.debtOffsetAmount) : '' }}
					</view>
					<text class="pay-time">支付时间：{{ formatTime(item.payTime) }}</text>
				</view>
				<view v-if="!loading && records.length === 0" class="empty">暂无佣金明细</view>
				<view v-else class="footer">{{ loading ? '加载中...' : (hasMore ? '上拉加载更多' : '没有更多了') }}</view>
			</view>
		</scroll-view>
	</view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getDistributionCommissions } from '@/api/distribution.js'

const statusBarHeight = ref(44)
const records = ref([])
const pageNum = ref(1)
const pageSize = 10
const total = ref(0)
const loading = ref(false)
const hasMore = computed(() => records.value.length < total.value)
const info = typeof uni.getWindowInfo === 'function' ? uni.getWindowInfo() : uni.getSystemInfoSync()
statusBarHeight.value = info.statusBarHeight || 44

onLoad(loadMore)

async function loadMore() {
	if (loading.value || (pageNum.value > 1 && !hasMore.value)) return
	loading.value = true
	try {
		const result = await getDistributionCommissions({ pageNum: pageNum.value, pageSize })
		records.value.push(...(result?.records || []))
		total.value = Number(result?.total) || 0
		pageNum.value += 1
	} finally {
		loading.value = false
	}
}
function money(value) { return ((Number(value) || 0) / 100).toFixed(2) }
function rate(value) { return `${((Number(value) || 0) / 100).toFixed(2)}%` }
function formatTime(value) { return value ? String(value).replace('T', ' ').slice(0, 16) : '--' }
function productType(value) { return value === 'MEMBERSHIP' ? '会员' : (value === 'BEAN_RECHARGE' ? '加油包' : '商品') }
function statusText(value) {
	return { AVAILABLE: '可提现', PARTIAL: '部分可提现', LOCKED: '提现中', WITHDRAWN: '已提现', REVERSED: '已冲正' }[value] || value || '--'
}
function statusClass(value) { return value === 'REVERSED' ? 'danger' : (value === 'WITHDRAWN' ? 'muted' : 'active') }
function goBack() { uni.navigateBack() }
</script>

<style lang="scss" scoped>
.page { min-height: 100vh; background: #f5f6fa; color: #172033; }
.nav { position: fixed; z-index: 10; top: 0; left: 0; right: 0; background: #fff; }
.nav-inner { height: 104rpx; padding: 0 28rpx; display: flex; align-items: center; }
.nav-back, .nav-space { width: 100rpx; }
.nav-back { color: #4f84ea; font-size: 60rpx; line-height: 1; }
.nav-title { flex: 1; text-align: center; font-size: 34rpx; font-weight: 650; }
.scroll { height: 100vh; }
.content { padding-left: 28rpx; padding-right: 28rpx; padding-bottom: 50rpx; }
.commission-card { margin-bottom: 20rpx; padding: 30rpx; border-radius: 26rpx; background: #fff; }
.card-head { display: flex; align-items: flex-start; justify-content: space-between; }
.product { min-width: 0; }
.product-name, .order-no { display: block; }
.product-name { font-size: 29rpx; font-weight: 650; }
.order-no { margin-top: 8rpx; color: #969dab; font-size: 21rpx; }
.status { padding: 8rpx 16rpx; border-radius: 18rpx; font-size: 21rpx; }
.active { color: #2677d8; background: #eaf3ff; }
.danger { color: #ca4d4d; background: #fff0f0; }
.muted { color: #727b8e; background: #f0f2f5; }
.amount-row { display: grid; grid-template-columns: repeat(3, 1fr); margin-top: 28rpx; padding: 24rpx 0; border-top: 2rpx solid #f0f2f6; border-bottom: 2rpx solid #f0f2f6; }
.amount-row view { text-align: center; }
.amount-row text { display: block; font-size: 27rpx; font-weight: 600; }
.amount-row text:last-child { margin-top: 8rpx; color: #9299a8; font-size: 21rpx; font-weight: 400; }
.commission-amount { color: #e08b32; }
.refund-tip { margin-top: 20rpx; padding: 16rpx 20rpx; border-radius: 16rpx; background: #fff2f2; color: #c95757; font-size: 22rpx; }
.pay-time { display: block; margin-top: 18rpx; color: #969dab; font-size: 21rpx; }
.empty, .footer { padding: 100rpx 0; text-align: center; color: #9aa1af; font-size: 25rpx; }
.footer { padding: 28rpx 0; }
</style>
