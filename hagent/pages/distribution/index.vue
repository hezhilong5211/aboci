<template>
	<view class="page">
		<view class="nav" :style="{ paddingTop: statusBarHeight + 'px' }">
			<view class="nav-inner">
				<view class="nav-back" @tap="goBack">‹</view>
				<text class="nav-title">推广中心</text>
				<view class="nav-placeholder"></view>
			</view>
		</view>

		<scroll-view class="scroll" scroll-y>
			<view class="content" :style="{ paddingTop: (statusBarHeight + 70) + 'px' }">
				<view v-if="dashboard.status === 'DISABLED'" class="disabled-tip">当前推广已停用，历史数据和余额仍可查看与提现。</view>

				<view class="balance-card">
					<text class="balance-label">可提现佣金</text>
					<text class="balance-value">¥{{ money(dashboard.availableAmount) }}</text>
					<view class="withdraw-btn" @tap="goWithdraw">提现</view>
				</view>

				<view class="summary-card">
					<view v-for="item in summary" :key="item.label" class="summary-item">
						<text class="summary-value">¥{{ money(item.value) }}</text>
						<text class="summary-label">{{ item.label }}</text>
					</view>
				</view>

				<view class="section-card">
					<text class="section-title">推广数据</text>
					<view class="data-grid">
						<view v-for="item in metrics" :key="item.label" class="data-item">
							<text class="data-value">{{ item.money ? '¥' + money(item.value) : number(item.value) }}</text>
							<text class="data-label">{{ item.label }}</text>
						</view>
					</view>
				</view>

				<view class="section-card">
					<text class="section-title">常用功能</text>
					<view class="quick-grid">
						<view v-for="item in quickEntries" :key="item.title" class="quick-item" @tap="openPage(item.url)">
							<view class="quick-icon">{{ item.icon }}</view>
							<text class="quick-title">{{ item.title }}</text>
						</view>
					</view>
				</view>

				<view class="rules-link" @tap="openRules">推广规则</view>
			</view>
		</scroll-view>
	</view>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { getDistributionDashboard } from '@/api/distribution.js'

const statusBarHeight = ref(44)
const loading = ref(false)
const dashboard = reactive({
	status: 'ACTIVE', availableAmount: 0, totalCommissionAmount: 0,
	lockedAmount: 0, withdrawnAmount: 0, debtAmount: 0,
	visitCount: 0, promotedUserCount: 0, paidUserCount: 0,
	validOrderCount: 0, validOrderAmount: 0, minWithdrawAmount: 0,
})

const info = typeof uni.getWindowInfo === 'function' ? uni.getWindowInfo() : uni.getSystemInfoSync()
statusBarHeight.value = info.statusBarHeight || 44

const summary = computed(() => [
	{ label: '累计佣金', value: dashboard.totalCommissionAmount },
	{ label: '提现中', value: dashboard.lockedAmount },
	{ label: '已提现', value: dashboard.withdrawnAmount },
	{ label: '待抵扣', value: dashboard.debtAmount },
])
const metrics = computed(() => [
	{ label: '二维码访问', value: dashboard.visitCount },
	{ label: '有效用户', value: dashboard.promotedUserCount },
	{ label: '付费用户', value: dashboard.paidUserCount },
	{ label: '有效订单', value: dashboard.validOrderCount },
	{ label: '订单金额', value: dashboard.validOrderAmount, money: true },
])
const quickEntries = [
	{ icon: '▣', title: '我的二维码', url: '/pages/distribution/qrcode' },
	{ icon: '◎', title: '推广用户', url: '/pages/distribution/users' },
	{ icon: '¥', title: '佣金明细', url: '/pages/distribution/commissions' },
	{ icon: '⇄', title: '提现记录', url: '/pages/distribution/withdraw/index?tab=records' },
]

onShow(loadDashboard)

async function loadDashboard() {
	if (loading.value) return
	loading.value = true
	try {
		Object.assign(dashboard, await getDistributionDashboard())
	} finally {
		loading.value = false
	}
}

function money(value) {
	return ((Number(value) || 0) / 100).toFixed(2)
}
function number(value) {
	return (Number(value) || 0).toLocaleString('en-US')
}
function goBack() {
	uni.navigateBack()
}
function goWithdraw() {
	uni.navigateTo({ url: '/pages/distribution/withdraw/index' })
}
function openPage(url) {
	uni.navigateTo({ url })
}
function openRules() {
	uni.navigateTo({ url: '/pages/legal/detail?type=distribution' })
}
</script>

<style lang="scss" scoped>
.page { min-height: 100vh; background: #f5f6fa; color: #172033; }
.nav { position: fixed; z-index: 10; top: 0; left: 0; right: 0; background: rgba(255,255,255,.96); }
.nav-inner { height: 104rpx; display: flex; align-items: center; padding: 0 28rpx; }
.nav-back, .nav-placeholder { width: 100rpx; }
.nav-back { color: #4f84ea; }
.nav-back { font-size: 60rpx; line-height: 1; }
.nav-title { flex: 1; text-align: center; font-size: 34rpx; font-weight: 650; }
.scroll { height: 100vh; }
.content { padding-left: 28rpx; padding-right: 28rpx; padding-bottom: 60rpx; }
.disabled-tip { margin-bottom: 22rpx; padding: 22rpx 26rpx; border-radius: 18rpx; color: #8a5a12; background: #fff3d9; font-size: 24rpx; line-height: 1.55; }
.balance-card { position: relative; padding: 42rpx 34rpx; border-radius: 32rpx; color: #fff; background: #477dea url('/static/distribution/commission-card-bg.jpg') center / cover no-repeat; box-shadow: 0 18rpx 40rpx rgba(79,132,234,.24); }
.balance-label { display: block; font-size: 26rpx; opacity: .86; }
.balance-value { display: block; margin-top: 16rpx; font-size: 64rpx; font-weight: 700; }
.withdraw-btn { position: absolute; right: 32rpx; bottom: 42rpx; padding: 18rpx 34rpx; border-radius: 34rpx; background: #fff; color: #4f74d8; font-size: 28rpx; font-weight: 600; }
.summary-card, .section-card { margin-top: 24rpx; border-radius: 28rpx; background: #fff; }
.summary-card { display: grid; grid-template-columns: repeat(2, 1fr); padding: 12rpx; }
.summary-item { padding: 26rpx 16rpx; text-align: center; }
.summary-value, .summary-label { display: block; }
.summary-value { font-size: 30rpx; font-weight: 650; }
.summary-label, .data-label { margin-top: 10rpx; color: #8b93a5; font-size: 23rpx; }
.section-card { padding: 30rpx; }
.section-title { display: block; font-size: 30rpx; font-weight: 650; }
.data-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 14rpx; margin-top: 24rpx; }
.data-item { padding: 24rpx 8rpx; border-radius: 20rpx; text-align: center; background: #f7f8fc; }
.data-value, .data-label { display: block; }
.data-value { font-size: 28rpx; font-weight: 650; }
.quick-grid { display: grid; grid-template-columns: repeat(4, 1fr); margin-top: 28rpx; }
.quick-item { text-align: center; }
.quick-icon { width: 76rpx; height: 76rpx; margin: 0 auto; border-radius: 24rpx; display: flex; align-items: center; justify-content: center; color: #4f75d8; background: #eef3ff; font-size: 34rpx; font-weight: 650; }
.quick-title { display: block; margin-top: 16rpx; color: #525b6d; font-size: 23rpx; }
.rules-link { padding: 36rpx 0 10rpx; text-align: center; color: #7d879b; font-size: 24rpx; }
</style>
